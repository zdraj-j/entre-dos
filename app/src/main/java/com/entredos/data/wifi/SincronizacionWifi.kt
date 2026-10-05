package com.entredos.data.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.entredos.core.sync.RelojHlc
import com.entredos.core.wifi.Cifrado
import com.entredos.core.wifi.SesionWifi
import com.entredos.data.local.AjustesLocales
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.sync.RoomBaseLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class EstadoWifi(
    val activo: Boolean = false,
    /** Otro teléfono del hogar visible en la red ahora mismo. */
    val parejaVisible: Boolean = false,
    val ultimoIntercambio: Long? = null,
    val ultimoResumen: String? = null,
    val error: String? = null,
)

/**
 * Sincronización por wifi en casa (entrega 6). Solo mientras la app está en primer plano y el
 * interruptor está activo: anuncia este teléfono con NSD, encuentra al otro del mismo hogar,
 * y se pasan lo que le falta a cada uno con [SesionWifi]. Es un complemento: Drive sigue siendo
 * la copia que permite recuperar todo al reinstalar.
 *
 * Para no conectarse los dos a la vez, inicia la conexión el teléfono cuyo nombre de servicio
 * es menor; el otro solo acepta.
 */
@Singleton
class SincronizacionWifi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: EntreDosDatabase,
    private val base: RoomBaseLocal,
    private val reloj: RelojHlc,
    private val ajustes: AjustesLocales,
    private val dispositivo: DispositivoActual,
) {
    private val _estado = MutableStateFlow(EstadoWifi())
    val estado: StateFlow<EstadoWifi> = _estado.asStateFlow()

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trabajo: Job? = null
    private var enPrimerPlano = false

    fun iniciar() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { enPrimerPlano = true; evaluar() }
            override fun onStop(owner: LifecycleOwner) { enPrimerPlano = false; evaluar() }
        })
        enPrimerPlano = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        alcance.launch {
            ajustes.ajustes.map { it.wifiActivo }.distinctUntilChanged().collect { withContext(Dispatchers.Main) { evaluar() } }
        }
    }

    /** "Sincronizar ahora" desde Ajustes. */
    fun intercambiarAhora() { pedidoManual.value++ }

    private val pedidoManual = MutableStateFlow(0)

    @Synchronized
    private fun evaluar() {
        val debe = enPrimerPlano && ajustes.actual.wifiActivo
        if (debe && trabajo?.isActive != true) {
            trabajo = alcance.launch {
                try { correr() } catch (e: Exception) { _estado.update { it.copy(error = e.message) } }
                finally { _estado.update { it.copy(activo = false, parejaVisible = false) } }
            }
        } else if (!debe) {
            trabajo?.cancel(); trabajo = null
        }
    }

    @OptIn(FlowPreview::class)
    @Suppress("DEPRECATION") // NsdServiceInfo.host: su reemplazo solo existe desde Android 14
    private suspend fun correr() = coroutineScope {
        val hogar = db.hogarDao().actual() ?: return@coroutineScope
        if (dispositivo.miembro() == null) return@coroutineScope
        val secreto = hogar.id.toByteArray()
        val huella = Cifrado.huellaHogar(hogar.id)
        val miNombre = "EntreDos-" + dispositivo.id().take(8)
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val candadoMulticast = wifi.createMulticastLock("entredos").apply { setReferenceCounted(false); acquire() }
        val servidor = ServerSocket(0)
        val pares = ConcurrentHashMap<String, InetSocketAddress>()
        _estado.update { it.copy(activo = true, error = null) }

        val registro = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, codigo: Int) { _estado.update { it.copy(error = "No se pudo anunciar en la red ($codigo)") } }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, codigo: Int) = Unit
        }
        nsd.registerService(NsdServiceInfo().apply {
            serviceName = miNombre; serviceType = SesionWifi.TIPO_SERVICIO; port = servidor.localPort; setAttribute("h", huella)
        }, NsdManager.PROTOCOL_DNS_SD, registro)

        // Sin candado entre sesiones: si coinciden dos, cada una aplica con LWW y no se duplica nada.
        suspend fun sesion(socket: Socket, comoServidor: Boolean) {
            try {
                socket.use {
                    it.soTimeout = 20_000
                    val s = SesionWifi(base, reloj, secreto)
                    val r = if (comoServidor) s.comoServidor(it.getInputStream(), it.getOutputStream()) else s.comoCliente(it.getInputStream(), it.getOutputStream())
                    _estado.update { e -> e.copy(ultimoIntercambio = System.currentTimeMillis(), ultimoResumen = "Enviados ${r.enviados} · recibidos ${r.recibidos}", error = null) }
                }
            } catch (e: Exception) {
                _estado.update { it.copy(error = "Intercambio fallido: ${e.message}") }
            }
        }

        /** [todos] = también a los pares que normalmente inician ellos (pedido manual). */
        suspend fun conectarATodos(todos: Boolean = false) {
            pares.filterKeys { todos || miNombre < it }.values.forEach { dir ->
                runCatching { Socket().apply { connect(dir, 5_000) } }.onSuccess { sesion(it, comoServidor = false) }
                    .onFailure { _estado.update { e -> e.copy(error = "No se pudo conectar: ${it.message}") } }
            }
        }

        try {
            // Aceptar conexiones del otro teléfono.
            launch {
                while (isActive) {
                    val s = runCatching { servidor.accept() }.getOrNull() ?: break
                    launch { sesion(s, comoServidor = true) }
                }
            }
            // Encontrar al otro teléfono del hogar.
            launch {
                descubrir(nsd).collect { info ->
                    if (info.serviceName == miNombre || !info.serviceName.startsWith("EntreDos-")) return@collect
                    val resuelto = resolver(nsd, info) ?: return@collect
                    val suHuella = resuelto.attributes["h"]?.let { String(it) }
                    if (suHuella != null && suHuella != huella) return@collect // otro hogar en la misma red
                    _estado.update { it.copy(parejaVisible = true) }
                    pares[resuelto.serviceName] = InetSocketAddress(resuelto.host, resuelto.port)
                    conectarATodos()
                }
            }
            // Cambios nuevos en este teléfono: intercambiar en unos segundos.
            launch {
                db.cambioPendienteDao().contar().distinctUntilChanged().filter { pares.isNotEmpty() }.debounce(3_000).collect { conectarATodos() }
            }
            // Pedido manual desde Ajustes: desde cualquiera de los dos teléfonos.
            launch { pedidoManual.filter { it > 0 }.collect { conectarATodos(todos = true) } }
            // Y cada dos minutos mientras se vean.
            while (isActive) { delay(120_000); if (pares.isNotEmpty()) conectarATodos() }
        } finally {
            runCatching { nsd.unregisterService(registro) }
            runCatching { servidor.close() }
            runCatching { candadoMulticast.release() }
        }
    }

    private fun descubrir(nsd: NsdManager) = callbackFlow {
        val oyente = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(tipo: String) = Unit
            override fun onDiscoveryStopped(tipo: String) = Unit
            override fun onStartDiscoveryFailed(tipo: String, codigo: Int) { close(IllegalStateException("No se pudo buscar en la red ($codigo)")) }
            override fun onStopDiscoveryFailed(tipo: String, codigo: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) { _estado.update { it.copy(parejaVisible = false) } }
        }
        nsd.discoverServices(SesionWifi.TIPO_SERVICIO, NsdManager.PROTOCOL_DNS_SD, oyente)
        awaitClose { runCatching { nsd.stopServiceDiscovery(oyente) } }
    }

    private val resolviendo = Mutex()

    /** En Android anterior a 14 solo se puede resolver un servicio a la vez. */
    @Suppress("DEPRECATION")
    private suspend fun resolver(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? = resolviendo.withLock {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, codigo: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(i: NsdServiceInfo) { if (cont.isActive) cont.resume(i.takeIf { it.host != null }) }
            })
        }
    }
}

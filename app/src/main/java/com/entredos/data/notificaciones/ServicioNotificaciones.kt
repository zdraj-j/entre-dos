package com.entredos.data.notificaciones

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.entredos.core.LectoresBancos
import com.entredos.core.ResultadoLector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lee las notificaciones del teléfono (requiere el acceso a notificaciones) y convierte en gasto
 * las de los bancos. El texto original nunca se guarda ni se sube: solo el gasto ya armado.
 */
@AndroidEntryPoint
class ServicioNotificaciones : NotificationListenerService() {

    @Inject lateinit var ingreso: IngresoNotificacion
    @Inject lateinit var receptorGenerico: ReceptorGenerico
    @Inject lateinit var avisos: AvisosGasto
    @Inject lateinit var diagnostico: RegistroDiagnostico

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val paquete = sbn.packageName
        if (paquete == packageName || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val corto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val largo = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val texto = listOfNotNull(corto, largo?.takeIf { it != corto }).joinToString(". ").ifBlank { null }

        alcance.launch {
            val (resultado, nota) = when (val r = LectoresBancos.leer(paquete, titulo, texto)) {
                is ResultadoLector.Gasto -> ingreso.registrar(r.lectura.monto, r.lectura.comercio, paquete, r.banco.nombre, sbn.postTime) to "Gasto ${r.banco.nombre}: ${r.lectura.monto}"
                ResultadoLector.Descartada -> null to "Banco: no es gasto"
                ResultadoLector.NoEsBanco -> receptorGenerico.alRecibir(paquete, titulo, texto, sbn.postTime).let { it to if (it != null) "Lector genérico" else "Ignorada" }
            }
            diagnostico.anotar(NotificacionCapturada(sbn.postTime, paquete, titulo, texto, if (resultado is ResultadoIngreso.Duplicado) "Duplicado" else nota))
            avisos.mostrar(resultado)
        }
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}

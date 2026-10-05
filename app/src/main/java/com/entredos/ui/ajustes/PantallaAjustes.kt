package com.entredos.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.BuildConfig
import com.entredos.core.ModoPeriodo
import com.entredos.data.local.Ajustes
import com.entredos.data.local.AjustesLocales
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.HogarEntity
import com.entredos.data.repository.ExportacionRepositorio
import com.entredos.data.repository.HogarRepositorio
import com.entredos.data.sync.CambiosSinSubir
import com.entredos.data.sync.Estado
import com.entredos.data.sync.EstadoSync
import com.entredos.data.sync.ServicioHogar
import com.entredos.data.sync.Sincronizador
import com.entredos.data.wifi.EstadoWifi
import com.entredos.data.wifi.SincronizacionWifi
import com.entredos.ui.comun.EncabezadoVolver
import com.entredos.ui.comun.FilaAjuste
import com.entredos.ui.comun.GrupoAjustes
import com.entredos.ui.comun.recordarGuardado
import com.entredos.ui.permisos.Permisos
import com.entredos.ui.permisos.recordarPermisos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class EstadoAjustes(
    val hogar: HogarEntity? = null,
    val locales: Ajustes = Ajustes(),
    val sync: Estado = Estado(),
    val sinSubir: Int = 0,
    val reglas: Int = 0,
    val wifi: EstadoWifi = EstadoWifi(),
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    db: EntreDosDatabase,
    private val ajustesLocales: AjustesLocales,
    estadoSync: EstadoSync,
    wifi: SincronizacionWifi,
    private val hogar: HogarRepositorio,
    private val exportacion: ExportacionRepositorio,
    private val sincronizador: Sincronizador,
    private val servicioHogar: ServicioHogar,
) : ViewModel() {

    val estado = combine(
        combine(db.hogarDao().observar(), ajustesLocales.ajustes, estadoSync.estado) { h, a, s -> Triple(h, a, s) },
        db.cambioPendienteDao().contar(), db.reglaComercioDao().observar(), wifi.estado,
    ) { (h, a, s), sinSubir, reglas, w -> EstadoAjustes(h, a, s, sinSubir, reglas.size, w) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoAjustes())

    val mensaje = MutableStateFlow<String?>(null)
    /** Cambios que se perderían al salir; null si no hay aviso. */
    val avisoSalir = MutableStateFlow<Int?>(null)

    fun guardarMes(dia: Int, modo: ModoPeriodo) = hacer { hogar.guardar(estado.value.hogar?.nombre ?: "", dia, modo) }
    fun renombrarHogar(nombre: String) = hacer { estado.value.hogar?.let { hogar.guardar(nombre, it.diaInicio, it.modoPeriodo) } }
    fun clasificacionManual(activo: Boolean) = ajustesLocales.clasificacionManual(activo)
    fun wifi(activo: Boolean) = ajustesLocales.wifi(activo)
    fun sincronizarAhora() = hacer { sincronizador.sincronizar() }
    suspend fun respaldo() = exportacion.respaldo()

    fun salir(forzar: Boolean) = hacer {
        try { servicioHogar.salir(forzar); avisoSalir.value = null } catch (e: CambiosSinSubir) { avisoSalir.value = e.cuantos }
    }

    private fun hacer(accion: suspend () -> Unit) {
        viewModelScope.launch { runCatching { accion() }.onFailure { mensaje.value = it.message } }
    }
}

/** Hogar › Ajustes. */
@Composable
fun PantallaAjustes(
    alVolver: () -> Unit, alReglas: () -> Unit, alOtrasApps: () -> Unit, alImportar: () -> Unit, alDiagnostico: () -> Unit, alReconectar: () -> Unit,
    vm: AjustesViewModel = hiltViewModel(),
) {
    val e by vm.estado.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val aviso by vm.avisoSalir.collectAsState()
    val permisos = recordarPermisos()
    val contexto = LocalContext.current
    val guardarZip = recordarGuardado("application/zip")
    var editandoMes by remember { mutableStateOf(false) }
    var editandoNombre by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }
    val todoActivo = permisos.notificaciones && permisos.bateria && permisos.avisos

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoVolver("Ajustes", alVolver = alVolver)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            GrupoAjustes("El mes", listOf(
                { FilaAjuste("Nombre del hogar", valor = e.hogar?.nombre ?: "", alTocar = { editandoNombre = true }) },
                { FilaAjuste("Día en que empieza", valor = "${e.hogar?.diaInicio ?: 1}", alTocar = { editandoMes = true }) },
                {
                    FilaAjuste(
                        "Cierre del mes", valor = if (e.hogar?.modoPeriodo == ModoPeriodo.AUTOMATICO) "Automático" else "Manual",
                        ayuda = if (e.hogar?.modoPeriodo == ModoPeriodo.AUTOMATICO) "Se archiva solo el día de inicio" else "Lo cierran ustedes desde Mes",
                        alTocar = { editandoMes = true },
                    )
                },
            ))

            GrupoAjustes("Gastos detectados", listOf(
                {
                    FilaAjuste(
                        "Elegir siempre la categoría", ayuda = "Si está apagado, la app aprende y clasifica sola",
                        control = { Switch(e.locales.clasificacionManual, vm::clasificacionManual) },
                    )
                },
                { FilaAjuste("Reglas aprendidas", valor = "${e.reglas}", alTocar = alReglas) },
                { FilaAjuste("Leer otras apps", valor = if (e.locales.lectorGenericoActivo) "${e.locales.appsGenericas.size} apps" else "Apagado", alTocar = alOtrasApps) },
            ))

            GrupoAjustes("Sincronización", listOf(
                {
                    FilaAjuste(
                        "Cuenta del hogar", ayuda = if (e.sync.requiereInicio) "El permiso venció: toca para volver a conectar" else e.sync.correo,
                        alTocar = alReconectar,
                    )
                },
                {
                    FilaAjuste(
                        "Estado",
                        ayuda = e.sync.ultimoError ?: e.sync.ultimaSyncOk?.let { "Última vez: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
                        valor = when { e.sync.ultimoError != null -> "Con error"; e.sinSubir > 0 -> "${e.sinSubir} sin subir"; else -> "Al día" },
                        alTocar = vm::sincronizarAhora,
                    )
                },
                {
                    FilaAjuste(
                        "Wifi en casa",
                        ayuda = when {
                            !e.locales.wifiActivo -> "Se pasan los cambios sin internet, con la app abierta"
                            e.wifi.parejaVisible -> "El otro teléfono está en la red"
                            e.wifi.error != null -> e.wifi.error
                            else -> "Buscando el otro teléfono…"
                        },
                        control = { Switch(e.locales.wifiActivo, vm::wifi) },
                    )
                },
            ))

            GrupoAjustes("Datos", listOf(
                { FilaAjuste("Importar", ayuda = "Desde otra app o un respaldo", alTocar = alImportar) },
                { FilaAjuste("Exportar respaldo", ayuda = "Un .zip que también se puede importar", alTocar = { guardarZip("respaldo-entre-dos.zip") { vm.respaldo().bytes } }) },
                { FilaAjuste("Permisos", valor = if (todoActivo) "Todo activo" else "Falta alguno", alTocar = { if (!permisos.notificaciones) Permisos.abrirAccesoNotificaciones(contexto) else if (!permisos.bateria) Permisos.pedirSinOptimizacion(contexto) }) },
                { FilaAjuste("Diagnóstico", alTocar = alDiagnostico) },
            ))

            TextButton(onClick = { confirmarSalir = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Salir del hogar", color = MaterialTheme.colorScheme.error)
            }
            Text("Entre dos ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (editandoMes && e.hogar != null) {
        var dia by remember { mutableStateOf(e.hogar!!.diaInicio.toString()) }
        var modo by remember { mutableStateOf(e.hogar!!.modoPeriodo) }
        val diaValido = dia.toIntOrNull()?.let { it in 1..28 } == true
        AlertDialog(
            onDismissRequest = { editandoMes = false },
            title = { Text("El mes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(2) }, label = { Text("Día en que empieza (1 a 28)") }, isError = !diaValido, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(modo == ModoPeriodo.MANUAL, { modo = ModoPeriodo.MANUAL }, { Text("Manual") })
                        FilterChip(modo == ModoPeriodo.AUTOMATICO, { modo = ModoPeriodo.AUTOMATICO }, { Text("Automático") })
                    }
                    Text(
                        if (modo == ModoPeriodo.MANUAL) "El mes termina cuando alguno de los dos toca \"Cerrar el mes\"."
                        else "El mes se archiva solo el día de inicio; las deudas siguen abiertas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.guardarMes(dia.toInt(), modo); editandoMes = false }, enabled = diaValido) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editandoMes = false }) { Text("Cancelar") } },
        )
    }
    if (editandoNombre && e.hogar != null) {
        var nombre by remember { mutableStateOf(e.hogar!!.nombre) }
        AlertDialog(
            onDismissRequest = { editandoNombre = false },
            title = { Text("Nombre del hogar") },
            text = { OutlinedTextField(nombre, { nombre = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renombrarHogar(nombre); editandoNombre = false }, enabled = nombre.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editandoNombre = false }) { Text("Cancelar") } },
        )
    }
    if (confirmarSalir) AlertDialog(
        onDismissRequest = { confirmarSalir = false },
        title = { Text("¿Salir del hogar?") },
        text = { Text("Se borra todo de este teléfono. Los datos siguen en la cuenta del hogar: para volver, conectas la cuenta y tocas \"Soy …\".") },
        confirmButton = { TextButton(onClick = { vm.salir(forzar = false); confirmarSalir = false }) { Text("Salir", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmarSalir = false }) { Text("Cancelar") } },
    )
    aviso?.let { n ->
        AlertDialog(
            onDismissRequest = { vm.avisoSalir.value = null },
            title = { Text("Hay cambios sin subir") },
            text = { Text("$n cambios aún no llegan a la cuenta del hogar. Si sales ahora se pierden. Conéctate a internet y espera a que diga \"Al día\".") },
            confirmButton = { TextButton(onClick = { vm.salir(forzar = true) }) { Text("Salir igual", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { vm.avisoSalir.value = null }) { Text("Cancelar") } },
        )
    }
}

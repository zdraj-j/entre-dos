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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Miembro
import com.entredos.core.ModoPeriodo
import com.entredos.data.importacion.nombreTitular
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.repository.ExportacionRepositorio
import com.entredos.data.repository.HogarRepositorio
import com.entredos.ui.comun.Dato
import com.entredos.ui.comun.Seccion
import com.entredos.ui.comun.recordarGuardado
import com.entredos.ui.hogar.SeccionSincronizacion
import com.entredos.ui.permisos.Permisos
import com.entredos.ui.permisos.recordarPermisos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AjustesViewModel @Inject constructor(
    db: EntreDosDatabase,
    private val hogar: HogarRepositorio,
    private val exportacion: ExportacionRepositorio,
) : ViewModel() {
    val estado = combine(db.hogarDao().observar(), db.miembroDao().observar(), db.dispositivoDao().observar()) { h, m, d -> Triple(h, m, d?.miembro) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(null, emptyList(), null))
    val mensaje = MutableStateFlow<String?>(null)

    fun guardarHogar(nombre: String, dia: Int, modo: ModoPeriodo) = viewModelScope.launch {
        runCatching { hogar.guardar(nombre, dia, modo) }.onFailure { mensaje.value = it.message }
    }
    fun renombrar(m: Miembro, nombre: String) = viewModelScope.launch { runCatching { hogar.renombrar(m, nombre) }.onFailure { mensaje.value = it.message } }
    suspend fun respaldo() = exportacion.respaldo()
}

@Composable
fun PantallaAjustes(
    alImportaciones: () -> Unit, alDeteccion: () -> Unit, alDiagnostico: () -> Unit,
    alReconectar: () -> Unit, alSalir: () -> Unit,
    vm: AjustesViewModel = hiltViewModel(),
) {
    val (hogar, miembros, yo) = vm.estado.collectAsState().value
    val mensaje by vm.mensaje.collectAsState()
    val permisos = recordarPermisos()
    val c = LocalContext.current
    val guardarZip = recordarGuardado("application/zip")
    var editandoHogar by remember { mutableStateOf(false) }
    var renombrando by remember { mutableStateOf<Miembro?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Seccion("Hogar") {
            Dato("Nombre", hogar?.nombre ?: "—", alTocar = { editandoHogar = true })
            Miembro.entries.forEach { m ->
                val etiqueta = if (m == yo) "Tú" else "Tu pareja"
                val existe = miembros.any { it.id == m.name }
                Dato(etiqueta, if (existe) nombreTitular(miembros, m) else "Aún no se ha unido", alTocar = if (existe) ({ renombrando = m }) else null)
            }
            Dato("Día de inicio del periodo", "${hogar?.diaInicio ?: "—"}", alTocar = { editandoHogar = true })
            Dato("Modo del periodo", if (hogar?.modoPeriodo == ModoPeriodo.AUTOMATICO) "Automático" else "Manual", alTocar = { editandoHogar = true })
            Text("Para que tu pareja se una, conecta en su teléfono la misma cuenta de Google del hogar.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Seccion("Clasificación de gastos") { SeccionClasificacion() }
        Dato("Detección y reglas", "", alTocar = alDeteccion)
        HorizontalDivider()
        Seccion("Deudas") {
            Text("Categoría de una persona pagada por la otra: el dueño debe todo. Categoría compartida: quien no pagó debe la mitad, o lo que se haya ajustado. Los gastos importados no generan deuda.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Seccion("Permisos") {
            Dato("Acceso a notificaciones", if (permisos.notificaciones) "Activo" else "Falta", alTocar = { Permisos.abrirAccesoNotificaciones(c) })
            Dato("Sin optimización de batería", if (permisos.bateria) "Activo" else "Falta", alTocar = { Permisos.pedirSinOptimizacion(c) })
            Dato("Avisos de la app", if (permisos.avisos) "Activo" else "Falta")
        }
        HorizontalDivider()
        Column(Modifier.padding(vertical = 8.dp)) { SeccionSincronizacion(alReconectar = alReconectar, alSalir = alSalir) }
        HorizontalDivider()
        Seccion("Datos") {
            Dato("Importar y deshacer importaciones", "", alTocar = alImportaciones)
            Dato("Exportar respaldo (.zip)", "", alTocar = { guardarZip("respaldo-entre-dos.zip") { vm.respaldo().bytes } })
            Text("El respaldo usa el mismo formato del importador: sirve para guardar una copia o pasar los datos a otro hogar.", style = MaterialTheme.typography.bodySmall)
            Dato("Diagnóstico", "", alTocar = alDiagnostico)
        }
    }

    if (editandoHogar && hogar != null) {
        var nombre by remember { mutableStateOf(hogar.nombre) }
        var dia by remember { mutableStateOf(hogar.diaInicio.toString()) }
        var modo by remember { mutableStateOf(hogar.modoPeriodo) }
        val diaValido = dia.toIntOrNull()?.let { it in 1..28 } == true
        AlertDialog(
            onDismissRequest = { editandoHogar = false },
            title = { Text("Hogar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nombre, { nombre = it.take(40) }, label = { Text("Nombre del hogar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(2) }, label = { Text("Día de inicio (1 a 28)") }, isError = !diaValido, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(modo == ModoPeriodo.MANUAL, { modo = ModoPeriodo.MANUAL }, { Text("Manual") })
                        FilterChip(modo == ModoPeriodo.AUTOMATICO, { modo = ModoPeriodo.AUTOMATICO }, { Text("Automático") })
                    }
                    Text(
                        if (modo == ModoPeriodo.MANUAL) "El periodo termina solo cuando alguno toca \"Cerrar y archivar\"."
                        else "El periodo se archiva solo el día de inicio de cada mes; las deudas siguen abiertas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.guardarHogar(nombre, dia.toInt(), modo); editandoHogar = false }, enabled = diaValido) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editandoHogar = false }) { Text("Cancelar") } },
        )
    }
    renombrando?.let { m ->
        var nombre by remember { mutableStateOf(nombreTitular(miembros, m)) }
        AlertDialog(
            onDismissRequest = { renombrando = null },
            title = { Text("Cambiar nombre") },
            text = { OutlinedTextField(nombre, { nombre = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.renombrar(m, nombre); renombrando = null }, enabled = nombre.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renombrando = null }) { Text("Cancelar") } },
        )
    }
}

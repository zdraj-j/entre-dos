package com.entredos.ui.deteccion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.ReglaComercioEntity
import com.entredos.data.notificaciones.RegistroDiagnostico
import com.entredos.data.repository.ReglasRepositorio
import com.entredos.data.sync.EstadoSync
import com.entredos.ui.comun.Barra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DeteccionViewModel @Inject constructor(db: EntreDosDatabase, private val repo: ReglasRepositorio) : ViewModel() {
    val reglas = db.reglaComercioDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categorias = db.categoriaDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val error = MutableStateFlow<String?>(null)

    private fun hacer(listo: () -> Unit = {}, accion: suspend () -> Unit) = viewModelScope.launch {
        runCatching { accion() }.onSuccess { error.value = null; listo() }.onFailure { error.value = it.message }
    }

    fun automatica(r: ReglaComercioEntity, activa: Boolean) = hacer { repo.automatica(r, activa) }
    fun eliminar(r: ReglaComercioEntity) = hacer { repo.eliminar(r) }
    fun crear(palabra: String, cat: String, auto: Boolean, listo: () -> Unit) = hacer(listo) { repo.crear(palabra, cat, auto) }
}

/** Detección y reglas (sección 11): con 3 confirmaciones en la misma categoría se activa sola. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaDeteccion(alVolver: () -> Unit, alLectorGenerico: () -> Unit, vm: DeteccionViewModel = hiltViewModel()) {
    val reglas by vm.reglas.collectAsState()
    val categorias by vm.categorias.collectAsState()
    val error by vm.error.collectAsState()
    var creando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Barra("Reglas aprendidas", alVolver)
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = { creando = true }) { Text("Nueva regla") }
                    OutlinedButton(onClick = alLectorGenerico) { Text("Leer otras apps") }
                }
                Text("La app aprende de lo que confirmas: tres veces el mismo comercio en la misma categoría y lo clasifica sola.", style = MaterialTheme.typography.bodySmall)
            }
            if (reglas.isEmpty()) item { Text("Aún no hay reglas.", Modifier.padding(vertical = 12.dp)) }
            items(reglas, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.palabra, fontWeight = FontWeight.SemiBold)
                        Text("→ ${categorias.firstOrNull { it.id == r.categoriaId }?.nombre ?: "Categoría eliminada"} · ${r.confirmaciones} confirmaciones", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(r.automatica, { vm.automatica(r, it) })
                    TextButton(onClick = { vm.eliminar(r) }) { Text("Borrar") }
                }
                HorizontalDivider()
            }
        }
    }

    if (creando) {
        var palabra by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf(categorias.firstOrNull()?.id) }
        var auto by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { creando = false },
            title = { Text("Nueva regla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(palabra, { palabra = it.take(40) }, label = { Text("Palabra del comercio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { categorias.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, { Text(c.nombre) }) } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Clasificar sola", Modifier.weight(1f)); Switch(auto, { auto = it }) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.crear(palabra, cat!!, auto) { creando = false } }, enabled = cat != null && palabra.isNotBlank()) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { creando = false }) { Text("Cancelar") } },
        )
    }
}

@HiltViewModel
class DiagnosticoViewModel @Inject constructor(private val registro: RegistroDiagnostico, estado: EstadoSync) : ViewModel() {
    val capturadas = registro.capturadas
    val sync = estado.estado
    fun limpiar() = registro.limpiar()
}

/** Ajustes › Diagnóstico: lo que vio el teléfono (solo en memoria) y el estado de la sincronización. */
@Composable
fun PantallaDiagnostico(alVolver: () -> Unit, vm: DiagnosticoViewModel = hiltViewModel()) {
    val capturadas by vm.capturadas.collectAsState()
    val sync by vm.sync.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Barra("Diagnóstico", alVolver)
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Text("Sincronización", style = MaterialTheme.typography.titleMedium)
                Text("Último error: ${sync.ultimoError ?: "ninguno"}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Notificaciones capturadas (${capturadas.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::limpiar) { Text("Limpiar") }
                }
                Text("Se guardan solo en memoria hasta cerrar la app y nunca se suben. Sirven para ver qué manda cada banco.", style = MaterialTheme.typography.bodySmall)
            }
            items(capturadas) { n ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(n.cuando))} · ${n.paquete}", style = MaterialTheme.typography.labelMedium)
                    Text(listOfNotNull(n.titulo, n.texto).joinToString(" — "), style = MaterialTheme.typography.bodySmall)
                    Text(n.resultado, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
        }
    }
}

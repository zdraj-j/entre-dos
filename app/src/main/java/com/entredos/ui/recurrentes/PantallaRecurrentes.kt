package com.entredos.ui.recurrentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.FormatoPesos
import com.entredos.core.Frecuencia
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.RecurrenteEntity
import com.entredos.data.repository.RecurrentesRepositorio
import com.entredos.ui.comun.Barra
import com.entredos.ui.comun.CampoMonto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecurrentesViewModel @Inject constructor(db: EntreDosDatabase, private val repo: RecurrentesRepositorio) : ViewModel() {
    val recurrentes = db.recurrenteDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categorias = db.categoriaDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mensaje = MutableStateFlow<String?>(null)

    private fun hacer(listo: () -> Unit = {}, accion: suspend () -> Unit) = viewModelScope.launch {
        runCatching { accion() }.onSuccess { listo() }.onFailure { mensaje.value = it.message }
    }

    fun guardar(r: RecurrenteEntity?, nombre: String, cat: String, monto: Long, f: Frecuencia, palabra: String, listo: () -> Unit) =
        hacer(listo) { repo.guardar(r, nombre, cat, monto, f, palabra); mensaje.value = null }
    fun registrar(r: RecurrenteEntity) = hacer { repo.registrarHoy(r); mensaje.value = "Registrado ${r.nombre} como gasto de hoy" }
    fun eliminar(r: RecurrenteEntity, listo: () -> Unit) = hacer(listo) { repo.eliminar(r) }
}

private fun textoFrecuencia(f: Frecuencia) = when (f) {
    Frecuencia.SEMANAL -> "Semanal"; Frecuencia.QUINCENAL -> "Quincenal"; Frecuencia.MENSUAL -> "Mensual"; Frecuencia.ANUAL -> "Anual"
}

/** Pagos fijos (sección 10). La frecuencia es informativa: no se crean gastos solos según el calendario. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaRecurrentes(alVolver: () -> Unit, vm: RecurrentesViewModel = hiltViewModel()) {
    val lista by vm.recurrentes.collectAsState()
    val categorias by vm.categorias.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    var editando by remember { mutableStateOf<RecurrenteEntity?>(null) }
    var creando by remember { mutableStateOf(false) }

    Scaffold(topBar = { Barra("Gastos recurrentes", alVolver) }, floatingActionButton = { FloatingActionButton(onClick = { creando = true }) { Icon(Icons.Default.Add, "Nuevo recurrente") } }) { m ->
        LazyColumn(Modifier.fillMaxSize().padding(m).padding(horizontal = 16.dp)) {
            item {
                Text("Con una palabra del comercio, cuando el banco avise un cobro parecido (10 % o $ 2.000 de margen) se confirma solo.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            items(lista, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.nombre, fontWeight = FontWeight.SemiBold)
                        Text("${FormatoPesos.pesos(r.montoEsperado)} · ${textoFrecuencia(r.frecuencia)} · ${categorias.firstOrNull { it.id == r.categoriaId }?.nombre ?: "Categoría eliminada"}", style = MaterialTheme.typography.bodySmall)
                        r.palabraComercio?.let { Text("Comercio: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = { vm.registrar(r) }) { Text("Registrar") }
                    TextButton(onClick = { editando = r }) { Text("Editar") }
                }
                HorizontalDivider()
            }
        }
    }

    if (creando || editando != null) {
        val r = editando
        var nombre by remember(r) { mutableStateOf(r?.nombre ?: "") }
        var monto by remember(r) { mutableStateOf(r?.montoEsperado?.toString() ?: "") }
        var cat by remember(r) { mutableStateOf(r?.categoriaId ?: categorias.firstOrNull()?.id) }
        var frecuencia by remember(r) { mutableStateOf(r?.frecuencia ?: Frecuencia.MENSUAL) }
        var palabra by remember(r) { mutableStateOf(r?.palabraComercio ?: "") }
        val cerrar = { creando = false; editando = null }
        AlertDialog(
            onDismissRequest = cerrar,
            title = { Text(if (r == null) "Nuevo recurrente" else "Editar recurrente") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(nombre, { nombre = it.take(60) }, label = { Text("Nombre (ej.: Netflix, Arriendo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CampoMonto(monto, { monto = it }, "Monto esperado")
                    Text("Categoría", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { categorias.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, { Text(c.nombre) }) } }
                    Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Frecuencia.entries.forEach { f -> FilterChip(frecuencia == f, { frecuencia = f }, { Text(textoFrecuencia(f)) }) } }
                    OutlinedTextField(palabra, { palabra = it.take(40) }, label = { Text("Palabra del comercio (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (r != null) TextButton(onClick = { vm.eliminar(r, cerrar) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.guardar(r, nombre, cat!!, monto.toLongOrNull() ?: 0, frecuencia, palabra, cerrar) }, enabled = cat != null) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
        )
    }
}

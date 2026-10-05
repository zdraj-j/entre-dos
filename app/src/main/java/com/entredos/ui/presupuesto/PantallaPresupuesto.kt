package com.entredos.ui.presupuesto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.entredos.core.CategoriaConTope
import com.entredos.core.EstadoPeriodo
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.core.Presupuesto
import com.entredos.core.Responsable
import com.entredos.data.importacion.nombreTitular
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.repository.CategoriasRepositorio
import com.entredos.data.repository.PeriodoRepositorio
import com.entredos.ui.comun.CampoMonto
import com.entredos.ui.comun.PALETA
import com.entredos.ui.comun.SelectorColor
import com.entredos.ui.teclado.colorDe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoPresupuesto(
    val categorias: List<CategoriaEntity> = emptyList(),
    val periodo: EstadoPeriodo = EstadoPeriodo(emptyMap(), 0, 0),
    val miembros: List<MiembroEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PresupuestoViewModel @Inject constructor(
    db: EntreDosDatabase,
    periodo: PeriodoRepositorio,
    private val repo: CategoriasRepositorio,
) : ViewModel() {
    val estado = periodo.observarInicio().flatMapLatest { inicio ->
        combine(db.categoriaDao().observar(), db.parteGastoDao().observarPartesDesde(inicio), db.miembroDao().observar()) { cats, partes, miembros ->
            EstadoPresupuesto(cats, Presupuesto.calcular(cats.map { CategoriaConTope(it.id, it.presupuesto) }, partes), miembros)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoPresupuesto())

    val error = MutableStateFlow<String?>(null)

    fun guardar(c: CategoriaEntity?, nombre: String, color: String, resp: Responsable, presupuesto: Long?, destacada: Boolean, listo: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.guardar(c, nombre, color, resp, presupuesto, destacada) }
                .onSuccess { error.value = null; listo() }.onFailure { error.value = it.message }
        }
    }

    fun eliminar(c: CategoriaEntity, listo: () -> Unit) { viewModelScope.launch { repo.eliminar(c); listo() } }
}

/** [alVerCategoria] abre Movimientos filtrado (null = todos los gastos del periodo). */
@Composable
fun PantallaPresupuesto(alVerCategoria: (String?) -> Unit, vm: PresupuestoViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    var editando by remember { mutableStateOf<CategoriaEntity?>(null) }
    var creando by remember { mutableStateOf(false) }
    val p = e.periodo

    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { creando = true }) { Icon(Icons.Default.Add, "Nueva categoría") } }) { margen ->
        LazyColumn(Modifier.fillMaxSize().padding(margen)) {
            item {
                Column(Modifier.fillMaxWidth().clickable { alVerCategoria(null) }.padding(16.dp)) {
                    Text("Presupuesto del periodo", style = MaterialTheme.typography.titleLarge)
                    Text("Gastado ${FormatoPesos.pesos(p.gastado)} de ${FormatoPesos.pesos(p.asignado)}")
                    Text(
                        if (p.disponible >= 0) "Disponible ${FormatoPesos.pesos(p.disponible)}" else "Se pasaron ${FormatoPesos.pesos(-p.disponible)}",
                        color = if (p.disponible < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider()
            }
            if (e.categorias.isEmpty()) item { Text("Aún no hay categorías. Crea la primera con el botón +.", Modifier.padding(16.dp)) }
            items(e.categorias, key = { it.id }) { c ->
                val est = p.porCategoria[c.id]
                Row(Modifier.fillMaxWidth().clickable { alVerCategoria(c.id) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(colorDe(c.color), CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.nombre, fontWeight = FontWeight.SemiBold)
                        val gastado = est?.gastado ?: 0
                        if (c.presupuesto != null) {
                            LinearProgressIndicator(
                                progress = { (gastado.toFloat() / c.presupuesto).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                                color = if (est?.excedida == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${FormatoPesos.pesos(gastado)} de ${FormatoPesos.pesos(c.presupuesto)} · " +
                                    if (est?.excedida == true) "excedida ${FormatoPesos.pesos(-(est.disponible ?: 0))}" else "quedan ${FormatoPesos.pesos(est?.disponible ?: c.presupuesto)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else Text("${FormatoPesos.pesos(gastado)} · sin tope", style = MaterialTheme.typography.bodySmall)
                        Text(responsableTexto(c.responsable, e.miembros), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editando = c }) { Icon(Icons.Default.Edit, "Editar ${c.nombre}") }
                }
                HorizontalDivider()
            }
        }
    }
    if (creando || editando != null) EditorCategoria(editando, e.miembros, vm) { creando = false; editando = null }
}

fun responsableTexto(r: Responsable, miembros: List<MiembroEntity>) = when (r) {
    Responsable.COMPARTIDA -> "Compartida"
    Responsable.A -> "De " + nombreTitular(miembros, Miembro.A)
    Responsable.B -> "De " + nombreTitular(miembros, Miembro.B)
}

@Composable
private fun EditorCategoria(c: CategoriaEntity?, miembros: List<MiembroEntity>, vm: PresupuestoViewModel, cerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(c?.nombre ?: "") }
    var color by remember { mutableStateOf(c?.color ?: PALETA.random()) }
    var resp by remember { mutableStateOf(c?.responsable ?: Responsable.COMPARTIDA) }
    var tope by remember { mutableStateOf(c?.presupuesto?.toString() ?: "") }
    var destacada by remember { mutableStateOf(c?.destacada ?: false) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    val error by vm.error.collectAsState()

    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(if (c == null) "Nueva categoría" else "Editar categoría") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nombre, { nombre = it.take(40) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectorColor(color) { color = it }
                Text("¿De quién es?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Responsable.entries.forEach { r -> FilterChip(resp == r, { resp = r }, { Text(responsableTexto(r, miembros).removePrefix("De ")) }) }
                }
                Text("Compartida: quien no pagó debe la mitad. De una persona: si la paga el otro, ella le debe todo.", style = MaterialTheme.typography.bodySmall)
                CampoMonto(tope, { tope = it }, "Presupuesto por periodo (vacío = sin tope)")
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Destacar en registro rápido", Modifier.weight(1f)); Switch(destacada, { destacada = it }) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (c != null) TextButton(onClick = { confirmarBorrado = true }) { Text("Eliminar categoría", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.guardar(c, nombre, color, resp, tope.toLongOrNull(), destacada, cerrar) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
    if (confirmarBorrado && c != null) AlertDialog(
        onDismissRequest = { confirmarBorrado = false },
        title = { Text("¿Eliminar ${c.nombre}?") },
        text = { Text("Los gastos ya registrados no se borran; aparecerán como de una categoría eliminada.") },
        confirmButton = { TextButton(onClick = { vm.eliminar(c) { confirmarBorrado = false; cerrar() } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
    )
}

package com.entredos.ui.hogar

import androidx.compose.foundation.background
import com.entredos.ui.comun.EncabezadoVolver
import androidx.compose.foundation.layout.height
import com.entredos.ui.comun.TituloPantalla
import com.entredos.ui.comun.Tarjeta
import com.entredos.ui.comun.MontoGrande
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.FilaCategoria
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.BarraProgreso
import androidx.compose.material.icons.filled.Category
import androidx.compose.foundation.layout.PaddingValues
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
import com.entredos.ui.comun.colorDe
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

/** Hogar › Categorías y topes: crear, editar y eliminar categorías. */
@Composable
fun PantallaCategorias(alVolver: () -> Unit, vm: PresupuestoViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    var editando by remember { mutableStateOf<CategoriaEntity?>(null) }
    var creando by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { EncabezadoVolver("Categorías y topes", "Lo asignado vale para todos los meses hasta que lo cambies", alVolver) },
        floatingActionButton = { if (e.categorias.isNotEmpty()) FloatingActionButton(onClick = { creando = true }) { Icon(Icons.Default.Add, "Nueva categoría") } },
    ) { margen ->
        LazyColumn(Modifier.fillMaxSize().padding(margen), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            if (e.categorias.isEmpty()) item {
                EstadoVacio(
                    Icons.Default.Category, "Crea sus categorías",
                    "Mercado, arriendo, transporte… Cada una puede ser compartida o de uno de los dos, con tope o sin tope.",
                    "Crear categoría",
                ) { creando = true }
            } else item {
                Tarjeta(contenidoPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    e.categorias.forEachIndexed { i, c ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Row(
                            Modifier.fillMaxWidth().clickable { editando = c }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(12.dp).background(colorDe(c.color), CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(c.nombre, style = MaterialTheme.typography.bodyLarge)
                                Text(responsableTexto(c.responsable, e.miembros), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(c.presupuesto?.let { FormatoPesos.pesos(it) } ?: "Sin tope", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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

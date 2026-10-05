package com.entredos.ui.metas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Savings
import androidx.compose.ui.Alignment
import com.entredos.ui.comun.BarraProgreso
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.PuntoColor
import com.entredos.ui.comun.Tarjeta
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.MetaEntity
import com.entredos.data.repository.MetasRepositorio
import com.entredos.ui.comun.Barra
import com.entredos.ui.comun.CampoMonto
import com.entredos.ui.comun.PALETA
import com.entredos.ui.comun.SelectorColor
import com.entredos.ui.teclado.colorDe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetaConAhorro(val meta: MetaEntity, val ahorrado: Long)

@HiltViewModel
class MetasViewModel @Inject constructor(db: EntreDosDatabase, private val repo: MetasRepositorio) : ViewModel() {
    val metas = combine(db.metaDao().observar(), db.movimientoMetaDao().observarAhorros()) { metas, ahorros ->
        val porMeta = ahorros.associate { it.metaId to it.total }
        metas.map { MetaConAhorro(it, porMeta[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val error = MutableStateFlow<String?>(null)

    private fun hacer(listo: () -> Unit = {}, accion: suspend () -> Unit) = viewModelScope.launch {
        runCatching { accion() }.onSuccess { error.value = null; listo() }.onFailure { error.value = it.message }
    }

    fun guardar(m: MetaEntity?, nombre: String, objetivo: Long, color: String, listo: () -> Unit) = hacer(listo) { repo.guardar(m, nombre, objetivo, color) }
    fun mover(m: MetaConAhorro, monto: Long, listo: () -> Unit) = hacer(listo) { repo.mover(m.meta, monto, m.ahorrado) }
    fun eliminar(m: MetaEntity, listo: () -> Unit) = hacer(listo) { repo.eliminar(m) }
}

/** Metas compartidas (sección 9). Abonar a una meta no cuenta como gasto. */
@Composable
fun PantallaMetas(alVolver: () -> Unit, vm: MetasViewModel = hiltViewModel()) {
    val metas by vm.metas.collectAsState()
    val error by vm.error.collectAsState()
    var editando by remember { mutableStateOf<MetaEntity?>(null) }
    var creando by remember { mutableStateOf(false) }
    var moviendo by remember { mutableStateOf<MetaConAhorro?>(null) }

    Scaffold(
        topBar = { Barra("Metas de ahorro", alVolver) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { if (metas.isNotEmpty()) FloatingActionButton(onClick = { creando = true }) { Icon(Icons.Default.Add, "Nueva meta") } },
    ) { m ->
        LazyColumn(Modifier.fillMaxSize().padding(m).padding(horizontal = MARGEN), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (metas.isEmpty()) item {
                EstadoVacio(Icons.Default.Savings, "Ahorren para algo juntos", "Un viaje, un fondo de emergencias… Abonar a una meta no cuenta como gasto.", "Crear meta") { creando = true }
            } else item {
                Tarjeta(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Total ahorrado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Monto(metas.sumOf { it.ahorrado }, estilo = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, peso = FontWeight.Bold)
                }
            }
            items(metas, key = { it.meta.id }) { mc ->
                val cumplida = mc.ahorrado >= mc.meta.objetivo
                Tarjeta(alTocar = { editando = mc.meta }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PuntoColor(mc.meta.color)
                        Text(mc.meta.nombre, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${(100 * mc.ahorrado / mc.meta.objetivo).coerceAtMost(999)} %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BarraProgreso(mc.ahorrado.toFloat() / mc.meta.objetivo, color = colorDe(mc.meta.color))
                    Text(
                        if (cumplida) "¡Meta cumplida! ${FormatoPesos.pesos(mc.ahorrado)} ahorrados"
                        else "${FormatoPesos.pesos(mc.ahorrado)} de ${FormatoPesos.pesos(mc.meta.objetivo)} · faltan ${FormatoPesos.pesos(mc.meta.objetivo - mc.ahorrado)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { moviendo = mc }) { Text("Abonar o retirar") }
                }
            }
        }
    }

    if (creando || editando != null) {
        val meta = editando
        var nombre by remember(meta) { mutableStateOf(meta?.nombre ?: "") }
        var objetivo by remember(meta) { mutableStateOf(meta?.objetivo?.toString() ?: "") }
        var color by remember(meta) { mutableStateOf(meta?.color ?: PALETA.random()) }
        val cerrar = { creando = false; editando = null }
        AlertDialog(
            onDismissRequest = cerrar,
            title = { Text(if (meta == null) "Nueva meta" else "Editar meta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(nombre, { nombre = it.take(60) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CampoMonto(objetivo, { objetivo = it }, "Monto objetivo")
                    SelectorColor(color) { color = it }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (meta != null) TextButton(onClick = { vm.eliminar(meta, cerrar) }) { Text("Eliminar meta", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.guardar(meta, nombre, objetivo.toLongOrNull() ?: 0, color, cerrar) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
        )
    }

    moviendo?.let { mc ->
        var monto by remember { mutableStateOf("") }
        var retirar by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { moviendo = null },
            title = { Text(mc.meta.nombre) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!retirar, { retirar = false }, { Text("Abonar") })
                        FilterChip(retirar, { retirar = true }, { Text("Retirar") })
                    }
                    CampoMonto(monto, { monto = it }, "Monto")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { val v = monto.toLong(); vm.mover(mc, if (retirar) -v else v) { moviendo = null } }, enabled = (monto.toLongOrNull() ?: 0) > 0) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { moviendo = null }) { Text("Cancelar") } },
        )
    }
}

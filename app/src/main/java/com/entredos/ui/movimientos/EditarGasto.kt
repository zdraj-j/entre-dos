package com.entredos.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.ParteGastoEntity
import com.entredos.data.repository.GastosRepositorio
import com.entredos.ui.comun.CampoMonto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditarGastoViewModel @Inject constructor(private val db: EntreDosDatabase, private val gastos: GastosRepositorio) : ViewModel() {
    val categorias = db.categoriaDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val error = MutableStateFlow<String?>(null)

    suspend fun cargar(id: String): Pair<GastoEntity, List<ParteGastoEntity>>? =
        db.gastoDao().porId(id)?.let { it to db.parteGastoDao().porGasto(id) }

    /**
     * Si solo cambia el nombre, las partes se conservan (y con ellas la deuda y sus abonos).
     * Si cambian monto o categorías, se reparte en partes iguales y la deuda se recalcula.
     */
    fun guardar(gasto: GastoEntity, partes: List<ParteGastoEntity>, nombre: String, monto: Long, categorias: List<String>, listo: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val mismas = monto == gasto.monto && categorias.toSet() == partes.map { it.categoriaId }.toSet()
                gastos.editar(gasto.copy(nombre = nombre.ifBlank { gasto.nombre }), if (mismas) null else gastos.partesIguales(monto, categorias))
            }.onSuccess { error.value = null; listo() }.onFailure { error.value = it.message }
        }
    }

    fun eliminar(gasto: GastoEntity, listo: () -> Unit) { viewModelScope.launch { gastos.eliminar(gasto); listo() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoEditarGasto(id: String, alCerrar: () -> Unit, vm: EditarGastoViewModel = hiltViewModel()) {
    val categorias by vm.categorias.collectAsState()
    val error by vm.error.collectAsState()
    var datos by remember { mutableStateOf<Pair<GastoEntity, List<ParteGastoEntity>>?>(null) }
    var nombre by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    val elegidas = remember { mutableStateListOf<String>() }
    var confirmarBorrado by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        vm.cargar(id)?.let { d ->
            datos = d; nombre = d.first.nombre; monto = d.first.monto.toString()
            elegidas.clear(); elegidas.addAll(d.second.map { it.categoriaId }.distinct())
        } ?: alCerrar()
    }
    val (gasto, partes) = datos ?: return
    val valor = monto.toLongOrNull()

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Editar movimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nombre, { nombre = it.take(120) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CampoMonto(monto, { monto = it }, "Monto")
                Text("Categorías", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categorias.forEach { c -> FilterChip(c.id in elegidas, { if (c.id in elegidas) elegidas.remove(c.id) else elegidas.add(c.id) }, { Text(c.nombre) }) }
                }
                if (partes.size > 1) Text("Si cambias el monto o las categorías, se reparte en partes iguales.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { confirmarBorrado = true }) { Text("Eliminar movimiento", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.guardar(gasto, partes, nombre, valor!!, elegidas.toList(), alCerrar) }, enabled = valor != null && valor >= elegidas.size && elegidas.isNotEmpty()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
    if (confirmarBorrado) AlertDialog(
        onDismissRequest = { confirmarBorrado = false },
        title = { Text("¿Eliminar este movimiento?") },
        text = { Text("También se borran las deudas que había generado, en los dos teléfonos.") },
        confirmButton = { TextButton(onClick = { vm.eliminar(gasto) { confirmarBorrado = false; alCerrar() } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
    )
}

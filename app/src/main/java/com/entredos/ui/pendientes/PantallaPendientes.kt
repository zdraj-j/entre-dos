package com.entredos.ui.pendientes

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.ui.Alignment
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.Tarjeta
import com.entredos.ui.comun.TituloPantalla
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.core.Reparto
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.GastoEntity
import java.text.DateFormat
import java.util.Date

/** Movimientos › "Pendientes por clasificar". */
@Composable
fun PantallaPendientes(vm: PendientesViewModel = hiltViewModel()) {
    val pendientes by vm.pendientes.collectAsState()
    val categorias by vm.categorias.collectAsState()
    var abierto by remember { mutableStateOf<GastoEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = MARGEN), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TituloPantalla("Por clasificar", "Gastos detectados en los avisos de tus bancos", Modifier.padding(horizontal = 0.dp)) }
        if (pendientes.isEmpty()) item { EstadoVacio(Icons.Default.DoneAll, "Todo clasificado", "Cuando llegue un aviso de tu banco que la app no pueda clasificar sola, aparecerá aquí.") }
        items(pendientes, key = { it.id }) { g ->
            Tarjeta(alTocar = { abierto = g }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(g.nombre, fontWeight = FontWeight.SemiBold)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(g.fecha)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Monto(g.monto)
                }
                Text("Toca para clasificar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    abierto?.let { g -> VentanaClasificacion(g, categorias, obligatoria = false, vm = vm, alTerminar = { abierto = null }) }
}

/** Al tocar el cuerpo del aviso "Gasto detectado": la ventana no se puede cerrar sin decidir. */
@Composable
fun VentanaDesdeAviso(gastoId: String, alTerminar: () -> Unit, vm: PendientesViewModel = hiltViewModel()) {
    val categorias by vm.categorias.collectAsState()
    var gasto by remember { mutableStateOf<GastoEntity?>(null) }
    LaunchedEffect(gastoId) {
        val g = vm.cargar(gastoId)
        // Ya se clasificó (por ejemplo, desde un botón del aviso o en el otro teléfono).
        if (g == null || g.estado != com.entredos.core.EstadoGasto.PENDIENTE) alTerminar() else gasto = g
    }
    gasto?.let { VentanaClasificacion(it, categorias, obligatoria = true, vm = vm, alTerminar = alTerminar) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VentanaClasificacion(
    gasto: GastoEntity,
    categorias: List<CategoriaEntity>,
    obligatoria: Boolean,
    vm: PendientesViewModel,
    alTerminar: () -> Unit,
) {
    var nombre by remember { mutableStateOf(gasto.nombre) }
    var monto by remember { mutableStateOf(gasto.monto.toString()) }
    val elegidas = remember { mutableStateListOf<String>() }
    val error by vm.error.collectAsState()
    val montoValido = monto.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = { if (!obligatoria) alTerminar() },
        properties = DialogProperties(dismissOnBackPress = !obligatoria, dismissOnClickOutside = !obligatoria),
        title = { Text("Clasificar gasto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nombre, { nombre = it.take(120) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    monto, { monto = it.filter(Char::isDigit).take(10) }, label = { Text("Monto") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Text("Categorías", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categorias.forEach { c ->
                        FilterChip(c.id in elegidas, { if (c.id in elegidas) elegidas.remove(c.id) else elegidas.add(c.id) }, { Text(c.nombre) })
                    }
                }
                if (elegidas.size > 1 && montoValido != null) {
                    val partes = Reparto.dividirEnPartesIguales(montoValido, elegidas.size)
                    Text("Se reparte en partes iguales: ${partes.joinToString(" + ") { FormatoPesos.pesos(it) }}", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.transferencia(gasto, nombre, alTerminar) }) { Text("Es transferencia") }
                    TextButton(onClick = { vm.noRegistrar(gasto, alTerminar) }) { Text("No registrar") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.confirmar(gasto, nombre, montoValido!!, elegidas.toList(), alTerminar) },
                enabled = montoValido != null && elegidas.isNotEmpty() && montoValido >= elegidas.size,
            ) { Text("Guardar") }
        },
        dismissButton = if (obligatoria) null else ({ TextButton(onClick = alTerminar) { Text("Después") } }),
    )
}

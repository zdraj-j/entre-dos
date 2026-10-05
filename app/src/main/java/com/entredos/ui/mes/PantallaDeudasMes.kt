package com.entredos.ui.mes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.Deuda
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.data.importacion.nombreTitular
import com.entredos.ui.comun.AvatarTitular
import com.entredos.ui.comun.CampoMonto
import com.entredos.ui.comun.EncabezadoVolver
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.Tarjeta

/** Mes › Deudas: el saldo, registrar un pago, agregar una deuda y de dónde sale el saldo. */
@Composable
fun PantallaDeudasMes(alVolver: () -> Unit, vm: MesViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    val error by vm.error.collectAsState()
    var pagando by remember { mutableStateOf(false) }
    var agregando by remember { mutableStateOf(false) }
    var abonando by remember { mutableStateOf<Deuda?>(null) }
    val yo = e.yo
    val pareja = yo?.let { nombreTitular(e.miembros, it.otro) } ?: "Tu pareja"
    val neto = e.balance.neto
    val parejaUnida = e.miembros.size == 2
    fun frase(d: Deuda) = when {
        d.deudor == yo -> "Le debes a ${nombreTitular(e.miembros, d.acreedor)}"
        d.acreedor == yo -> "${nombreTitular(e.miembros, d.deudor)} te debe"
        else -> "${nombreTitular(e.miembros, d.deudor)} le debe a ${nombreTitular(e.miembros, d.acreedor)}"
    } + if (d.abonado > 0) " · ya abonó ${FormatoPesos.pesos(d.abonado)}" else ""

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoVolver("Deudas", alVolver = alVolver)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tarjeta(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { AvatarTitular(nombreTitular(e.miembros, Miembro.A), true); Box(Modifier.padding(start = 0.dp)) { AvatarTitular(nombreTitular(e.miembros, Miembro.B), false) } }
                    Text(
                        when { neto > 0 -> "$pareja te debe"; neto < 0 -> "Le debes a $pareja"; else -> "Están a mano" },
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Monto(kotlin.math.abs(neto), estilo = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pagando = true }, enabled = neto != 0L) { Text("Registrar un pago") }
                    TextButton(onClick = { agregando = true }, enabled = parejaUnida) { Text("Agregar deuda") }
                }
                if (!parejaUnida) Text("Las deudas empiezan cuando tu pareja se une al hogar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Tarjeta {
                Text("De dónde sale", style = MaterialTheme.typography.titleMedium)
                if (e.abiertas.isEmpty()) EstadoVacio(Icons.Default.CheckCircle, "Nada pendiente", "Cuando uno pague algo que le toca al otro, aparecerá aquí.")
                e.abiertas.forEach { d ->
                    Row(Modifier.fillMaxWidth().clickable { abonando = d }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.concepto, style = MaterialTheme.typography.bodyLarge)
                            Text(frase(d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Monto(d.pendiente)
                    }
                }
            }
            Box(Modifier.height(8.dp))
        }
    }

    if (pagando) {
        val quienPaga = if (neto > 0) pareja else "Tú"
        var monto by remember { mutableStateOf(kotlin.math.abs(neto).toString()) }
        val valor = monto.toLongOrNull()
        AlertDialog(
            onDismissRequest = { pagando = false },
            title = { Text("Registrar un pago") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$quienPaga paga. Se abona a las deudas más viejas primero.")
                    CampoMonto(monto, { monto = it }, "Monto del pago")
                }
            },
            confirmButton = { TextButton(onClick = { vm.registrarPago(valor!!); pagando = false }, enabled = valor != null && valor > 0) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { pagando = false }) { Text("Cancelar") } },
        )
    }
    abonando?.let { d ->
        var monto by remember { mutableStateOf(d.pendiente.toString()) }
        val valor = monto.toLongOrNull()
        AlertDialog(
            onDismissRequest = { abonando = null },
            title = { Text(d.concepto) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${frase(d)}. Pendiente: ${FormatoPesos.pesos(d.pendiente)}"); CampoMonto(monto, { monto = it }, "Monto a abonar") } },
            confirmButton = { TextButton(onClick = { vm.abonar(d, valor!!); abonando = null }, enabled = valor != null && valor in 1..d.pendiente) { Text(if (valor == d.pendiente) "Saldar" else "Abonar") } },
            dismissButton = { TextButton(onClick = { abonando = null }) { Text("Cancelar") } },
        )
    }
    if (agregando) {
        var yoDebo by remember { mutableStateOf(true) }
        var monto by remember { mutableStateOf("") }
        var concepto by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { agregando = false },
            title = { Text("Agregar deuda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(yoDebo, { yoDebo = true }, { Text("Yo le debo") })
                        FilterChip(!yoDebo, { yoDebo = false }, { Text("Me debe") })
                    }
                    CampoMonto(monto, { monto = it }, "Monto")
                    OutlinedTextField(concepto, { concepto = it.take(120) }, label = { Text("Concepto (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.agregarDeuda(yoDebo, monto.toLong(), concepto); agregando = false }, enabled = (monto.toLongOrNull() ?: 0) > 0) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { agregando = false }) { Text("Cancelar") } },
        )
    }
}

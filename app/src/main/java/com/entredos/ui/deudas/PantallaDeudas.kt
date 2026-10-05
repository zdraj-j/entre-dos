package com.entredos.ui.deudas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.Alignment
import com.entredos.ui.comun.Avatar
import com.entredos.ui.comun.BarraProgreso
import com.entredos.ui.comun.Dato
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.Tarjeta
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Balance
import com.entredos.core.CalculadoraDeudas
import com.entredos.core.Deuda
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.data.importacion.nombreTitular
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.AbonoEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.repository.DeudasRepositorio
import com.entredos.ui.comun.Barra
import com.entredos.ui.comun.CampoMonto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class EstadoDeudas(
    val abiertas: List<Deuda> = emptyList(),
    val saldadas: List<Deuda> = emptyList(),
    val abonos: List<AbonoEntity> = emptyList(),
    val yo: Miembro? = null,
    val miembros: List<MiembroEntity> = emptyList(),
    val balance: Balance = Balance(0, 0),
)

@HiltViewModel
class DeudasViewModel @Inject constructor(db: EntreDosDatabase, private val repo: DeudasRepositorio) : ViewModel() {
    val estado = combine(repo.observar(), db.abonoDao().observarHistorial(), db.dispositivoDao().observar(), db.miembroDao().observar()) { deudas, abonos, d, miembros ->
        val yo = d?.miembro
        EstadoDeudas(
            abiertas = deudas.filter { !it.saldada }, saldadas = deudas.filter { it.saldada }, abonos = abonos, yo = yo, miembros = miembros,
            balance = yo?.let { CalculadoraDeudas.balance(deudas, it) } ?: Balance(0, 0),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDeudas())

    val error = MutableStateFlow<String?>(null)

    private fun hacer(accion: suspend () -> Unit) = viewModelScope.launch {
        runCatching { accion() }.onSuccess { error.value = null }.onFailure { error.value = it.message }
    }

    fun abonar(d: Deuda, monto: Long) = hacer { repo.abonar(d, monto, System.currentTimeMillis()) }
    fun saldar(d: Deuda) = hacer { repo.abonar(d, d.pendiente, System.currentTimeMillis()) }
    fun saldarTodo() = hacer { repo.saldarTodo(estado.value.abiertas, System.currentTimeMillis()) }
    fun agregar(yoDebo: Boolean, monto: Long, concepto: String) = hacer {
        val yo = estado.value.yo ?: error("Este teléfono aún no tiene titular")
        repo.agregarManual(if (yoDebo) yo else yo.otro, monto, concepto.ifBlank { "Deuda" }, System.currentTimeMillis())
    }
}

@Composable
fun PantallaDeudas(alVolver: () -> Unit, vm: DeudasViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    val error by vm.error.collectAsState()
    var abonando by remember { mutableStateOf<Deuda?>(null) }
    var agregando by remember { mutableStateOf(false) }
    val parejaUnida = e.miembros.size == 2
    val pareja = e.yo?.let { nombreTitular(e.miembros, it.otro) } ?: "tu pareja"
    fun nombre(m: Miembro) = if (m == e.yo) "Tú" else nombreTitular(e.miembros, m)

    Column(Modifier.fillMaxSize()) {
        Barra("Deudas", alVolver)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = MARGEN), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                val neto = e.balance.neto
                val (titulo, fondo, tinta) = when {
                    neto > 0 -> Triple("$pareja te debe", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    neto < 0 -> Triple("Le debes a $pareja", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    else -> Triple("Están a mano", MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.onSurface)
                }
                Tarjeta(color = fondo) {
                    Text(titulo, style = MaterialTheme.typography.labelLarge, color = tinta)
                    Monto(kotlin.math.abs(neto), estilo = MaterialTheme.typography.headlineMedium, color = tinta, peso = FontWeight.Bold)
                    Text("Tú debes ${FormatoPesos.pesos(e.balance.yoDebo)} · Te deben ${FormatoPesos.pesos(e.balance.meDeben)}", style = MaterialTheme.typography.bodySmall, color = tinta)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::saldarTodo, enabled = e.abiertas.isNotEmpty()) { Text("Saldar todo") }
                    OutlinedButton(onClick = { agregando = true }, enabled = parejaUnida) { Text("Agregar deuda") }
                }
                if (!parejaUnida) Text("Las deudas empiezan cuando tu pareja se une al hogar.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Text("Pendientes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            if (e.abiertas.isEmpty()) item { EstadoVacio(Icons.Default.CheckCircle, "Sin deudas pendientes", "Cuando uno pague algo que le toca al otro, aparecerá aquí.") }
            items(e.abiertas, key = { it.id }) { d ->
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(nombreTitular(e.miembros, d.deudor))
                        Column(Modifier.weight(1f)) {
                            Text(d.concepto, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    d.deudor == e.yo -> "Le debes a ${nombre(d.acreedor)}"
                                    d.acreedor == e.yo -> "${nombre(d.deudor)} te debe"
                                    else -> "${nombre(d.deudor)} le debe a ${nombre(d.acreedor)}"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Monto(d.pendiente)
                    }
                    if (d.abonado > 0) {
                        BarraProgreso(d.abonado.toFloat() / d.monto)
                        Text("Abonado ${FormatoPesos.pesos(d.abonado)} de ${FormatoPesos.pesos(d.monto)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { abonando = d }) { Text("Abonar") }
                        TextButton(onClick = { vm.saldar(d) }) { Text("Saldar") }
                    }
                }
            }
            if (e.abonos.isNotEmpty()) item {
                Tarjeta(Modifier.padding(top = 6.dp)) {
                    Text("Historial de pagos", style = MaterialTheme.typography.titleMedium)
                    e.abonos.take(50).forEach { a ->
                        Dato(DateFormat.getDateInstance().format(Date(a.fecha)) + if (a.porCierre) " · al cerrar" else "", FormatoPesos.pesos(a.monto))
                    }
                }
            }
        }
    }

    abonando?.let { d ->
        var monto by remember { mutableStateOf("") }
        val valor = monto.toLongOrNull()
        AlertDialog(
            onDismissRequest = { abonando = null },
            title = { Text("Abonar a ${d.concepto}") },
            text = { Column { Text("Pendiente: ${FormatoPesos.pesos(d.pendiente)}"); CampoMonto(monto, { monto = it }, "Monto del abono") } },
            confirmButton = { TextButton(onClick = { vm.abonar(d, valor!!); abonando = null }, enabled = valor != null && valor in 1..d.pendiente) { Text("Abonar") } },
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
            confirmButton = { TextButton(onClick = { vm.agregar(yoDebo, monto.toLong(), concepto); agregando = false }, enabled = (monto.toLongOrNull() ?: 0) > 0) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { agregando = false }) { Text("Cancelar") } },
        )
    }
}

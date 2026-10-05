package com.entredos.ui.periodos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import com.entredos.ui.comun.BarraProgreso
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.MontoGrande
import com.entredos.ui.comun.Tarjeta
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.entredos.core.TipoGasto
import com.entredos.core.exportacion.ReportePeriodoCsv
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.dao.GastoConPartes
import com.entredos.data.local.entity.PeriodoCerradoEntity
import com.entredos.data.repository.ResumenPeriodo
import com.entredos.ui.comun.Barra
import com.entredos.ui.comun.Dato
import com.entredos.ui.comun.Seccion
import com.entredos.ui.comun.recordarGuardado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val FORMATO = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "CO"))
private fun dia(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(FORMATO)

@HiltViewModel
class PeriodosViewModel @Inject constructor(private val db: EntreDosDatabase) : ViewModel() {
    val periodos = db.periodoCerradoDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun detalle(id: String): Triple<PeriodoCerradoEntity, ResumenPeriodo, List<GastoConPartes>>? {
        val p = db.periodoCerradoDao().porId(id) ?: return null
        val resumen = runCatching { Escritor.JSON.decodeFromString(ResumenPeriodo.serializer(), p.resumenJson) }
            .getOrDefault(ResumenPeriodo(emptyList(), emptyList(), emptyList()))
        return Triple(p, resumen, db.gastoDao().confirmadosEntre(p.inicio, p.fin).filter { it.gasto.tipo == TipoGasto.GASTO })
    }

    suspend fun nombresCategorias(): Map<String, String> = db.categoriaDao().todosConEliminados().associate { it.id to it.nombre }
    suspend fun nombresMiembros(): Map<String, String> = db.miembroDao().todos().associate { it.id to it.nombre }
}

@Composable
fun PantallaPeriodos(alVolver: () -> Unit, alAbrir: (String) -> Unit, vm: PeriodosViewModel = hiltViewModel()) {
    val periodos by vm.periodos.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Barra("Meses anteriores", alVolver)
        LazyColumn(Modifier.padding(horizontal = MARGEN), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (periodos.isEmpty()) item { EstadoVacio(Icons.Default.History, "Aún no hay meses cerrados", "Cuando cierren el mes desde la pestaña Mes, o llegue el día de inicio en modo automático, quedará aquí con su resumen.") }
            items(periodos, key = { it.id }) { p ->
                val diferencia = p.totalPresupuestado - p.totalGastado
                Tarjeta(alTocar = { alAbrir(p.id) }) {
                    Text("${dia(p.inicio)} – ${dia(p.fin)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Monto(p.totalGastado, estilo = MaterialTheme.typography.titleLarge)
                    if (p.totalPresupuestado > 0) BarraProgreso(p.totalGastado.toFloat() / p.totalPresupuestado, excedida = diferencia < 0)
                    Text(
                        if (diferencia >= 0) "Quedó sin gastar ${FormatoPesos.pesos(diferencia)} de ${FormatoPesos.pesos(p.totalPresupuestado)}" else "Se excedió ${FormatoPesos.pesos(-diferencia)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diferencia < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaDetallePeriodo(id: String, alVolver: () -> Unit, vm: PeriodosViewModel = hiltViewModel()) {
    var datos by remember { mutableStateOf<Triple<PeriodoCerradoEntity, ResumenPeriodo, List<GastoConPartes>>?>(null) }
    var categorias by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var miembros by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(id) { datos = vm.detalle(id); categorias = vm.nombresCategorias(); miembros = vm.nombresMiembros() }
    val guardar = recordarGuardado("text/csv")

    Column(Modifier.fillMaxSize()) {
        Barra("Mes cerrado", alVolver)
        val (p, r, gastos) = datos ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = MARGEN)) {
            Tarjeta(Modifier.padding(vertical = 6.dp)) {
                MontoGrande("${dia(p.inicio)} – ${dia(p.fin)}", p.totalGastado, "de ${FormatoPesos.pesos(p.totalPresupuestado)} presupuestados")
                if (p.totalPresupuestado > 0) BarraProgreso(p.totalGastado.toFloat() / p.totalPresupuestado, excedida = p.totalGastado > p.totalPresupuestado)
                if (r.importado) Text("Periodo armado al importar datos.", style = MaterialTheme.typography.bodySmall)
            }
            Seccion("Quién gastó") { r.porPersona.forEach { Dato(it.nombre, FormatoPesos.pesos(it.total)) } }
            Seccion("Presupuestos") {
                r.porCategoria.forEach { Dato(it.nombre, FormatoPesos.pesos(it.total) + (it.asignado?.let { a -> " de ${FormatoPesos.pesos(a)}" } ?: "")) }
            }
            Seccion("Comercios con más gasto") { r.comercios.forEach { Dato(it.nombre, FormatoPesos.pesos(it.total)) } }
            Seccion("Gastos (${gastos.size})") {
                gastos.forEach { g -> Dato("${dia(g.gasto.fecha)} · ${g.gasto.nombre}", FormatoPesos.pesos(g.gasto.monto)) }
            }
            Button(onClick = {
                guardar("periodo-${p.inicio}.csv") {
                    ReportePeriodoCsv.armar(
                        p.inicio, p.fin, p.totalPresupuestado, p.totalGastado,
                        r.porCategoria.map { ReportePeriodoCsv.FilaPresupuesto(it.nombre, it.asignado, it.total) },
                        r.porPersona.map { it.nombre to it.total }, r.comercios.map { it.nombre to it.total },
                        gastos.map { g ->
                            ReportePeriodoCsv.FilaGastoReporte(
                                g.gasto.fecha, g.gasto.nombre, g.partesActivas.joinToString(" + ") { categorias[it.categoriaId] ?: "?" },
                                miembros[g.gasto.pagadoPor.name] ?: g.gasto.pagadoPor.name, g.gasto.monto,
                            )
                        },
                        ZoneId.systemDefault(),
                    ).toByteArray()
                }
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("Descargar CSV") }
        }
    }
}

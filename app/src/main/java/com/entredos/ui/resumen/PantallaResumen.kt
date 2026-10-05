package com.entredos.ui.resumen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.entredos.ui.comun.Avatar
import com.entredos.ui.comun.BarraProgreso
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.MontoGrande
import com.entredos.ui.comun.PuntoColor
import com.entredos.ui.comun.Tarjeta
import com.entredos.ui.comun.TituloPantalla
import com.entredos.ui.teclado.colorDe
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Estadisticas
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.core.ResumenCalculado
import com.entredos.core.TipoGasto
import com.entredos.data.importacion.nombreTitular
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.local.entity.PeriodoCerradoEntity
import com.entredos.data.repository.CierreRepositorio
import com.entredos.data.repository.DeudasRepositorio
import com.entredos.data.repository.PeriodoRepositorio
import com.entredos.ui.comun.Dato
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoResumen(
    val resumen: ResumenCalculado = ResumenCalculado(0, emptyList(), emptyMap(), emptyList()),
    val categorias: Map<String, CategoriaEntity> = emptyMap(),
    val miembros: List<MiembroEntity> = emptyList(),
    val deudasAbiertas: Int = 0,
    val cerrados: List<PeriodoCerradoEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResumenViewModel @Inject constructor(
    db: EntreDosDatabase,
    periodo: PeriodoRepositorio,
    deudas: DeudasRepositorio,
    private val cierre: CierreRepositorio,
) : ViewModel() {

    private val gastos = periodo.observarInicio().flatMapLatest { db.gastoDao().observarConfirmados(it, Long.MAX_VALUE) }

    val estado = combine(gastos, db.categoriaDao().observar(), db.miembroDao().observar(), deudas.observar(), db.periodoCerradoDao().observar()) { g, cats, miembros, ds, cerrados ->
        EstadoResumen(
            Estadisticas.calcular(g.filter { it.gasto.tipo == TipoGasto.GASTO }.map(CierreRepositorio::aResumen)),
            cats.associateBy { it.id }, miembros, ds.count { !it.saldada }, cerrados,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoResumen())

    val recienCerrado = MutableStateFlow<PeriodoCerradoEntity?>(null)

    fun cerrar() { viewModelScope.launch { recienCerrado.value = cierre.cerrar() } }
}

@Composable
fun PantallaResumen(
    alVerGastos: (String?) -> Unit,
    alDeudas: () -> Unit, alMetas: () -> Unit, alRecurrentes: () -> Unit, alDeteccion: () -> Unit,
    alPeriodos: () -> Unit, alVerPeriodo: (String) -> Unit,
    vm: ResumenViewModel = hiltViewModel(),
) {
    val e by vm.estado.collectAsState()
    val cerrado by vm.recienCerrado.collectAsState()
    var confirmar by remember { mutableStateOf(false) }
    val r = e.resumen

    val maximo = r.porCategoria.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        TituloPantalla("Resumen", "Periodo actual")
        Column(Modifier.padding(horizontal = MARGEN), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tarjeta(alTocar = { alVerGastos(null) }) {
                MontoGrande("Gasto del periodo", r.total)
                if (r.total > 0) {
                    val a = r.porPersona[Miembro.A] ?: 0
                    RepartoPersonas(a.toFloat() / r.total)
                    Miembro.entries.forEach { m ->
                        Dato(nombreTitular(e.miembros, m), FormatoPesos.pesos(r.porPersona[m] ?: 0), inicio = { Avatar(nombreTitular(e.miembros, m), fondo = colorPersona(m), tinta = Color.White) })
                    }
                }
            }
            Tarjeta {
                Text("Por categoría", style = MaterialTheme.typography.titleMedium)
                if (r.porCategoria.isEmpty()) Text("Aún no hay gastos en este periodo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.porCategoria.forEach { (id, total) ->
                    val c = e.categorias[id]
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { alVerGastos(id) }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PuntoColor(c?.color ?: "#8A948E", 10)
                            Text(c?.nombre ?: "Categoría eliminada", Modifier.weight(1f))
                            Monto(total)
                        }
                        BarraProgreso(total.toFloat() / maximo, color = colorDe(c?.color ?: "#8A948E"))
                    }
                }
            }
            if (r.comercios.isNotEmpty()) Tarjeta {
                Text("Comercios con más gasto", style = MaterialTheme.typography.titleMedium)
                r.comercios.forEachIndexed { i, (c, t) -> Dato("${i + 1}. $c", FormatoPesos.pesos(t)) }
            }
            if (e.cerrados.size >= 2) Tarjeta {
                Text("Tendencia", style = MaterialTheme.typography.titleMedium)
                Tendencia(e.cerrados.take(6).reversed().map { it.totalGastado })
            }
            Tarjeta {
                Dato("Deudas", if (e.deudasAbiertas > 0) "${e.deudasAbiertas} abiertas" else "Al día", alTocar = alDeudas, inicio = { Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) })
                Dato("Metas de ahorro", "", alTocar = alMetas, inicio = { Icon(Icons.Default.Savings, null, tint = MaterialTheme.colorScheme.primary) })
                Dato("Gastos recurrentes", "", alTocar = alRecurrentes, inicio = { Icon(Icons.Default.Repeat, null, tint = MaterialTheme.colorScheme.primary) })
                Dato("Detección y reglas", "", alTocar = alDeteccion, inicio = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) })
                Dato("Periodos anteriores", "${e.cerrados.size}", alTocar = alPeriodos, inicio = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary) })
            }
            Button(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Cerrar y archivar periodo") }
        }
    }

    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("Cerrar el periodo") },
        text = {
            Text(
                (if (e.deudasAbiertas > 0) "Hay ${e.deudasAbiertas} deudas abiertas. Al cerrar quedan saldadas. " else "") +
                    "Se guarda el resumen, el conteo de gasto vuelve a cero y tu pareja ve el mismo reinicio. No se borra ningún movimiento.",
            )
        },
        confirmButton = { TextButton(onClick = { vm.cerrar(); confirmar = false }) { Text(if (e.deudasAbiertas > 0) "Saldar y cerrar" else "Cerrar") } },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (e.deudasAbiertas > 0) TextButton(onClick = { confirmar = false; alDeudas() }) { Text("Ir a Deudas") }
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            }
        },
    )
    cerrado?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.recienCerrado.value = null },
            title = { Text("Periodo cerrado") },
            text = { Text("Gastado ${FormatoPesos.pesos(p.totalGastado)} de ${FormatoPesos.pesos(p.totalPresupuestado)} presupuestados.") },
            confirmButton = { TextButton(onClick = { vm.recienCerrado.value = null; alVerPeriodo(p.id) }) { Text("Ver y descargar") } },
            dismissButton = { TextButton(onClick = { vm.recienCerrado.value = null }) { Text("Listo") } },
        )
    }
}

private val COLOR_A = Color(0xFF2E7D5B)
private val COLOR_B = Color(0xFF3A5BA0)
private fun colorPersona(m: Miembro) = if (m == Miembro.A) COLOR_A else COLOR_B

/** Una barra partida en dos: cuánto del gasto pagó cada titular. */
@Composable
private fun RepartoPersonas(fraccionA: Float) {
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        if (fraccionA > 0f) Box(Modifier.weight(fraccionA.coerceAtLeast(0.001f)).fillMaxHeight().background(COLOR_A))
        if (fraccionA < 1f) Box(Modifier.weight((1f - fraccionA).coerceAtLeast(0.001f)).fillMaxHeight().background(COLOR_B))
    }
}

/** Barras simples del gasto por periodo cerrado (del más viejo al más reciente). */
@Composable
private fun Tendencia(valores: List<Long>) {
    val color = MaterialTheme.colorScheme.primary
    val max = (valores.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val ancho = size.width / valores.size
        valores.forEachIndexed { i, v ->
            val alto = size.height * v / max
            drawRect(color, topLeft = Offset(i * ancho + ancho * 0.15f, size.height - alto), size = Size(ancho * 0.7f, alto))
        }
    }
    Text("Últimos ${valores.size} periodos cerrados · máximo ${FormatoPesos.pesos(max)}", style = MaterialTheme.typography.bodySmall)
}

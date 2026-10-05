package com.entredos.ui.resumen

import androidx.compose.foundation.Canvas
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
import com.entredos.ui.comun.Seccion
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Resumen del periodo", style = MaterialTheme.typography.titleLarge)
        Dato("Gasto total", FormatoPesos.pesos(r.total), alTocar = { alVerGastos(null) })
        HorizontalDivider()
        Seccion("Por categoría") {
            if (r.porCategoria.isEmpty()) Text("Aún no hay gastos en este periodo.")
            r.porCategoria.forEach { (id, total) -> Dato(e.categorias[id]?.nombre ?: "Categoría eliminada", FormatoPesos.pesos(total), alTocar = { alVerGastos(id) }) }
        }
        Seccion("Quién gastó") { Miembro.entries.forEach { m -> Dato(nombreTitular(e.miembros, m), FormatoPesos.pesos(r.porPersona[m] ?: 0)) } }
        Seccion("Comercios con más gasto") {
            if (r.comercios.isEmpty()) Text("Sin comercios detectados.")
            r.comercios.forEach { (c, t) -> Dato(c, FormatoPesos.pesos(t)) }
        }
        if (e.cerrados.size >= 2) Seccion("Tendencia") { Tendencia(e.cerrados.take(6).reversed().map { it.totalGastado }) }
        Seccion("Más") {
            Dato("Deudas", if (e.deudasAbiertas > 0) "${e.deudasAbiertas} abiertas" else "Al día", alTocar = alDeudas)
            Dato("Metas de ahorro", "", alTocar = alMetas)
            Dato("Gastos recurrentes", "", alTocar = alRecurrentes)
            Dato("Detección y reglas", "", alTocar = alDeteccion)
            Dato("Periodos anteriores", "${e.cerrados.size}", alTocar = alPeriodos)
        }
        Button(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Cerrar y archivar periodo") }
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

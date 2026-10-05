package com.entredos.ui.mes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.data.importacion.nombreTitular
import com.entredos.ui.comun.BarraProgreso
import com.entredos.ui.comun.EncabezadoPantalla
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.Tarjeta
import com.entredos.ui.comun.colorDe
import com.entredos.ui.tema.EstiloCifras
import com.entredos.ui.tema.Extra
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ES = Locale("es", "CO")

/**
 * Pestaña Mes: lo que queda, las deudas (resumidas), todas las categorías, quién pagó y el cierre.
 * [alVerCategoria] abre Gastos filtrado; [alDeudas] abre la vista de deudas; [alVerPeriodo] el mes recién cerrado.
 */
@Composable
fun PantallaMes(alVerCategoria: (String) -> Unit, alDeudas: () -> Unit, alCategorias: () -> Unit, alVerPeriodo: (String) -> Unit, vm: MesViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    val cerrado by vm.recienCerrado.collectAsState()
    var confirmar by remember { mutableStateOf(false) }
    val p = e.presupuesto
    val ultimoDia = e.fin.minusDays(1)
    val faltan = ChronoUnit.DAYS.between(java.time.LocalDate.now(), e.fin).coerceAtLeast(0)
    val titulo = if (e.diaInicio == 1) e.inicio.format(DateTimeFormatter.ofPattern("MMMM", ES)).replaceFirstChar(Char::uppercase) else "Este periodo"
    val fechas = "${e.inicio.dayOfMonth} ${e.inicio.format(DateTimeFormatter.ofPattern("MMM", ES))} al ${ultimoDia.dayOfMonth} ${ultimoDia.format(DateTimeFormatter.ofPattern("MMM", ES))}"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoPantalla(titulo, "$fechas · faltan $faltan días")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Lo que queda
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnilloPequeno(if (p.asignado > 0) p.gastado.toFloat() / p.asignado else 0f, excedido = p.disponible < 0)
                    Column {
                        if (p.asignado > 0) {
                            Text(if (p.disponible >= 0) "Quedan" else "Se pasaron", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Monto(kotlin.math.abs(p.disponible), estilo = MaterialTheme.typography.headlineMedium, color = if (p.disponible < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Text("de ${FormatoPesos.pesos(p.asignado)}", style = MaterialTheme.typography.bodyMedium.merge(EstiloCifras), color = MaterialTheme.colorScheme.outline)
                        } else {
                            Text("Gastado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Monto(p.gastado, estilo = MaterialTheme.typography.headlineMedium)
                            Text("Aún no hay topes asignados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (e.seVanAPasar && p.disponible >= 0) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text("A este ritmo se pasarán antes de fin de mes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Deudas, en una fila
            val neto = e.balance.neto
            val pareja = e.yo?.let { nombreTitular(e.miembros, it.otro) } ?: "Tu pareja"
            Surface(onClick = alDeudas, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Deudas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when { neto > 0 -> "$pareja te debe"; neto < 0 -> "Le debes a $pareja"; else -> "Están a mano" },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (neto != 0L) Monto(kotlin.math.abs(neto), estilo = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }

            // Categorías, completas
            Tarjeta {
                val pasadas = e.categorias.count { p.porCategoria[it.id]?.excedida == true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Categorías", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (e.categorias.isNotEmpty()) Text(
                        if (pasadas > 0) "$pasadas pasada${if (pasadas > 1) "s" else ""} · ${e.categorias.size - pasadas} al día" else "Todas al día",
                        style = MaterialTheme.typography.labelLarge, color = if (pasadas > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (e.categorias.isEmpty()) EstadoVacio(Icons.Default.Category, "Sin categorías", "Créalas en Hogar › Categorías y topes.", "Crear categorías", alCategorias)
                // Primero las pasadas y las que más se acercan a su tope
                e.categorias.sortedByDescending { c -> c.presupuesto?.let { t -> (p.porCategoria[c.id]?.gastado ?: 0).toDouble() / t } ?: -1.0 }.forEach { c ->
                    val est = p.porCategoria[c.id]
                    val gastado = est?.gastado ?: 0
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { alVerCategoria(c.id) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).background(colorDe(c.color), CircleShape))
                            Text(c.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            val (texto, color) = when {
                                c.presupuesto == null -> "${FormatoPesos.pesos(gastado)} · sin tope" to MaterialTheme.colorScheme.onSurfaceVariant
                                est?.excedida == true -> "Pasada ${FormatoPesos.pesos(gastado - c.presupuesto)}" to MaterialTheme.colorScheme.error
                                else -> "Quedan ${FormatoPesos.pesos(c.presupuesto - gastado)}" to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(texto, style = MaterialTheme.typography.labelLarge.merge(EstiloCifras), color = color)
                        }
                        if (c.presupuesto != null) BarraProgreso(gastado.toFloat() / c.presupuesto, color = colorDe(c.color), excedida = est?.excedida == true)
                    }
                }
            }

            // Quién pagó
            val a = e.porPersona[Miembro.A] ?: 0
            val b = e.porPersona[Miembro.B] ?: 0
            if (a + b > 0) Tarjeta {
                val pa = (100 * a / (a + b)).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quién pagó", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${nombreTitular(e.miembros, Miembro.A)} $pa % · ${nombreTitular(e.miembros, Miembro.B)} ${100 - pa} %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    if (a > 0) Box(Modifier.weight(a.toFloat()).fillMaxHeight().background(Extra.colores.titularA))
                    if (b > 0) Box(Modifier.weight(b.toFloat()).fillMaxHeight().background(Extra.colores.titularB))
                }
            }

            OutlinedButton(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Cerrar el mes") }
            Box(Modifier.height(8.dp))
        }
    }

    if (confirmar) {
        val abiertas = e.abiertas.size
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Cerrar el mes") },
            text = {
                Text(
                    (if (abiertas > 0) "Hay $abiertas deudas abiertas: al cerrar quedan saldadas. " else "") +
                        "Se guarda el resumen, el conteo vuelve a cero y tu pareja ve el mismo cambio. No se borra ningún gasto.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.cerrarMes(); confirmar = false }) { Text(if (abiertas > 0) "Saldar y cerrar" else "Cerrar el mes") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
    cerrado?.let { periodo ->
        AlertDialog(
            onDismissRequest = { vm.recienCerrado.value = null },
            title = { Text("Mes cerrado") },
            text = { Text("Gastaron ${FormatoPesos.pesos(periodo.totalGastado)} de ${FormatoPesos.pesos(periodo.totalPresupuestado)}. Lo encuentras en Hogar › Meses anteriores.") },
            confirmButton = { TextButton(onClick = { vm.recienCerrado.value = null; alVerPeriodo(periodo.id) }) { Text("Ver resumen") } },
            dismissButton = { TextButton(onClick = { vm.recienCerrado.value = null }) { Text("Listo") } },
        )
    }
}

@Composable
private fun AnilloPequeno(fraccion: Float, excedido: Boolean) {
    val pista = MaterialTheme.colorScheme.outlineVariant
    val color = if (excedido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val grosor = 10.dp.toPx()
            val lado = size.minDimension - grosor
            val esquina = Offset(grosor / 2, grosor / 2)
            drawArc(pista, 0f, 360f, false, esquina, Size(lado, lado), style = Stroke(grosor))
            drawArc(color, -90f, 360f * fraccion.coerceIn(0f, 1f), false, esquina, Size(lado, lado), style = Stroke(grosor, cap = StrokeCap.Round))
        }
        Text("${(fraccion * 100).toInt()} %", style = MaterialTheme.typography.titleMedium)
    }
}

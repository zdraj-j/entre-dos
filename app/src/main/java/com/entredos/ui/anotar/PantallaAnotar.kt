package com.entredos.ui.anotar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.core.Tecla
import com.entredos.ui.comun.colorDe
import com.entredos.ui.tema.EstiloCifras
import com.entredos.ui.teclado.AvisoTeclado
import com.entredos.ui.teclado.CategoriaTecla
import com.entredos.ui.teclado.DialogoFecha
import com.entredos.ui.teclado.DialogoMontos
import com.entredos.ui.teclado.DialogoNota
import com.entredos.ui.teclado.DialogoReparto
import com.entredos.ui.teclado.TecladoViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pestaña Anotar: monto, categorías y teclado. Tocar una categoría guarda el gasto.
 * Arriba, un solo dato de contexto: cuánto queda del mes.
 */
@Composable
fun PantallaAnotar(vm: TecladoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsState()
    val aviso by vm.aviso.collectAsState()
    val barra = remember { SnackbarHostState() }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    val b = estado.borrador

    LaunchedEffect(aviso) {
        when (val a = aviso) {
            is AvisoTeclado.Registrado -> {
                val r = barra.showSnackbar(a.texto, actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.deshacer(a) else vm.avisoMostrado()
            }
            is AvisoTeclado.Error -> { barra.showSnackbar(a.texto, duration = SnackbarDuration.Short); vm.avisoMostrado() }
            null -> Unit
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(barra) }, containerColor = MaterialTheme.colorScheme.background) { margen ->
        Column(Modifier.fillMaxSize().padding(margen)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Anotar gasto", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = if (estado.disponiblePeriodo >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        if (estado.disponiblePeriodo >= 0) "Quedan ${FormatoPesos.pesos(estado.disponiblePeriodo)}" else "Se pasaron ${FormatoPesos.pesos(-estado.disponiblePeriodo)}",
                        style = MaterialTheme.typography.labelLarge.merge(EstiloCifras),
                        color = if (estado.disponiblePeriodo >= 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Monto y opciones del gasto
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                if (b.calculadora.tieneOperacion) Text(b.calculadora.textoExpresion, style = MaterialTheme.typography.bodyLarge.merge(EstiloCifras), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    FormatoPesos.pesos(maxOf(b.calculadora.total, 0)),
                    style = MaterialTheme.typography.displayMedium.merge(EstiloCifras).copy(fontSize = 52.sp, letterSpacing = (-1).sp),
                    color = if (b.calculadora.valido) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Opcion(Icons.Default.CalendarToday, textoFecha(b.fecha), activa = b.fecha != LocalDate.now()) { dialogo = Dialogo.Fecha }
                    Opcion(Icons.AutoMirrored.Filled.Notes, if (b.nota.isBlank()) "Nota" else b.nota, activa = b.nota.isNotBlank()) { dialogo = Dialogo.Nota }
                    Opcion(Icons.AutoMirrored.Filled.CallSplit, "Dividir", activa = b.varias) { vm.alternarVarias() }
                }
            }

            Categorias(
                categorias = estado.categorias,
                alTocar = vm::tocarCategoria,
                alMantener = { c ->
                    if (c.compartida && !b.varias) { if (b.calculadora.valido) dialogo = Dialogo.Reparto(c) else vm.faltaMonto() }
                },
            )

            // Ayuda corta, o las acciones de "Dividir" cuando hay categorías elegidas
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                if (b.varias && b.seleccion.size >= 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = vm::registrarVarias) { Text("Guardar en ${b.seleccion.size}") }
                        TextButton(onClick = { if (b.calculadora.valido) dialogo = Dialogo.Montos else vm.faltaMonto() }) { Text("Ajustar montos") }
                    }
                } else {
                    Text(
                        when {
                            estado.categorias.isEmpty() -> "Crea tus categorías en Hogar › Categorías y topes"
                            b.varias -> "Elige dos o más categorías"
                            else -> "Toca una categoría y queda guardado"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Teclado(vm::tecla)
        }
    }

    when (val d = dialogo) {
        Dialogo.Nota -> DialogoNota(b.nota, { vm.fijarNota(it); dialogo = null }, { dialogo = null })
        Dialogo.Fecha -> DialogoFecha(b.fecha, { vm.fijarFecha(it); dialogo = null }, { dialogo = null })
        Dialogo.Montos -> DialogoMontos(
            total = b.calculadora.total,
            categorias = b.seleccion.mapNotNull { id -> estado.categorias.firstOrNull { it.id == id } },
            alConfirmar = { vm.registrarConMontos(it); dialogo = null },
            alCancelar = { dialogo = null },
        )
        is Dialogo.Reparto -> DialogoReparto(
            total = b.calculadora.total, categoria = d.categoria.nombre, nombrePareja = estado.nombrePareja ?: "Tu pareja",
            alConfirmar = { vm.registrarConReparto(d.categoria.id, it); dialogo = null }, alCancelar = { dialogo = null },
        )
        null -> Unit
    }
}

private sealed interface Dialogo {
    data object Nota : Dialogo
    data object Fecha : Dialogo
    data object Montos : Dialogo
    data class Reparto(val categoria: CategoriaTecla) : Dialogo
}

@Composable
private fun Opcion(icono: ImageVector, texto: String, activa: Boolean, alTocar: () -> Unit) {
    Surface(
        onClick = alTocar, shape = CircleShape,
        color = if (activa) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (activa) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tinta = if (activa) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(icono, contentDescription = null, tint = tinta, modifier = Modifier.size(16.dp))
            Text(texto, style = MaterialTheme.typography.labelLarge, color = tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Categorias(categorias: List<CategoriaTecla>, alTocar: (String) -> Unit, alMantener: (CategoriaTecla) -> Unit) {
    val vibrar = LocalHapticFeedback.current
    var todas by remember { mutableStateOf(false) }
    // Hasta 9 a la vista (ordenadas por uso); si hay más, la novena casilla abre la lista completa.
    val visibles = if (categorias.size > 9) categorias.take(8) else categorias
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (visibles.map<CategoriaTecla, CategoriaTecla?> { it } + if (categorias.size > 9) listOf(null) else emptyList()).chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { c ->
                    val forma = RoundedCornerShape(14.dp)
                    if (c == null) {
                        Surface(onClick = { todas = true }, modifier = Modifier.weight(1f).height(46.dp), shape = forma, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Box(contentAlignment = Alignment.Center) { Text("Más…", style = MaterialTheme.typography.labelLarge) }
                        }
                        return@forEach
                    }
                    Row(
                        Modifier.weight(1f).height(46.dp)
                            .background(if (c.seleccionada) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest, forma)
                            .border(if (c.seleccionada) 2.dp else 1.dp, if (c.seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, forma)
                            .combinedClickable(
                                onClick = { vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove); alTocar(c.id) },
                                onLongClick = { vibrar.performHapticFeedback(HapticFeedbackType.LongPress); alMantener(c) },
                                onLongClickLabel = if (c.compartida) "Ajustar reparto" else null,
                            )
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(if (c.excedida) MaterialTheme.colorScheme.error else colorDe(c.color), CircleShape))
                        Spacer(Modifier.size(8.dp))
                        Text(c.nombre, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (todas) androidx.compose.material3.AlertDialog(
        onDismissRequest = { todas = false },
        title = { Text("Categorías") },
        text = {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categorias.forEach { c ->
                    androidx.compose.material3.FilterChip(c.seleccionada, { todas = false; alTocar(c.id) }, { Text(c.nombre) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(colorDe(c.color), CircleShape)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { todas = false }) { Text("Cerrar") } },
    )
}

private data class DefTecla(val texto: String, val tecla: Tecla, val descripcion: String, val tipo: Int)

private val TECLAS = listOf(
    DefTecla("7", Tecla.Digito("7"), "7", 0), DefTecla("8", Tecla.Digito("8"), "8", 0), DefTecla("9", Tecla.Digito("9"), "9", 0), DefTecla("⌫", Tecla.Borrar, "Borrar", 1),
    DefTecla("4", Tecla.Digito("4"), "4", 0), DefTecla("5", Tecla.Digito("5"), "5", 0), DefTecla("6", Tecla.Digito("6"), "6", 0), DefTecla("+", Tecla.Sumar, "Sumar", 1),
    DefTecla("1", Tecla.Digito("1"), "1", 0), DefTecla("2", Tecla.Digito("2"), "2", 0), DefTecla("3", Tecla.Digito("3"), "3", 0), DefTecla("−", Tecla.Restar, "Restar", 1),
    DefTecla("000", Tecla.Digito("000"), "Triple cero", 0), DefTecla("0", Tecla.Digito("0"), "0", 0), DefTecla("C", Tecla.Limpiar, "Limpiar", 1), DefTecla("=", Tecla.Igual, "Calcular", 2),
)

@Composable
private fun Teclado(alTecla: (Tecla) -> Unit) {
    val vibrar = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TECLAS.chunked(4).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { t ->
                    val (fondo, tinta) = when (t.tipo) {
                        2 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        1 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerLowest to MaterialTheme.colorScheme.onSurface
                    }
                    Surface(
                        onClick = { vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove); alTecla(t.tecla) },
                        modifier = Modifier.weight(1f).height(54.dp).semantics { contentDescription = t.descripcion },
                        shape = RoundedCornerShape(16.dp), color = fondo,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(t.texto, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = tinta) }
                    }
                }
            }
        }
    }
}

private fun textoFecha(f: LocalDate): String = when (f) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> f.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "CO")))
}

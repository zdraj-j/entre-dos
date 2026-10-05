package com.entredos.ui.teclado

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pantalla de inicio: monto, categorías y teclado. Registrar un gasto = monto + toque en categoría. */
@Composable
fun PantallaTeclado(alAbrirPendientes: () -> Unit, vm: TecladoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsState()
    val aviso by vm.aviso.collectAsState()
    val barra = remember { SnackbarHostState() }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }

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

    Scaffold(snackbarHost = { SnackbarHost(barra) }) { margen ->
        Column(Modifier.fillMaxSize().padding(margen)) {
            Encabezado(estado.pendientes, alAbrirPendientes)
            Pantallita(estado)
            Opciones(
                estado = estado,
                alNota = { dialogo = Dialogo.Nota },
                alFecha = { dialogo = Dialogo.Fecha },
                alVarias = vm::alternarVarias,
                alRegistrarVarias = vm::registrarVarias,
                alAjustarMontos = { if (estado.borrador.calculadora.valido) dialogo = Dialogo.Montos else vm.faltaMonto() },
            )
            Text(
                if (estado.borrador.varias) "Elige las categorías y registra" else "Toca una categoría para guardar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Categorias(
                categorias = estado.categorias,
                alTocar = vm::tocarCategoria,
                alMantener = { c ->
                    if (c.compartida && !estado.borrador.varias) {
                        if (estado.borrador.calculadora.valido) dialogo = Dialogo.Reparto(c) else vm.faltaMonto()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Teclado(vm::tecla)
        }
    }

    when (val d = dialogo) {
        Dialogo.Nota -> DialogoNota(estado.borrador.nota, { vm.fijarNota(it); dialogo = null }, { dialogo = null })
        Dialogo.Fecha -> DialogoFecha(estado.borrador.fecha, { vm.fijarFecha(it); dialogo = null }, { dialogo = null })
        Dialogo.Montos -> DialogoMontos(
            total = estado.borrador.calculadora.total,
            categorias = estado.borrador.seleccion.mapNotNull { id -> estado.categorias.firstOrNull { it.id == id } },
            alConfirmar = { vm.registrarConMontos(it); dialogo = null },
            alCancelar = { dialogo = null },
        )
        is Dialogo.Reparto -> DialogoReparto(
            total = estado.borrador.calculadora.total,
            categoria = d.categoria.nombre,
            nombrePareja = estado.nombrePareja ?: "Tu pareja",
            alConfirmar = { vm.registrarConReparto(d.categoria.id, it); dialogo = null },
            alCancelar = { dialogo = null },
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
private fun Encabezado(pendientes: Int, alAbrirPendientes: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Entre dos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (pendientes > 0) {
            TextButton(onClick = alAbrirPendientes, modifier = Modifier.semantics { contentDescription = "$pendientes gastos por clasificar" }) {
                Badge { Text("$pendientes") }
                Spacer(Modifier.size(8.dp))
                Text("por clasificar")
            }
        }
    }
}

@Composable
private fun Pantallita(estado: EstadoTeclado) {
    val calc = estado.borrador.calculadora
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp).heightIn(min = 100.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(calc.textoExpresion, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            FormatoPesos.pesos(maxOf(calc.total, 0)),
            fontSize = 48.sp,
            style = com.entredos.ui.tema.EstiloCifras,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = if (calc.valido) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Text(
            "Disponible del periodo: ${FormatoPesos.pesos(estado.disponiblePeriodo)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (estado.disponiblePeriodo < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Opciones(
    estado: EstadoTeclado,
    alNota: () -> Unit,
    alFecha: () -> Unit,
    alVarias: () -> Unit,
    alRegistrarVarias: () -> Unit,
    alAjustarMontos: () -> Unit,
) {
    val b = estado.borrador
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = b.nota.isNotBlank(), onClick = alNota, label = { Text(if (b.nota.isBlank()) "Nota" else b.nota, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        FilterChip(selected = b.fecha != LocalDate.now(), onClick = alFecha, label = { Text("Fecha: ${textoFecha(b.fecha)}") })
        FilterChip(selected = b.varias, onClick = alVarias, label = { Text("Varias categorías") })
        if (b.varias && b.seleccion.size >= 2) {
            Button(onClick = alRegistrarVarias) { Text("Registrar en ${b.seleccion.size}") }
            TextButton(onClick = alAjustarMontos) { Text("Ajustar montos") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Categorias(
    categorias: List<CategoriaTecla>,
    alTocar: (String) -> Unit,
    alMantener: (CategoriaTecla) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categorias.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Text("Aún no hay categorías. Créalas en Presupuesto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val vibrar = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categorias, key = { it.id }) { c ->
            val borde = if (c.seleccionada) MaterialTheme.colorScheme.primary else Color.Transparent
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .border(2.dp, borde, RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove); alTocar(c.id) },
                        onLongClick = { vibrar.performHapticFeedback(HapticFeedbackType.LongPress); alMantener(c) },
                        onLongClickLabel = if (c.compartida) "Ajustar reparto" else null,
                    ),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(colorDe(c.color), CircleShape))
                        Spacer(Modifier.size(6.dp))
                        Text(c.nombre, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        when {
                            c.disponible == null -> "sin tope"
                            c.excedida -> "excedida ${FormatoPesos.pesos(-c.disponible)}"
                            else -> "quedan ${FormatoPesos.pesos(c.disponible)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (c.excedida) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TECLAS.chunked(4).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { t ->
                    val alPulsar = { vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove); alTecla(t.tecla) }
                    val mod = Modifier.weight(1f).height(56.dp).semantics { contentDescription = t.descripcion }
                    val forma = RoundedCornerShape(14.dp)
                    when (t.tipo) {
                        2 -> Button(onClick = alPulsar, modifier = mod, shape = forma) { Text(t.texto, fontSize = 24.sp) }
                        1 -> FilledTonalButton(onClick = alPulsar, modifier = mod, shape = forma) { Text(t.texto, fontSize = 24.sp) }
                        else -> Surface(onClick = alPulsar, modifier = mod, shape = forma, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 1.dp) {
                            Box(contentAlignment = Alignment.Center) { Text(t.texto, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
                        }
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

internal fun colorDe(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

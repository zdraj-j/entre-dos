package com.entredos.ui.comun

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.entredos.core.FormatoPesos
import com.entredos.ui.teclado.colorDe
import com.entredos.ui.tema.EstiloCifras

val PALETA = listOf("#2E7D5B", "#3A5BA0", "#C77A12", "#B4472B", "#6B4FA0", "#1F8A8A", "#8A5A2E", "#A23B72", "#4F6D2E", "#5C6670")

/** Margen lateral de todas las pantallas. */
val MARGEN = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Barra(titulo: String, alVolver: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (alVolver != null) IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Título de pantalla principal (las de la barra inferior), sin flecha de volver. */
@Composable
fun TituloPantalla(titulo: String, subtitulo: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = MARGEN, end = MARGEN, top = 20.dp, bottom = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium)
        subtitulo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Tarjeta blanca con esquinas de 20 dp; el bloque básico de casi todas las pantallas. */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    alTocar: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).let { if (alTocar != null) it.clickable(onClick = alTocar) else it },
    ) {
        Column(Modifier.padding(MARGEN).animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp), content = contenido)
    }
}

/** Monto en pesos con cifras alineadas. */
@Composable
fun Monto(valor: Long, modifier: Modifier = Modifier, estilo: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified, peso: FontWeight? = FontWeight.SemiBold) {
    Text(FormatoPesos.pesos(valor), modifier, style = estilo.merge(EstiloCifras), color = color, fontWeight = peso, maxLines = 1)
}

/** Monto grande de cabecera con su etiqueta encima y una nota debajo. */
@Composable
fun MontoGrande(etiqueta: String, valor: Long, nota: String? = null, colorNota: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Monto(valor, estilo = MaterialTheme.typography.headlineMedium, peso = FontWeight.Bold)
        nota?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colorNota) }
    }
}

@Composable
fun PuntoColor(hex: String, tamano: Int = 12) {
    Box(Modifier.size(tamano.dp).background(colorDe(hex), CircleShape))
}

/** Círculo con la inicial de un titular. */
@Composable
fun Avatar(nombre: String, fondo: Color = MaterialTheme.colorScheme.primaryContainer, tinta: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(Modifier.size(32.dp).background(fondo, CircleShape), contentAlignment = Alignment.Center) {
        Text(nombre.trim().take(1).uppercase().ifEmpty { "?" }, color = tinta, style = MaterialTheme.typography.labelLarge)
    }
}

/** Barra de progreso redondeada; se pone del color de error al pasarse. */
@Composable
fun BarraProgreso(fraccion: Float, color: Color = MaterialTheme.colorScheme.primary, excedida: Boolean = false, modifier: Modifier = Modifier) {
    val animada by animateFloatAsState(fraccion.coerceIn(0f, 1f), label = "progreso")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth(animada).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (excedida) MaterialTheme.colorScheme.error else color))
    }
}

/** Pantalla o sección sin datos: qué falta y qué hacer. */
@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, texto: String, accion: String? = null, alAccion: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
        }
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (accion != null && alAccion != null) Button(onClick = alAccion, modifier = Modifier.padding(top = 8.dp)) { Text(accion) }
    }
}

/** Campo de monto en pesos: solo dígitos. Vacío = null. */
@Composable
fun CampoMonto(valor: String, alCambiar: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        valor, { alCambiar(it.filter(Char::isDigit).take(10)) }, label = { Text(etiqueta) }, singleLine = true,
        prefix = { Text("$ ") }, textStyle = MaterialTheme.typography.bodyLarge.merge(EstiloCifras),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

@Composable
fun SelectorColor(actual: String, alElegir: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETA.forEach { hex ->
            Box(
                Modifier.size(28.dp).background(colorDe(hex), CircleShape)
                    .border(3.dp, if (hex.equals(actual, true)) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { alElegir(hex) }.semantics { contentDescription = "Color $hex" },
            )
        }
    }
}

/** Fila de un dato con su valor a la derecha; con [alTocar] muestra una flecha. */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, alTocar: (() -> Unit)? = null, inicio: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.small)
            .let { if (alTocar != null) it.clickable(onClick = alTocar) else it }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        inicio?.invoke()
        Text(etiqueta, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (valor.isNotEmpty()) Text(valor, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color, style = MaterialTheme.typography.bodyLarge.merge(EstiloCifras))
        if (alTocar != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Sección dentro de una tarjeta, con título. */
@Composable
fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Tarjeta(Modifier.padding(vertical = 6.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        contenido()
    }
}

/** Fila de una categoría con su punto de color, el monto y, si tiene tope, la barra. */
@Composable
fun FilaCategoria(nombre: String, color: String, gastado: Long, tope: Long?, alTocar: (() -> Unit)? = null, final: (@Composable () -> Unit)? = null) {
    val excedida = tope != null && gastado > tope
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).let { if (alTocar != null) it.clickable(onClick = alTocar) else it }.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PuntoColor(color)
            Text(nombre, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Monto(gastado)
            final?.invoke()
        }
        if (tope != null) {
            BarraProgreso(gastado.toFloat() / tope, color = colorDe(color), excedida = excedida)
            Text(
                if (excedida) "Excedida ${FormatoPesos.pesos(gastado - tope)} · tope ${FormatoPesos.pesos(tope)}"
                else "Quedan ${FormatoPesos.pesos(tope - gastado)} de ${FormatoPesos.pesos(tope)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (excedida) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

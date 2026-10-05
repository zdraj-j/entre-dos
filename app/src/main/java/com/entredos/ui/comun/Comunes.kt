package com.entredos.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.entredos.ui.teclado.colorDe

val PALETA = listOf("#2E7D5B", "#3A5BA0", "#C77A12", "#B4472B", "#6B4FA0", "#1F8A8A", "#8A5A2E", "#A23B72", "#4F6D2E", "#5C6670")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Barra(titulo: String, alVolver: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(titulo) },
        navigationIcon = {
            if (alVolver != null) IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
        },
    )
}

/** Campo de monto en pesos: solo dígitos. Vacío = null. */
@Composable
fun CampoMonto(valor: String, alCambiar: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        valor, { alCambiar(it.filter(Char::isDigit).take(10)) }, label = { Text(etiqueta) }, singleLine = true,
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

/** Fila de un dato con su valor a la derecha. */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, alTocar: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().let { if (alTocar != null) it.clickable(onClick = alTocar) else it }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, Modifier.weight(1f))
        Text(valor, color = color)
    }
}

@Composable
fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        contenido()
    }
}

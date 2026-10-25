package com.entredos.ui.teclado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.entredos.core.FormatoPesos
import com.entredos.core.Reparto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun DialogoNombre(actual: String, alConfirmar: (String) -> Unit, alCancelar: () -> Unit) {
    var texto by remember { mutableStateOf(actual) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Nombre del gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    texto, { texto = it.take(120) }, placeholder = { Text("Ej.: Almuerzo con la familia") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Text("Si lo dejas vacío, el gasto se llama como su categoría.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { alConfirmar(texto) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DialogoFecha(actual: LocalDate, alConfirmar: (LocalDate) -> Unit, alCancelar: () -> Unit) {
    // El DatePicker trabaja en UTC: se convierte la fecha a medianoche UTC y de vuelta.
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = actual.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = alCancelar,
        confirmButton = {
            TextButton(onClick = {
                val ms = estado.selectedDateMillis ?: return@TextButton
                val elegida = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                alConfirmar(minOf(elegida, LocalDate.now()))
            }) { Text("Listo") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { alConfirmar(LocalDate.now().minusDays(1)) }) { Text("Ayer") }
                TextButton(onClick = { alConfirmar(LocalDate.now()) }) { Text("Hoy") }
            }
        },
    ) { DatePicker(estado) }
}

/**
 * Se abre solo con un monto válido y al menos dos categorías (lo controla PantallaAnotar).
 * "Ajustar montos": un campo por categoría. La última se calcula sola con lo que falta,
 * así la suma siempre da el total.
 */
@Composable
internal fun DialogoMontos(total: Long, categorias: List<CategoriaTecla>, alConfirmar: (List<Long>) -> Unit, alCancelar: () -> Unit) {
    val iguales = remember(total, categorias.size) { Reparto.dividirEnPartesIguales(maxOf(total, categorias.size.toLong()), categorias.size) }
    val campos = remember { mutableStateListOf(*iguales.dropLast(1).map { it.toString() }.toTypedArray()) }
    val primeras = campos.map { it.toLongOrNull() ?: 0L }
    val ultima = Reparto.completarUltima(total, primeras)
    val montos = primeras + ultima
    val error = Reparto.validarDivision(total, montos)

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Ajustar montos de ${FormatoPesos.pesos(total)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categorias.dropLast(1).forEachIndexed { i, c ->
                    OutlinedTextField(
                        campos[i], { campos[i] = it.filter(Char::isDigit).take(10) },
                        label = { Text(c.nombre) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("${categorias.last().nombre}: ${FormatoPesos.pesos(ultima)} (lo que falta)", style = MaterialTheme.typography.bodyLarge)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { alConfirmar(montos) }, enabled = error == null) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/** "Ajustar reparto" de un gasto en una categoría compartida: cuánto asume quien pagó (tú). */
@Composable
internal fun DialogoReparto(total: Long, categoria: String, nombrePareja: String, alConfirmar: (Long) -> Unit, alCancelar: () -> Unit) {
    var mio by remember { mutableStateOf((total - total / 2).toString()) }
    val yo = mio.toLongOrNull()
    val valido = yo != null && yo in 0..total

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Reparto en $categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pagas tú ${FormatoPesos.pesos(total)}. ¿Cuánto asumes?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { mio = (total - total / 2).toString() }, label = { Text("Mitad") })
                    AssistChip(onClick = { mio = total.toString() }, label = { Text("Todo yo") })
                    AssistChip(onClick = { mio = "0" }, label = { Text("Todo $nombrePareja") })
                }
                OutlinedTextField(
                    mio, { mio = it.filter(Char::isDigit).take(10) }, label = { Text("Tú asumes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                if (valido) Text("$nombrePareja te deberá ${FormatoPesos.pesos(total - yo!!)}")
                else Text("Debe estar entre 0 y ${FormatoPesos.pesos(total)}", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { alConfirmar(yo!!) }, enabled = valido) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

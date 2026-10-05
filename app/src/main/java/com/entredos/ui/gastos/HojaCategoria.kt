package com.entredos.ui.gastos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.entredos.core.FormatoPesos
import com.entredos.core.Reparto
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.colorDe

/**
 * Hoja "¿En qué categoría?": todas las categorías en dos columnas; con varias, el monto se
 * reparte en partes iguales. Si [obligatoria], no se puede cerrar sin decidir (al abrir desde el aviso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCategoria(
    gasto: GastoEntity,
    categorias: List<CategoriaEntity>,
    obligatoria: Boolean,
    alGuardar: (List<String>) -> Unit,
    alTransferencia: () -> Unit,
    alNoEsGasto: () -> Unit,
    alCerrar: () -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !obligatoria || it != SheetValue.Hidden })
    val elegidas = remember { mutableStateListOf<String>() }
    ModalBottomSheet(
        onDismissRequest = { if (!obligatoria) alCerrar() },
        sheetState = estadoHoja,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !obligatoria),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Monto(gasto.monto, estilo = MaterialTheme.typography.headlineMedium)
                Text(gasto.nombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("¿En qué categoría?", style = MaterialTheme.typography.titleMedium)
            categorias.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { c ->
                        val sel = c.id in elegidas
                        Surface(
                            onClick = { if (sel) elegidas.remove(c.id) else elegidas.add(c.id) },
                            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                if (sel) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                else Box(Modifier.size(10.dp).background(colorDe(c.color), CircleShape))
                                Spacer(Modifier.size(8.dp))
                                Text(c.nombre, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (fila.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (elegidas.size > 1 && gasto.monto >= elegidas.size) {
                val partes = Reparto.dividirEnPartesIguales(gasto.monto, elegidas.size)
                Text("Se reparte en partes iguales: ${partes.joinToString(" + ") { FormatoPesos.pesos(it) }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { alGuardar(elegidas.toList()) }, enabled = elegidas.isNotEmpty() && gasto.monto >= elegidas.size, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (elegidas.size > 1) "Guardar en ${elegidas.size} categorías" else "Guardar")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = alTransferencia) { Text("Es una transferencia", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = alNoEsGasto) { Text("No es un gasto", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

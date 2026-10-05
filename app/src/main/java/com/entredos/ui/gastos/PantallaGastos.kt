package com.entredos.ui.gastos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.data.local.entity.GastoEntity
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.IconoCategoria
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.Tarjeta
import com.entredos.ui.comun.colorDe
import com.entredos.ui.tema.EstiloCifras
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val ES = Locale("es", "CO")

/** Pestaña Gastos: lo que está por confirmar arriba y, debajo, los gastos del mes por día. [alEditar] abre la edición. */
@Composable
fun PantallaGastos(alEditar: (String) -> Unit, vm: GastosViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    var buscando by remember { mutableStateOf(e.filtro.texto.isNotEmpty()) }
    var menuMes by remember { mutableStateOf(false) }
    var hoja by remember { mutableStateOf<GastoEntity?>(null) }
    val nombres = e.miembros.associate { it.id to it.nombre }
    val cats = e.categorias.associateBy { it.id }
    val mes = e.inicioPeriodo?.let { if (e.soloPeriodo) it.format(DateTimeFormatter.ofPattern("MMMM", ES)).replaceFirstChar(Char::uppercase) else "Todos los meses" } ?: ""

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gastos", style = MaterialTheme.typography.headlineMedium)
                    Box {
                        Row(Modifier.clickable { menuMes = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$mes · ${FormatoPesos.pesos(e.total)}", style = MaterialTheme.typography.labelLarge.merge(EstiloCifras), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Cambiar mes", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(menuMes, { menuMes = false }) {
                            DropdownMenuItem(text = { Text("Este mes") }, onClick = { vm.fijarSoloPeriodo(true); menuMes = false })
                            DropdownMenuItem(text = { Text("Todos los meses") }, onClick = { vm.fijarSoloPeriodo(false); menuMes = false })
                        }
                    }
                }
                Surface(onClick = { buscando = !buscando; if (!buscando) vm.buscar("") }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (buscando) Icons.Default.Close else Icons.Default.Search, contentDescription = if (buscando) "Cerrar búsqueda" else "Buscar") }
                }
            }
            if (buscando) OutlinedTextField(
                e.filtro.texto, vm::buscar, placeholder = { Text("Nombre o comercio") }, singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            e.filtro.categoriaId?.let { id ->
                InputChip(
                    selected = true, onClick = { vm.categoria(null) }, label = { Text(cats[id]?.nombre ?: "Categoría") },
                    leadingIcon = { Box(Modifier.size(10.dp).background(colorDe(cats[id]?.color ?: "#8E8A9B"), CircleShape)) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Quitar filtro", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (e.pendientes.isNotEmpty() && e.filtro.texto.isEmpty() && e.filtro.categoriaId == null) item {
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Text("Por confirmar · ${e.pendientes.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
                e.pendientes.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    PorConfirmarFila(p, alElegir = { vm.confirmar(p.gasto, it) }, alOtra = { hoja = p.gasto })
                }
            }
        }

        if (e.dias.isEmpty()) item {
            EstadoVacio(
                Icons.AutoMirrored.Filled.ReceiptLong,
                if (e.filtro.texto.isNotEmpty() || e.filtro.categoriaId != null) "Nada con este filtro" else "Aún no hay gastos este mes",
                if (e.filtro.texto.isNotEmpty() || e.filtro.categoriaId != null) "Prueba con otra búsqueda o quita el filtro." else "Anota uno en la pestaña Anotar o espera el primer aviso de tu banco.",
            )
        } else item {
            Tarjeta(contenidoPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                e.dias.forEach { (dia, lista) ->
                    Text(
                        textoDia(dia).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
                    )
                    lista.forEach { m ->
                        val categoriasDe = m.categoriaIds.mapNotNull { cats[it] }
                        Row(
                            Modifier.fillMaxWidth().clickable { alEditar(m.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconoCategoria(categoriasDe.firstOrNull()?.color ?: "#8E8A9B")
                            Column(Modifier.weight(1f)) {
                                Text(m.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${categoriasDe.joinToString(" + ") { it.nombre }.ifEmpty { "Sin categoría" }} · ${nombres[m.pagadoPor.name] ?: ""}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Monto(m.monto)
                        }
                    }
                }
            }
        }
    }

    hoja?.let { g ->
        HojaCategoria(
            gasto = g, categorias = e.categorias, obligatoria = false,
            alGuardar = { vm.confirmarVarias(g, it); hoja = null },
            alTransferencia = { vm.transferencia(g); hoja = null },
            alNoEsGasto = { vm.noEsGasto(g); hoja = null },
            alCerrar = { hoja = null },
        )
    }
}

@Composable
private fun PorConfirmarFila(p: PorConfirmar, alElegir: (String) -> Unit, alOtra: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.gasto.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Monto(p.gasto.monto)
        }
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, ES).format(Date(p.gasto.fecha)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            p.sugeridas.forEach { c ->
                Surface(
                    onClick = { alElegir(c.id) }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colorDe(c.color), CircleShape))
                        Spacer(Modifier.size(6.dp))
                        Text(c.nombre, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Surface(onClick = alOtra, modifier = Modifier.height(40.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Otra", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

private fun textoDia(d: LocalDate): String = when (d) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))
}

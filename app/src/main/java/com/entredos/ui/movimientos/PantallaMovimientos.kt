package com.entredos.ui.movimientos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.ui.comun.Avatar
import com.entredos.ui.comun.EstadoVacio
import com.entredos.ui.comun.MARGEN
import com.entredos.ui.comun.Monto
import com.entredos.ui.comun.PuntoColor
import com.entredos.ui.comun.Tarjeta
import com.entredos.ui.comun.TituloPantalla
import com.entredos.ui.tema.EstiloCifras
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "CO"))

private fun textoDia(d: LocalDate): String = when (d) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> d.format(FORMATO_DIA).replaceFirstChar { it.uppercase() }
}

/** [alAbrir] recibe el id del gasto para editarlo. */
@Composable
fun PantallaMovimientos(alAbrir: (String) -> Unit, alPendientes: () -> Unit, pendientes: Int, vm: MovimientosViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    val total = e.dias.sumOf { (_, lista) -> lista.sumOf { it.monto } }
    val nombres = e.miembros.associate { it.id to it.nombre }
    val categorias = e.categorias.associateBy { it.id }
    val hayFiltro = e.filtro.texto.isNotBlank() || e.filtro.persona != null || e.filtro.categoriaId != null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TituloPantalla("Movimientos", "${if (e.soloPeriodo) "Este periodo" else "Todos"} · ${FormatoPesos.pesos(total)}") }
        if (pendientes > 0) item {
            Tarjeta(Modifier.padding(horizontal = MARGEN, vertical = 4.dp), alTocar = alPendientes, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(if (pendientes == 1) "1 gasto por clasificar" else "$pendientes gastos por clasificar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Detectados en avisos del banco", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                e.filtro.texto, vm::buscar, placeholder = { Text("Buscar por nombre o comercio") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = MARGEN, vertical = 8.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = MARGEN), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(e.soloPeriodo, { vm.fijarSoloPeriodo(true) }, { Text("Este periodo") }) }
                item { FilterChip(!e.soloPeriodo, { vm.fijarSoloPeriodo(false) }, { Text("Todos") }) }
                item { Spacer(Modifier.size(8.dp)) }
                item { FilterChip(e.filtro.persona == null, { vm.persona(null) }, { Text("Ambos") }) }
                items(e.miembros) { m ->
                    val miembro = Miembro.valueOf(m.id)
                    FilterChip(e.filtro.persona == miembro, { vm.persona(miembro) }, { Text(m.nombre) })
                }
            }
        }
        item { FiltroCategorias(e.categorias, e.filtro.categoriaId, vm::categoria) }

        if (e.dias.isEmpty()) item {
            EstadoVacio(
                Icons.AutoMirrored.Filled.ReceiptLong,
                if (hayFiltro) "Nada con estos filtros" else "Aún no hay movimientos",
                if (hayFiltro) "Prueba con otra búsqueda, otra persona u otra categoría." else "Registra un gasto desde el teclado o espera el primer aviso de tu banco.",
            )
        }
        e.dias.forEach { (dia, movimientos) ->
            item(key = dia.toString()) {
                Row(Modifier.fillMaxWidth().padding(start = MARGEN, end = MARGEN, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(textoDia(dia), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(FormatoPesos.pesos(movimientos.sumOf { it.monto }), style = MaterialTheme.typography.labelLarge.merge(EstiloCifras), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(movimientos, key = { it.id }) { m ->
                val cats = m.categoriaIds.mapNotNull { categorias[it] }
                val quien = nombres[m.pagadoPor.name] ?: ""
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.medium).clickable { alAbrir(m.id) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(quien)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cats.take(3).forEach { PuntoColor(it.color, 8) }
                            Text(
                                cats.joinToString(" + ") { it.nombre }.ifEmpty { "Categoría eliminada" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Monto(m.monto)
                }
            }
        }
    }
}

/** Fila de fichas: "Todas" y una por categoría, con su color. Tocar la marcada la desmarca. */
@Composable
fun FiltroCategorias(categorias: List<CategoriaEntity>, seleccionada: String?, alElegir: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = MARGEN, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(seleccionada == null, { alElegir(null) }, { Text("Todas") }) }
        items(categorias, key = { it.id }) { c ->
            FilterChip(
                seleccionada == c.id, { alElegir(if (seleccionada == c.id) null else c.id) }, { Text(c.nombre) },
                leadingIcon = { PuntoColor(c.color, 10) },
            )
        }
    }
}

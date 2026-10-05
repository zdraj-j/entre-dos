package com.entredos.ui.movimientos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.data.local.entity.CategoriaEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [alAbrir] recibe el id del gasto para editarlo (pantalla de edición existente). */
@Composable
fun PantallaMovimientos(alAbrir: (String) -> Unit, alPendientes: () -> Unit, pendientes: Int, vm: MovimientosViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    val formatoDia = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "CO"))

    Column(Modifier.fillMaxSize()) {
        if (pendientes > 0) androidx.compose.material3.TextButton(onClick = alPendientes, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Pendientes por clasificar: $pendientes")
        }
        OutlinedTextField(
            e.filtro.texto, vm::buscar, placeholder = { Text("Buscar por nombre o comercio") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(e.soloPeriodo, { vm.fijarSoloPeriodo(true) }, { Text("Este periodo") }) }
            item { FilterChip(!e.soloPeriodo, { vm.fijarSoloPeriodo(false) }, { Text("Todos") }) }
            item { FilterChip(e.filtro.persona == null, { vm.persona(null) }, { Text("Ambos") }) }
            items(e.miembros) { m ->
                val miembro = Miembro.valueOf(m.id)
                FilterChip(e.filtro.persona == miembro, { vm.persona(miembro) }, { Text(m.nombre) })
            }
        }
        FiltroCategorias(e.categorias, e.filtro.categoriaId, vm::categoria)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (e.dias.isEmpty()) item {
                Text("No hay movimientos con estos filtros.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.dias.forEach { (dia, movimientos) ->
                item(key = dia.toString()) {
                    Text(
                        dia.format(formatoDia).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(movimientos, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { alAbrir(m.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.nombre, style = MaterialTheme.typography.bodyLarge)
                            val nombres = e.categorias.filter { it.id in m.categoriaIds }.joinToString(", ") { it.nombre }
                            val quien = e.miembros.firstOrNull { it.id == m.pagadoPor.name }?.nombre ?: ""
                            Text("$nombres · $quien", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(FormatoPesos.pesos(m.monto), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Fila de fichas: "Todas" y una por categoría. Tocar la marcada la desmarca. */
@Composable
fun FiltroCategorias(categorias: List<CategoriaEntity>, seleccionada: String?, alElegir: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(seleccionada == null, { alElegir(null) }, { Text("Todas") }) }
        items(categorias, key = { it.id }) { c ->
            FilterChip(seleccionada == c.id, { alElegir(if (seleccionada == c.id) null else c.id) }, { Text(c.nombre) })
        }
    }
}

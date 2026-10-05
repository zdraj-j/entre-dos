package com.entredos.ui.hogar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Miembro
import com.entredos.data.importacion.nombreTitular
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.repository.HogarRepositorio
import com.entredos.ui.comun.AvatarTitular
import com.entredos.ui.comun.EncabezadoPantalla
import com.entredos.ui.comun.FilaOpcion
import com.entredos.ui.comun.Tarjeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoHogar(
    val nombre: String = "Nuestro hogar",
    val miembros: List<com.entredos.data.local.entity.MiembroEntity> = emptyList(),
    val yo: Miembro? = null,
    val categorias: Int = 0,
    val fijos: Int = 0,
    val meses: Int = 0,
)

@HiltViewModel
class HogarViewModel @Inject constructor(db: EntreDosDatabase, private val repo: HogarRepositorio) : ViewModel() {
    val estado = combine(
        combine(db.hogarDao().observar(), db.miembroDao().observar(), db.dispositivoDao().observar()) { h, m, d -> Triple(h, m, d?.miembro) },
        db.categoriaDao().observar(), db.recurrenteDao().observar(), db.periodoCerradoDao().observar(),
    ) { (h, m, yo), cats, fijos, meses ->
        EstadoHogar(h?.nombre ?: "Nuestro hogar", m, yo, cats.size, fijos.size, meses.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoHogar())

    fun renombrar(m: Miembro, nombre: String) { viewModelScope.launch { runCatching { repo.renombrar(m, nombre) } } }
}

/** Pestaña Hogar: quiénes son y lo que se configura una vez y se revisa de vez en cuando. */
@Composable
fun PantallaHogar(alCategorias: () -> Unit, alFijos: () -> Unit, alMeses: () -> Unit, alAjustes: () -> Unit, vm: HogarViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    var renombrando by remember { mutableStateOf<Miembro?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoPantalla(e.nombre, "Lo que se configura una vez y se revisa de vez en cuando")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tarjeta(contenidoPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Miembro.entries.forEach { m ->
                    val unido = e.miembros.any { it.id == m.name }
                    val nombre = nombreTitular(e.miembros, m)
                    com.entredos.ui.comun.FilaAjuste(
                        texto = if (unido) nombre else "Tu pareja aún no se une",
                        ayuda = if (!unido) "Que conecte la misma cuenta de Google del hogar en su teléfono" else null,
                        valor = if (m == e.yo) "Tú" else "",
                        alTocar = if (unido) ({ renombrando = m }) else null,
                        control = { Row(verticalAlignment = Alignment.CenterVertically) { AvatarTitular(nombre, m == Miembro.A) } },
                    )
                }
            }
            Tarjeta(contenidoPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                FilaOpcion(Icons.Default.DonutLarge, "Categorías y topes", "${e.categorias}", alCategorias)
                FilaOpcion(Icons.Default.Repeat, "Gastos fijos", "${e.fijos}", alFijos)
                FilaOpcion(Icons.Default.History, "Meses anteriores", "${e.meses}", alMeses)
                FilaOpcion(Icons.Default.Settings, "Ajustes", alTocar = alAjustes)
            }
        }
    }

    renombrando?.let { m ->
        var nombre by remember { mutableStateOf(nombreTitular(e.miembros, m)) }
        AlertDialog(
            onDismissRequest = { renombrando = null },
            title = { Text("Cambiar nombre") },
            text = { OutlinedTextField(nombre, { nombre = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renombrar(m, nombre); renombrando = null }, enabled = nombre.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renombrando = null }) { Text("Cancelar") } },
        )
    }
}

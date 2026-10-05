package com.entredos.ui.importacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.entredos.core.EstadoLote
import com.entredos.data.importacion.ImportadorRepositorio
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.LoteImportacionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ImportacionesViewModel @Inject constructor(db: EntreDosDatabase, private val repo: ImportadorRepositorio) : ViewModel() {
    val lotes = db.loteImportacionDao().observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deshaciendo = MutableStateFlow<String?>(null)

    fun deshacer(lote: LoteImportacionEntity) {
        viewModelScope.launch {
            deshaciendo.value = lote.id
            try { repo.deshacer(lote) } finally { deshaciendo.value = null }
        }
    }
}

/** Ajustes › Importaciones: cada lote con sus conteos y la opción de deshacerlo completo. */
@Composable
fun PantallaImportaciones(alImportar: () -> Unit, vm: ImportacionesViewModel = hiltViewModel()) {
    val lotes by vm.lotes.collectAsState()
    val deshaciendo by vm.deshaciendo.collectAsState()
    var confirmar by remember { mutableStateOf<LoteImportacionEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Importaciones", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = alImportar) { Text("Importar datos") }
        if (lotes.isEmpty()) Text("Aún no has importado nada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(lotes, key = { it.id }) { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.archivo)
                        Text(DateFormat.getDateTimeInstance().format(Date(l.fecha)), style = MaterialTheme.typography.bodySmall)
                        Text("${l.gastos} gastos · ${l.categorias} categorías · ${l.deudas} deudas", style = MaterialTheme.typography.bodySmall)
                    }
                    when {
                        l.estado == EstadoLote.DESHECHO -> Text("Deshecha", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        deshaciendo == l.id -> Text("Deshaciendo…")
                        else -> TextButton(onClick = { confirmar = l }) { Text("Deshacer") }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    confirmar?.let { l ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text("¿Deshacer esta importación?") },
            text = { Text("Se borran ${l.gastos} gastos, sus categorías nuevas (salvo las que ya usaste después), ${l.deudas} deudas y los periodos anteriores creados. También desaparecen en el teléfono de tu pareja.") },
            confirmButton = { TextButton(onClick = { vm.deshacer(l); confirmar = null }) { Text("Deshacer") } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
        )
    }
}

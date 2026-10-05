package com.entredos.ui.hogar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.sync.CambiosSinSubir
import com.entredos.data.sync.EstadoSync
import com.entredos.data.sync.ServicioHogar
import com.entredos.data.sync.Sincronizador
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SincronizacionViewModel @Inject constructor(
    db: EntreDosDatabase,
    estadoSync: EstadoSync,
    private val sincronizador: Sincronizador,
    private val servicio: ServicioHogar,
) : ViewModel() {
    val estado = estadoSync.estado
    val sinSubir = db.cambioPendienteDao().contar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Cuántos cambios se perderían al salir; null si no hay aviso pendiente. */
    val avisoSalir = MutableStateFlow<Int?>(null)
    val salio = MutableStateFlow(false)

    fun sincronizarAhora() { viewModelScope.launch { sincronizador.sincronizar() } }

    fun salir(forzar: Boolean) {
        viewModelScope.launch {
            try {
                servicio.salir(forzar)
                avisoSalir.value = null
                salio.value = true
            } catch (e: CambiosSinSubir) {
                avisoSalir.value = e.cuantos
            }
        }
    }
}

/** Bloque para la pantalla de Ajustes. [alReconectar] y [alSalir] llevan a PantallaInicioHogar. */
@Composable
fun SeccionSincronizacion(alReconectar: () -> Unit, alSalir: () -> Unit, vm: SincronizacionViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsState()
    val sinSubir by vm.sinSubir.collectAsState()
    val aviso by vm.avisoSalir.collectAsState()
    val salio by vm.salio.collectAsState()
    if (salio) { alSalir(); return }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sincronización", style = MaterialTheme.typography.titleMedium)
        Text("Cuenta del hogar: ${estado.correo ?: "sin conectar"}")
        Text("Última sincronización: ${estado.ultimaSyncOk?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "nunca"}")
        Text("Cambios sin subir: $sinSubir")
        estado.ultimoError?.let { Text("Último error: $it", color = MaterialTheme.colorScheme.error) }
        if (estado.requiereInicio) {
            Text("El permiso de Drive venció. Vuelve a conectar la cuenta.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = alReconectar) { Text("Volver a conectar") }
        }
        OutlinedButton(onClick = vm::sincronizarAhora) { Text("Sincronizar ahora") }
        TextButton(onClick = { vm.salir(forzar = false) }) { Text("Salir del hogar") }
    }

    aviso?.let { cuantos ->
        AlertDialog(
            onDismissRequest = { vm.avisoSalir.value = null },
            title = { Text("Hay cambios sin subir") },
            text = { Text("$cuantos cambios aún no llegan a Drive. Si sales ahora se pierden. Conéctate a internet y sincroniza primero.") },
            confirmButton = { TextButton(onClick = { vm.salir(forzar = true) }) { Text("Salir igual") } },
            dismissButton = { TextButton(onClick = { vm.avisoSalir.value = null }) { Text("Cancelar") } },
        )
    }
}

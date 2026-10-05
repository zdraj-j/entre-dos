package com.entredos.ui.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.entredos.data.local.AjustesLocales
import com.entredos.data.wifi.SincronizacionWifi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class WifiViewModel @Inject constructor(private val ajustesLocales: AjustesLocales, private val wifi: SincronizacionWifi) : ViewModel() {
    val ajustes = ajustesLocales.ajustes
    val estado = wifi.estado
    fun activar(activo: Boolean) = ajustesLocales.wifi(activo)
    fun ahora() = wifi.intercambiarAhora()
}

/** Ajustes › Wifi en casa. */
@Composable
fun SeccionWifi(vm: WifiViewModel = hiltViewModel()) {
    val ajustes by vm.ajustes.collectAsState()
    val e by vm.estado.collectAsState()
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sincronizar por wifi en casa", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Con la app abierta y los dos teléfonos en la misma red, se pasan los cambios directamente, " +
                        "aunque no haya internet. Va cifrado y solo entre teléfonos de este hogar. Drive sigue siendo la copia principal.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(ajustes.wifiActivo, vm::activar)
        }
        if (ajustes.wifiActivo) {
            Text(
                when {
                    !e.activo -> "Inactivo (se activa con la app abierta)"
                    e.parejaVisible -> "El otro teléfono está en la red"
                    else -> "Buscando el otro teléfono en esta red…"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            e.ultimoIntercambio?.let { Text("Último intercambio: ${DateFormat.getDateTimeInstance().format(Date(it))} · ${e.ultimoResumen ?: ""}", style = MaterialTheme.typography.bodySmall) }
            e.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (e.parejaVisible) OutlinedButton(onClick = vm::ahora) { Text("Intercambiar ahora") }
        }
    }
}

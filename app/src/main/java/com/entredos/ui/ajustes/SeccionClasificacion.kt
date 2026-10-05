package com.entredos.ui.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ClasificacionViewModel @Inject constructor(private val ajustesLocales: AjustesLocales) : ViewModel() {
    val ajustes = ajustesLocales.ajustes
    fun manual(activo: Boolean) = ajustesLocales.clasificacionManual(activo)
}

/** Ajustes › Clasificación de gastos. */
@Composable
fun SeccionClasificacion(vm: ClasificacionViewModel = hiltViewModel()) {
    val ajustes by vm.ajustes.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Elegir presupuesto siempre a mano", style = MaterialTheme.typography.titleSmall)
            Text(
                "Los gastos detectados no se clasifican solos por recurrentes ni por reglas aprendidas: todos quedan pendientes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(ajustes.clasificacionManual, vm::manual)
    }
}

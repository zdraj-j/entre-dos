package com.entredos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.entredos.data.notificaciones.AvisosGasto
import com.entredos.ui.AppEntreDos
import com.entredos.ui.tema.TemaEntreDos
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Gasto del aviso "Gasto detectado" que abrió la app: se muestra la ventana obligatoria. */
    private val gastoDesdeAviso = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        leer(intent)
        setContent {
            TemaEntreDos {
                AppEntreDos(gastoDesdeAviso = gastoDesdeAviso.value, alCerrarAviso = { gastoDesdeAviso.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        leer(intent)
    }

    private fun leer(intent: Intent?) {
        intent?.getStringExtra(AvisosGasto.EXTRA_GASTO_PENDIENTE)?.let { gastoDesdeAviso.value = it }
    }
}

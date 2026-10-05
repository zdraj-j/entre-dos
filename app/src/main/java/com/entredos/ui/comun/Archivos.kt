package com.entredos.ui.comun

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guarda un archivo donde la persona elija. Uso:
 * `val guardar = recordarGuardado("text/csv")` y luego `guardar("nombre.csv") { bytes }`.
 */
@Composable
fun recordarGuardado(tipo: String): (String, suspend () -> ByteArray) -> Unit {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var contenido by remember { mutableStateOf<(suspend () -> ByteArray)?>(null) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(tipo)) { uri ->
        val generar = contenido ?: return@rememberLauncherForActivityResult
        if (uri != null) alcance.launch {
            val bytes = generar()
            withContext(Dispatchers.IO) { contexto.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
        contenido = null
    }
    return { nombre, generar -> contenido = generar; lanzador.launch(nombre) }
}

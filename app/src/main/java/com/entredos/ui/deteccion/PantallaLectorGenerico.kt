package com.entredos.ui.deteccion

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.entredos.core.Movimientos
import com.entredos.data.local.AjustesLocales
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AppInstalada(val paquete: String, val nombre: String)

@HiltViewModel
class LectorGenericoViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val ajustesLocales: AjustesLocales,
) : ViewModel() {
    val ajustes = ajustesLocales.ajustes

    /** Apps con ícono en el lanzador (requiere el bloque <queries> del manifiesto). */
    val apps: List<AppInstalada> = run {
        val pm = context.packageManager
        val lanzables = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(lanzables, 0)
            .map { AppInstalada(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.paquete != context.packageName }
            .distinctBy { it.paquete }
            .sortedBy { Movimientos.normalizar(it.nombre) }
    }

    fun activar(activo: Boolean) = ajustesLocales.lectorGenerico(activo)
    fun alternar(paquete: String) = ajustesLocales.alternarApp(paquete)
}

/** Detección › "Leer otras apps (experimental)". */
@Composable
fun PantallaLectorGenerico(vm: LectorGenericoViewModel = hiltViewModel()) {
    val ajustes by vm.ajustes.collectAsState()
    var buscar by remember { mutableStateOf("") }
    val visibles = remember(buscar) {
        val b = Movimientos.normalizar(buscar.trim())
        vm.apps.filter { b.isEmpty() || Movimientos.normalizar(it.nombre).contains(b) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Leer otras apps (experimental)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Para bancos sin lector propio. Solo lee las apps que marques, y lo que detecte siempre " +
                        "queda pendiente para que lo revises. En Diagnóstico puedes ver qué capturó.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(ajustes.lectorGenericoActivo, vm::activar)
        }
        if (ajustes.lectorGenericoActivo) {
            OutlinedTextField(buscar, { buscar = it }, placeholder = { Text("Buscar app") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("${ajustes.appsGenericas.size} apps elegidas", style = MaterialTheme.typography.labelLarge)
            LazyColumn(Modifier.fillMaxSize()) {
                items(visibles, key = { it.paquete }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.alternar(app.paquete) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(app.paquete in ajustes.appsGenericas, { vm.alternar(app.paquete) })
                        Column {
                            Text(app.nombre)
                            Text(app.paquete, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

package com.entredos.ui.permisos

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.entredos.ui.comun.Tarjeta
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.entredos.data.notificaciones.ServicioNotificaciones

data class EstadoPermisos(val notificaciones: Boolean, val bateria: Boolean, val avisos: Boolean)

object Permisos {
    fun estado(c: Context) = EstadoPermisos(
        notificaciones = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName),
        bateria = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName),
        avisos = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    )

    fun abrirAccesoNotificaciones(c: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(c, ServicioNotificaciones::class.java).flattenToString())
        } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { c.startActivity(intent) }.onFailure { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    @SuppressLint("BatteryLife")
    fun pedirSinOptimizacion(c: Context) {
        runCatching { c.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))) }
            .onFailure { c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

/** Estado de los permisos que se vuelve a leer cada vez que la app vuelve a primer plano. */
@Composable
fun recordarPermisos(): EstadoPermisos {
    val c = LocalContext.current
    var estado by remember { mutableStateOf(Permisos.estado(c)) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) estado = Permisos.estado(c) }
        ciclo.lifecycle.addObserver(o)
        onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    return estado
}

/** Pantalla obligatoria (sección 1.1): sin acceso a notificaciones la app no sigue. */
@Composable
fun PantallaPermisos(estado: EstadoPermisos, alContinuar: () -> Unit) {
    val c = LocalContext.current
    val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Antes de empezar", style = MaterialTheme.typography.headlineMedium)
        Text("Entre dos registra solo los gastos leyendo los avisos de tus bancos. El texto de las notificaciones nunca sale de tu teléfono.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TarjetaPermiso(
            Icons.Default.Notifications, "Acceso a notificaciones", "Obligatorio. Es como la app ve los avisos del banco.",
            estado.notificaciones, "Dar acceso",
        ) { Permisos.abrirAccesoNotificaciones(c) }
        TarjetaPermiso(
            Icons.Default.BatteryChargingFull, "Sin optimización de batería", "Recomendado. Evita que Android apague la detección, sobre todo en Xiaomi y Samsung.",
            estado.bateria, "Quitar optimización",
        ) { Permisos.pedirSinOptimizacion(c) }
        TarjetaPermiso(
            Icons.Default.NotificationsActive, "Avisos de la app", "Recomendado. Para mostrarte \"Gasto detectado\" y clasificarlo con un toque.",
            estado.avisos, "Permitir",
        ) { if (Build.VERSION.SDK_INT >= 33) pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = alContinuar, enabled = estado.notificaciones, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continuar") }
    }
}

@Composable
private fun TarjetaPermiso(icono: ImageVector, titulo: String, texto: String, listo: Boolean, accion: String, alPedir: () -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (listo) Icons.Default.CheckCircle else icono, contentDescription = null, tint = if (listo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!listo) OutlinedButton(onClick = alPedir) { Text(accion) }
    }
}

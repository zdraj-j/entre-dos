package com.entredos.data.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.entredos.core.FormatoPesos
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.GastoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avisos de la app (sección 2.4 del plan):
 * - "Gasto detectado" con hasta 3 botones de categoría; tocar el cuerpo abre la ventana obligatoria.
 * - "Gasto registrado", breve, que se quita solo.
 */
@Singleton
class AvisosGasto @Inject constructor(@ApplicationContext private val context: Context) {

    private val gestor = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CANAL_DETECTADOS, "Gastos detectados", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CANAL_REGISTRADOS, "Gastos registrados", NotificationManager.IMPORTANCE_LOW))
        }
    }

    /** Lo que devolvió IngresoNotificacion, convertido en el aviso que corresponde. */
    fun mostrar(resultado: ResultadoIngreso?) {
        when (resultado) {
            is ResultadoIngreso.Pendiente -> detectado(resultado.gasto, resultado.sugerencias)
            is ResultadoIngreso.Confirmado -> registrado(resultado.gasto, resultado.categoria.nombre)
            ResultadoIngreso.Duplicado, null -> Unit
        }
    }

    fun detectado(gasto: GastoEntity, sugerencias: List<CategoriaEntity>) {
        val abrir = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_GASTO_PENDIENTE, gasto.id)
        }
        val aviso = NotificationCompat.Builder(context, CANAL_DETECTADOS)
            .setSmallIcon(android.R.drawable.ic_menu_add) // cámbialo por el ícono de la app
            .setContentTitle("Gasto detectado: ${FormatoPesos.pesos(gasto.monto)}")
            .setContentText(gasto.nombre)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, id(gasto.id), abrir, FLAGS))
        sugerencias.take(3).forEachIndexed { i, c ->
            val accion = Intent(context, ReceptorAccionesAviso::class.java).apply {
                action = ReceptorAccionesAviso.ACCION_CLASIFICAR
                putExtra(ReceptorAccionesAviso.EXTRA_GASTO, gasto.id)
                putExtra(ReceptorAccionesAviso.EXTRA_CATEGORIA, c.id)
            }
            aviso.addAction(0, c.nombre, PendingIntent.getBroadcast(context, id(gasto.id) * 4 + i + 1, accion, FLAGS))
        }
        publicar(id(gasto.id), aviso)
    }

    fun registrado(gasto: GastoEntity, categoria: String) {
        val aviso = NotificationCompat.Builder(context, CANAL_REGISTRADOS)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Gasto registrado")
            .setContentText("${FormatoPesos.pesos(gasto.monto)} en $categoria")
            .setTimeoutAfter(4_000)
            .setAutoCancel(true)
        publicar(id(gasto.id) + 1, aviso)
    }

    fun cancelar(gastoId: String) = gestor.cancel(id(gastoId))

    private fun publicar(id: Int, aviso: NotificationCompat.Builder) {
        val permitido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permitido) gestor.notify(id, aviso.build())
    }

    private fun id(gastoId: String) = gastoId.hashCode() and 0x0FFFFFFF

    companion object {
        const val CANAL_DETECTADOS = "gastos_detectados"
        const val CANAL_REGISTRADOS = "gastos_registrados"
        /** Extra del intent que abre la app desde el aviso: id del gasto para la ventana obligatoria. */
        const val EXTRA_GASTO_PENDIENTE = "gastoPendienteId"
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}

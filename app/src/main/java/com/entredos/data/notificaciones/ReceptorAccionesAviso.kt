package com.entredos.data.notificaciones

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.entredos.core.EstadoGasto
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.repository.GastosRepositorio
import com.entredos.data.repository.ParteNueva
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Botón de categoría del aviso "Gasto detectado": clasifica sin abrir la app. */
@AndroidEntryPoint
class ReceptorAccionesAviso : BroadcastReceiver() {

    @Inject lateinit var db: EntreDosDatabase
    @Inject lateinit var gastos: GastosRepositorio
    @Inject lateinit var avisos: AvisosGasto

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACCION_CLASIFICAR) return
        val gastoId = intent.getStringExtra(EXTRA_GASTO) ?: return
        val categoriaId = intent.getStringExtra(EXTRA_CATEGORIA) ?: return
        val pendiente = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val gasto = db.gastoDao().porId(gastoId)
                avisos.cancelar(gastoId)
                if (gasto != null && gasto.estado == EstadoGasto.PENDIENTE) {
                    val confirmado = gastos.confirmarPendiente(gasto, gasto.nombre, listOf(ParteNueva(categoriaId, gasto.monto)))
                    db.categoriaDao().porId(categoriaId)?.let { avisos.registrado(confirmado, it.nombre) }
                }
            } finally {
                pendiente.finish()
            }
        }
    }

    companion object {
        const val ACCION_CLASIFICAR = "com.entredos.CLASIFICAR_GASTO"
        const val EXTRA_GASTO = "gastoId"
        const val EXTRA_CATEGORIA = "categoriaId"
    }
}

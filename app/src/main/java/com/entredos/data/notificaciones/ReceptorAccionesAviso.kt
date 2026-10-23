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

/**
 * Botones del aviso "Gasto detectado": clasifican o descartan sin abrir la app. No se publica un aviso de
 * confirmación: quien tocó el botón ya sabe lo que hizo.
 */
@AndroidEntryPoint
class ReceptorAccionesAviso : BroadcastReceiver() {

    @Inject lateinit var db: EntreDosDatabase
    @Inject lateinit var gastos: GastosRepositorio
    @Inject lateinit var avisos: AvisosGasto

    override fun onReceive(context: Context, intent: Intent) {
        val gastoId = intent.getStringExtra(EXTRA_GASTO) ?: return
        // null = "No es un gasto".
        val categoriaId = when (intent.action) {
            ACCION_CLASIFICAR -> intent.getStringExtra(EXTRA_CATEGORIA) ?: return
            ACCION_NO_ES_GASTO -> null
            else -> return
        }
        val pendiente = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val gasto = db.gastoDao().porId(gastoId)
                avisos.cancelar(gastoId)
                if (gasto != null && gasto.estado == EstadoGasto.PENDIENTE) {
                    if (categoriaId != null) gastos.confirmarPendiente(gasto, gasto.nombre, listOf(ParteNueva(categoriaId, gasto.monto)))
                    else gastos.ignorar(gasto)
                }
            } finally {
                pendiente.finish()
            }
        }
    }

    companion object {
        const val ACCION_CLASIFICAR = "com.entredos.CLASIFICAR_GASTO"
        const val ACCION_NO_ES_GASTO = "com.entredos.NO_ES_GASTO"
        const val EXTRA_GASTO = "gastoId"
        const val EXTRA_CATEGORIA = "categoriaId"
    }
}

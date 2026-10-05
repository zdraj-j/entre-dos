package com.entredos.data.repository

import androidx.room.withTransaction
import com.entredos.core.CalculadoraDeudas
import com.entredos.core.Deuda
import com.entredos.core.Miembro
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.AbonoEntity
import com.entredos.data.local.entity.DeudaManualEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeudasRepositorio @Inject constructor(
    private val db: EntreDosDatabase,
    private val escritor: Escritor,
) {

    /** Todas las deudas, recalculadas cada vez que cambia un gasto, una deuda manual o un abono. */
    fun observar(): Flow<List<Deuda>> = combine(
        db.parteGastoDao().observarParaDeudas(),
        db.deudaManualDao().observarParaDeudas(),
        db.abonoDao().observarTotales(),
        db.miembroDao().observar(),
    ) { partes, manuales, abonos, miembros ->
        CalculadoraDeudas.calcular(
            partes = partes,
            manuales = manuales,
            abonadoPorDeuda = abonos.associate { it.deudaId to it.total },
            parejaUnida = miembros.size == 2,
        )
    }

    suspend fun abonar(deuda: Deuda, monto: Long, fecha: Long) {
        require(monto in 1..deuda.pendiente) { "El abono debe estar entre 1 y ${deuda.pendiente}" }
        escritor.guardar(Tablas.ABONO, AbonoEntity(deudaId = deuda.id, monto = monto, fecha = fecha))
    }

    /** "Saldar todo" y el cierre de periodo: un abono por lo pendiente de cada deuda abierta. */
    suspend fun saldarTodo(deudas: List<Deuda>, fecha: Long, porCierre: Boolean = false) = db.withTransaction {
        CalculadoraDeudas.abonosParaSaldarTodo(deudas).forEach { (id, monto) ->
            escritor.guardar(Tablas.ABONO, AbonoEntity(deudaId = id, monto = monto, fecha = fecha, porCierre = porCierre))
        }
    }

    /** "Registrar un pago": [deudor] le paga [monto] a su pareja; se abona a sus deudas de la más vieja a la más nueva. */
    suspend fun registrarPago(deudas: List<Deuda>, deudor: Miembro, monto: Long, fecha: Long) = db.withTransaction {
        CalculadoraDeudas.repartirPago(deudas, deudor, monto).forEach { (id, abono) ->
            escritor.guardar(Tablas.ABONO, AbonoEntity(deudaId = id, monto = abono, fecha = fecha))
        }
    }

    suspend fun agregarManual(deudor: Miembro, monto: Long, concepto: String, fecha: Long, categoriaId: String? = null) {
        require(monto > 0) { "El monto debe ser mayor que cero" }
        escritor.guardar(
            Tablas.DEUDA_MANUAL,
            DeudaManualEntity(deudor = deudor, acreedor = deudor.otro, monto = monto, concepto = concepto, categoriaId = categoriaId, fecha = fecha),
        )
    }
}

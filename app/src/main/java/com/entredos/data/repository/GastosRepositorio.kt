package com.entredos.data.repository

import androidx.room.withTransaction
import com.entredos.core.EstadoGasto
import com.entredos.core.Miembro
import com.entredos.core.OrigenGasto
import com.entredos.core.Reparto
import com.entredos.core.TipoGasto
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.ParteGastoEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Cuánto va a cada categoría y, si se ajustó el reparto, cuánto asume quien pagó. */
data class ParteNueva(val categoriaId: String, val monto: Long, val cargoPagador: Long? = null)

@Singleton
class GastosRepositorio @Inject constructor(
    private val db: EntreDosDatabase,
    private val escritor: Escritor,
) {

    /**
     * Registra un gasto confirmado con sus partes. Es lo que hace el teclado al tocar una categoría.
     * Devuelve el gasto guardado, para poder ofrecer "Deshacer".
     */
    suspend fun registrar(
        nombre: String,
        fecha: Long,
        pagadoPor: Miembro,
        partes: List<ParteNueva>,
        comercio: String? = null,
        tipo: TipoGasto = TipoGasto.GASTO,
        origen: OrigenGasto = OrigenGasto.MANUAL,
        appBanco: String? = null,
    ): GastoEntity {
        val total = partes.sumOf { it.monto }
        Reparto.validarDivision(total, partes.map { it.monto })?.let { throw IllegalArgumentException(it) }

        return db.withTransaction {
            val gasto = escritor.guardar(
                Tablas.GASTO,
                GastoEntity(
                    monto = total, nombre = nombre, comercio = comercio, fecha = fecha,
                    pagadoPor = pagadoPor, tipo = tipo, estado = EstadoGasto.CONFIRMADO,
                    origen = origen, appBanco = appBanco,
                ),
            )
            guardarPartes(gasto, partes)
            gasto
        }
    }

    /** Monto repartido en partes iguales entre varias categorías (el caso normal del teclado). */
    fun partesIguales(monto: Long, categoriaIds: List<String>): List<ParteNueva> =
        Reparto.dividirEnPartesIguales(monto, categoriaIds.size)
            .zip(categoriaIds) { m, id -> ParteNueva(id, m) }

    /**
     * Cambia monto, nombre, quién pagó o categorías. Las partes viejas se eliminan y se crean
     * nuevas, así las deudas se recalculan solas. Si solo cambia el nombre, las partes se
     * conservan y la deuda sigue con sus abonos.
     */
    suspend fun editar(gasto: GastoEntity, partes: List<ParteNueva>?): GastoEntity = db.withTransaction {
        val guardado = escritor.guardar(Tablas.GASTO, gasto.copy(monto = partes?.sumOf { it.monto } ?: gasto.monto))
        if (partes != null) {
            db.parteGastoDao().porGasto(gasto.id).forEach { escritor.eliminar(Tablas.PARTE_GASTO, it) }
            guardarPartes(guardado, partes)
        }
        guardado
    }

    /** Borra el gasto y sus partes; con ellas desaparecen las deudas que generó. */
    suspend fun eliminar(gasto: GastoEntity) = db.withTransaction {
        db.parteGastoDao().porGasto(gasto.id).forEach { escritor.eliminar(Tablas.PARTE_GASTO, it) }
        escritor.eliminar(Tablas.GASTO, gasto)
    }

    private suspend fun guardarPartes(gasto: GastoEntity, partes: List<ParteNueva>) {
        for (p in partes) {
            val categoria = requireNotNull(db.categoriaDao().porId(p.categoriaId)) { "Categoría inexistente: ${p.categoriaId}" }
            val cargos = Reparto.repartir(p.monto, categoria.responsable, gasto.pagadoPor, p.cargoPagador)
            escritor.guardar(
                Tablas.PARTE_GASTO,
                ParteGastoEntity(gastoId = gasto.id, categoriaId = p.categoriaId, monto = p.monto, cargoA = cargos.cargoA, cargoB = cargos.cargoB),
            )
        }
    }
}

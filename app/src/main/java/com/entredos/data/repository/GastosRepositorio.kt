package com.entredos.data.repository

import androidx.room.withTransaction
import com.entredos.core.Clasificacion
import com.entredos.core.EstadoGasto
import com.entredos.core.Miembro
import com.entredos.core.OrigenGasto
import com.entredos.core.Reparto
import com.entredos.core.ReglaInfo
import com.entredos.core.TipoGasto
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.ParteGastoEntity
import com.entredos.data.local.entity.ReglaComercioEntity
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
        origen: OrigenGasto = OrigenGasto.MANUAL,
        appBanco: String? = null,
    ): GastoEntity {
        val total = partes.sumOf { it.monto }
        Reparto.validarDivision(total, partes.map { it.monto })?.let { throw IllegalArgumentException(it) }
        return db.withTransaction {
            val gasto = escritor.guardar(
                Tablas.GASTO,
                GastoEntity(
                    monto = total, nombre = nombre, comercio = comercio, fecha = fecha, pagadoPor = pagadoPor,
                    tipo = TipoGasto.GASTO, estado = EstadoGasto.CONFIRMADO, origen = origen, appBanco = appBanco,
                ),
            )
            guardarPartes(gasto, partes)
            aprenderSiCorresponde(gasto, partes)
            gasto
        }
    }

    /** Gasto detectado en una notificación que espera clasificación. Aún no tiene partes. */
    suspend fun registrarPendiente(monto: Long, nombre: String, comercio: String?, fecha: Long, pagadoPor: Miembro, appBanco: String): GastoEntity {
        require(monto > 0) { "El monto debe ser mayor que cero" }
        return escritor.guardar(
            Tablas.GASTO,
            GastoEntity(
                monto = monto, nombre = nombre, comercio = comercio, fecha = fecha, pagadoPor = pagadoPor,
                estado = EstadoGasto.PENDIENTE, origen = OrigenGasto.NOTIFICACION, appBanco = appBanco,
            ),
        )
    }

    /**
     * Clasifica un pendiente: con categorías queda confirmado como gasto; con [transferencia]
     * queda confirmado sin partes y no cuenta en el presupuesto.
     */
    suspend fun confirmarPendiente(gasto: GastoEntity, nombre: String, partes: List<ParteNueva>, transferencia: Boolean = false): GastoEntity =
        db.withTransaction {
            if (transferencia) {
                escritor.guardar(Tablas.GASTO, gasto.copy(nombre = nombre, tipo = TipoGasto.TRANSFERENCIA, estado = EstadoGasto.CONFIRMADO))
            } else {
                val total = partes.sumOf { it.monto }
                Reparto.validarDivision(total, partes.map { it.monto })?.let { throw IllegalArgumentException(it) }
                val confirmado = escritor.guardar(
                    Tablas.GASTO,
                    gasto.copy(nombre = nombre, monto = total, tipo = TipoGasto.GASTO, estado = EstadoGasto.CONFIRMADO),
                )
                guardarPartes(confirmado, partes)
                aprenderSiCorresponde(confirmado, partes)
                confirmado
            }
        }

    /** "No registrar": queda ignorado (no se borra, para que no vuelva a entrar como duplicado). */
    suspend fun ignorar(gasto: GastoEntity): GastoEntity =
        escritor.guardar(Tablas.GASTO, gasto.copy(estado = EstadoGasto.IGNORADO))

    /** Monto repartido en partes iguales entre varias categorías (el caso normal del teclado). */
    fun partesIguales(monto: Long, categoriaIds: List<String>): List<ParteNueva> =
        Reparto.dividirEnPartesIguales(monto, categoriaIds.size)
            .zip(categoriaIds) { m, id -> ParteNueva(id, m) }

    /**
     * Cambia monto, nombre, quién pagó o categorías. Las partes viejas se eliminan y se crean
     * nuevas, así las deudas se recalculan solas. Si solo cambia el nombre ([partes] = null),
     * las partes se conservan y la deuda sigue con sus abonos.
     */
    suspend fun editar(gasto: GastoEntity, partes: List<ParteNueva>?): GastoEntity = db.withTransaction {
        val guardado = escritor.guardar(Tablas.GASTO, gasto.copy(monto = partes?.sumOf { it.monto } ?: gasto.monto))
        if (partes != null) {
            db.parteGastoDao().porGasto(gasto.id).forEach { escritor.eliminar(Tablas.PARTE_GASTO, it) }
            guardarPartes(guardado, partes)
        }
        guardado
    }

    /** Borra el gasto y sus partes; con ellas desaparecen las deudas que generó. También es el "Deshacer". */
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

    /** Un comercio confirmado en una sola categoría suma a su regla (sección 11 del plan). */
    private suspend fun aprenderSiCorresponde(gasto: GastoEntity, partes: List<ParteNueva>) {
        val comercio = gasto.comercio?.takeIf { it.isNotBlank() } ?: return
        val categoriaId = partes.singleOrNull()?.categoriaId ?: return
        val palabra = Clasificacion.palabraDeComercio(comercio).ifBlank { return }
        val actual = db.reglaComercioDao().porPalabra(palabra)
        val nueva = Clasificacion.aprender(
            actual?.let { ReglaInfo(it.id, it.palabra, it.categoriaId, it.confirmaciones, it.automatica) },
            categoriaId,
        )
        escritor.guardar(
            Tablas.REGLA_COMERCIO,
            (actual ?: ReglaComercioEntity(palabra = palabra, categoriaId = categoriaId))
                .copy(categoriaId = nueva.categoriaId, confirmaciones = nueva.confirmaciones, automatica = nueva.automatica),
        )
    }
}

package com.entredos.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.entredos.core.DeudaManualInfo
import com.entredos.core.ParteParaDeuda
import com.entredos.data.local.entity.AbonoEntity
import com.entredos.data.local.entity.ArchivoAplicadoEntity
import com.entredos.data.local.entity.CambioPendienteEntity
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.DeudaManualEntity
import com.entredos.data.local.entity.DispositivoLocalEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.HogarEntity
import com.entredos.data.local.entity.LoteImportacionEntity
import com.entredos.data.local.entity.MetaEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.local.entity.MovimientoMetaEntity
import com.entredos.data.local.entity.ParteGastoEntity
import com.entredos.data.local.entity.PeriodoCerradoEntity
import com.entredos.data.local.entity.RecurrenteEntity
import com.entredos.data.local.entity.ReglaComercioEntity
import kotlinx.coroutines.flow.Flow

/*
 * Regla para todas las consultas: lo marcado como eliminado (sync_eliminado = 1) no se muestra.
 * Las escrituras de la app pasan SIEMPRE por el Escritor, nunca por upsert directo,
 * para que queden en la lista de cambios por subir. Los upsert los usa el Escritor y,
 * en la entrega 2, el aplicador de cambios remotos.
 */

@Dao
interface HogarDao {
    @Upsert suspend fun upsert(e: HogarEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM hogar") suspend fun todosConEliminados(): List<HogarEntity>
    @Query("SELECT * FROM hogar WHERE sync_eliminado = 0 LIMIT 1") fun observar(): Flow<HogarEntity?>
    @Query("SELECT * FROM hogar LIMIT 1") suspend fun actual(): HogarEntity?
    @Query("SELECT * FROM hogar WHERE id = :id") suspend fun porId(id: String): HogarEntity?
}

@Dao
interface MiembroDao {
    @Upsert suspend fun upsert(e: MiembroEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM miembro") suspend fun todosConEliminados(): List<MiembroEntity>
    @Query("SELECT * FROM miembro WHERE sync_eliminado = 0 ORDER BY id") fun observar(): Flow<List<MiembroEntity>>
    @Query("SELECT * FROM miembro WHERE sync_eliminado = 0 ORDER BY id") suspend fun todos(): List<MiembroEntity>
    @Query("SELECT * FROM miembro WHERE id = :id") suspend fun porId(id: String): MiembroEntity?
}

@Dao
interface CategoriaDao {
    @Upsert suspend fun upsert(e: CategoriaEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM categoria") suspend fun todosConEliminados(): List<CategoriaEntity>
    @Query("SELECT * FROM categoria WHERE sync_eliminado = 0 ORDER BY destacada DESC, orden, nombre")
    fun observar(): Flow<List<CategoriaEntity>>
    @Query("SELECT * FROM categoria WHERE sync_eliminado = 0 ORDER BY destacada DESC, orden, nombre")
    suspend fun todas(): List<CategoriaEntity>
    @Query("SELECT * FROM categoria WHERE id = :id") suspend fun porId(id: String): CategoriaEntity?
}

data class GastoConPartes(
    @Embedded val gasto: GastoEntity,
    @Relation(parentColumn = "id", entityColumn = "gastoId") val partes: List<ParteGastoEntity>,
) {
    val partesActivas: List<ParteGastoEntity> get() = partes.filter { !it.sync.eliminado }
}

@Dao
interface GastoDao {
    @Upsert suspend fun upsert(e: GastoEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM gasto") suspend fun todosConEliminados(): List<GastoEntity>
    @Query("SELECT * FROM gasto WHERE id = :id") suspend fun porId(id: String): GastoEntity?

    @Transaction
    @Query(
        """SELECT * FROM gasto WHERE sync_eliminado = 0 AND estado = 'CONFIRMADO'
           AND fecha >= :desde AND fecha < :hasta ORDER BY fecha DESC"""
    )
    fun observarConfirmados(desde: Long, hasta: Long): Flow<List<GastoConPartes>>

    @Transaction
    @Query("SELECT * FROM gasto WHERE sync_eliminado = 0 AND estado = 'PENDIENTE' ORDER BY fecha")
    fun observarPendientes(): Flow<List<GastoConPartes>>

    @Query("SELECT COUNT(*) FROM gasto WHERE sync_eliminado = 0 AND estado = 'PENDIENTE'")
    fun contarPendientes(): Flow<Int>

    /** Duplicado de notificación: mismo monto y comercio (o app bancaria) en ±5 minutos. */
    @Query(
        """SELECT * FROM gasto WHERE sync_eliminado = 0 AND monto = :monto
           AND fecha BETWEEN :desde AND :hasta
           AND ((:comercio IS NOT NULL AND comercio = :comercio) OR (:comercio IS NULL AND appBanco = :appBanco))
           LIMIT 1"""
    )
    suspend fun buscarDuplicado(monto: Long, comercio: String?, appBanco: String?, desde: Long, hasta: Long): GastoEntity?

    @Query("SELECT idOrigen FROM gasto WHERE idOrigen IS NOT NULL AND sync_eliminado = 0")
    suspend fun idsOrigenImportados(): List<String>

    @Query("SELECT * FROM gasto WHERE loteImportacion = :lote AND sync_eliminado = 0")
    suspend fun porLote(lote: String): List<GastoEntity>
}

@Dao
interface ParteGastoDao {
    @Upsert suspend fun upsert(e: ParteGastoEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM parte_gasto") suspend fun todosConEliminados(): List<ParteGastoEntity>
    @Query("SELECT * FROM parte_gasto WHERE id = :id") suspend fun porId(id: String): ParteGastoEntity?
    @Query("SELECT * FROM parte_gasto WHERE gastoId = :gastoId AND sync_eliminado = 0")
    suspend fun porGasto(gastoId: String): List<ParteGastoEntity>

    /** Partes que pueden generar deuda: gastos confirmados de tipo GASTO, sin eliminar. */
    @Query(
        """SELECT p.id AS parteId, p.gastoId AS gastoId, g.nombre AS concepto, g.fecha AS fecha,
                  g.pagadoPor AS pagadoPor, p.cargoA AS cargoA, p.cargoB AS cargoB,
                  (g.origen = 'IMPORTACION') AS importado
           FROM parte_gasto p JOIN gasto g ON g.id = p.gastoId
           WHERE p.sync_eliminado = 0 AND g.sync_eliminado = 0
             AND g.estado = 'CONFIRMADO' AND g.tipo = 'GASTO'"""
    )
    fun observarParaDeudas(): Flow<List<ParteParaDeuda>>
}

@Dao
interface DeudaManualDao {
    @Upsert suspend fun upsert(e: DeudaManualEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM deuda_manual") suspend fun todosConEliminados(): List<DeudaManualEntity>
    @Query("SELECT * FROM deuda_manual WHERE id = :id") suspend fun porId(id: String): DeudaManualEntity?
    @Query(
        """SELECT id, deudor, acreedor, monto, concepto, fecha FROM deuda_manual
           WHERE sync_eliminado = 0"""
    )
    fun observarParaDeudas(): Flow<List<DeudaManualInfo>>
}

data class AbonoTotal(val deudaId: String, val total: Long)

@Dao
interface AbonoDao {
    @Upsert suspend fun upsert(e: AbonoEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM abono") suspend fun todosConEliminados(): List<AbonoEntity>
    @Query("SELECT * FROM abono WHERE id = :id") suspend fun porId(id: String): AbonoEntity?
    @Query("SELECT deudaId, SUM(monto) AS total FROM abono WHERE sync_eliminado = 0 GROUP BY deudaId")
    fun observarTotales(): Flow<List<AbonoTotal>>
    @Query("SELECT * FROM abono WHERE sync_eliminado = 0 ORDER BY fecha DESC")
    fun observarHistorial(): Flow<List<AbonoEntity>>
    @Query("SELECT * FROM abono WHERE deudaId = :deudaId AND sync_eliminado = 0")
    suspend fun deDeuda(deudaId: String): List<AbonoEntity>
}

@Dao
interface PeriodoCerradoDao {
    @Upsert suspend fun upsert(e: PeriodoCerradoEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM periodo_cerrado") suspend fun todosConEliminados(): List<PeriodoCerradoEntity>
    @Query("SELECT * FROM periodo_cerrado WHERE id = :id") suspend fun porId(id: String): PeriodoCerradoEntity?
    @Query("SELECT * FROM periodo_cerrado WHERE sync_eliminado = 0 ORDER BY fin DESC")
    fun observar(): Flow<List<PeriodoCerradoEntity>>
    /** Fin del último cierre: el periodo actual empieza ahí. */
    @Query("SELECT MAX(fin) FROM periodo_cerrado WHERE sync_eliminado = 0")
    fun observarUltimoFin(): Flow<Long?>
}

@Dao
interface MetaDao {
    @Upsert suspend fun upsert(e: MetaEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM meta") suspend fun todosConEliminados(): List<MetaEntity>
    @Query("SELECT * FROM meta WHERE sync_eliminado = 0 ORDER BY nombre") fun observar(): Flow<List<MetaEntity>>
    @Query("SELECT * FROM meta WHERE id = :id") suspend fun porId(id: String): MetaEntity?
}

data class AhorroMeta(val metaId: String, val total: Long)

@Dao
interface MovimientoMetaDao {
    @Upsert suspend fun upsert(e: MovimientoMetaEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM movimiento_meta") suspend fun todosConEliminados(): List<MovimientoMetaEntity>
    @Query("SELECT * FROM movimiento_meta WHERE id = :id") suspend fun porId(id: String): MovimientoMetaEntity?
    @Query("SELECT metaId, SUM(monto) AS total FROM movimiento_meta WHERE sync_eliminado = 0 GROUP BY metaId")
    fun observarAhorros(): Flow<List<AhorroMeta>>
}

@Dao
interface RecurrenteDao {
    @Upsert suspend fun upsert(e: RecurrenteEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM recurrente") suspend fun todosConEliminados(): List<RecurrenteEntity>
    @Query("SELECT * FROM recurrente WHERE id = :id") suspend fun porId(id: String): RecurrenteEntity?
    @Query("SELECT * FROM recurrente WHERE sync_eliminado = 0 ORDER BY nombre") fun observar(): Flow<List<RecurrenteEntity>>
    @Query("SELECT * FROM recurrente WHERE sync_eliminado = 0 AND palabraComercio IS NOT NULL")
    suspend fun conPalabra(): List<RecurrenteEntity>
}

@Dao
interface ReglaComercioDao {
    @Upsert suspend fun upsert(e: ReglaComercioEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM regla_comercio") suspend fun todosConEliminados(): List<ReglaComercioEntity>
    @Query("SELECT * FROM regla_comercio WHERE id = :id") suspend fun porId(id: String): ReglaComercioEntity?
    @Query("SELECT * FROM regla_comercio WHERE sync_eliminado = 0 ORDER BY palabra") fun observar(): Flow<List<ReglaComercioEntity>>
    @Query("SELECT * FROM regla_comercio WHERE sync_eliminado = 0") suspend fun todas(): List<ReglaComercioEntity>
}

@Dao
interface LoteImportacionDao {
    @Upsert suspend fun upsert(e: LoteImportacionEntity)
    /** Para fotos: incluye los eliminados. */
    @Query("SELECT * FROM lote_importacion") suspend fun todosConEliminados(): List<LoteImportacionEntity>
    @Query("SELECT * FROM lote_importacion WHERE id = :id") suspend fun porId(id: String): LoteImportacionEntity?
    @Query("SELECT * FROM lote_importacion WHERE sync_eliminado = 0 ORDER BY fecha DESC")
    fun observar(): Flow<List<LoteImportacionEntity>>
}

/* ---------- Solo locales ---------- */

@Dao
interface DispositivoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun guardar(e: DispositivoLocalEntity)
    @Query("SELECT * FROM dispositivo_local WHERE clave = 1") suspend fun actual(): DispositivoLocalEntity?
    @Query("SELECT * FROM dispositivo_local WHERE clave = 1") fun observar(): Flow<DispositivoLocalEntity?>
    @Query("UPDATE dispositivo_local SET ultimaSecuenciaSubida = :secuencia WHERE clave = 1")
    suspend fun guardarSecuencia(secuencia: Long)
}

@Dao
interface CambioPendienteDao {
    @Insert suspend fun insertar(e: CambioPendienteEntity)
    /** Lote para subir, en orden. Máximo 500 por archivo de cambios. */
    @Query("SELECT * FROM cambio_pendiente ORDER BY secuencia LIMIT :limite")
    suspend fun tomar(limite: Int = 500): List<CambioPendienteEntity>
    @Query("DELETE FROM cambio_pendiente WHERE secuencia <= :hasta") suspend fun borrarHasta(hasta: Long)
    @Query("SELECT COUNT(*) FROM cambio_pendiente") fun contar(): Flow<Int>
}

@Dao
interface ArchivoAplicadoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertar(e: ArchivoAplicadoEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertarVarios(e: List<ArchivoAplicadoEntity>)
    @Query("SELECT EXISTS(SELECT 1 FROM archivo_aplicado WHERE nombre = :nombre)") suspend fun existe(nombre: String): Boolean
    @Query("SELECT nombre FROM archivo_aplicado") suspend fun nombres(): List<String>
}

@Dao
interface SistemaDao {
    /** Marca HLC más alta guardada, para inicializar el reloj al abrir la app. */
    @Query(
        """SELECT MAX(m) FROM (
             SELECT MAX(sync_actualizadoEn) AS m FROM hogar UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM miembro UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM categoria UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM gasto UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM parte_gasto UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM deuda_manual UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM abono UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM periodo_cerrado UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM meta UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM movimiento_meta UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM recurrente UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM regla_comercio UNION ALL
             SELECT MAX(sync_actualizadoEn) FROM lote_importacion)"""
    )
    suspend fun ultimaMarca(): String?
}

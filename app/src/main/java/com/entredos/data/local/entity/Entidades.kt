package com.entredos.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.entredos.core.EstadoGasto
import com.entredos.core.EstadoLote
import com.entredos.core.Frecuencia
import com.entredos.core.Miembro
import com.entredos.core.ModoPeriodo
import com.entredos.core.OrigenGasto
import com.entredos.core.Responsable
import com.entredos.core.TipoGasto
import kotlinx.serialization.Serializable
import java.util.UUID

/*
 * Una base de datos por hogar: al salir del hogar se borra completa, por eso
 * ninguna tabla lleva hogarId. Montos en pesos (Long) y fechas en epoch millis.
 */

/** Campos de sincronización comunes. El reloj y el id de dispositivo los pone el Escritor. */
@Serializable
data class SyncInfo(
    val actualizadoEn: String = "",
    val actualizadoPor: String = "",
    val eliminado: Boolean = false,
)

interface Sincronizable<T> {
    val id: String
    val sync: SyncInfo
    fun conSync(sync: SyncInfo): T
}

fun nuevoId(): String = UUID.randomUUID().toString()

@Serializable
@Entity(tableName = "hogar")
data class HogarEntity(
    @PrimaryKey override val id: String,
    val nombre: String,
    val diaInicio: Int,
    val modoPeriodo: ModoPeriodo = ModoPeriodo.MANUAL,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<HogarEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

/** Id = "A" o "B". */
@Serializable
@Entity(tableName = "miembro")
data class MiembroEntity(
    @PrimaryKey override val id: String,
    val nombre: String,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<MiembroEntity> {
    val miembro: Miembro get() = Miembro.valueOf(id)
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "categoria")
data class CategoriaEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val nombre: String,
    val color: String,
    val responsable: Responsable,
    /** Presupuesto por periodo en pesos. Null = sin tope. */
    val presupuesto: Long? = null,
    val destacada: Boolean = false,
    val orden: Int = 0,
    val loteImportacion: String? = null,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<CategoriaEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(
    tableName = "gasto",
    indices = [Index("fecha"), Index("estado"), Index("loteImportacion"), Index("idOrigen")],
)
data class GastoEntity(
    @PrimaryKey override val id: String = nuevoId(),
    /** Monto total. Debe ser igual a la suma de sus partes. */
    val monto: Long,
    val nombre: String,
    val comercio: String? = null,
    val fecha: Long,
    val pagadoPor: Miembro,
    val tipo: TipoGasto = TipoGasto.GASTO,
    val estado: EstadoGasto = EstadoGasto.CONFIRMADO,
    val origen: OrigenGasto = OrigenGasto.MANUAL,
    /** Paquete de la app bancaria que lo detectó (solo origen NOTIFICACION). */
    val appBanco: String? = null,
    val loteImportacion: String? = null,
    /** Id en la app de donde se importó, para no duplicar al reimportar. */
    val idOrigen: String? = null,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<GastoEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

/** Parte de un gasto en una categoría. cargoA + cargoB = monto. Su id es también el id de su deuda. */
@Serializable
@Entity(tableName = "parte_gasto", indices = [Index("gastoId"), Index("categoriaId")])
data class ParteGastoEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val gastoId: String,
    val categoriaId: String,
    val monto: Long,
    val cargoA: Long,
    val cargoB: Long,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<ParteGastoEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "deuda_manual")
data class DeudaManualEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val deudor: Miembro,
    val acreedor: Miembro,
    val monto: Long,
    val concepto: String,
    val categoriaId: String? = null,
    val fecha: Long,
    val loteImportacion: String? = null,
    val idOrigen: String? = null,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<DeudaManualEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

/** Abono a una deuda: deudaId es el id de una parte de gasto o de una deuda manual. */
@Serializable
@Entity(tableName = "abono", indices = [Index("deudaId")])
data class AbonoEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val deudaId: String,
    val monto: Long,
    val fecha: Long,
    /** Abono creado al cerrar un periodo, en vez de uno hecho a mano. */
    val porCierre: Boolean = false,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<AbonoEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "periodo_cerrado", indices = [Index("fin")])
data class PeriodoCerradoEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val inicio: Long,
    val fin: Long,
    val totalPresupuestado: Long,
    val totalGastado: Long,
    /** Positivo: B le debía a A al cerrar. Negativo: A le debía a B. */
    val deudaNetaA: Long,
    /** Resumen por categoría, persona y comercio, en JSON. */
    val resumenJson: String,
    val cerradoPor: Miembro,
    /** Generado por el importador a partir de fechas históricas. */
    val loteImportacion: String? = null,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<PeriodoCerradoEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val nombre: String,
    val objetivo: Long,
    val color: String,
    val loteImportacion: String? = null,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<MetaEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

/** Monto positivo = abono a la meta; negativo = retiro. */
@Serializable
@Entity(tableName = "movimiento_meta", indices = [Index("metaId")])
data class MovimientoMetaEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val metaId: String,
    val monto: Long,
    val fecha: Long,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<MovimientoMetaEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "recurrente")
data class RecurrenteEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val nombre: String,
    val categoriaId: String,
    val montoEsperado: Long,
    val frecuencia: Frecuencia,
    val palabraComercio: String? = null,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<RecurrenteEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "regla_comercio", indices = [Index("palabra")])
data class ReglaComercioEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val palabra: String,
    val categoriaId: String,
    val confirmaciones: Int = 0,
    val automatica: Boolean = false,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<ReglaComercioEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

@Serializable
@Entity(tableName = "lote_importacion")
data class LoteImportacionEntity(
    @PrimaryKey override val id: String = nuevoId(),
    val fecha: Long,
    val archivo: String,
    val gastos: Int,
    val categorias: Int,
    val deudas: Int,
    val metas: Int,
    val estado: EstadoLote = EstadoLote.ACTIVO,
    @Embedded(prefix = "sync_") override val sync: SyncInfo = SyncInfo(),
) : Sincronizable<LoteImportacionEntity> {
    override fun conSync(sync: SyncInfo) = copy(sync = sync)
}

/* ---------- Solo locales: nunca se sincronizan ---------- */

/** Una sola fila (clave = 1): este teléfono y el titular que lo usa. */
@Entity(tableName = "dispositivo_local")
data class DispositivoLocalEntity(
    @PrimaryKey val clave: Int = 1,
    val dispositivoId: String,
    val miembro: Miembro?,
    /** Último archivo cambios-<dispositivo>-<secuencia>.json que subió este teléfono. */
    val ultimaSecuenciaSubida: Long = 0,
)

/** Cambio guardado en este teléfono que falta subir. */
@Entity(tableName = "cambio_pendiente")
data class CambioPendienteEntity(
    @PrimaryKey(autoGenerate = true) val secuencia: Long = 0,
    val tabla: String,
    val registroId: String,
    val json: String,
    val hlc: String,
    val dispositivo: String,
    val eliminado: Boolean,
)

/** Archivo de cambios (de Drive o del wifi) que ya se aplicó en este teléfono. */
@Entity(tableName = "archivo_aplicado")
data class ArchivoAplicadoEntity(
    @PrimaryKey val nombre: String,
    val aplicadoEn: Long,
)

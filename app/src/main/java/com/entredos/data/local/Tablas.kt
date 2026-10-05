package com.entredos.data.local

import com.entredos.data.local.entity.AbonoEntity
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.DeudaManualEntity
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
import com.entredos.data.local.entity.Sincronizable
import kotlinx.serialization.KSerializer

/**
 * Cada tabla sincronizable: su nombre en los archivos de cambios, cómo se serializa
 * y cómo se guarda. El aplicador de cambios remotos (entrega 2) usa este mismo registro.
 */
class Tabla<T : Sincronizable<T>>(
    val nombre: String,
    val serializer: KSerializer<T>,
    val upsert: suspend (EntreDosDatabase, T) -> Unit,
    val porId: suspend (EntreDosDatabase, String) -> T?,
)

object Tablas {
    val HOGAR = Tabla("hogar", HogarEntity.serializer(), { db, e -> db.hogarDao().upsert(e) }, { db, id -> db.hogarDao().porId(id) })
    val MIEMBRO = Tabla("miembro", MiembroEntity.serializer(), { db, e -> db.miembroDao().upsert(e) }, { db, id -> db.miembroDao().porId(id) })
    val CATEGORIA = Tabla("categoria", CategoriaEntity.serializer(), { db, e -> db.categoriaDao().upsert(e) }, { db, id -> db.categoriaDao().porId(id) })
    val GASTO = Tabla("gasto", GastoEntity.serializer(), { db, e -> db.gastoDao().upsert(e) }, { db, id -> db.gastoDao().porId(id) })
    val PARTE_GASTO = Tabla("parte_gasto", ParteGastoEntity.serializer(), { db, e -> db.parteGastoDao().upsert(e) }, { db, id -> db.parteGastoDao().porId(id) })
    val DEUDA_MANUAL = Tabla("deuda_manual", DeudaManualEntity.serializer(), { db, e -> db.deudaManualDao().upsert(e) }, { db, id -> db.deudaManualDao().porId(id) })
    val ABONO = Tabla("abono", AbonoEntity.serializer(), { db, e -> db.abonoDao().upsert(e) }, { db, id -> db.abonoDao().porId(id) })
    val PERIODO_CERRADO = Tabla("periodo_cerrado", PeriodoCerradoEntity.serializer(), { db, e -> db.periodoCerradoDao().upsert(e) }, { db, id -> db.periodoCerradoDao().porId(id) })
    val META = Tabla("meta", MetaEntity.serializer(), { db, e -> db.metaDao().upsert(e) }, { db, id -> db.metaDao().porId(id) })
    val MOVIMIENTO_META = Tabla("movimiento_meta", MovimientoMetaEntity.serializer(), { db, e -> db.movimientoMetaDao().upsert(e) }, { db, id -> db.movimientoMetaDao().porId(id) })
    val RECURRENTE = Tabla("recurrente", RecurrenteEntity.serializer(), { db, e -> db.recurrenteDao().upsert(e) }, { db, id -> db.recurrenteDao().porId(id) })
    val REGLA_COMERCIO = Tabla("regla_comercio", ReglaComercioEntity.serializer(), { db, e -> db.reglaComercioDao().upsert(e) }, { db, id -> db.reglaComercioDao().porId(id) })
    val LOTE_IMPORTACION = Tabla("lote_importacion", LoteImportacionEntity.serializer(), { db, e -> db.loteImportacionDao().upsert(e) }, { db, id -> db.loteImportacionDao().porId(id) })

    val todas: List<Tabla<*>> = listOf(
        HOGAR, MIEMBRO, CATEGORIA, GASTO, PARTE_GASTO, DEUDA_MANUAL, ABONO,
        PERIODO_CERRADO, META, MOVIMIENTO_META, RECURRENTE, REGLA_COMERCIO, LOTE_IMPORTACION,
    )

    fun porNombre(nombre: String): Tabla<*>? = todas.firstOrNull { it.nombre == nombre }
}

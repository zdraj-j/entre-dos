package com.entredos.data.local

import com.entredos.core.sync.Cambio
import com.entredos.core.sync.Lww
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
 * Cada tabla sincronizable: su nombre en los archivos de cambios, cómo se serializa,
 * cómo se guarda y cómo se lee. Lo usan el Escritor y el aplicador de cambios remotos.
 */
class Tabla<T : Sincronizable<T>>(
    val nombre: String,
    val serializer: KSerializer<T>,
    val upsert: suspend (EntreDosDatabase, T) -> Unit,
    val porId: suspend (EntreDosDatabase, String) -> T?,
    val todos: suspend (EntreDosDatabase) -> List<T>,
) {
    /**
     * Aplica un cambio que llegó de la pareja, solo si gana por LWW.
     * Escribe directo en la tabla, sin pasar por el Escritor: no debe volver a subirse.
     */
    suspend fun aplicarRemoto(db: EntreDosDatabase, cambio: Cambio): Boolean {
        val local = porId(db, cambio.id)
        val gana = Lww.ganaRemoto(local?.sync?.actualizadoEn, local?.sync?.actualizadoPor, cambio.hlc, cambio.dispositivo)
        if (gana) upsert(db, Escritor.JSON.decodeFromString(serializer, cambio.datos))
        return gana
    }

    /** Todos los registros (eliminados incluidos) como cambios, para armar una foto. */
    suspend fun comoCambios(db: EntreDosDatabase): List<Cambio> = todos(db).map { e ->
        Cambio(nombre, e.id, e.sync.actualizadoEn, e.sync.actualizadoPor, e.sync.eliminado, Escritor.JSON.encodeToString(serializer, e))
    }
}

object Tablas {
    val HOGAR = Tabla("hogar", HogarEntity.serializer(), { db, e -> db.hogarDao().upsert(e) }, { db, id -> db.hogarDao().porId(id) }, { it.hogarDao().todosConEliminados() })
    val MIEMBRO = Tabla("miembro", MiembroEntity.serializer(), { db, e -> db.miembroDao().upsert(e) }, { db, id -> db.miembroDao().porId(id) }, { it.miembroDao().todosConEliminados() })
    val CATEGORIA = Tabla("categoria", CategoriaEntity.serializer(), { db, e -> db.categoriaDao().upsert(e) }, { db, id -> db.categoriaDao().porId(id) }, { it.categoriaDao().todosConEliminados() })
    val GASTO = Tabla("gasto", GastoEntity.serializer(), { db, e -> db.gastoDao().upsert(e) }, { db, id -> db.gastoDao().porId(id) }, { it.gastoDao().todosConEliminados() })
    val PARTE_GASTO = Tabla("parte_gasto", ParteGastoEntity.serializer(), { db, e -> db.parteGastoDao().upsert(e) }, { db, id -> db.parteGastoDao().porId(id) }, { it.parteGastoDao().todosConEliminados() })
    val DEUDA_MANUAL = Tabla("deuda_manual", DeudaManualEntity.serializer(), { db, e -> db.deudaManualDao().upsert(e) }, { db, id -> db.deudaManualDao().porId(id) }, { it.deudaManualDao().todosConEliminados() })
    val ABONO = Tabla("abono", AbonoEntity.serializer(), { db, e -> db.abonoDao().upsert(e) }, { db, id -> db.abonoDao().porId(id) }, { it.abonoDao().todosConEliminados() })
    val PERIODO_CERRADO = Tabla("periodo_cerrado", PeriodoCerradoEntity.serializer(), { db, e -> db.periodoCerradoDao().upsert(e) }, { db, id -> db.periodoCerradoDao().porId(id) }, { it.periodoCerradoDao().todosConEliminados() })
    val META = Tabla("meta", MetaEntity.serializer(), { db, e -> db.metaDao().upsert(e) }, { db, id -> db.metaDao().porId(id) }, { it.metaDao().todosConEliminados() })
    val MOVIMIENTO_META = Tabla("movimiento_meta", MovimientoMetaEntity.serializer(), { db, e -> db.movimientoMetaDao().upsert(e) }, { db, id -> db.movimientoMetaDao().porId(id) }, { it.movimientoMetaDao().todosConEliminados() })
    val RECURRENTE = Tabla("recurrente", RecurrenteEntity.serializer(), { db, e -> db.recurrenteDao().upsert(e) }, { db, id -> db.recurrenteDao().porId(id) }, { it.recurrenteDao().todosConEliminados() })
    val REGLA_COMERCIO = Tabla("regla_comercio", ReglaComercioEntity.serializer(), { db, e -> db.reglaComercioDao().upsert(e) }, { db, id -> db.reglaComercioDao().porId(id) }, { it.reglaComercioDao().todosConEliminados() })
    val LOTE_IMPORTACION = Tabla("lote_importacion", LoteImportacionEntity.serializer(), { db, e -> db.loteImportacionDao().upsert(e) }, { db, id -> db.loteImportacionDao().porId(id) }, { it.loteImportacionDao().todosConEliminados() })

    val todas: List<Tabla<*>> = listOf(
        HOGAR, MIEMBRO, CATEGORIA, GASTO, PARTE_GASTO, DEUDA_MANUAL, ABONO,
        PERIODO_CERRADO, META, MOVIMIENTO_META, RECURRENTE, REGLA_COMERCIO, LOTE_IMPORTACION,
    )

    fun porNombre(nombre: String): Tabla<*>? = todas.firstOrNull { it.nombre == nombre }
}

package com.entredos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.entredos.data.local.dao.AbonoDao
import com.entredos.data.local.dao.ArchivoAplicadoDao
import com.entredos.data.local.dao.CambioPendienteDao
import com.entredos.data.local.dao.CategoriaDao
import com.entredos.data.local.dao.DeudaManualDao
import com.entredos.data.local.dao.DispositivoDao
import com.entredos.data.local.dao.GastoDao
import com.entredos.data.local.dao.HogarDao
import com.entredos.data.local.dao.LoteImportacionDao
import com.entredos.data.local.dao.MiembroDao
import com.entredos.data.local.dao.ParteGastoDao
import com.entredos.data.local.dao.PeriodoCerradoDao
import com.entredos.data.local.dao.RecurrenteDao
import com.entredos.data.local.dao.ReglaComercioDao
import com.entredos.data.local.dao.SistemaDao
import com.entredos.data.local.entity.AbonoEntity
import com.entredos.data.local.entity.ArchivoAplicadoEntity
import com.entredos.data.local.entity.CambioPendienteEntity
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.DeudaManualEntity
import com.entredos.data.local.entity.DispositivoLocalEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.HogarEntity
import com.entredos.data.local.entity.LoteImportacionEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.local.entity.ParteGastoEntity
import com.entredos.data.local.entity.PeriodoCerradoEntity
import com.entredos.data.local.entity.RecurrenteEntity
import com.entredos.data.local.entity.ReglaComercioEntity

@Database(
    version = 4,
    exportSchema = true,
    entities = [
        HogarEntity::class, MiembroEntity::class, CategoriaEntity::class,
        GastoEntity::class, ParteGastoEntity::class, DeudaManualEntity::class, AbonoEntity::class,
        PeriodoCerradoEntity::class,
        RecurrenteEntity::class, ReglaComercioEntity::class, LoteImportacionEntity::class,
        DispositivoLocalEntity::class, CambioPendienteEntity::class, ArchivoAplicadoEntity::class,
    ],
)
abstract class EntreDosDatabase : RoomDatabase() {
    abstract fun hogarDao(): HogarDao
    abstract fun miembroDao(): MiembroDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun gastoDao(): GastoDao
    abstract fun parteGastoDao(): ParteGastoDao
    abstract fun deudaManualDao(): DeudaManualDao
    abstract fun abonoDao(): AbonoDao
    abstract fun periodoCerradoDao(): PeriodoCerradoDao
    abstract fun recurrenteDao(): RecurrenteDao
    abstract fun reglaComercioDao(): ReglaComercioDao
    abstract fun loteImportacionDao(): LoteImportacionDao
    abstract fun dispositivoDao(): DispositivoDao
    abstract fun cambioPendienteDao(): CambioPendienteDao
    abstract fun archivoAplicadoDao(): ArchivoAplicadoDao
    abstract fun sistemaDao(): SistemaDao

    companion object {
        const val NOMBRE = "entre_dos.db"

        /**
         * 3 → 4: el gasto guarda el banco del que salió el pago y deja de tener tipo (ya no hay
         * transferencias: las que había quedan ignoradas, como "No es un gasto"). SQLite de
         * Android 8 no tiene DROP COLUMN, así que la tabla se copia a una nueva.
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `gasto_nueva` (`id` TEXT NOT NULL, `monto` INTEGER NOT NULL, `nombre` TEXT NOT NULL,
                       `comercio` TEXT, `fecha` INTEGER NOT NULL, `pagadoPor` TEXT NOT NULL, `estado` TEXT NOT NULL, `origen` TEXT NOT NULL,
                       `appBanco` TEXT, `banco` TEXT, `loteImportacion` TEXT, `idOrigen` TEXT, `sync_actualizadoEn` TEXT NOT NULL,
                       `sync_actualizadoPor` TEXT NOT NULL, `sync_eliminado` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """INSERT INTO `gasto_nueva` (id, monto, nombre, comercio, fecha, pagadoPor, estado, origen, appBanco, banco,
                       loteImportacion, idOrigen, sync_actualizadoEn, sync_actualizadoPor, sync_eliminado)
                       SELECT id, monto, nombre, comercio, fecha, pagadoPor, CASE WHEN tipo = 'TRANSFERENCIA' THEN 'IGNORADO' ELSE estado END,
                       origen, appBanco, NULL, loteImportacion, idOrigen, sync_actualizadoEn, sync_actualizadoPor, sync_eliminado FROM `gasto`"""
                )
                db.execSQL("DROP TABLE `gasto`")
                db.execSQL("ALTER TABLE `gasto_nueva` RENAME TO `gasto`")
                for (columna in listOf("fecha", "estado", "loteImportacion", "idOrigen")) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_gasto_$columna` ON `gasto` (`$columna`)")
                }
            }
        }
    }
}

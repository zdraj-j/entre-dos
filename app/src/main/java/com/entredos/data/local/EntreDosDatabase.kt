package com.entredos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 3,
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
    }
}

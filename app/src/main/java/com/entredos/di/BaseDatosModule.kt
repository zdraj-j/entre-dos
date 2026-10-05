package com.entredos.di

import android.content.Context
import androidx.room.Room
import com.entredos.core.sync.Hlc
import com.entredos.core.sync.RelojHlc
import com.entredos.data.local.EntreDosDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BaseDatosModule {

    @Provides @Singleton
    fun baseDatos(@ApplicationContext context: Context): EntreDosDatabase =
        Room.databaseBuilder(context, EntreDosDatabase::class.java, EntreDosDatabase.NOMBRE)
            // Solo mientras no haya usuarios reales. Antes de usarla en serio: migraciones.
            .fallbackToDestructiveMigration()
            .build()

    /** El reloj arranca desde la marca más alta guardada, para no retroceder nunca. */
    @Provides @Singleton
    fun reloj(db: EntreDosDatabase): RelojHlc = RelojHlc().apply {
        val ultima = runBlocking { db.sistemaDao().ultimaMarca() }
        inicializar(ultima?.takeIf { it.isNotEmpty() }?.let(Hlc::decodificar))
    }

    @Provides fun hogarDao(db: EntreDosDatabase) = db.hogarDao()
    @Provides fun miembroDao(db: EntreDosDatabase) = db.miembroDao()
    @Provides fun categoriaDao(db: EntreDosDatabase) = db.categoriaDao()
    @Provides fun gastoDao(db: EntreDosDatabase) = db.gastoDao()
    @Provides fun parteGastoDao(db: EntreDosDatabase) = db.parteGastoDao()
    @Provides fun deudaManualDao(db: EntreDosDatabase) = db.deudaManualDao()
    @Provides fun abonoDao(db: EntreDosDatabase) = db.abonoDao()
    @Provides fun periodoCerradoDao(db: EntreDosDatabase) = db.periodoCerradoDao()
    @Provides fun metaDao(db: EntreDosDatabase) = db.metaDao()
    @Provides fun movimientoMetaDao(db: EntreDosDatabase) = db.movimientoMetaDao()
    @Provides fun recurrenteDao(db: EntreDosDatabase) = db.recurrenteDao()
    @Provides fun reglaComercioDao(db: EntreDosDatabase) = db.reglaComercioDao()
    @Provides fun loteImportacionDao(db: EntreDosDatabase) = db.loteImportacionDao()
    @Provides fun cambioPendienteDao(db: EntreDosDatabase) = db.cambioPendienteDao()
}

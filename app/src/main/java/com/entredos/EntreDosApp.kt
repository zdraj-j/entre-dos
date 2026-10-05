package com.entredos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.entredos.data.repository.CierreRepositorio
import com.entredos.data.sync.SincronizacionAutomatica
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EntreDosApp : Application(), Configuration.Provider {

    @Inject lateinit var fabricaTrabajos: HiltWorkerFactory
    @Inject lateinit var sincronizacion: SincronizacionAutomatica
    @Inject lateinit var cierre: CierreRepositorio

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()

    override fun onCreate() {
        super.onCreate()
        sincronizacion.iniciar()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { cierre.archivarSiCorresponde() } }
    }
}

package com.entredos.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class TrabajoSync @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sincronizador: Sincronizador,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val r = sincronizador.sincronizar()
        val error = r.exceptionOrNull()
        return when {
            r.isSuccess -> Result.success()
            error is SinAutorizacion -> Result.success() // espera a que la persona vuelva a conectar
            error is FormatoNoSoportado -> Result.failure()
            error is IOException && runAttemptCount < 5 -> Result.retry()
            else -> Result.failure()
        }
    }
}

/** Cuándo sincroniza: cada 15 minutos con red, y pocos segundos después de cada cambio o al abrir la app. */
@Singleton
class ProgramadorSync @Inject constructor(@ApplicationContext context: Context) {

    private val trabajos = WorkManager.getInstance(context)
    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun iniciarPeriodica() {
        val pedido = PeriodicWorkRequestBuilder<TrabajoSync>(15, TimeUnit.MINUTES)
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        trabajos.enqueueUniquePeriodicWork(PERIODICA, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    fun pedirAhora() {
        val pedido = OneTimeWorkRequestBuilder<TrabajoSync>()
            .setConstraints(conRed)
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Si hay una corriendo, esta va detrás: así ningún cambio se queda esperando 15 minutos.
        trabajos.enqueueUniqueWork(INMEDIATA, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
    }

    fun cancelarTodo() {
        trabajos.cancelUniqueWork(PERIODICA)
        trabajos.cancelUniqueWork(INMEDIATA)
    }

    private companion object {
        const val PERIODICA = "sync-periodica"
        const val INMEDIATA = "sync-inmediata"
    }
}

package com.entredos.data.sync

import com.entredos.core.sync.MotorSync
import com.entredos.core.sync.ResultadoSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Punto único para sincronizar: nunca corren dos sincronizaciones a la vez y se registra el resultado. */
@Singleton
class Sincronizador @Inject constructor(
    private val motor: MotorSync,
    private val estado: EstadoSync,
) {
    private val candado = Mutex()

    suspend fun sincronizar(): Result<ResultadoSync> = candado.withLock {
        if (estado.correo == null) return Result.failure(SinAutorizacion())
        try {
            Result.success(motor.sincronizar()).also { estado.registrarExito() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SinAutorizacion) {
            Result.failure(e)
        } catch (e: Exception) {
            estado.registrarError(e.message)
            Result.failure(e)
        }
    }
}

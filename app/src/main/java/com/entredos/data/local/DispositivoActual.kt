package com.entredos.data.local

import com.entredos.core.Miembro
import com.entredos.data.local.entity.DispositivoLocalEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identidad de este teléfono. Reemplaza al inicio de sesión anónimo de Firebase:
 * el id se crea la primera vez y vive solo en la base local. Al reinstalar se crea
 * uno nuevo y la persona recupera su lugar eligiendo "Soy …" (queda ligado a su miembro).
 */
@Singleton
class DispositivoActual @Inject constructor(private val db: EntreDosDatabase) {

    private val candado = Mutex()
    @Volatile private var cache: DispositivoLocalEntity? = null

    suspend fun id(): String = cargar().dispositivoId

    /** Titular que usa este teléfono, o null si aún no eligió (primer uso o reinstalación). */
    suspend fun miembro(): Miembro? = cargar().miembro

    suspend fun asignarMiembro(miembro: Miembro) = candado.withLock {
        val actual = cache ?: db.dispositivoDao().actual() ?: nuevo()
        val nuevo = actual.copy(miembro = miembro)
        db.dispositivoDao().guardar(nuevo)
        cache = nuevo
    }

    private suspend fun cargar(): DispositivoLocalEntity = cache ?: candado.withLock {
        cache ?: (db.dispositivoDao().actual() ?: nuevo().also { db.dispositivoDao().guardar(it) })
            .also { cache = it }
    }

    private fun nuevo() = DispositivoLocalEntity(dispositivoId = UUID.randomUUID().toString(), miembro = null)
}

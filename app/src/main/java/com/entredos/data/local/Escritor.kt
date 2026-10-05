package com.entredos.data.local

import androidx.room.withTransaction
import com.entredos.core.sync.RelojHlc
import com.entredos.data.local.entity.CambioPendienteEntity
import com.entredos.data.local.entity.Sincronizable
import com.entredos.data.local.entity.SyncInfo
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Única puerta de escritura de la app. Cada guardado, en una sola transacción:
 * 1. le pone la marca del reloj y el id de este teléfono,
 * 2. lo guarda en su tabla,
 * 3. lo deja en la lista de cambios por subir.
 *
 * Puede llamarse dentro de db.withTransaction { } para guardar varias cosas juntas
 * (un gasto y sus partes, por ejemplo): si algo falla, no se guarda nada.
 */
@Singleton
class Escritor @Inject constructor(
    private val db: EntreDosDatabase,
    private val reloj: RelojHlc,
    private val dispositivo: DispositivoActual,
) {
    suspend fun <T : Sincronizable<T>> guardar(tabla: Tabla<T>, entidad: T): T {
        val dispositivoId = dispositivo.id()
        return db.withTransaction {
            val marca = reloj.ahora().codificar()
            val nueva = entidad.conSync(SyncInfo(marca, dispositivoId, entidad.sync.eliminado))
            tabla.upsert(db, nueva)
            db.cambioPendienteDao().insertar(
                CambioPendienteEntity(
                    tabla = tabla.nombre,
                    registroId = nueva.id,
                    json = JSON.encodeToString(tabla.serializer, nueva),
                    hlc = marca,
                    dispositivo = dispositivoId,
                    eliminado = nueva.sync.eliminado,
                )
            )
            nueva
        }
    }

    /** Borrado lógico: se marca como eliminado para que el borrado también llegue a la pareja. */
    suspend fun <T : Sincronizable<T>> eliminar(tabla: Tabla<T>, entidad: T): T =
        guardar(tabla, entidad.conSync(entidad.sync.copy(eliminado = true)))

    companion object {
        val JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

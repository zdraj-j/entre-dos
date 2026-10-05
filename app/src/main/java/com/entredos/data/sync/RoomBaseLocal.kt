package com.entredos.data.sync

import androidx.room.withTransaction
import com.entredos.core.sync.BaseLocal
import com.entredos.core.sync.Cambio
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.ArchivoAplicadoEntity
import javax.inject.Inject
import javax.inject.Singleton

/** La base de Room vista por el motor de sincronización. */
@Singleton
class RoomBaseLocal @Inject constructor(
    private val db: EntreDosDatabase,
    private val dispositivo: DispositivoActual,
) : BaseLocal {

    override suspend fun dispositivoId(): String = dispositivo.id()

    override suspend fun pendientes(limite: Int): List<Pair<Long, Cambio>> =
        db.cambioPendienteDao().tomar(limite).map {
            it.secuencia to Cambio(it.tabla, it.registroId, it.hlc, it.dispositivo, it.eliminado, it.json)
        }

    override suspend fun ultimaSecuenciaSubida(): Long {
        dispositivo.id() // asegura que exista la fila del dispositivo
        return db.dispositivoDao().actual()?.ultimaSecuenciaSubida ?: 0
    }

    override suspend fun confirmarSubida(hastaPendiente: Long, secuencia: Long, nombreArchivo: String) =
        db.withTransaction {
            db.cambioPendienteDao().borrarHasta(hastaPendiente)
            db.dispositivoDao().guardarSecuencia(secuencia)
            db.archivoAplicadoDao().insertar(ArchivoAplicadoEntity(nombreArchivo, System.currentTimeMillis()))
        }

    override suspend fun aplicados(): Set<String> = db.archivoAplicadoDao().nombres().toSet()

    override suspend fun aplicar(nombreArchivo: String, cambios: List<Cambio>, tambienMarcar: Set<String>) =
        db.withTransaction {
            for (cambio in cambios) {
                // Una tabla desconocida viene de una versión más nueva de la app: se ignora.
                Tablas.porNombre(cambio.tabla)?.aplicarRemoto(db, cambio)
            }
            val ahora = System.currentTimeMillis()
            db.archivoAplicadoDao().insertarVarios((tambienMarcar + nombreArchivo).map { ArchivoAplicadoEntity(it, ahora) })
        }

    override suspend fun registrosParaFoto(): List<Cambio> = Tablas.todas.flatMap { it.comoCambios(db) }
}

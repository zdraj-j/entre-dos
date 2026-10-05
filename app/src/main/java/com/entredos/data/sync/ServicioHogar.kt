package com.entredos.data.sync

import androidx.room.withTransaction
import com.entredos.core.Miembro
import com.entredos.core.sync.NombresSync
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.HogarEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.local.entity.nuevoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SituacionDrive {
    /** La cuenta no tiene hogar: se puede crear uno. */
    data object SinHogar : SituacionDrive

    /** Ya hay hogar y se descargó: la persona elige "Soy …" o se une como titular B. */
    data class ConHogar(val nombreHogar: String, val miembros: List<MiembroEntity>) : SituacionDrive {
        val puedeUnirseComoB: Boolean get() = miembros.size == 1
    }
}

class CambiosSinSubir(val cuantos: Int) : Exception("Hay $cuantos cambios sin subir")

/**
 * Primer uso, unión de la pareja y recuperación del lugar. No hay códigos de invitación:
 * la cuenta de Google del hogar es lo que comparten los dos.
 */
@Singleton
class ServicioHogar @Inject constructor(
    private val db: EntreDosDatabase,
    private val escritor: Escritor,
    private val dispositivo: DispositivoActual,
    private val drive: DriveAlmacen,
    private val sincronizador: Sincronizador,
    private val estado: EstadoSync,
    private val programador: ProgramadorSync,
) {

    /** Tras conectar la cuenta: recuerda el correo y revisa si ya hay un hogar en ese Drive. */
    suspend fun revisarDrive(): SituacionDrive {
        estado.guardarCorreo(drive.correoDeLaCuenta())
        if (drive.buscar(NombresSync.HOGAR) == null) return SituacionDrive.SinHogar
        sincronizador.sincronizar().getOrThrow() // baja la foto y los cambios: aquí se recupera todo
        val hogar = db.hogarDao().actual()
        return SituacionDrive.ConHogar(hogar?.nombre ?: "Hogar", db.miembroDao().todos())
    }

    suspend fun crearHogar(nombreHogar: String, miNombre: String, diaInicio: Int) {
        require(diaInicio in 1..28) { "El día de inicio debe estar entre 1 y 28" }
        require(miNombre.isNotBlank()) { "Escribe tu nombre" }
        check(drive.buscar(NombresSync.HOGAR) == null) { "Esta cuenta ya tiene un hogar" }

        val hogar = HogarEntity(id = nuevoId(), nombre = nombreHogar.ifBlank { "Nuestro hogar" }, diaInicio = diaInicio)
        db.withTransaction {
            escritor.guardar(Tablas.HOGAR, hogar)
            escritor.guardar(Tablas.MIEMBRO, MiembroEntity(id = Miembro.A.name, nombre = miNombre.trim()))
        }
        dispositivo.asignarMiembro(Miembro.A)
        drive.crear(NombresSync.HOGAR, """{"formato":$FORMATO_SYNC,"hogarId":"${hogar.id}","creado":${System.currentTimeMillis()}}""".toByteArray())
        terminar()
    }

    /** Recuperar el lugar tras reinstalar, o entrar en un teléfono nuevo. */
    suspend fun soy(miembro: Miembro) {
        checkNotNull(db.miembroDao().porId(miembro.name)) { "Ese titular no existe en el hogar" }
        dispositivo.asignarMiembro(miembro)
        terminar()
    }

    suspend fun unirmeComoTitularB(miNombre: String) {
        require(miNombre.isNotBlank()) { "Escribe tu nombre" }
        check(db.miembroDao().todos().size == 1) { "El hogar ya tiene dos titulares" }
        escritor.guardar(Tablas.MIEMBRO, MiembroEntity(id = Miembro.B.name, nombre = miNombre.trim()))
        dispositivo.asignarMiembro(Miembro.B)
        terminar()
    }

    /**
     * Salir del hogar: borra todo lo local. Los datos siguen en Drive.
     * Si hay cambios sin subir, falla con [CambiosSinSubir] salvo que se fuerce.
     */
    suspend fun salir(forzar: Boolean = false) {
        if (!forzar) {
            sincronizador.sincronizar()
            val pendientes = db.cambioPendienteDao().contar().first()
            if (pendientes > 0) throw CambiosSinSubir(pendientes)
        }
        programador.cancelarTodo()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        estado.limpiar()
    }

    private suspend fun terminar() {
        sincronizador.sincronizar()
        programador.iniciarPeriodica()
    }
}

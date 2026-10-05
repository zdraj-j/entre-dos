package com.entredos.data.repository

import androidx.room.withTransaction
import com.entredos.core.Frecuencia
import com.entredos.core.Miembro
import com.entredos.core.ModoPeriodo
import com.entredos.core.Responsable
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.RecurrenteEntity
import com.entredos.data.local.entity.ReglaComercioEntity
import com.entredos.core.Clasificacion
import javax.inject.Inject
import javax.inject.Singleton

/** Todas las escrituras pasan por el Escritor: así llegan a la pareja. */

@Singleton
class CategoriasRepositorio @Inject constructor(private val db: EntreDosDatabase, private val escritor: Escritor) {

    suspend fun guardar(
        existente: CategoriaEntity?, nombre: String, color: String, responsable: Responsable, presupuesto: Long?, destacada: Boolean,
    ): CategoriaEntity {
        require(nombre.isNotBlank()) { "Escribe un nombre" }
        require(presupuesto == null || presupuesto > 0) { "El presupuesto debe ser mayor que cero o sin tope" }
        val repetida = db.categoriaDao().todas().any { it.id != existente?.id && it.nombre.equals(nombre.trim(), ignoreCase = true) }
        require(!repetida) { "Ya hay una categoría con ese nombre" }
        val base = existente ?: CategoriaEntity(nombre = nombre, color = color, responsable = responsable, orden = db.categoriaDao().todas().size)
        return escritor.guardar(Tablas.CATEGORIA, base.copy(nombre = nombre.trim(), color = color, responsable = responsable, presupuesto = presupuesto, destacada = destacada))
    }

    /** Los gastos ya registrados conservan sus partes; en las listas la categoría aparece como eliminada. */
    suspend fun eliminar(c: CategoriaEntity) = escritor.eliminar(Tablas.CATEGORIA, c)
}

@Singleton
class RecurrentesRepositorio @Inject constructor(
    private val escritor: Escritor,
    private val gastos: GastosRepositorio,
    private val dispositivo: DispositivoActual,
) {
    suspend fun guardar(
        existente: RecurrenteEntity?, nombre: String, categoriaId: String, monto: Long, frecuencia: Frecuencia, palabra: String?,
    ): RecurrenteEntity {
        require(nombre.isNotBlank()) { "Escribe un nombre" }
        require(monto > 0) { "El monto debe ser mayor que cero" }
        val base = existente ?: RecurrenteEntity(nombre = nombre, categoriaId = categoriaId, montoEsperado = monto, frecuencia = frecuencia)
        return escritor.guardar(
            Tablas.RECURRENTE,
            base.copy(nombre = nombre.trim(), categoriaId = categoriaId, montoEsperado = monto, frecuencia = frecuencia, palabraComercio = palabra?.trim()?.ifBlank { null }),
        )
    }

    /** "Registrar": lo anota como gasto de hoy, a nombre de quien lo toca. */
    suspend fun registrarHoy(r: RecurrenteEntity) {
        val yo = dispositivo.miembro() ?: error("Este teléfono aún no tiene titular")
        gastos.registrar(r.nombre, System.currentTimeMillis(), yo, listOf(ParteNueva(r.categoriaId, r.montoEsperado)), comercio = r.palabraComercio)
    }

    suspend fun eliminar(r: RecurrenteEntity) = escritor.eliminar(Tablas.RECURRENTE, r)
}

@Singleton
class ReglasRepositorio @Inject constructor(private val db: EntreDosDatabase, private val escritor: Escritor) {

    suspend fun crear(palabra: String, categoriaId: String, automatica: Boolean) {
        val limpia = Clasificacion.palabraDeComercio(palabra)
        require(limpia.isNotBlank()) { "Escribe una palabra del comercio" }
        val actual = db.reglaComercioDao().porPalabra(limpia)
        escritor.guardar(
            Tablas.REGLA_COMERCIO,
            (actual ?: ReglaComercioEntity(palabra = limpia, categoriaId = categoriaId))
                .copy(categoriaId = categoriaId, automatica = automatica, confirmaciones = maxOf(actual?.confirmaciones ?: 0, if (automatica) 3 else 0)),
        )
    }

    suspend fun automatica(r: ReglaComercioEntity, activa: Boolean) = escritor.guardar(Tablas.REGLA_COMERCIO, r.copy(automatica = activa))
    suspend fun eliminar(r: ReglaComercioEntity) = escritor.eliminar(Tablas.REGLA_COMERCIO, r)
}

@Singleton
class HogarRepositorio @Inject constructor(private val db: EntreDosDatabase, private val escritor: Escritor) {

    suspend fun guardar(nombreHogar: String, diaInicio: Int, modo: ModoPeriodo) {
        require(diaInicio in 1..28) { "El día de inicio debe estar entre 1 y 28" }
        val hogar = db.hogarDao().actual() ?: error("No hay hogar")
        escritor.guardar(Tablas.HOGAR, hogar.copy(nombre = nombreHogar.ifBlank { hogar.nombre }, diaInicio = diaInicio, modoPeriodo = modo))
    }

    /** Cambia el nombre de un titular para los dos teléfonos. */
    suspend fun renombrar(miembro: Miembro, nombre: String) {
        require(nombre.isNotBlank()) { "Escribe un nombre" }
        val m = db.miembroDao().porId(miembro.name) ?: error("Ese titular aún no se ha unido")
        escritor.guardar(Tablas.MIEMBRO, m.copy(nombre = nombre.trim()))
    }
}

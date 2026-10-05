package com.entredos.data.notificaciones

import com.entredos.core.CategoriaInfo
import com.entredos.core.Clasificacion
import com.entredos.core.LectorGenerico
import com.entredos.core.OrigenGasto
import com.entredos.core.RecurrenteInfo
import com.entredos.core.ReglaInfo
import com.entredos.data.local.AjustesLocales
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.repository.GastosRepositorio
import com.entredos.data.repository.ParteNueva
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ResultadoIngreso {
    data object Duplicado : ResultadoIngreso
    /** Se clasificó solo (recurrente o regla). Mostrar el aviso breve "Gasto registrado". */
    data class Confirmado(val gasto: GastoEntity, val categoria: CategoriaEntity) : ResultadoIngreso
    /** Mostrar el aviso "Gasto detectado" con estas categorías como botones. */
    data class Pendiente(val gasto: GastoEntity, val sugerencias: List<CategoriaEntity>) : ResultadoIngreso
}

/**
 * Qué pasa con un gasto leído de una notificación (sección 2.3 del plan), en orden:
 * duplicado, recurrente, regla aprendida y, si nada aplica, pendiente.
 * Los lectores de cada banco y el genérico terminan aquí.
 */
@Singleton
class IngresoNotificacion @Inject constructor(
    private val db: EntreDosDatabase,
    private val gastos: GastosRepositorio,
    private val ajustes: AjustesLocales,
    private val dispositivo: DispositivoActual,
) {

    /**
     * @param forzarPendiente true para el lector genérico: nunca se clasifica solo.
     * @return null si este teléfono aún no pertenece a un hogar.
     */
    suspend fun registrar(monto: Long, comercio: String?, appBanco: String, fecha: Long, forzarPendiente: Boolean = false): ResultadoIngreso? {
        val yo = dispositivo.miembro() ?: return null
        val ventana = 5 * 60_000L
        if (db.gastoDao().buscarDuplicado(monto, comercio, appBanco, fecha - ventana, fecha + ventana) != null) {
            return ResultadoIngreso.Duplicado
        }

        val categorias = db.categoriaDao().todas()
        if (!forzarPendiente && !ajustes.actual.clasificacionManual) {
            val recurrentes = db.recurrenteDao().conPalabra()
            Clasificacion.recurrenteQueCoincide(
                comercio, monto, recurrentes.map { RecurrenteInfo(it.id, it.categoriaId, it.montoEsperado, it.palabraComercio) },
            )?.let { r ->
                val recurrente = recurrentes.first { it.id == r.id }
                return confirmar(recurrente.nombre, comercio, monto, fecha, appBanco, r.categoriaId, categorias)
            }
            Clasificacion.reglaAutomatica(comercio, reglas())?.let { regla ->
                return confirmar(comercio ?: "Gasto", comercio, monto, fecha, appBanco, regla.categoriaId, categorias)
            }
        }

        val gasto = gastos.registrarPendiente(monto, comercio ?: nombreApp(appBanco), comercio, fecha, yo, appBanco)
        val sugeridas = Clasificacion.sugerencias(comercio, reglas(), categorias.map { CategoriaInfo(it.id, it.nombre, it.destacada) })
        return ResultadoIngreso.Pendiente(gasto, sugeridas.mapNotNull { s -> categorias.firstOrNull { it.id == s.id } })
    }

    private suspend fun confirmar(
        nombre: String, comercio: String?, monto: Long, fecha: Long, appBanco: String, categoriaId: String, categorias: List<CategoriaEntity>,
    ): ResultadoIngreso? {
        val categoria = categorias.firstOrNull { it.id == categoriaId } ?: return null
        val yo = dispositivo.miembro() ?: return null
        val gasto = gastos.registrar(nombre, fecha, yo, listOf(ParteNueva(categoriaId, monto)), comercio, OrigenGasto.NOTIFICACION, appBanco)
        return ResultadoIngreso.Confirmado(gasto, categoria)
    }

    private suspend fun reglas() = db.reglaComercioDao().todas().map { ReglaInfo(it.id, it.palabra, it.categoriaId, it.confirmaciones, it.automatica) }

    private fun nombreApp(paquete: String) = paquete.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/** Lector genérico: solo para las apps elegidas en Detección; lo que lee siempre queda pendiente. */
@Singleton
class ReceptorGenerico @Inject constructor(
    private val ajustes: AjustesLocales,
    private val ingreso: IngresoNotificacion,
) {
    /** Llamar desde onNotificationPosted para los paquetes que no tienen lector propio. */
    suspend fun alRecibir(paquete: String, titulo: String?, texto: String?, cuando: Long): ResultadoIngreso? {
        val a = ajustes.actual
        if (!a.lectorGenericoActivo || paquete !in a.appsGenericas) return null
        val lectura = LectorGenerico.leer(titulo, texto) ?: return null
        return ingreso.registrar(lectura.monto, lectura.comercio, paquete, cuando, forzarPendiente = true)
    }
}

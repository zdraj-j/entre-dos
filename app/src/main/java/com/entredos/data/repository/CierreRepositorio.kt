package com.entredos.data.repository

import androidx.room.withTransaction
import com.entredos.core.CalculadoraDeudas
import com.entredos.core.Estadisticas
import com.entredos.core.GastoResumen
import com.entredos.core.Miembro
import com.entredos.core.ModoPeriodo
import com.entredos.core.Periodo
import com.entredos.core.TipoGasto
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.dao.GastoConPartes
import com.entredos.data.local.entity.PeriodoCerradoEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** "Cerrar y archivar" (sección 7.1) y el archivo automático del modo automático. */
@Singleton
class CierreRepositorio @Inject constructor(
    private val db: EntreDosDatabase,
    private val escritor: Escritor,
    private val periodo: PeriodoRepositorio,
    private val deudas: DeudasRepositorio,
    private val dispositivo: DispositivoActual,
) {
    private val zona: ZoneId get() = ZoneId.systemDefault()

    /**
     * Guarda el resumen del tramo actual y salda las deudas abiertas con abonos "por cierre".
     * No borra movimientos ni presupuestos. El fin de este cierre es el inicio del siguiente
     * periodo en los dos teléfonos.
     */
    suspend fun cerrar(): PeriodoCerradoEntity {
        val inicio = periodo.observarInicio().first()
        val fin = System.currentTimeMillis()
        val abiertas = deudas.observar().first().filter { !it.saldada }
        val neto = CalculadoraDeudas.balance(abiertas, Miembro.A).neto
        return db.withTransaction {
            val cerrado = guardarPeriodo(inicio, fin, neto, idFijo = null)
            deudas.saldarTodo(abiertas, fin, porCierre = true)
            cerrado
        }
    }

    /**
     * Modo automático: si ya pasó el día de inicio y el periodo anterior no tiene cierre, lo archiva.
     * El id es fijo por fecha para que si los dos teléfonos lo hacen a la vez, quede uno solo.
     * Las deudas no se saldan: siguen abiertas en el periodo nuevo.
     */
    suspend fun archivarSiCorresponde() {
        val hogar = db.hogarDao().actual() ?: return
        if (hogar.modoPeriodo != ModoPeriodo.AUTOMATICO) return
        val ultimoFin = db.periodoCerradoDao().observarUltimoFin().first()
        val (inicio, fin) = Periodo.pendienteDeArchivar(hogar.modoPeriodo, hogar.diaInicio, ultimoFin, LocalDate.now(zona), zona) ?: return
        val neto = CalculadoraDeudas.balance(deudas.observar().first(), Miembro.A).neto
        guardarPeriodo(inicio, fin, neto, idFijo = "auto-$fin")
    }

    private suspend fun guardarPeriodo(inicio: Long, fin: Long, netoA: Long, idFijo: String?): PeriodoCerradoEntity {
        val yo = dispositivo.miembro() ?: Miembro.A
        val categorias = db.categoriaDao().todosConEliminados().associateBy { it.id }
        val nombres = db.miembroDao().todos().associate { it.id to it.nombre }
        val gastos = db.gastoDao().confirmadosEntre(inicio, fin).filter { it.gasto.tipo == TipoGasto.GASTO }
        val r = Estadisticas.calcular(gastos.map(::aResumen))
        val activas = categorias.values.filter { !it.sync.eliminado }
        val gastadoPor = r.porCategoria.toMap()
        val porCategoria = (activas.map { it.id } + gastadoPor.keys).distinct().map { id ->
            TotalNombre(id, categorias[id]?.nombre ?: "Categoría eliminada", gastadoPor[id] ?: 0, categorias[id]?.takeIf { !it.sync.eliminado }?.presupuesto)
        }.sortedByDescending { it.total }
        val resumen = ResumenPeriodo(
            porCategoria = porCategoria,
            porPersona = r.porPersona.map { (m, total) -> TotalNombre(m.name, nombres[m.name] ?: "Titular ${m.name}", total) },
            comercios = r.comercios.map { (c, total) -> TotalNombre(nombre = c, total = total) },
        )
        val base = PeriodoCerradoEntity(
            inicio = inicio, fin = fin, totalPresupuestado = activas.sumOf { it.presupuesto ?: 0 }, totalGastado = r.total,
            deudaNetaA = netoA, resumenJson = Escritor.JSON.encodeToString(ResumenPeriodo.serializer(), resumen), cerradoPor = yo,
        )
        return escritor.guardar(Tablas.PERIODO_CERRADO, if (idFijo != null) base.copy(id = idFijo) else base)
    }

    companion object {
        fun aResumen(g: GastoConPartes) =
            GastoResumen(g.gasto.monto, g.gasto.pagadoPor, g.gasto.comercio, g.partesActivas.map { it.categoriaId to it.monto })
    }
}

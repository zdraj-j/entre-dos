package com.entredos.data.importacion

import androidx.room.withTransaction
import com.entredos.core.EstadoGasto
import com.entredos.core.EstadoLote
import com.entredos.core.Miembro
import com.entredos.core.OrigenGasto
import com.entredos.core.Reparto
import com.entredos.core.Responsable
import com.entredos.core.TipoGasto
import com.entredos.core.importacion.DestinoCategoria
import com.entredos.core.importacion.HuellaGasto
import com.entredos.core.importacion.PlanImportacion
import com.entredos.core.importacion.PlanImportador
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.Escritor
import com.entredos.data.local.Tablas
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.DeudaManualEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.HogarEntity
import com.entredos.data.local.entity.LoteImportacionEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.local.entity.ParteGastoEntity
import com.entredos.data.local.entity.PeriodoCerradoEntity
import com.entredos.data.local.entity.ReglaComercioEntity
import com.entredos.data.repository.PeriodoRepositorio
import com.entredos.data.repository.ResumenPeriodo
import com.entredos.data.repository.TotalNombre
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que hay en la base y el plan necesita para detectar duplicados y enlazar categorías. */
data class ContextoImportacion(
    val hogar: HogarEntity?,
    val miembros: List<MiembroEntity>,
    val categorias: List<CategoriaEntity>,
    val idsOrigenGastos: Set<String>,
    val idsOrigenDeudas: Set<String>,
    val huellas: List<HuellaGasto>,
    val inicioPeriodoActual: Long,
    val rangosCerrados: List<Pair<Long, Long>>,
)

data class OpcionesImportacion(val crearPeriodos: Boolean = true, val aprenderReglas: Boolean = true)

data class ResultadoImportacion(val lote: LoteImportacionEntity, val periodos: Int, val reglas: Int)

@Singleton
class ImportadorRepositorio @Inject constructor(
    private val db: EntreDosDatabase,
    private val escritor: Escritor,
    private val dispositivo: DispositivoActual,
    private val periodo: PeriodoRepositorio,
) {
    private val zona: ZoneId get() = ZoneId.systemDefault()

    suspend fun contexto() = ContextoImportacion(
        hogar = db.hogarDao().actual(),
        miembros = db.miembroDao().todos(),
        categorias = db.categoriaDao().todas(),
        idsOrigenGastos = db.gastoDao().idsOrigenImportados().toSet(),
        idsOrigenDeudas = db.deudaManualDao().idsOrigen().toSet(),
        huellas = db.gastoDao().huellas(),
        inicioPeriodoActual = periodo.observarInicio().first(),
        rangosCerrados = db.periodoCerradoDao().rangos().map { it.inicio to it.fin },
    )

    /**
     * Guarda todo lo del plan bajo un lote nuevo. Escribe en bloques de 100 gastos para no
     * hacer transacciones enormes; todo pasa por el Escritor, así que llega a la pareja.
     */
    suspend fun aplicar(
        plan: PlanImportacion,
        destinos: Map<String, DestinoCategoria>,
        opciones: OpcionesImportacion,
        archivo: String,
        ctx: ContextoImportacion,
    ): ResultadoImportacion {
        val yo = dispositivo.miembro() ?: error("Este teléfono aún no tiene titular")
        var lote = escritor.guardar(
            Tablas.LOTE_IMPORTACION,
            LoteImportacionEntity(fecha = System.currentTimeMillis(), archivo = archivo, gastos = 0, categorias = 0, deudas = 0),
        )

        // 1. Categorías nuevas
        val idPorClave = mutableMapOf<String, String>()
        val responsablePorId = ctx.categorias.associate { it.id to it.responsable }.toMutableMap()
        val nombrePorId = ctx.categorias.associate { it.id to it.nombre }.toMutableMap()
        destinos.forEach { (clave, d) -> if (d is DestinoCategoria.Existente) idPorClave[clave] = d.id }
        db.withTransaction {
            plan.categoriasNuevas.entries.forEachIndexed { i, (clave, n) ->
                val c = escritor.guardar(
                    Tablas.CATEGORIA,
                    CategoriaEntity(
                        nombre = n.nombre, color = n.color ?: PALETA[i % PALETA.size], responsable = n.responsable,
                        presupuesto = n.presupuesto, destacada = n.destacada, orden = ctx.categorias.size + i, loteImportacion = lote.id,
                    ),
                )
                idPorClave[clave] = c.id
                responsablePorId[c.id] = c.responsable
                nombrePorId[c.id] = c.nombre
            }
        }

        // 2. Gastos y sus partes
        for (bloque in plan.gastos.chunked(100)) {
            db.withTransaction {
                for (g in bloque) {
                    val gasto = escritor.guardar(
                        Tablas.GASTO,
                        GastoEntity(
                            monto = g.monto, nombre = g.nombre, comercio = g.comercio, fecha = g.fecha, pagadoPor = g.pagadoPor,
                            tipo = if (g.transferencia) TipoGasto.TRANSFERENCIA else TipoGasto.GASTO,
                            estado = EstadoGasto.CONFIRMADO, origen = OrigenGasto.IMPORTACION,
                            loteImportacion = lote.id, idOrigen = g.idOrigen,
                        ),
                    )
                    for (p in g.partes) {
                        val categoriaId = idPorClave.getValue(p.categoria)
                        val cargos = Reparto.repartir(p.monto, responsablePorId[categoriaId] ?: Responsable.COMPARTIDA, g.pagadoPor, p.cargoPagador)
                        escritor.guardar(
                            Tablas.PARTE_GASTO,
                            ParteGastoEntity(gastoId = gasto.id, categoriaId = categoriaId, monto = p.monto, cargoA = cargos.cargoA, cargoB = cargos.cargoB),
                        )
                    }
                }
            }
        }

        // 3. Deudas abiertas
        db.withTransaction {
            plan.deudas.forEach { d ->
                escritor.guardar(
                    Tablas.DEUDA_MANUAL,
                    DeudaManualEntity(deudor = d.deudor, acreedor = d.acreedor, monto = d.monto, concepto = d.concepto, fecha = d.fecha, loteImportacion = lote.id, idOrigen = d.idOrigen),
                )
            }
        }

        // 4. Periodos anteriores
        var periodos = 0
        if (opciones.crearPeriodos && ctx.hogar != null) {
            val nombres = ctx.miembros.associate { it.id to it.nombre }
            val presupuestado = (ctx.categorias.sumOf { it.presupuesto ?: 0 } + plan.categoriasNuevas.values.sumOf { it.presupuesto ?: 0 })
            val historicos = PlanImportador.periodosHistoricos(plan.gastos, ctx.hogar.diaInicio, ctx.inicioPeriodoActual, ctx.rangosCerrados, zona)
            db.withTransaction {
                historicos.forEach { h ->
                    val resumen = ResumenPeriodo(
                        porCategoria = h.porCategoria.map { (clave, total) -> idPorClave.getValue(clave).let { TotalNombre(it, nombrePorId[it] ?: clave, total) } }.sortedByDescending { it.total },
                        porPersona = h.porPersona.map { (m, total) -> TotalNombre(m.name, nombres[m.name] ?: "Titular ${m.name}", total) },
                        comercios = h.comercios.map { (c, total) -> TotalNombre(nombre = c, total = total) },
                        importado = true,
                    )
                    escritor.guardar(
                        Tablas.PERIODO_CERRADO,
                        PeriodoCerradoEntity(
                            inicio = h.inicio, fin = h.fin, totalPresupuestado = presupuestado, totalGastado = h.total, deudaNetaA = 0,
                            resumenJson = Escritor.JSON.encodeToString(ResumenPeriodo.serializer(), resumen), cerradoPor = yo, loteImportacion = lote.id,
                        ),
                    )
                }
            }
            periodos = historicos.size
        }

        // 5. Reglas de comercio (solo las que aún no existen)
        var reglas = 0
        if (opciones.aprenderReglas) {
            db.withTransaction {
                PlanImportador.reglas(plan.gastos).forEach { (palabra, clave) ->
                    if (db.reglaComercioDao().porPalabra(palabra) == null) {
                        escritor.guardar(Tablas.REGLA_COMERCIO, ReglaComercioEntity(palabra = palabra, categoriaId = idPorClave.getValue(clave), confirmaciones = 3, automatica = true))
                        reglas++
                    }
                }
            }
        }

        lote = escritor.guardar(
            Tablas.LOTE_IMPORTACION,
            lote.copy(gastos = plan.gastos.size, categorias = plan.categoriasNuevas.size, deudas = plan.deudas.size),
        )
        return ResultadoImportacion(lote, periodos, reglas)
    }

    /**
     * Marca como eliminado todo lo que creó el lote; el borrado llega a la pareja.
     * Una categoría importada que ya se usó en gastos registrados después se conserva.
     * Las reglas aprendidas se conservan (se apagan desde Detección).
     */
    suspend fun deshacer(lote: LoteImportacionEntity) {
        for (bloque in db.gastoDao().porLote(lote.id).chunked(100)) {
            db.withTransaction {
                bloque.forEach { g ->
                    db.parteGastoDao().porGasto(g.id).forEach { escritor.eliminar(Tablas.PARTE_GASTO, it) }
                    escritor.eliminar(Tablas.GASTO, g)
                }
            }
        }
        db.withTransaction {
            db.categoriaDao().porLote(lote.id).forEach { c ->
                if (db.parteGastoDao().usosFueraDelLote(c.id, lote.id) == 0) escritor.eliminar(Tablas.CATEGORIA, c)
            }
            db.deudaManualDao().porLote(lote.id).forEach { escritor.eliminar(Tablas.DEUDA_MANUAL, it) }
            db.periodoCerradoDao().porLote(lote.id).forEach { escritor.eliminar(Tablas.PERIODO_CERRADO, it) }
            escritor.guardar(Tablas.LOTE_IMPORTACION, lote.copy(estado = EstadoLote.DESHECHO))
        }
    }

    private companion object {
        val PALETA = listOf("#2E7D5B", "#3A5BA0", "#C77A12", "#B4472B", "#6B4FA0", "#1F8A8A", "#8A5A2E", "#A23B72", "#4F6D2E", "#5C6670")
    }
}

/** Para mostrar nombres de titular en el asistente. */
fun nombreTitular(miembros: List<MiembroEntity>, m: Miembro): String =
    miembros.firstOrNull { it.id == m.name }?.nombre ?: if (m == Miembro.B) "Titular B (aún no se une)" else "Titular A"

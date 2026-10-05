package com.entredos.core.importacion

import com.entredos.core.Clasificacion
import com.entredos.core.Miembro
import com.entredos.core.Movimientos
import com.entredos.core.Periodo
import com.entredos.core.Responsable
import java.time.Instant
import java.time.ZoneId

data class ParteImportada(val categoria: String, val monto: Long, val cargoPagador: Long?)

/** Un gasto del archivo, ya con sus partes juntas (las filas con el mismo id_origen). */
data class GastoImportado(
    val idOrigen: String?,
    val lineas: List<Int>,
    val fecha: Long,
    val pagadoPor: String,
    val descripcion: String?,
    val comercio: String?,
    val transferencia: Boolean,
    val partes: List<ParteImportada>,
) {
    val monto: Long get() = partes.sumOf { it.monto }
}

/** A dónde va cada categoría del archivo. */
sealed interface DestinoCategoria {
    data class Existente(val id: String) : DestinoCategoria
    data class Nueva(
        val nombre: String,
        val responsable: Responsable,
        val presupuesto: Long? = null,
        val color: String? = null,
        val destacada: Boolean = false,
    ) : DestinoCategoria
}

data class ParteLista(val categoria: String, val monto: Long, val cargoPagador: Long?)

data class GastoListo(
    val idOrigen: String?,
    val fecha: Long,
    val pagadoPor: Miembro,
    val nombre: String,
    val comercio: String?,
    val transferencia: Boolean,
    /** [ParteLista.categoria] es la clave normalizada de [EntradaPlan.categorias]. */
    val partes: List<ParteLista>,
) {
    val monto: Long get() = partes.sumOf { it.monto }
}

data class DeudaLista(val idOrigen: String?, val fecha: Long, val deudor: Miembro, val acreedor: Miembro, val monto: Long, val concepto: String)

/** Huella de un gasto que ya existe, para detectar posibles duplicados sin id_origen. */
data class HuellaGasto(val fecha: Long, val monto: Long, val texto: String)

data class EntradaPlan(
    val gastos: List<GastoImportado>,
    val deudas: List<FilaDeuda> = emptyList(),
    val metas: List<FilaMeta> = emptyList(),
    /** Nombre de persona normalizado → titular. */
    val personas: Map<String, Miembro>,
    /** Nombre de categoría normalizado → destino. */
    val categorias: Map<String, DestinoCategoria>,
    val idsOrigenGastos: Set<String> = emptySet(),
    val idsOrigenDeudas: Set<String> = emptySet(),
    val metasExistentes: Set<String> = emptySet(),
    val huellasExistentes: List<HuellaGasto> = emptyList(),
    val omitirPosiblesDuplicados: Boolean = true,
    val zona: ZoneId,
)

data class PlanImportacion(
    val gastos: List<GastoListo>,
    val deudas: List<DeudaLista>,
    val metas: List<FilaMeta>,
    /** Claves de las categorías nuevas que se crearán (solo las que usa algún gasto). */
    val categoriasNuevas: Map<String, DestinoCategoria.Nueva>,
    val duplicados: Int,
    val posiblesDuplicados: Int,
    val errores: List<ErrorFila>,
)

object PlanImportador {

    fun clave(nombre: String) = Movimientos.normalizar(nombre.trim())

    /** Junta las filas con el mismo id_origen en un solo gasto y valida que coincidan. */
    fun agrupar(filas: List<FilaGasto>): Leido<GastoImportado> {
        val errores = mutableListOf<ErrorFila>()
        val gastos = mutableListOf<GastoImportado>()
        val (conId, sinId) = filas.partition { it.idOrigen != null }
        for ((id, grupo) in conId.groupBy { it.idOrigen!! }) {
            val p = grupo.first()
            val consistente = grupo.all {
                it.fecha == p.fecha && it.pagadoPor == p.pagadoPor && it.descripcion == p.descripcion &&
                    it.comercio == p.comercio && it.transferencia == p.transferencia
            }
            if (!consistente) {
                errores += ErrorFila("gastos.csv", p.linea, "Las filas del gasto $id no coinciden en fecha, quién pagó, descripción, comercio o tipo (líneas ${grupo.joinToString { it.linea.toString() }})")
                continue
            }
            gastos += GastoImportado(id, grupo.map { it.linea }, p.fecha, p.pagadoPor, p.descripcion, p.comercio, p.transferencia,
                grupo.map { ParteImportada(it.categoria, it.monto, it.cargoPagador) })
        }
        sinId.forEach { f ->
            gastos += GastoImportado(null, listOf(f.linea), f.fecha, f.pagadoPor, f.descripcion, f.comercio, f.transferencia,
                listOf(ParteImportada(f.categoria, f.monto, f.cargoPagador)))
        }
        return Leido(gastos.sortedBy { it.lineas.first() }, errores)
    }

    fun armar(e: EntradaPlan): PlanImportacion {
        val errores = mutableListOf<ErrorFila>()
        val listos = mutableListOf<GastoListo>()
        var duplicados = 0
        var posibles = 0
        val huellas = e.huellasExistentes.map { huella(it.fecha, it.monto, it.texto, e.zona) }.toMutableSet()

        for (g in e.gastos) {
            val linea = g.lineas.first()
            if (g.idOrigen != null && g.idOrigen in e.idsOrigenGastos) { duplicados++; continue }
            val quien = e.personas[clave(g.pagadoPor)]
            if (quien == null) { errores += ErrorFila("gastos.csv", linea, "No se asignó a ningún titular: ${g.pagadoPor}"); continue }
            val sinDestino = g.partes.map { it.categoria }.filter { clave(it) !in e.categorias }
            if (sinDestino.isNotEmpty()) { errores += ErrorFila("gastos.csv", linea, "Categoría sin asignar: ${sinDestino.joinToString()}"); continue }

            val texto = g.comercio ?: g.descripcion ?: ""
            if (g.idOrigen == null) {
                val h = huella(g.fecha, g.monto, texto, e.zona)
                if (h in huellas) {
                    posibles++
                    if (e.omitirPosiblesDuplicados) continue
                }
                huellas += h
            }
            listos += GastoListo(
                idOrigen = g.idOrigen, fecha = g.fecha, pagadoPor = quien,
                nombre = g.descripcion ?: g.comercio ?: g.partes.joinToString(", ") { it.categoria },
                comercio = g.comercio, transferencia = g.transferencia,
                partes = g.partes.map { ParteLista(clave(it.categoria), it.monto, it.cargoPagador) },
            )
        }

        val deudas = mutableListOf<DeudaLista>()
        for (d in e.deudas) {
            if (d.idOrigen != null && d.idOrigen in e.idsOrigenDeudas) { duplicados++; continue }
            val deudor = e.personas[clave(d.deudor)]
            val acreedor = e.personas[clave(d.acreedor)]
            when {
                deudor == null || acreedor == null -> errores += ErrorFila("deudas_abiertas.csv", d.linea, "Persona sin asignar: ${if (deudor == null) d.deudor else d.acreedor}")
                deudor == acreedor -> errores += ErrorFila("deudas_abiertas.csv", d.linea, "Deudor y acreedor quedaron en el mismo titular")
                else -> deudas += DeudaLista(d.idOrigen, d.fecha, deudor, acreedor, d.monto, d.concepto ?: "Deuda importada")
            }
        }

        val metas = e.metas.filter { clave(it.nombre) !in e.metasExistentes.map(::clave) }
        val usadas = listos.flatMap { g -> g.partes.map { it.categoria } }.toSet()
        val nuevas = e.categorias.filterKeys { it in usadas }.mapNotNull { (k, v) -> (v as? DestinoCategoria.Nueva)?.let { k to it } }.toMap()

        return PlanImportacion(listos, deudas, metas, nuevas, duplicados, posibles, errores)
    }

    private fun huella(fecha: Long, monto: Long, texto: String, zona: ZoneId) =
        Triple(Instant.ofEpochMilli(fecha).atZone(zona).toLocalDate(), monto, Movimientos.normalizar(texto.trim()))

    // ---------- Periodos anteriores ----------

    data class PeriodoHistorico(
        val inicio: Long,
        val fin: Long,
        val total: Long,
        /** Clave de categoría → gastado. */
        val porCategoria: Map<String, Long>,
        val porPersona: Map<Miembro, Long>,
        /** Los 5 comercios con más gasto. */
        val comercios: List<Pair<String, Long>>,
    )

    /**
     * Agrupa los gastos importados anteriores al periodo actual en periodos mensuales según el
     * día de inicio, y omite los que se cruzan con un periodo cerrado que ya existe.
     */
    fun periodosHistoricos(
        gastos: List<GastoListo>,
        diaInicio: Int,
        inicioPeriodoActual: Long,
        existentes: List<Pair<Long, Long>>,
        zona: ZoneId,
    ): List<PeriodoHistorico> {
        val anteriores = gastos.filter { !it.transferencia && it.fecha < inicioPeriodoActual }
        return anteriores.groupBy { g ->
            Periodo.ultimoDiaDeInicio(diaInicio, Instant.ofEpochMilli(g.fecha).atZone(zona).toLocalDate())
        }.map { (desde, lista) ->
            val inicio = desde.atStartOfDay(zona).toInstant().toEpochMilli()
            val fin = minOf(desde.plusMonths(1).atStartOfDay(zona).toInstant().toEpochMilli(), inicioPeriodoActual)
            PeriodoHistorico(
                inicio = inicio, fin = fin,
                total = lista.sumOf { it.monto },
                porCategoria = lista.flatMap { it.partes }.groupBy { it.categoria }.mapValues { (_, v) -> v.sumOf { it.monto } },
                porPersona = lista.groupBy { it.pagadoPor }.mapValues { (_, v) -> v.sumOf { it.monto } },
                comercios = lista.filter { it.comercio != null }.groupBy { it.comercio!! }
                    .map { (c, v) -> c to v.sumOf { it.monto } }.sortedByDescending { it.second }.take(5),
            )
        }.filter { p -> existentes.none { (i, f) -> p.inicio < f && i < p.fin } }
            .sortedBy { it.inicio }
    }

    // ---------- Reglas ----------

    /**
     * Comercios con 3 o más gastos (de una sola categoría) en la misma categoría:
     * palabra del comercio → clave de categoría. Si un comercio cumple en varias, gana la de más gastos.
     */
    fun reglas(gastos: List<GastoListo>): Map<String, String> =
        gastos.asSequence()
            .filter { !it.transferencia && it.partes.size == 1 && !it.comercio.isNullOrBlank() }
            .map { Clasificacion.palabraDeComercio(it.comercio!!) to it.partes.single().categoria }
            .filter { it.first.isNotBlank() }
            .groupingBy { it }.eachCount()
            .filter { it.value >= Clasificacion.CONFIRMACIONES_PARA_AUTOMATICA }
            .entries.groupBy { it.key.first }
            .mapValues { (_, v) -> v.maxBy { it.value }.key.second }
}

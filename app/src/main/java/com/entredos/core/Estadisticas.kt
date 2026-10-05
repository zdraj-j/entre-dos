package com.entredos.core

/** Un gasto confirmado de tipo GASTO, con lo necesario para las estadísticas. */
data class GastoResumen(
    val monto: Long,
    val pagadoPor: Miembro,
    val comercio: String?,
    /** (id de categoría, monto de la parte). */
    val partes: List<Pair<String, Long>>,
)

data class ResumenCalculado(
    val total: Long,
    /** De mayor a menor. */
    val porCategoria: List<Pair<String, Long>>,
    val porPersona: Map<Miembro, Long>,
    /** Los 5 comercios con más gasto (nombre como apareció primero). */
    val comercios: List<Pair<String, Long>>,
)

object Estadisticas {
    fun calcular(gastos: List<GastoResumen>): ResumenCalculado {
        val porCategoria = gastos.flatMap { it.partes }
            .groupBy({ it.first }, { it.second })
            .map { (id, montos) -> id to montos.sum() }
            .sortedByDescending { it.second }
        val comercios = gastos.filter { !it.comercio.isNullOrBlank() }
            .groupBy { Movimientos.normalizar(it.comercio!!.trim()) }
            .map { (_, lista) -> lista.first().comercio!!.trim() to lista.sumOf { it.monto } }
            .sortedByDescending { it.second }
            .take(5)
        return ResumenCalculado(
            total = gastos.sumOf { it.monto },
            porCategoria = porCategoria,
            porPersona = Miembro.entries.associateWith { m -> gastos.filter { it.pagadoPor == m }.sumOf { it.monto } },
            comercios = comercios,
        )
    }
}

package com.entredos.core

data class CategoriaConTope(val id: String, val presupuesto: Long?)

/** Monto de una parte de gasto confirmado, de tipo GASTO, dentro del periodo. */
data class ParteEnPeriodo(val categoriaId: String, val monto: Long)

data class EstadoCategoria(
    val categoriaId: String,
    val gastado: Long,
    /** Null = sin tope. */
    val presupuesto: Long?,
) {
    /** Lo que queda; negativo si se pasó. Null si no tiene tope. */
    val disponible: Long? get() = presupuesto?.let { it - gastado }
    val excedida: Boolean get() = (disponible ?: 0) < 0
}

data class EstadoPeriodo(val porCategoria: Map<String, EstadoCategoria>, val asignado: Long, val gastado: Long) {
    /** Asignado menos todo lo gastado; negativo si se pasaron. */
    val disponible: Long get() = asignado - gastado
}

object Presupuesto {
    fun calcular(categorias: List<CategoriaConTope>, partes: List<ParteEnPeriodo>): EstadoPeriodo {
        val gastadoPorCategoria = partes.groupBy { it.categoriaId }.mapValues { (_, v) -> v.sumOf { it.monto } }
        val porCategoria = categorias.associate { c ->
            c.id to EstadoCategoria(c.id, gastadoPorCategoria[c.id] ?: 0, c.presupuesto)
        }
        return EstadoPeriodo(
            porCategoria = porCategoria,
            asignado = categorias.sumOf { it.presupuesto ?: 0 },
            gastado = partes.sumOf { it.monto },
        )
    }
}

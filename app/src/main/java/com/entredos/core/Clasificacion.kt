package com.entredos.core

import kotlin.math.abs

data class RecurrenteInfo(val id: String, val categoriaId: String, val montoEsperado: Long, val palabraComercio: String?)

data class ReglaInfo(val id: String, val palabra: String, val categoriaId: String, val confirmaciones: Int, val automatica: Boolean)

data class CategoriaInfo(val id: String, val nombre: String, val destacada: Boolean)

/** Cómo quedó una regla tras confirmar un gasto de un comercio en una sola categoría. */
data class ReglaActualizada(val categoriaId: String, val confirmaciones: Int, val automatica: Boolean)

/** Reglas de la sección 2.3 del plan: recurrentes, reglas aprendidas y sugerencias. */
object Clasificacion {

    const val CONFIRMACIONES_PARA_AUTOMATICA = 3

    /** Misma palabra de comercio y monto parecido: margen del 10 % o de $2.000, el que sea mayor. */
    fun recurrenteQueCoincide(comercio: String?, monto: Long, recurrentes: List<RecurrenteInfo>): RecurrenteInfo? {
        val texto = Movimientos.normalizar(comercio ?: return null)
        return recurrentes.firstOrNull { r ->
            val palabra = r.palabraComercio?.let(Movimientos::normalizar)?.takeIf { it.isNotBlank() } ?: return@firstOrNull false
            val margen = maxOf(r.montoEsperado / 10, 2_000)
            palabra in texto && abs(monto - r.montoEsperado) <= margen
        }
    }

    /** Regla con clasificación automática cuya palabra aparece en el comercio. Gana la palabra más larga. */
    fun reglaAutomatica(comercio: String?, reglas: List<ReglaInfo>): ReglaInfo? {
        val texto = Movimientos.normalizar(comercio ?: return null)
        return reglas.filter { it.automatica && Movimientos.normalizar(it.palabra) in texto }
            .maxByOrNull { it.palabra.length }
    }

    /**
     * Al confirmar un comercio en una sola categoría: misma categoría suma una confirmación;
     * otra categoría reinicia el conteo. Con 3 se activa la automática (una vez activada,
     * solo la persona la apaga desde Detección).
     */
    fun aprender(actual: ReglaInfo?, categoriaId: String): ReglaActualizada {
        if (actual == null || actual.categoriaId != categoriaId) {
            return ReglaActualizada(categoriaId, 1, CONFIRMACIONES_PARA_AUTOMATICA <= 1)
        }
        val conteo = actual.confirmaciones + 1
        return ReglaActualizada(categoriaId, conteo, actual.automatica || conteo >= CONFIRMACIONES_PARA_AUTOMATICA)
    }

    /** Hasta [cuantas] categorías para los botones del aviso: la de la regla, luego destacadas, luego alfabético. */
    fun sugerencias(comercio: String?, reglas: List<ReglaInfo>, categorias: List<CategoriaInfo>, cuantas: Int = 3): List<CategoriaInfo> {
        val texto = comercio?.let(Movimientos::normalizar)
        val deRegla = texto?.let { t ->
            reglas.filter { Movimientos.normalizar(it.palabra) in t }.maxByOrNull { it.confirmaciones }?.categoriaId
        }
        val orden = compareByDescending<CategoriaInfo> { it.id == deRegla }
            .thenByDescending { it.destacada }
            .thenBy { Movimientos.normalizar(it.nombre) }
        return categorias.sortedWith(orden).take(cuantas)
    }

    /** Palabra que identifica al comercio en una regla: el comercio normalizado, sin números de sucursal. */
    fun palabraDeComercio(comercio: String): String =
        Movimientos.normalizar(comercio).replace(Regex("""\b(sucursal|suc|no|nro)?\.?\s*\d+\b"""), "").replace(Regex("""\s+"""), " ").trim()
}

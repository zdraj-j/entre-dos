package com.entredos.core

/** Cuánto de una parte de gasto asume cada titular. Siempre suman el monto de la parte. */
data class Cargos(val cargoA: Long, val cargoB: Long) {
    val total: Long get() = cargoA + cargoB

    fun de(miembro: Miembro): Long = if (miembro == Miembro.A) cargoA else cargoB
}

object Reparto {

    /**
     * Reparte el monto de una parte entre los dos titulares.
     *
     * - Categoría personal: la asume completa su dueño.
     * - Categoría compartida: 50/50; si el monto es impar, el peso que sobra lo asume quien pagó.
     * - [cargoPagador] (reparto ajustado a mano): lo que asume quien pagó; el resto, el otro.
     */
    fun repartir(
        monto: Long,
        responsable: Responsable,
        pagadoPor: Miembro,
        cargoPagador: Long? = null,
    ): Cargos {
        require(monto > 0) { "El monto debe ser mayor que cero: $monto" }
        if (cargoPagador != null) {
            require(cargoPagador in 0..monto) { "El cargo de quien pagó debe estar entre 0 y $monto: $cargoPagador" }
            return armar(pagadoPor, cargoPagador, monto - cargoPagador)
        }
        return when (responsable) {
            Responsable.A -> Cargos(monto, 0)
            Responsable.B -> Cargos(0, monto)
            Responsable.COMPARTIDA -> {
                val delOtro = monto / 2
                armar(pagadoPor, monto - delOtro, delOtro)
            }
        }
    }

    /**
     * Divide un monto en [partes] iguales. Los pesos que sobran van a las primeras partes,
     * así la suma siempre da el monto exacto.
     */
    fun dividirEnPartesIguales(monto: Long, partes: Int): List<Long> {
        require(monto > 0) { "El monto debe ser mayor que cero: $monto" }
        require(partes in 1..monto) { "No se puede dividir $monto en $partes partes" }
        val base = monto / partes
        val sobrante = (monto % partes).toInt()
        return List(partes) { i -> base + if (i < sobrante) 1 else 0 }
    }

    /**
     * Valida una división con montos distintos: todas las partes mayores que cero
     * y la suma igual al total. Devuelve el motivo del error o null si está bien.
     */
    fun validarDivision(total: Long, partes: List<Long>): String? = when {
        partes.isEmpty() -> "Elige al menos una categoría"
        partes.any { it <= 0 } -> "Ninguna parte puede quedar en cero"
        partes.sum() != total -> "Las partes suman ${partes.sum()} y el gasto es de $total"
        else -> null
    }

    /** En "Ajustar montos", la última parte se calcula sola con lo que falta. */
    fun completarUltima(total: Long, primeras: List<Long>): Long = total - primeras.sum()

    private fun armar(pagadoPor: Miembro, cargoPagador: Long, cargoOtro: Long): Cargos =
        if (pagadoPor == Miembro.A) Cargos(cargoPagador, cargoOtro) else Cargos(cargoOtro, cargoPagador)
}

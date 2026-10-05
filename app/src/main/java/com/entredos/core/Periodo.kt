package com.entredos.core

import java.time.LocalDate
import java.time.ZoneId

object Periodo {

    /**
     * Inicio del periodo actual en epoch millis.
     *
     * - Manual: empieza en el último cierre. Si nunca se ha cerrado, en el último día de inicio.
     * - Automático: el más reciente entre el último cierre y el último día de inicio
     *   (si se cerró a mano a mitad de mes, cuenta desde ese cierre).
     */
    fun inicioActual(
        modo: ModoPeriodo,
        diaInicio: Int,
        ultimoCierre: Long?,
        hoy: LocalDate,
        zona: ZoneId,
    ): Long {
        val porDia = ultimoDiaDeInicio(diaInicio, hoy).atStartOfDay(zona).toInstant().toEpochMilli()
        return when (modo) {
            ModoPeriodo.MANUAL -> ultimoCierre ?: porDia
            ModoPeriodo.AUTOMATICO -> maxOf(ultimoCierre ?: Long.MIN_VALUE, porDia)
        }
    }

    /** El día de inicio más reciente que ya llegó (hoy incluido). */
    fun ultimoDiaDeInicio(diaInicio: Int, hoy: LocalDate): LocalDate {
        require(diaInicio in 1..28) { "El día de inicio debe estar entre 1 y 28" }
        return if (hoy.dayOfMonth >= diaInicio) hoy.withDayOfMonth(diaInicio)
        else hoy.minusMonths(1).withDayOfMonth(diaInicio)
    }
}

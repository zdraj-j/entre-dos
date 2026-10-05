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

    /**
     * Modo automático: el periodo que ya terminó y aún no tiene cierre, como (inicio, fin).
     * Si la app no se abrió en meses, el hueco queda en un solo periodo desde el último cierre.
     * Null si no hay nada que archivar.
     */
    fun pendienteDeArchivar(modo: ModoPeriodo, diaInicio: Int, ultimoCierre: Long?, hoy: LocalDate, zona: ZoneId): Pair<Long, Long>? {
        if (modo != ModoPeriodo.AUTOMATICO) return null
        val inicioActual = ultimoDiaDeInicio(diaInicio, hoy)
        val fin = inicioActual.atStartOfDay(zona).toInstant().toEpochMilli()
        if (ultimoCierre != null && ultimoCierre >= fin) return null
        val inicio = ultimoCierre ?: inicioActual.minusMonths(1).atStartOfDay(zona).toInstant().toEpochMilli()
        return inicio to fin
    }

    /** Fin (exclusivo) del periodo que empieza en [inicio]: el siguiente día de inicio. */
    fun finDelPeriodo(diaInicio: Int, inicio: LocalDate): LocalDate {
        val siguiente = inicio.plusMonths(1)
        return siguiente.withDayOfMonth(minOf(diaInicio, siguiente.lengthOfMonth()))
    }

    /**
     * ¿Se van a pasar del presupuesto a este ritmo? Proyecta lo gastado hasta hoy al total de días
     * del periodo. Solo avisa desde el tercer día, para no alarmar por un gasto grande el día 1.
     */
    fun seVanAPasar(gastado: Long, asignado: Long, inicio: LocalDate, fin: LocalDate, hoy: LocalDate): Boolean {
        if (asignado <= 0) return false
        if (gastado > asignado) return true
        val total = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin).coerceAtLeast(1)
        val transcurridos = (java.time.temporal.ChronoUnit.DAYS.between(inicio, hoy) + 1).coerceIn(1, total)
        if (transcurridos < 3) return false
        return gastado * total / transcurridos > asignado
    }
}

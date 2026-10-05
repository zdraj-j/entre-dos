package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PeriodoYPresupuestoTest {
    private val bogota = ZoneId.of("America/Bogota")
    private fun ms(f: LocalDate) = f.atStartOfDay(bogota).toInstant().toEpochMilli()

    @Test fun ultimoDiaDeInicio() {
        assertEquals(LocalDate.of(2026, 10, 1), Periodo.ultimoDiaDeInicio(1, LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 9, 15), Periodo.ultimoDiaDeInicio(15, LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 1, 25), Periodo.ultimoDiaDeInicio(25, LocalDate.of(2026, 2, 3)))
    }

    @Test fun manualEmpiezaEnElUltimoCierre() {
        val cierre = ms(LocalDate.of(2026, 9, 20))
        assertEquals(cierre, Periodo.inicioActual(ModoPeriodo.MANUAL, 1, cierre, LocalDate.of(2026, 10, 5), bogota))
        assertEquals(ms(LocalDate.of(2026, 10, 1)), Periodo.inicioActual(ModoPeriodo.MANUAL, 1, null, LocalDate.of(2026, 10, 5), bogota))
    }

    @Test fun automaticoTomaLoMasReciente() {
        val cierreViejo = ms(LocalDate.of(2026, 9, 20))
        val cierreNuevo = ms(LocalDate.of(2026, 10, 3))
        val hoy = LocalDate.of(2026, 10, 5)
        assertEquals(ms(LocalDate.of(2026, 10, 1)), Periodo.inicioActual(ModoPeriodo.AUTOMATICO, 1, cierreViejo, hoy, bogota))
        assertEquals(cierreNuevo, Periodo.inicioActual(ModoPeriodo.AUTOMATICO, 1, cierreNuevo, hoy, bogota))
    }

    @Test fun disponiblePorCategoriaYTotal() {
        val estado = Presupuesto.calcular(
            listOf(CategoriaConTope("mercado", 900_000), CategoriaConTope("salidas", 100_000), CategoriaConTope("varios", null)),
            listOf(ParteEnPeriodo("mercado", 400_000), ParteEnPeriodo("salidas", 115_000), ParteEnPeriodo("varios", 30_000)),
        )
        assertEquals(500_000L, estado.porCategoria["mercado"]!!.disponible)
        assertTrue(estado.porCategoria["salidas"]!!.excedida)
        assertEquals(null, estado.porCategoria["varios"]!!.disponible)
        assertEquals(1_000_000L, estado.asignado)
        assertEquals(545_000L, estado.gastado)
        assertEquals(455_000L, estado.disponible)
    }

    @Test fun completarUltimaParte() {
        assertEquals(25_000L, Reparto.completarUltima(85_000, listOf(60_000)))
    }
}

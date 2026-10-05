package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MesTest {
    private fun d(m: Int, dia: Int) = LocalDate.of(2026, m, dia)

    @Test fun finDelPeriodo() {
        assertEquals(d(11, 1), Periodo.finDelPeriodo(1, d(10, 1)))
        assertEquals(d(11, 15), Periodo.finDelPeriodo(15, d(10, 15)))
        assertEquals(LocalDate.of(2027, 2, 28), Periodo.finDelPeriodo(28, LocalDate.of(2027, 1, 28)))
    }

    @Test fun ritmo() {
        // 80 % gastado al día 5 de 31: se pasan.
        assertTrue(Periodo.seVanAPasar(2_400_000, 3_000_000, d(10, 1), d(11, 1), d(10, 5)))
        // 40 % gastado a mitad de mes: van bien.
        assertFalse(Periodo.seVanAPasar(1_200_000, 3_000_000, d(10, 1), d(11, 1), d(10, 16)))
        // Día 1 con un gasto grande: aún no avisa.
        assertFalse(Periodo.seVanAPasar(1_200_000, 3_000_000, d(10, 1), d(11, 1), d(10, 1)))
        // Ya se pasaron: siempre avisa.
        assertTrue(Periodo.seVanAPasar(3_100_000, 3_000_000, d(10, 1), d(11, 1), d(10, 1)))
        assertFalse(Periodo.seVanAPasar(500_000, 0, d(10, 1), d(11, 1), d(10, 20)))
    }

    @Test fun pagoSeRepartePorAntiguedad() {
        val deudas = listOf(
            Deuda("nueva", OrigenDeuda.GASTO, Miembro.B, Miembro.A, 50_000, 0, "Mercado", 300),
            Deuda("vieja", OrigenDeuda.GASTO, Miembro.B, Miembro.A, 600_000, 300_000, "Arriendo", 100),
            Deuda("mia", OrigenDeuda.GASTO, Miembro.A, Miembro.B, 17_500, 0, "Taxi", 200),
        )
        assertEquals(linkedMapOf("vieja" to 300_000L, "nueva" to 20_000L), CalculadoraDeudas.repartirPago(deudas, Miembro.B, 320_000))
        assertEquals(mapOf("vieja" to 300_000L, "nueva" to 50_000L), CalculadoraDeudas.repartirPago(deudas, Miembro.B, 999_999))
        assertEquals(mapOf("mia" to 17_500L), CalculadoraDeudas.repartirPago(deudas, Miembro.A, 17_500))
    }
}

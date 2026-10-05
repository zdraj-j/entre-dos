package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepartoTest {

    @Test fun categoriaPersonalLaAsumeSuDuenoAunqueLaPagueElOtro() {
        assertEquals(Cargos(0, 50_000), Reparto.repartir(50_000, Responsable.B, pagadoPor = Miembro.A))
    }

    @Test fun compartidaParLaMitadCadaUno() {
        assertEquals(Cargos(50_000, 50_000), Reparto.repartir(100_000, Responsable.COMPARTIDA, Miembro.A))
    }

    @Test fun compartidaImparElPesoSobranteLoAsumeQuienPago() {
        assertEquals(Cargos(1_001, 1_000), Reparto.repartir(2_001, Responsable.COMPARTIDA, Miembro.A))
        assertEquals(Cargos(1_000, 1_001), Reparto.repartir(2_001, Responsable.COMPARTIDA, Miembro.B))
    }

    @Test fun repartoAjustadoUsaElCargoDeQuienPago() {
        // Pagó B 100.000 y asume 60.000: A queda con 40.000.
        assertEquals(Cargos(40_000, 60_000), Reparto.repartir(100_000, Responsable.COMPARTIDA, Miembro.B, cargoPagador = 60_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cargoAjustadoMayorQueElMontoFalla() {
        Reparto.repartir(10_000, Responsable.COMPARTIDA, Miembro.A, cargoPagador = 12_000)
    }

    @Test fun dividirEnPartesIgualesRepartirElSobranteEnLasPrimeras() {
        assertEquals(listOf(33_334L, 33_333L, 33_333L), Reparto.dividirEnPartesIguales(100_000, 3))
        assertEquals(listOf(2L, 2L, 1L), Reparto.dividirEnPartesIguales(5, 3))
    }

    @Test fun validarDivision() {
        assertNull(Reparto.validarDivision(85_000, listOf(60_000, 25_000)))
        assertEquals("Ninguna parte puede quedar en cero", Reparto.validarDivision(85_000, listOf(85_000, 0)))
        assertEquals("Las partes suman 80000 y el gasto es de 85000", Reparto.validarDivision(85_000, listOf(60_000, 20_000)))
    }
}

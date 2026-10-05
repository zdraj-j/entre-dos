package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MovimientosTest {
    private val bogota = ZoneId.of("America/Bogota")
    private fun ms(d: Int, h: Int) = LocalDate.of(2026, 10, d).atTime(h, 0).atZone(bogota).toInstant().toEpochMilli()

    private val lista = listOf(
        MovimientoResumen("1", "Compra Éxito", "Éxito", Miembro.A, setOf("mercado", "aseo"), ms(3, 10), 85_000),
        MovimientoResumen("2", "Taxi", null, Miembro.B, setOf("transporte"), ms(3, 22), 12_000),
        MovimientoResumen("3", "Mercado D1", "D1", Miembro.B, setOf("mercado"), ms(4, 9), 35_000),
    )

    @Test fun buscarSinTildes() {
        assertEquals(listOf("1"), Movimientos.filtrar(lista, FiltroMovimientos(texto = "exito")).map { it.id })
    }

    @Test fun filtrarPorCategoriaIncluyeGastosDivididos() {
        assertEquals(listOf("1", "3"), Movimientos.filtrar(lista, FiltroMovimientos(categoriaId = "mercado")).map { it.id })
        assertEquals(listOf("1"), Movimientos.filtrar(lista, FiltroMovimientos(categoriaId = "aseo")).map { it.id })
    }

    @Test fun combinarFiltros() {
        assertEquals(listOf("3"), Movimientos.filtrar(lista, FiltroMovimientos(persona = Miembro.B, categoriaId = "mercado")).map { it.id })
    }

    @Test fun agruparPorDia() {
        val dias = Movimientos.porDia(lista, bogota)
        assertEquals(listOf(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 3)), dias.map { it.first })
        assertEquals(listOf("2", "1"), dias[1].second.map { it.id })
    }
}

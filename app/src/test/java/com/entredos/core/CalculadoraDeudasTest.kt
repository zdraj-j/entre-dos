package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraDeudasTest {

    private fun parte(id: String, pagadoPor: Miembro, cargos: Cargos, importado: Boolean = false, fecha: Long = 1) =
        ParteParaDeuda(id, "g-$id", "Gasto $id", fecha, pagadoPor, cargos.cargoA, cargos.cargoB, importado)

    @Test fun categoriaPropiaPagadaPorSuDuenoNoGeneraDeuda() {
        val deudas = CalculadoraDeudas.calcular(listOf(parte("1", Miembro.A, Cargos(30_000, 0))), emptyList(), emptyMap(), true)
        assertTrue(deudas.isEmpty())
    }

    @Test fun categoriaAjenaElDuenoDebeTodo() {
        val d = CalculadoraDeudas.calcular(listOf(parte("1", Miembro.A, Cargos(0, 30_000))), emptyList(), emptyMap(), true).single()
        assertEquals(Miembro.B, d.deudor)
        assertEquals(Miembro.A, d.acreedor)
        assertEquals(30_000L, d.monto)
    }

    @Test fun compartidaElQueNoPagoDebeSuMitad() {
        val cargos = Reparto.repartir(100_000, Responsable.COMPARTIDA, Miembro.B)
        val d = CalculadoraDeudas.calcular(listOf(parte("1", Miembro.B, cargos)), emptyList(), emptyMap(), true).single()
        assertEquals(Miembro.A, d.deudor)
        assertEquals(50_000L, d.monto)
    }

    @Test fun importadosNoGeneranDeuda() {
        val deudas = CalculadoraDeudas.calcular(listOf(parte("1", Miembro.A, Cargos(0, 30_000), importado = true)), emptyList(), emptyMap(), true)
        assertTrue(deudas.isEmpty())
    }

    @Test fun sinParejaUnidaNoHayDeudas() {
        val deudas = CalculadoraDeudas.calcular(listOf(parte("1", Miembro.A, Cargos(0, 30_000))), emptyList(), emptyMap(), false)
        assertTrue(deudas.isEmpty())
    }

    @Test fun abonosYBalance() {
        val partes = listOf(
            parte("1", Miembro.A, Cargos(0, 30_000)),        // B le debe 30.000 a A
            parte("2", Miembro.B, Cargos(50_000, 50_000)),   // A le debe 50.000 a B
        )
        val manual = DeudaManualInfo("m1", Miembro.B, Miembro.A, 10_000, "Préstamo", 2)
        val deudas = CalculadoraDeudas.calcular(partes, listOf(manual), mapOf("2" to 20_000L, "m1" to 10_000L), true)

        assertEquals(3, deudas.size)
        assertTrue(deudas.first { it.id == "m1" }.saldada)
        assertEquals(30_000L, deudas.first { it.id == "2" }.pendiente)

        val deA = CalculadoraDeudas.balance(deudas, Miembro.A)
        assertEquals(Balance(yoDebo = 30_000, meDeben = 30_000), deA)
        assertEquals(0L, deA.neto)
    }

    @Test fun saldarTodoAbonaSoloLoPendiente() {
        val deudas = CalculadoraDeudas.calcular(
            listOf(parte("1", Miembro.A, Cargos(0, 30_000)), parte("2", Miembro.A, Cargos(0, 5_000))),
            emptyList(), mapOf("1" to 10_000L, "2" to 5_000L), true,
        )
        assertEquals(mapOf("1" to 20_000L), CalculadoraDeudas.abonosParaSaldarTodo(deudas))
    }
}

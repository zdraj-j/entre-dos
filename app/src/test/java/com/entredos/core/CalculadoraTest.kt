package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraTest {

    private fun teclear(vararg teclas: Tecla) = teclas.fold(Calculadora()) { c, t -> c.tecla(t) }
    private fun d(v: String) = Tecla.Digito(v)

    @Test fun escribirConTripleCero() {
        val c = teclear(d("3"), d("5"), d("000"))
        assertEquals(35_000L, c.total)
        assertEquals("", c.textoExpresion)
    }

    @Test fun noEmpiezaConCeros() {
        assertEquals("", teclear(d("0"), d("000")).expresion)
        assertEquals("5+", teclear(d("5"), Tecla.Sumar, d("0")).expresion)
    }

    @Test fun sumarYRestar() {
        val c = teclear(d("12"), d("000"), Tecla.Sumar, d("8"), d("5"), d("0"), d("0"), Tecla.Restar, d("500"))
        assertEquals(20_000L, c.total)
        assertEquals("12.000 + 8.500 − 500", c.textoExpresion)
    }

    @Test fun cambiarDeOperacionReemplazaLaAnterior() {
        assertEquals("7-", teclear(d("7"), Tecla.Sumar, Tecla.Restar).expresion)
        assertEquals("", teclear(Tecla.Sumar).expresion)
    }

    @Test fun igualColapsaYUnTotalNegativoNoEsValido() {
        assertEquals("20500", teclear(d("12"), d("000"), Tecla.Sumar, d("8500")).tecla(Tecla.Igual).expresion)
        val negativo = teclear(d("5"), Tecla.Restar, d("9"))
        assertFalse(negativo.valido)
        assertEquals("", negativo.tecla(Tecla.Igual).expresion)
    }

    @Test fun limiteDeDigitosYBorrar() {
        val c = teclear(d("9"), d("999"), d("999"), d("999"), d("9"))
        assertEquals(10, c.expresion.length)
        assertEquals("999999999", c.tecla(Tecla.Borrar).expresion)
        assertTrue(c.valido)
    }

    @Test fun formatoDePesos() {
        assertEquals("$ 1.234.567", FormatoPesos.pesos(1_234_567))
        assertEquals("-15.000", FormatoPesos.miles(-15_000))
        assertEquals("0", FormatoPesos.miles(0))
    }
}

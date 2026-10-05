package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClasificacionTest {

    private val netflix = RecurrenteInfo("r1", "suscripciones", 44_900, "netflix")
    private val arriendo = RecurrenteInfo("r2", "arriendo", 1_200_000, "inmobiliaria")

    @Test fun recurrenteConMargen() {
        assertEquals(netflix, Clasificacion.recurrenteQueCoincide("NETFLIX.COM", 46_000, listOf(netflix, arriendo)))
        assertNull(Clasificacion.recurrenteQueCoincide("NETFLIX.COM", 50_000, listOf(netflix)))
        assertEquals(arriendo, Clasificacion.recurrenteQueCoincide("Inmobiliaria Sur", 1_320_000, listOf(arriendo)))
        assertNull(Clasificacion.recurrenteQueCoincide(null, 44_900, listOf(netflix)))
    }

    @Test fun reglaAutomaticaGanaLaPalabraMasLarga() {
        val reglas = listOf(
            ReglaInfo("1", "exito", "mercado", 3, true),
            ReglaInfo("2", "exito express", "antojos", 4, true),
            ReglaInfo("3", "d1", "mercado", 2, false),
        )
        assertEquals("antojos", Clasificacion.reglaAutomatica("ÉXITO EXPRESS 23", reglas)?.categoriaId)
        assertEquals("mercado", Clasificacion.reglaAutomatica("Éxito Laureles", reglas)?.categoriaId)
        assertNull(Clasificacion.reglaAutomatica("D1 Centro", reglas))
    }

    @Test fun aprenderTresConfirmacionesActivaLaAutomatica() {
        val primera = Clasificacion.aprender(null, "mercado")
        assertEquals(ReglaActualizada("mercado", 1, false), primera)
        val r = ReglaInfo("1", "d1", "mercado", 2, false)
        assertEquals(ReglaActualizada("mercado", 3, true), Clasificacion.aprender(r, "mercado"))
        assertEquals(ReglaActualizada("aseo", 1, false), Clasificacion.aprender(r, "aseo"))
    }

    @Test fun sugerenciasEnOrden() {
        val cats = listOf(
            CategoriaInfo("aseo", "Aseo", false), CategoriaInfo("mercado", "Mercado", true),
            CategoriaInfo("antojos", "Antojos", false), CategoriaInfo("salud", "Salud", true),
        )
        val reglas = listOf(ReglaInfo("1", "farmatodo", "aseo", 2, false))
        assertEquals(listOf("aseo", "mercado", "salud"), Clasificacion.sugerencias("FARMATODO 12", reglas, cats).map { it.id })
        assertEquals(listOf("mercado", "salud", "antojos"), Clasificacion.sugerencias(null, reglas, cats).map { it.id })
    }

    @Test fun palabraDeComercio() {
        assertEquals("d1", Clasificacion.palabraDeComercio("D1 SUCURSAL 23"))
        assertEquals("exito laureles", Clasificacion.palabraDeComercio("Éxito Laureles"))
    }
}

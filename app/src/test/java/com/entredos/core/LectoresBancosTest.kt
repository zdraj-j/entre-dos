package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LectoresBancosTest {

    @Test fun bancolombiaCompra() {
        val r = LectoresBancos.leer("com.todo1.mobile", "Bancolombia", "Compraste $85.400 en EXITO LAURELES con tu T.Deb *1234")
        assertEquals(ResultadoLector.Gasto(Banco.BANCOLOMBIA, LecturaNotificacion(85_400, "EXITO LAURELES")), r)
    }

    @Test fun appDesconocidaPasaAlGenerico() {
        assertEquals(ResultadoLector.NoEsBanco, LectoresBancos.leer("com.otro.banco", null, "Compraste $1.000 en X"))
    }

    @Test fun nequiEnvioEntrePersonasNoEsGasto() {
        assertEquals(ResultadoLector.Descartada, LectoresBancos.leer("com.nequi.MobileApp", "Nequi", "Le enviaste $50.000 a JUAN PEREZ"))
        assertTrue(LectoresBancos.leer("com.nequi.MobileApp", "Nequi", "Pagaste $23.900 en TIENDA D1") is ResultadoLector.Gasto)
    }

    @Test fun smsSoloSiMencionaAlBancoDeBogota() {
        assertEquals(ResultadoLector.NoEsBanco, LectoresBancos.leer("com.google.android.apps.messaging", "891333", "Compra por $40.000 en ARA"))
        val r = LectoresBancos.leer("com.google.android.apps.messaging", "85784", "Banco de Bogota: compra por $40.000 en ARA CENTRO. Saldo $1.200.000")
        assertEquals(ResultadoLector.Gasto(Banco.BANCO_DE_BOGOTA, LecturaNotificacion(40_000, "ARA CENTRO")), r)
    }

    @Test fun rappiSoloTarjetaYSinEstadosDelPedido() {
        assertEquals(ResultadoLector.Descartada, LectoresBancos.leer("com.grability.rappi", "Rappi", "Tu pedido de $35.000 va en camino"))
        assertEquals(ResultadoLector.Descartada, LectoresBancos.leer("com.grability.rappi", "Rappi", "Pagaste $35.000 en Crepes"))
        assertTrue(LectoresBancos.leer("com.grability.rappi", "RappiCard", "Compra con tu tarjeta por $35.000 en CREPES Y WAFFLES") is ResultadoLector.Gasto)
    }

    @Test fun otpYRechazosSeDescartan() {
        assertEquals(ResultadoLector.Descartada, LectoresBancos.leer("com.nu.production", "Nu", "Tu código para la compra de $90.000 es 123456"))
        assertEquals(ResultadoLector.Descartada, LectoresBancos.leer("com.davivienda.daviviendaapp", null, "Compra rechazada por $10.000 en X"))
    }
}

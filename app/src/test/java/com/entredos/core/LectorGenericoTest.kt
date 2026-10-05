package com.entredos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LectorGenericoTest {

    @Test fun compraConComercio() {
        assertEquals(LecturaNotificacion(35_000, "D1 SUCURSAL 23"), LectorGenerico.leer("Compra aprobada", "Compraste $35.000 en D1 SUCURSAL 23 con tu tarjeta"))
    }

    @Test fun montoConDecimalesYSaldoDespues() {
        val l = LectorGenerico.leer(null, "Pago por COP 1.234.567,89 en EXITO LAURELES. Saldo disponible $2.000.000")
        assertEquals(LecturaNotificacion(1_234_568, "EXITO LAURELES"), l)
    }

    @Test fun formatoEnInglesYEnLineaNoEsComercio() {
        assertEquals(LecturaNotificacion(12_500, "NETFLIX"), LectorGenerico.leer(null, "Transacción por $12,500.00 en línea en NETFLIX"))
    }

    @Test fun sinComercio() {
        assertEquals(LecturaNotificacion(200_000, null), LectorGenerico.leer("Retiro", "Retiraste $200.000 del cajero"))
    }

    @Test fun descarta() {
        assertNull(LectorGenerico.leer(null, "Tu código de verificación es 123456"))
        assertNull(LectorGenerico.leer(null, "Recibiste $50.000 de Juan"))
        assertNull(LectorGenerico.leer(null, "Compra rechazada por $20.000 en Rappi"))
        assertNull(LectorGenerico.leer(null, "Tu cupo disponible es $1.500.000"))
        assertNull(LectorGenerico.leer(null, "Hola, ¿cómo vas?"))
        assertNull(LectorGenerico.leer("Promo", "Gana $100.000 en bonos"))
    }
}

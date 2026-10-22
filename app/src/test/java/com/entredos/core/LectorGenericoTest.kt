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

    @Test fun publicidadNoEsGasto() {
        assertNull(LectorGenerico.leer("Exito", "Compra hoy tu mercado y recibe hasta $50.000 de regreso. Aplican TyC"))
        assertNull(LectorGenerico.leer("891222", "Tienes un credito preaprobado por $15.000.000. Solicitalo en la app"))
        assertNull(LectorGenerico.leer("Falabella", "Paga con tu tarjeta y llevate 20% de dcto en tecnologia"))
        assertNull(LectorGenerico.leer("Claro", "Paga tu factura de $65.900 antes del 15 y evita la suspension"))
        assertNull(LectorGenerico.leer("85432", "SOAT desde $350.000 con pago en cuotas. Cotiza en https://soat.ejemplo.co/ya"))
        assertNull(LectorGenerico.leer("Banco", "Por compras de $100.000 o mas participas por una moto"))
        assertNull(LectorGenerico.leer("Banco", "Recuerda: el pago minimo de tu tarjeta es $120.000 y vence el 05/11"))
        assertNull(LectorGenerico.leer("Tienda", "Envio gratis en pedidos de $80.000. Para no recibir mas mensajes envia SALIR al 85432"))
        assertNull(LectorGenerico.leer("Banco", "Usa tu tarjeta en tus compras y acumula puntos: cada $1.000 suma"))
    }

    @Test fun transferenciasNoSonGasto() {
        assertNull(LectorGenerico.leer("Bancolombia", "Transferiste $500.000 desde tu cuenta *1234 a la cuenta *9876"))
        assertNull(LectorGenerico.leer("Banco", "Transferencia exitosa por $200.000 a JUAN PEREZ"))
        assertEquals(LecturaNotificacion(89_000, "EPM"), LectorGenerico.leer("Banco", "Transferencia exitosa por $89.000 por PSE en EPM"))
    }

    @Test fun sinMovimientoNoEsGasto() {
        assertNull(LectorGenerico.leer("Tienda", "Nuevo iPhone a $4.999.900 en tiendas"))
        assertNull(LectorGenerico.leer("Banco", "Tu saldo es $350.000"))
    }

    @Test fun avisosRealesSiguenPasando() {
        assertEquals(LecturaNotificacion(48_000, "RESTAURANTE LA 70"), LectorGenerico.leer("Banco", "Realizaste un pago por $48.000 en RESTAURANTE LA 70. Si no lo reconoces llama al 018000"))
        assertEquals(LecturaNotificacion(15_900, "SPOTIFY"), LectorGenerico.leer(null, "Se realizo un cobro de $15.900 en SPOTIFY con tu tarjeta *1234"))
    }
}

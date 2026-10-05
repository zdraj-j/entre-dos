package com.entredos.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    @Test fun codificarYDecodificarMantieneElOrden() {
        val a = Hlc(1_700_000_000_000, 3)
        val b = Hlc(1_700_000_000_000, 12)
        assertEquals(a, Hlc.decodificar(a.codificar()))
        assertTrue(a.codificar() < b.codificar())
    }

    @Test fun relojAtrasadoNoRetrocede() {
        var ahora = 10_000L
        val reloj = RelojHlc { ahora }
        val primero = reloj.ahora()
        ahora = 5_000 // alguien atrasó la hora del celular
        val segundo = reloj.ahora()
        assertTrue(segundo > primero)
        assertEquals(10_000L, segundo.millis)
        assertEquals(1, segundo.contador)
    }

    @Test fun recibirUnaMarcaRemotaAdelantaElReloj() {
        val reloj = RelojHlc { 1_000L }
        reloj.recibir(Hlc(2_000, 4))
        assertEquals(Hlc(2_000, 6), reloj.ahora())
    }

    @Test fun marcaRemotaMuyEnElFuturoNoAdelantaElReloj() {
        val reloj = RelojHlc { 1_000L }
        reloj.recibir(Hlc(1_000 + RelojHlc.MAX_ADELANTO_MS + 1, 0))
        assertEquals(Hlc(1_000, 0), reloj.ahora())
    }

    @Test fun inicializarConLaUltimaMarcaGuardada() {
        val reloj = RelojHlc { 500L }
        reloj.inicializar(Hlc(900, 2))
        assertEquals(Hlc(900, 3), reloj.ahora())
    }

    @Test fun lwwGanaLaMarcaMayorYEnEmpateElDispositivoMayor() {
        val viejo = Hlc(100, 0).codificar()
        val nuevo = Hlc(100, 1).codificar()
        assertTrue(Lww.ganaRemoto(viejo, "zzz", nuevo, "aaa"))
        assertFalse(Lww.ganaRemoto(nuevo, "aaa", viejo, "zzz"))
        assertTrue(Lww.ganaRemoto(nuevo, "aaa", nuevo, "bbb"))
        assertFalse(Lww.ganaRemoto(nuevo, "bbb", nuevo, "aaa"))
        assertTrue(Lww.ganaRemoto(null, null, viejo, "aaa"))
    }
}

package com.entredos.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorSyncTest {

    @Test fun loQueRegistraUnoLoVeElOtro() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        a.escribir("gasto", "g1", "Mercado 35000")
        a.escribir("gasto", "g2", "Taxi 12000")
        correr { a.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }
        assertEquals("Mercado 35000", b.valor("gasto", "g1"))
        assertEquals("Taxi 12000", b.valor("gasto", "g2"))
        assertEquals(0, a.cuantosPendientes())
    }

    @Test fun editandoLoMismoSinRedGanaElCambioMasReciente() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa", hora = 1_000); val b = TelefonoFalso("bbb", hora = 1_000)
        a.escribir("gasto", "g1", "original")
        correr { a.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }

        a.hora = 2_000; a.escribir("gasto", "g1", "editado por A")
        b.hora = 3_000; b.escribir("gasto", "g1", "editado por B")

        correr { a.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }
        correr { a.motor(drive).sincronizar() }
        assertEquals("editado por B", a.valor("gasto", "g1"))
        assertEquals("editado por B", b.valor("gasto", "g1"))
    }

    @Test fun elRelojAtrasadoDeUnTelefonoNoPisaUnCambioPosterior() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa", hora = 50_000); val b = TelefonoFalso("bbb", hora = 1_000) // B con la hora muy atrás
        a.escribir("gasto", "g1", "de A")
        correr { a.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }
        b.escribir("gasto", "g1", "corregido por B después")
        correr { b.motor(drive).sincronizar() }
        correr { a.motor(drive).sincronizar() }
        assertEquals("corregido por B después", a.valor("gasto", "g1"))
    }

    @Test fun losBorradosLleganAlOtroTelefono() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        a.escribir("gasto", "g1", "Mercado")
        correr { a.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }
        b.hora = 2_000; b.escribir("gasto", "g1", "Mercado", eliminado = true)
        correr { b.motor(drive).sincronizar() }
        correr { a.motor(drive).sincronizar() }
        assertNull(a.valor("gasto", "g1"))
    }

    @Test fun alReinstalarSeRecuperaTodoYSeSigueSincronizando() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        a.escribir("gasto", "g1", "de A")
        b.escribir("gasto", "g2", "de B")
        correr { a.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }

        val aNuevo = TelefonoFalso("aaa-reinstalado", hora = 5_000) // id de dispositivo nuevo
        correr { aNuevo.motor(drive).sincronizar() }
        assertEquals("de A", aNuevo.valor("gasto", "g1"))
        assertEquals("de B", aNuevo.valor("gasto", "g2"))

        aNuevo.escribir("gasto", "g3", "después de reinstalar")
        correr { aNuevo.motor(drive).sincronizar() }
        correr { b.motor(drive).sincronizar() }
        assertEquals("después de reinstalar", b.valor("gasto", "g3"))
    }

    @Test fun laFotoResumeYUnTelefonoAtrasadoNoPierdeNada() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        b.escribir("gasto", "inicial", "visto por los dos")
        correr { b.motor(drive, umbralFoto = 3).sincronizar() }
        correr { a.motor(drive, umbralFoto = 3).sincronizar() }

        // B se queda sin red mientras A registra varias cosas y compacta.
        for (i in 1..5) {
            a.hora += 1_000
            a.escribir("gasto", "a$i", "gasto $i")
            correr { a.motor(drive, umbralFoto = 3).sincronizar() }
        }
        assertEquals(1, drive.cuantasFotos())
        assertTrue(drive.cuantosCambios() <= 3)

        correr { b.motor(drive, umbralFoto = 3).sincronizar() }
        for (i in 1..5) assertEquals("gasto $i", b.valor("gasto", "a$i"))

        val c = TelefonoFalso("ccc")
        correr { c.motor(drive, umbralFoto = 3).sincronizar() }
        assertEquals("visto por los dos", c.valor("gasto", "inicial"))
        assertEquals("gasto 5", c.valor("gasto", "a5"))
    }

    @Test fun siLaAppSeCierraDespuesDeSubirNoQuedanArchivosDuplicados() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        a.escribir("gasto", "g1", "Mercado")
        a.fallarAlConfirmar = true
        try { correr { a.motor(drive).sincronizar() } } catch (_: IllegalStateException) { }
        assertEquals(1, a.cuantosPendientes())

        correr { a.motor(drive).sincronizar() }
        assertEquals(1, drive.cuantosCambios())
        correr { b.motor(drive).sincronizar() }
        assertEquals("Mercado", b.valor("gasto", "g1"))
    }

    @Test fun loteGrandeSeParteEnVariosArchivos() {
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        for (i in 1..1_200) a.escribir("gasto", "g$i", "gasto $i")
        val r = correr { a.motor(drive).sincronizar() }
        assertEquals(3, r.archivosSubidos)
        correr { b.motor(drive).sincronizar() }
        assertEquals("gasto 1200", b.valor("gasto", "g1200"))
    }

    @Test fun nombresDeArchivos() {
        val nombre = NombresSync.cambios("3f2a-9c1e", 42)
        assertEquals("cambios-3f2a-9c1e-00000042.json", nombre)
        assertEquals("3f2a-9c1e" to 42L, NombresSync.leerCambios(nombre))
        assertTrue(NombresSync.esFoto(NombresSync.foto(1_700_000_000_000, "3f2a-9c1e")))
        assertNull(NombresSync.leerCambios(NombresSync.HOGAR))
    }
}

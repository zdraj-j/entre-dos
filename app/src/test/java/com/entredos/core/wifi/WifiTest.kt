package com.entredos.core.wifi

import com.entredos.core.sync.AlmacenEnMemoria
import com.entredos.core.sync.Cambio
import com.entredos.core.sync.TelefonoFalso
import com.entredos.core.sync.correr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future

class WifiTest {
    private val secreto = "hogar-123".toByteArray()

    /** Conecta dos teléfonos por un socket real en este equipo y corre el intercambio. */
    private fun intercambiar(servidor: TelefonoFalso, cliente: TelefonoFalso, claveServidor: ByteArray = secreto, claveCliente: ByteArray = secreto): Pair<Result<ResultadoWifi>, Result<ResultadoWifi>> {
        val hilos = Executors.newFixedThreadPool(2)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { ss ->
            val s: Future<Result<ResultadoWifi>> = hilos.submit<Result<ResultadoWifi>> {
                ss.accept().use { c -> runCatching { correr { SesionWifi(servidor, servidor.reloj, claveServidor).comoServidor(c.getInputStream(), c.getOutputStream()) } } }
            }
            val r = Socket(InetAddress.getLoopbackAddress(), ss.localPort).use { c ->
                c.soTimeout = 5_000
                runCatching { correr { SesionWifi(cliente, cliente.reloj, claveCliente).comoCliente(c.getInputStream(), c.getOutputStream()) } }
            }
            return (s.get() to r).also { hilos.shutdown() }
        }
    }

    @Test fun vectorYFaltantes() {
        val r = listOf(
            Cambio("gasto", "1", "000000000001000-00000", "a", false, "x"),
            Cambio("gasto", "2", "000000000002000-00000", "a", false, "y"),
            Cambio("gasto", "3", "000000000001500-00000", "b", false, "z"),
        )
        assertEquals(mapOf("a" to "000000000002000-00000", "b" to "000000000001500-00000"), VectorVersiones.de(r))
        assertEquals(listOf("2", "3"), VectorVersiones.faltantes(r, mapOf("a" to "000000000001000-00000")).map { it.id })
    }

    @Test fun intercambioCompletoEnLosDosSentidos() {
        val a = TelefonoFalso("aaa"); val b = TelefonoFalso("bbb")
        a.escribir("gasto", "g1", "de A"); a.escribir("gasto", "g2", "otro de A")
        b.escribir("gasto", "g3", "de B")
        val (rs, rc) = intercambiar(servidor = a, cliente = b)
        assertEquals(ResultadoWifi("bbb", 2, 1), rs.getOrThrow())
        assertEquals(ResultadoWifi("aaa", 1, 2), rc.getOrThrow())
        assertEquals("de B", a.valor("gasto", "g3"))
        assertEquals("otro de A", b.valor("gasto", "g2"))

        val (otraVezS, otraVezC) = intercambiar(servidor = a, cliente = b)
        assertEquals(0, otraVezS.getOrThrow().enviados)
        assertEquals(0, otraVezC.getOrThrow().enviados)
    }

    @Test fun conflictoQuedaIgualQueConDrive() {
        val a = TelefonoFalso("aaa", hora = 1_000); val b = TelefonoFalso("bbb", hora = 1_000)
        a.escribir("gasto", "g1", "original")
        intercambiar(a, b)
        a.hora = 2_000; a.escribir("gasto", "g1", "editado por A")
        b.hora = 3_000; b.escribir("gasto", "g1", "editado por B")
        intercambiar(a, b)
        assertEquals("editado por B", a.valor("gasto", "g1"))
        assertEquals("editado por B", b.valor("gasto", "g1"))
    }

    @Test fun loQueLlegoPorDriveTambienSeReenvia() {
        // A sube a Drive por datos móviles; C (otro teléfono de B) lo baja; luego B solo tiene wifi con C.
        val drive = AlmacenEnMemoria()
        val a = TelefonoFalso("aaa"); val c = TelefonoFalso("ccc"); val b = TelefonoFalso("bbb")
        a.escribir("gasto", "g1", "de A por Drive")
        correr { a.motor(drive).sincronizar() }
        correr { c.motor(drive).sincronizar() }
        intercambiar(servidor = c, cliente = b)
        assertEquals("de A por Drive", b.valor("gasto", "g1"))
    }

    @Test fun otroHogarNoRecibeNada() {
        val a = TelefonoFalso("aaa"); val intruso = TelefonoFalso("zzz")
        a.escribir("gasto", "g1", "privado")
        val (rs, rc) = intercambiar(servidor = a, cliente = intruso, claveCliente = "otro-hogar".toByteArray())
        assertTrue(rs.exceptionOrNull() is AutenticacionFallida || rc.exceptionOrNull() is AutenticacionFallida)
        assertTrue(rc.isFailure)
        assertNull(intruso.valor("gasto", "g1"))
    }

    @Test fun losDatosAlteradosSeRechazan() {
        val canal = Cifrado.Canal(ByteArray(32) { it.toByte() })
        val trama = canal.cifrar("hola".toByteArray())
        assertEquals("hola", String(canal.descifrar(trama)))
        trama[trama.size - 1] = (trama.last() + 1).toByte()
        assertTrue(runCatching { canal.descifrar(trama) }.exceptionOrNull() is AutenticacionFallida)
        assertEquals(16, Cifrado.huellaHogar("x").length)
    }
}

package com.entredos.core.wifi

import com.entredos.core.sync.BaseLocal
import com.entredos.core.sync.Cambio
import com.entredos.core.sync.Hlc
import com.entredos.core.sync.RelojHlc
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

data class ResultadoWifi(val otroDispositivo: String, val enviados: Int, val recibidos: Int)

/**
 * Un intercambio entre los dos teléfonos de un hogar por una conexión TCP ya abierta.
 *
 * 1. Saludo con números aleatorios y prueba mutua de que ambos conocen el secreto del hogar
 *    (HMAC); nada se envía hasta que los dos se autentican.
 * 2. Con una clave de sesión derivada de ese saludo, todo va cifrado con AES-GCM.
 * 3. Cada uno manda su vector de versiones y luego lo que al otro le falta. Se aplica con LWW,
 *    igual que lo que llega por Drive, así que llegar por los dos caminos no duplica nada.
 *
 * El orden de lectura y escritura es fijo (cliente primero) para que nunca se bloqueen
 * los dos escribiendo a la vez.
 */
class SesionWifi(
    private val base: BaseLocal,
    private val reloj: RelojHlc,
    /** Secreto compartido del hogar (en la app, el id del hogar). */
    private val secreto: ByteArray,
) {

    suspend fun comoCliente(entrada: InputStream, salida: OutputStream): ResultadoWifi {
        val e = DataInputStream(entrada); val s = DataOutputStream(salida)
        val yo = base.dispositivoId()
        val nC = Cifrado.aleatorio(32)
        escribir(s, TIPO_HOLA, empacar { escribirTexto(it, yo); it.writeInt(VERSION); it.write(nC) })

        val (otro, nS, pruebaS) = desempacar(leer(e, TIPO_HOLA)) { d ->
            val otro = leerTexto(d); revisarVersion(d.readInt())
            Triple(otro, ByteArray(32).also(d::readFully), ByteArray(32).also(d::readFully))
        }
        if (!Cifrado.iguales(pruebaS, Cifrado.hmac(secreto, "S".toByteArray(), nC, nS))) throw AutenticacionFallida("El otro teléfono no es de este hogar")
        escribir(s, TIPO_PRUEBA, Cifrado.hmac(secreto, "C".toByteArray(), nS, nC))

        val canal = Cifrado.Canal(Cifrado.hmac(secreto, "sesion".toByteArray(), nC, nS))
        val registros = base.registrosParaFoto()
        escribir(s, TIPO_VECTOR, canal.cifrar(codificarVector(VectorVersiones.de(registros))))
        val vectorOtro = decodificarVector(canal.descifrar(leer(e, TIPO_VECTOR)))
        val mios = VectorVersiones.faltantes(registros, vectorOtro)
        escribir(s, TIPO_CAMBIOS, canal.cifrar(codificarCambios(mios)))
        val suyos = decodificarCambios(canal.descifrar(leer(e, TIPO_CAMBIOS)))
        aplicar(suyos)
        return ResultadoWifi(otro, mios.size, suyos.size)
    }

    suspend fun comoServidor(entrada: InputStream, salida: OutputStream): ResultadoWifi {
        val e = DataInputStream(entrada); val s = DataOutputStream(salida)
        val yo = base.dispositivoId()
        val (otro, nC) = desempacar(leer(e, TIPO_HOLA)) { d ->
            val otro = leerTexto(d); revisarVersion(d.readInt())
            otro to ByteArray(32).also(d::readFully)
        }
        val nS = Cifrado.aleatorio(32)
        escribir(s, TIPO_HOLA, empacar { escribirTexto(it, yo); it.writeInt(VERSION); it.write(nS); it.write(Cifrado.hmac(secreto, "S".toByteArray(), nC, nS)) })
        if (!Cifrado.iguales(leer(e, TIPO_PRUEBA), Cifrado.hmac(secreto, "C".toByteArray(), nS, nC))) throw AutenticacionFallida("El otro teléfono no es de este hogar")

        val canal = Cifrado.Canal(Cifrado.hmac(secreto, "sesion".toByteArray(), nC, nS))
        val vectorOtro = decodificarVector(canal.descifrar(leer(e, TIPO_VECTOR)))
        val registros = base.registrosParaFoto()
        escribir(s, TIPO_VECTOR, canal.cifrar(codificarVector(VectorVersiones.de(registros))))
        val suyos = decodificarCambios(canal.descifrar(leer(e, TIPO_CAMBIOS)))
        val mios = VectorVersiones.faltantes(registros, vectorOtro)
        escribir(s, TIPO_CAMBIOS, canal.cifrar(codificarCambios(mios)))
        aplicar(suyos)
        return ResultadoWifi(otro, mios.size, suyos.size)
    }

    private suspend fun aplicar(cambios: List<Cambio>) {
        if (cambios.isEmpty()) return
        cambios.maxOf { it.hlc }.let { reloj.recibir(Hlc.decodificar(it)) }
        base.aplicarCambios(cambios)
    }

    companion object {
        const val VERSION = 1
        const val TIPO_SERVICIO = "_entredos._tcp."
        private const val TIPO_HOLA: Byte = 1
        private const val TIPO_PRUEBA: Byte = 2
        private const val TIPO_VECTOR: Byte = 3
        private const val TIPO_CAMBIOS: Byte = 4
        private const val MAX_TRAMA = 50 * 1024 * 1024

        private fun revisarVersion(v: Int) {
            if (v != VERSION) throw AutenticacionFallida("El otro teléfono tiene otra versión de Entre dos ($v); actualicen los dos")
        }

        private fun escribir(s: DataOutputStream, tipo: Byte, datos: ByteArray) {
            s.writeByte(tipo.toInt()); s.writeInt(datos.size); s.write(datos); s.flush()
        }

        private fun leer(e: DataInputStream, esperado: Byte): ByteArray {
            val tipo = try { e.readByte() } catch (_: EOFException) { throw AutenticacionFallida("El otro teléfono cerró la conexión") }
            if (tipo != esperado) throw AutenticacionFallida("Mensaje inesperado ($tipo en lugar de $esperado)")
            val n = e.readInt()
            if (n !in 0..MAX_TRAMA) throw AutenticacionFallida("Mensaje de tamaño inválido")
            return ByteArray(n).also(e::readFully)
        }

        private fun empacar(bloque: (DataOutputStream) -> Unit): ByteArray =
            ByteArrayOutputStream().also { b -> DataOutputStream(b).use(bloque) }.toByteArray()

        private fun <T> desempacar(datos: ByteArray, bloque: (DataInputStream) -> T): T =
            DataInputStream(ByteArrayInputStream(datos)).use(bloque)

        private fun escribirTexto(s: DataOutputStream, t: String) { val b = t.toByteArray(); s.writeInt(b.size); s.write(b) }
        private fun leerTexto(e: DataInputStream): String {
            val n = e.readInt()
            if (n !in 0..MAX_TRAMA) throw AutenticacionFallida("Texto de tamaño inválido")
            return String(ByteArray(n).also(e::readFully))
        }

        fun codificarVector(v: Map<String, String>) = empacar { s -> s.writeInt(v.size); v.forEach { (k, h) -> escribirTexto(s, k); escribirTexto(s, h) } }
        fun decodificarVector(b: ByteArray): Map<String, String> = desempacar(b) { e -> (0 until e.readInt()).associate { leerTexto(e) to leerTexto(e) } }

        fun codificarCambios(lista: List<Cambio>) = empacar { s ->
            s.writeInt(lista.size)
            lista.forEach { c -> escribirTexto(s, c.tabla); escribirTexto(s, c.id); escribirTexto(s, c.hlc); escribirTexto(s, c.dispositivo); s.writeBoolean(c.eliminado); escribirTexto(s, c.datos) }
        }
        fun decodificarCambios(b: ByteArray): List<Cambio> = desempacar(b) { e ->
            List(e.readInt()) { Cambio(leerTexto(e), leerTexto(e), leerTexto(e), leerTexto(e), e.readBoolean(), leerTexto(e)) }
        }
    }
}

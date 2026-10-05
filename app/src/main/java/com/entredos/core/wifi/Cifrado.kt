package com.entredos.core.wifi

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AutenticacionFallida(mensaje: String) : Exception(mensaje)

/** HMAC-SHA256, huellas y AES-256-GCM para el intercambio por wifi. */
object Cifrado {
    private val azar = SecureRandom()

    fun aleatorio(n: Int) = ByteArray(n).also { azar.nextBytes(it) }

    fun hmac(clave: ByteArray, vararg partes: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(clave, "HmacSHA256"))
        partes.forEach { mac.update(it) }
        return mac.doFinal()
    }

    /** Comparación en tiempo constante. */
    fun iguales(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)

    /**
     * Lo que se anuncia en la red para encontrar al teléfono del mismo hogar sin revelar el id del hogar.
     * 16 caracteres hexadecimales.
     */
    fun huellaHogar(hogarId: String): String =
        MessageDigest.getInstance("SHA-256").digest("entredos-nsd:$hogarId".toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    class Canal(clave: ByteArray) {
        private val k = SecretKeySpec(clave, "AES")

        /** nonce (12 bytes) + texto cifrado con etiqueta. */
        fun cifrar(datos: ByteArray): ByteArray {
            val nonce = aleatorio(12)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, nonce))
            return nonce + c.doFinal(datos)
        }

        fun descifrar(trama: ByteArray): ByteArray {
            if (trama.size < 28) throw AutenticacionFallida("Trama demasiado corta")
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, trama, 0, 12))
            return try {
                c.doFinal(trama, 12, trama.size - 12)
            } catch (e: Exception) {
                throw AutenticacionFallida("Datos alterados o de otro hogar")
            }
        }
    }
}

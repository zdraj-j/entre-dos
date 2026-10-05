package com.entredos.core

import kotlin.math.roundToLong

data class LecturaNotificacion(val monto: Long, val comercio: String?)

/**
 * Lector para bancos sin lector propio. Es conservador: ante la duda no lee nada,
 * y lo que lee siempre queda pendiente de clasificar.
 */
object LectorGenerico {

    private val DESCARTAR = Regex(
        """\b(c[oó]digo|clave|otp|token|contrase[nñ]a|rechazad[oa]|anulad[oa]|fallid[oa]|declinad[oa]|""" +
            """no fue (posible|exitosa)|recibiste|te (envi|consign)|abono|recarga|devoluci[oó]n|reembolso|""" +
            """reintegro|consignaci[oó]n|extracto|promo|oferta|descuento|gana|bienvenid)""",
        RegexOption.IGNORE_CASE,
    )

    /** $ 35.000 · COP 1.234.567,89 · $12,500.00 · $35000 */
    private val MONTO = Regex("""(?:\$|\bCOP)\s?(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?!\d)""", RegexOption.IGNORE_CASE)

    private val ANTES_NO_ES_GASTO = Regex("""(cupo|saldo|disponible|puntos|cashback)\W*(\w+\W*){0,3}$""", RegexOption.IGNORE_CASE)

    private val EN = Regex("""\ben\s+""", RegexOption.IGNORE_CASE)
    private val NO_ES_COMERCIO = Regex("""^(tu|su|l[ií]nea|la app|el app|cuenta|tarjeta|el exterior|internet)\b""", RegexOption.IGNORE_CASE)
    private val FIN_COMERCIO = Regex("""[.,;|\n]|\s(con|el|por|desde|a las|hora|fecha|tarjeta)\s|\s\d{1,2}[:/]""", RegexOption.IGNORE_CASE)

    fun leer(titulo: String?, texto: String?): LecturaNotificacion? {
        val completo = listOfNotNull(titulo, texto).joinToString(". ").replace('\u00A0', ' ')
        if (completo.isBlank() || DESCARTAR.containsMatchIn(completo)) return null
        val monto = buscarMonto(completo) ?: return null
        return LecturaNotificacion(monto, buscarComercio(completo))
    }

    private fun buscarMonto(texto: String): Long? {
        for (m in MONTO.findAll(texto)) {
            val antes = texto.substring(maxOf(0, m.range.first - 30), m.range.first)
            if (ANTES_NO_ES_GASTO.containsMatchIn(antes)) continue
            val enteros = m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull() ?: continue
            val decimales = m.groupValues[2]
            val valor = if (decimales.isEmpty()) enteros
            else ("$enteros.${decimales.padEnd(2, '0')}").toDouble().roundToLong()
            if (valor > 0) return valor
        }
        return null
    }

    private fun buscarComercio(texto: String): String? {
        for (m in EN.findAll(texto)) {
            val resto = texto.substring(m.range.last + 1)
            if (NO_ES_COMERCIO.containsMatchIn(resto)) continue
            val fin = FIN_COMERCIO.find(resto)?.range?.first ?: resto.length
            val comercio = resto.substring(0, fin).trim().take(40)
            if (comercio.length >= 2 && comercio.any { it.isLetter() }) return comercio
        }
        return null
    }
}

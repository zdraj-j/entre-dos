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

    /**
     * Publicidad y recordatorios: invitaciones a comprar o pedir algo, porcentajes, enlaces,
     * «desde $», «en tus compras», préstamos, sorteos, fechas de pago y cómo dejar de recibir mensajes.
     */
    private val PUBLICIDAD = Regex(
        """\b(aprovecha|desc[uú]bre(lo|la)?|conoce|solic[ií]ta(lo|la)?|adqui[eé]re(lo|la)?|obt[eé]n|ll[eé]va(te|lo|la)|disfruta|""" +
            """participa|inscr[ií]bete|reclama|redime|cotiza|simula|ahorra|p[ií]de(lo|la)?|haz clic|""" +
            """compra\s+(tu|tus|ya|hoy|aqu[ií]|ahora)|paga\s+(tu|tus|con|en|ya|hoy)|(en|por|con)\s+(tus\s+)?compras|""" +
            """(desde|hasta|por solo|solo|antes|ahora)\s*(\$|cop\b)|o m[aá]s|superiores|""" +
            """sorteo|premios?|regalo|bonos?|gratis|beneficios?|exclusiv[oa]s?|te invita|invitamos|te ofrece|ofrecemos|""" +
            """pre-?aprobad[oa]|libre inversi[oó]n|te prestamos|compra de cartera|tasa|cuotas? sin inter[eé]s|""" +
            """recuerda|record(amos|atorio)|vence|vencimiento|fecha l[ií]mite|pago m[ií]nimo|est[aá] disponible|ya puedes|""" +
            """no recibir|desuscr|stop|salir al)\b|%|https?://|www\.|bit\.ly|\b[\w-]+\.(com|co|ly|me|link)/""",
        RegexOption.IGNORE_CASE,
    )

    /** Algo tiene que decir que salió plata: compra, pago, retiro, transacción, débito, cobro… */
    private val MOVIMIENTO = Regex(
        """\b(compr|pag[oóa]|retir|avance|transacci|transfer|debit|d[eé]bito autom|cargo|cobr|consumo|realiz|usaste|usad[ao]|uso de)""",
        RegexOption.IGNORE_CASE,
    )

    /** $ 35.000 · COP 1.234.567,89 · $12,500.00 · $35000 */
    private val MONTO = Regex("""(?:\$|\bCOP)\s?(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?!\d)""", RegexOption.IGNORE_CASE)

    /** Sin signo, solo justo después de «compra por» o «pago por» (SMS del Banco de Bogotá: «Tu compra por 20,900 fue aprobada»). */
    private val MONTO_SIN_SIGNO = Regex(
        """\b(?:compra|pago)\s+(?:por|de)\s+(?:valor\s+de\s+)?(?:\$|COP)?\s?(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val ANTES_NO_ES_GASTO = Regex("""(cupo|saldo|disponible|puntos|cashback)\W*(\w+\W*){0,3}$""", RegexOption.IGNORE_CASE)

    private val EN = Regex("""\ben\s+""", RegexOption.IGNORE_CASE)
    private val NO_ES_COMERCIO = Regex("""^(tu|su|l[ií]nea|la app|el app|cuenta|tarjeta|el exterior|internet)\b""", RegexOption.IGNORE_CASE)
    private val FIN_COMERCIO = Regex("""[.,;|\n¿?!]|\s(con|el|por|desde|a las|hora|fecha|tarjeta)\s|\s\d{1,2}[:/]""", RegexOption.IGNORE_CASE)
    /** Códigos pegados al final del comercio: «MERCADOPAGO COLOMBIA L3147735807». */
    private val CODIGO_AL_FINAL = Regex("""(\s+\S*\d{5,}\S*)+$""")

    /** [montoSinSigno]: el banco escribe el monto sin «$» (solo para lectores de bancos que lo hacen). */
    fun leer(titulo: String?, texto: String?, montoSinSigno: Boolean = false): LecturaNotificacion? {
        val completo = listOfNotNull(titulo, texto).joinToString(". ").replace('\u00A0', ' ')
        if (completo.isBlank() || DESCARTAR.containsMatchIn(completo) || PUBLICIDAD.containsMatchIn(completo)) return null
        if (!MOVIMIENTO.containsMatchIn(completo)) return null
        val monto = buscarMonto(completo, MONTO)
            ?: (if (montoSinSigno) buscarMonto(completo, MONTO_SIN_SIGNO) else null)
            ?: return null
        return LecturaNotificacion(monto, buscarComercio(completo))
    }

    private fun buscarMonto(texto: String, patron: Regex): Long? {
        for (m in patron.findAll(texto)) {
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
            val comercio = resto.substring(0, fin).trim().replace(CODIGO_AL_FINAL, "").take(40)
            if (comercio.length >= 2 && comercio.any { it.isLetter() }) return comercio
        }
        return null
    }
}

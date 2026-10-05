package com.entredos.core.importacion

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

/** Lectura tolerante de valores: el formato propio exige poco y las otras apps traen de todo. */
object Valores {

    /**
     * "35000", "35.000", "$ 1.234.567,89", "12,500.00", "-12.000", "(12.000)", "COP 35000".
     * Separador final seguido de 1 o 2 dígitos = decimales; de 3 = miles. Redondea al peso.
     */
    fun monto(texto: String?): Long? {
        var s = texto?.trim()?.uppercase() ?: return null
        if (s.isEmpty()) return null
        var negativo = false
        if (s.startsWith("(") && s.endsWith(")")) { negativo = true; s = s.substring(1, s.length - 1) }
        s = s.replace("COP", "").replace("$", "").replace(" ", "").replace("\u00A0", "")
        if (s.startsWith("-")) { negativo = !negativo; s = s.substring(1) }
        if (s.endsWith("-")) { negativo = !negativo; s = s.dropLast(1) }
        if (s.isEmpty() || !s.all { it.isDigit() || it == '.' || it == ',' }) return null

        val ultimo = s.indexOfLast { it == '.' || it == ',' }
        val valor: Double = if (ultimo < 0) {
            s.toDoubleOrNull() ?: return null
        } else {
            val despues = s.length - ultimo - 1
            if (despues in 1..2) {
                val enteros = s.substring(0, ultimo).replace(".", "").replace(",", "")
                "${enteros.ifEmpty { "0" }}.${s.substring(ultimo + 1)}".toDoubleOrNull() ?: return null
            } else if (despues == 3) {
                s.replace(".", "").replace(",", "").toDoubleOrNull() ?: return null
            } else return null
        }
        val redondeado = valor.roundToLong()
        return if (negativo) -redondeado else redondeado
    }

    /** Formato propio: AAAA-MM-DD, AAAA-MM-DDTHH:MM[:SS], con zona opcional (Z o -05:00). Sin hora = mediodía. */
    fun fechaIso(texto: String?, zona: ZoneId): Long? {
        var s = texto?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (s.length > 10 && s[10] == ' ') s = s.replaceRange(10, 11, "T")
        return try {
            if (s.length == 10) LocalDate.parse(s).atTime(LocalTime.NOON).atZone(zona).toInstant().toEpochMilli()
            else (runCatching { OffsetDateTime.parse(s).atZoneSameInstant(zona) }.getOrNull() ?: LocalDateTime.parse(s).atZone(zona))
                .toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun booleano(texto: String?): Boolean? = when (texto?.trim()?.lowercase()) {
        null, "" -> null
        "si", "sí", "s", "true", "1", "yes", "y" -> true
        "no", "n", "false", "0" -> false
        else -> null
    }

    fun color(texto: String?): String? =
        texto?.trim()?.takeIf { Regex("""^#[0-9a-fA-F]{6}$""").matches(it) }?.uppercase()

    // ---------- Fechas de otras apps ----------

    /** Formatos que se prueban con los CSV de otras apps. Con hora o sin ella. */
    val FORMATOS_FECHA = listOf(
        "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd", "dd.MM.yyyy", "d/M/yyyy", "M/d/yyyy",
    )

    private val HORAS = listOf("", " HH:mm", " HH:mm:ss", "'T'HH:mm", "'T'HH:mm:ss", " h:mm a")

    fun fechaConPatron(texto: String?, patron: String, zona: ZoneId): Long? {
        val s = texto?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (hora in HORAS) {
            try {
                val f = DateTimeFormatter.ofPattern(patron + hora, java.util.Locale.US)
                return if (hora.isEmpty()) LocalDate.parse(s, f).atTime(LocalTime.NOON).atZone(zona).toInstant().toEpochMilli()
                else LocalDateTime.parse(s, f).atZone(zona).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) { }
        }
        // Algunas apps agregan segundos con fracción o zona: se prueba solo la parte de la fecha.
        val soloFecha = s.takeWhile { it != ' ' && it != 'T' }
        if (soloFecha != s) return fechaConPatron(soloFecha, patron, zona)
        return null
    }

    /**
     * El formato que entiende más filas. Si dd/MM y MM/dd empatan (ningún día mayor que 12),
     * gana dd/MM, el usual en Colombia.
     */
    fun detectarFormatoFecha(muestras: List<String>, zona: ZoneId): String? {
        val validas = muestras.filter { it.isNotBlank() }.take(200)
        if (validas.isEmpty()) return null
        return FORMATOS_FECHA
            .map { p -> p to validas.count { fechaConPatron(it, p, zona) != null } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }
}

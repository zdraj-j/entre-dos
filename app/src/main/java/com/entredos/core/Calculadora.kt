package com.entredos.core

sealed interface Tecla {
    /** "0" a "9" o "000". */
    data class Digito(val valor: String) : Tecla
    data object Borrar : Tecla
    data object Sumar : Tecla
    data object Restar : Tecla
    data object Limpiar : Tecla
    data object Igual : Tecla
}

/**
 * Estado del teclado tipo calculadora. Inmutable: cada tecla devuelve uno nuevo.
 * Solo pesos enteros; cada número admite hasta [MAX_DIGITOS] dígitos.
 */
data class Calculadora(val expresion: String = "") {

    fun tecla(t: Tecla): Calculadora {
        val ultimo = expresion.lastOrNull()
        val nueva = when (t) {
            Tecla.Limpiar -> ""
            Tecla.Borrar -> expresion.dropLast(1)
            Tecla.Sumar, Tecla.Restar -> {
                val op = if (t == Tecla.Sumar) '+' else '-'
                when {
                    expresion.isEmpty() -> return this
                    ultimo == '+' || ultimo == '-' -> expresion.dropLast(1) + op
                    else -> expresion + op
                }
            }
            Tecla.Igual -> total.takeIf { it > 0 }?.toString() ?: ""
            is Tecla.Digito -> {
                val actual = expresion.split('+', '-').last()
                when {
                    actual.isEmpty() && t.valor.all { it == '0' } -> return this
                    actual.length + t.valor.length > MAX_DIGITOS -> return this
                    else -> expresion + t.valor
                }
            }
        }
        return Calculadora(nueva)
    }

    val total: Long
        get() {
            var suma = 0L
            var signo = 1L
            NUMEROS.findAll(expresion).forEach { m ->
                when (m.value) {
                    "+" -> signo = 1
                    "-" -> signo = -1
                    else -> suma += signo * m.value.toLong()
                }
            }
            return suma
        }

    /** Se puede guardar un gasto con este monto. */
    val valido: Boolean get() = total > 0

    val tieneOperacion: Boolean get() = expresion.any { it == '+' || it == '-' }

    /** "12.000 + 8.500" mientras hay operaciones; vacío si es un solo número. */
    val textoExpresion: String
        get() = if (!tieneOperacion) "" else NUMEROS.findAll(expresion).joinToString(" ") { m ->
            when (m.value) {
                "+" -> "+"
                "-" -> "−"
                else -> FormatoPesos.miles(m.value.toLong())
            }
        }

    companion object {
        const val MAX_DIGITOS = 10
        private val NUMEROS = Regex("""[+-]|\d+""")
    }
}

object FormatoPesos {
    /** 1234567 → "1.234.567". */
    fun miles(n: Long): String {
        val s = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(".").reversed()
        return if (n < 0) "-$s" else s
    }

    /** 35000 → "$ 35.000". */
    fun pesos(n: Long): String = "$ " + miles(n)
}

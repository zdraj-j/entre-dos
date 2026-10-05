package com.entredos.core.exportacion

/** Escritura de CSV según RFC 4180. */
object Csv {
    fun campo(valor: Any?): String {
        val s = valor?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun fila(vararg valores: Any?): String = valores.joinToString(",") { campo(it) }
}

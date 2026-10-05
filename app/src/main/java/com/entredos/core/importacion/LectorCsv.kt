package com.entredos.core.importacion

import com.entredos.core.Movimientos

data class FilaCsv(
    /** Número de línea en el archivo (la del encabezado es la 1), para los mensajes de error. */
    val linea: Int,
    val valores: List<String>,
)

data class TablaCsv(
    /** Encabezados normalizados: sin espacios a los lados, en minúscula y sin tildes. */
    val encabezados: List<String>,
    /** Encabezados tal como venían, para mostrarlos en el asistente. */
    val encabezadosOriginales: List<String>,
    val filas: List<FilaCsv>,
    val separador: Char,
) {
    fun indice(columna: String): Int? = encabezados.indexOf(columna).takeIf { it >= 0 }
    fun valor(fila: FilaCsv, indice: Int?): String? =
        indice?.let { fila.valores.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
}

/** CSV según RFC 4180: comillas dobles, comillas duplicadas, saltos de línea dentro de comillas, BOM y CRLF. */
object LectorCsv {

    fun leer(texto: String, separador: Char? = null): TablaCsv {
        val limpio = texto.removePrefix("\uFEFF")
        val sep = separador ?: detectarSeparador(limpio)
        val registros = registros(limpio, sep)
        if (registros.isEmpty()) return TablaCsv(emptyList(), emptyList(), emptyList(), sep)
        val (lineaEnc, enc) = registros.first()
        val filas = registros.drop(1)
            .filter { (_, valores) -> valores.any { it.isNotBlank() } }
            .map { (linea, valores) -> FilaCsv(linea, valores) }
        return TablaCsv(enc.map { normalizarEncabezado(it) }, enc.map { it.trim() }, filas, sep)
            .also { require(lineaEnc == 1) }
    }

    fun normalizarEncabezado(s: String) = Movimientos.normalizar(s.trim()).replace(Regex("""\s+"""), "_")

    /** Coma, punto y coma (Excel en español) o tabulación: el que más aparezca en la primera línea fuera de comillas. */
    fun detectarSeparador(texto: String): Char {
        var enComillas = false
        val conteo = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (c in texto) {
            if (c == '"') enComillas = !enComillas
            else if (!enComillas && (c == '\n' || c == '\r')) break
            else if (!enComillas && c in conteo) conteo[c] = conteo[c]!! + 1
        }
        return conteo.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /** Devuelve (línea donde empieza el registro, valores). */
    private fun registros(texto: String, sep: Char): List<Pair<Int, List<String>>> {
        val salida = mutableListOf<Pair<Int, List<String>>>()
        val campo = StringBuilder()
        var valores = mutableListOf<String>()
        var enComillas = false
        var linea = 1
        var inicio = 1
        var i = 0
        fun cerrarRegistro() {
            valores.add(campo.toString()); campo.clear()
            salida.add(inicio to valores)
            valores = mutableListOf()
        }
        while (i < texto.length) {
            val c = texto[i]
            if (enComillas) {
                when {
                    c == '"' && texto.getOrNull(i + 1) == '"' -> { campo.append('"'); i++ }
                    c == '"' -> enComillas = false
                    else -> { if (c == '\n') linea++; campo.append(c) }
                }
            } else when (c) {
                '"' -> if (campo.isEmpty()) enComillas = true else campo.append(c)
                sep -> { valores.add(campo.toString()); campo.clear() }
                '\r' -> Unit
                '\n' -> { cerrarRegistro(); linea++; inicio = linea }
                else -> campo.append(c)
            }
            i++
        }
        if (campo.isNotEmpty() || valores.isNotEmpty()) cerrarRegistro()
        return salida
    }
}

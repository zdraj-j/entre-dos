package com.entredos.data.importacion

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.inject.Inject

/** Contenido de lo que eligió la persona: un zip del formato propio o un CSV suelto. */
data class ArchivosImportacion(
    val nombre: String,
    /** Nombre del archivo dentro del zip (en minúscula) → texto. Un CSV suelto queda como "gastos.csv". */
    val textos: Map<String, String>,
) {
    val gastos: String? get() = textos["gastos.csv"]
    val categorias: String? get() = textos["categorias.csv"]
    val deudas: String? get() = textos["deudas_abiertas.csv"]
    val metas: String? get() = textos["metas.csv"]
    val manifiesto: String? get() = textos["manifest.json"]
}

class ArchivoInvalido(mensaje: String) : Exception(mensaje)

class LectorArchivo @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun leer(uri: Uri): ArchivosImportacion = withContext(Dispatchers.IO) {
        val nombre = nombreDe(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ArchivoInvalido("No se pudo abrir el archivo")
        if (bytes.size > MAX_BYTES) throw ArchivoInvalido("El archivo pasa de 20 MB")

        val esZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val textos = if (esZip) leerZip(bytes) else mapOf("gastos.csv" to texto(bytes))
        if (textos["gastos.csv"] == null) throw ArchivoInvalido("El zip no tiene gastos.csv en la raíz")
        ArchivosImportacion(nombre, textos)
    }

    private fun leerZip(bytes: ByteArray): Map<String, String> {
        val textos = mutableMapOf<String, String>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entrada = zip.nextEntry ?: break
                val base = entrada.name.substringAfterLast('/').lowercase()
                if (entrada.isDirectory || base !in PERMITIDOS) continue
                val contenido = zip.readBytes()
                total += contenido.size
                if (total > MAX_DESCOMPRIMIDO) throw ArchivoInvalido("El contenido del zip es demasiado grande")
                textos[base] = texto(contenido)
            }
        }
        return textos
    }

    /** UTF-8; si no lo es (Excel en Windows suele guardar en Windows-1252), se lee como Windows-1252. */
    private fun texto(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }

    private fun nombreDe(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "archivo"

    private companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MAX_DESCOMPRIMIDO = 100L * 1024 * 1024
        val PERMITIDOS = setOf("gastos.csv", "categorias.csv", "deudas_abiertas.csv", "metas.csv", "manifest.json")
    }
}

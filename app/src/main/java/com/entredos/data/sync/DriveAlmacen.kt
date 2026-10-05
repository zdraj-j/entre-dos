package com.entredos.data.sync

import com.entredos.core.sync.AlmacenRemoto
import com.entredos.core.sync.ArchivoNoEncontrado
import com.entredos.core.sync.ArchivoRemoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Entrega el token de acceso a Drive. Lanza [SinAutorizacion] si hace falta que la persona inicie sesión. */
interface ProveedorToken {
    suspend fun token(): String
    suspend fun invalidar(token: String)
}

class SinAutorizacion : Exception("Hay que volver a conectar la cuenta del hogar")

class ErrorDrive(val codigo: Int, cuerpo: String) : IOException("Drive respondió $codigo: ${cuerpo.take(300)}")

/**
 * Carpeta oculta de la app (appDataFolder) en el Drive de la cuenta del hogar, con la API REST v3.
 * Sin librerías de Google para Drive: solo HttpURLConnection.
 */
@Singleton
class DriveAlmacen @Inject constructor(private val tokens: ProveedorToken) : AlmacenRemoto {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listar(): List<ArchivoRemoto> = consultar(null)

    override suspend fun buscar(nombre: String): ArchivoRemoto? =
        consultar("name = '${nombre.replace("\\", "\\\\").replace("'", "\\'")}' and trashed = false").firstOrNull()

    override suspend fun descargar(archivo: ArchivoRemoto): ByteArray {
        val r = pedir("GET", "$API/files/${archivo.id}?alt=media")
        if (r.codigo == 404) throw ArchivoNoEncontrado(archivo.nombre)
        return r.exigirOk().cuerpo
    }

    override suspend fun crear(nombre: String, contenido: ByteArray): ArchivoRemoto {
        val limite = "entredos-" + UUID.randomUUID()
        val metadatos = """{"name":${json.encodeToString(String.serializer(), nombre)},"parents":["appDataFolder"]}"""
        val cuerpo = buildList<ByteArray> {
            add("--$limite\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadatos\r\n".toByteArray())
            add("--$limite\r\nContent-Type: application/json\r\n\r\n".toByteArray())
            add(contenido)
            add("\r\n--$limite--".toByteArray())
        }.reduce(ByteArray::plus)
        val r = pedir("POST", "$UPLOAD/files?uploadType=multipart&fields=id,name", cuerpo, "multipart/related; boundary=$limite").exigirOk()
        val obj = json.parseToJsonElement(r.texto).jsonObject
        return ArchivoRemoto(obj["id"]!!.jsonPrimitive.content, obj["name"]!!.jsonPrimitive.content)
    }

    override suspend fun reemplazar(archivo: ArchivoRemoto, contenido: ByteArray) {
        // HttpURLConnection no admite PATCH: Google acepta POST con X-HTTP-Method-Override.
        val r = pedir("POST", "$UPLOAD/files/${archivo.id}?uploadType=media", contenido, "application/json", metodoReal = "PATCH")
        if (r.codigo == 404) throw ArchivoNoEncontrado(archivo.nombre)
        r.exigirOk()
    }

    override suspend fun borrar(archivo: ArchivoRemoto) {
        val r = pedir("DELETE", "$API/files/${archivo.id}")
        if (r.codigo == 404) throw ArchivoNoEncontrado(archivo.nombre)
        r.exigirOk()
    }

    /** Correo de la cuenta conectada, para recordarla y pedir siempre esa misma. */
    suspend fun correoDeLaCuenta(): String {
        val r = pedir("GET", "$API/about?fields=user(emailAddress)").exigirOk()
        return json.parseToJsonElement(r.texto).jsonObject["user"]!!.jsonObject["emailAddress"]!!.jsonPrimitive.content
    }

    private suspend fun consultar(q: String?): List<ArchivoRemoto> {
        val archivos = mutableListOf<ArchivoRemoto>()
        var pagina: String? = null
        do {
            val url = buildString {
                append("$API/files?spaces=appDataFolder&pageSize=1000&fields=").append(cod("nextPageToken,files(id,name)"))
                if (q != null) append("&q=").append(cod(q))
                if (pagina != null) append("&pageToken=").append(cod(pagina!!))
            }
            val obj = json.parseToJsonElement(pedir("GET", url).exigirOk().texto).jsonObject
            obj["files"]?.jsonArray?.forEach {
                val f = it.jsonObject
                archivos += ArchivoRemoto(f["id"]!!.jsonPrimitive.content, f["name"]!!.jsonPrimitive.content)
            }
            pagina = obj["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pagina != null)
        return archivos
    }

    private class Respuesta(val codigo: Int, val cuerpo: ByteArray) {
        val texto get() = cuerpo.decodeToString()
        fun exigirOk(): Respuesta = if (codigo in 200..299) this else throw ErrorDrive(codigo, texto)
    }

    /** Hace la petición. Si el token venció (401), lo invalida y reintenta una vez. */
    private suspend fun pedir(
        metodo: String,
        url: String,
        cuerpo: ByteArray? = null,
        tipo: String? = null,
        metodoReal: String? = null,
    ): Respuesta {
        var token = tokens.token()
        var r = conectar(metodo, url, cuerpo, tipo, metodoReal, token)
        if (r.codigo == 401) {
            tokens.invalidar(token)
            token = tokens.token()
            r = conectar(metodo, url, cuerpo, tipo, metodoReal, token)
        }
        return r
    }

    private suspend fun conectar(
        metodo: String, url: String, cuerpo: ByteArray?, tipo: String?, metodoReal: String?, token: String,
    ): Respuesta = withContext(Dispatchers.IO) {
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.requestMethod = metodo
            con.connectTimeout = 15_000
            con.readTimeout = 30_000
            con.setRequestProperty("Authorization", "Bearer $token")
            if (metodoReal != null) con.setRequestProperty("X-HTTP-Method-Override", metodoReal)
            if (cuerpo != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", tipo ?: "application/octet-stream")
                con.setFixedLengthStreamingMode(cuerpo.size)
                con.outputStream.use { it.write(cuerpo) }
            }
            val codigo = con.responseCode
            val flujo = if (codigo in 200..299) con.inputStream else con.errorStream
            Respuesta(codigo, flujo?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            con.disconnect()
        }
    }

    private fun cod(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}

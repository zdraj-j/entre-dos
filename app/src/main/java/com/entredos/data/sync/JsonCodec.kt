package com.entredos.data.sync

import com.entredos.core.sync.ArchivoCambios
import com.entredos.core.sync.Cambio
import com.entredos.core.sync.Codec
import com.entredos.core.sync.Foto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

/** Formato de los archivos en Drive. Si algún día cambia, se sube FORMATO y las versiones viejas avisan. */
const val FORMATO_SYNC = 1

class FormatoNoSoportado(formato: Int) :
    Exception("Hay datos de una versión más nueva de Entre dos (formato $formato). Actualiza la app.")

@Serializable
private data class CambioDto(
    val tabla: String,
    val id: String,
    val hlc: String,
    val dispositivo: String,
    val eliminado: Boolean,
    /** La entidad como objeto JSON (no como texto), para que el archivo se pueda leer a simple vista. */
    val datos: JsonElement,
)

@Serializable
private data class ArchivoCambiosDto(val formato: Int, val dispositivo: String, val secuencia: Long, val cambios: List<CambioDto>)

@Serializable
private data class FotoDto(val formato: Int, val creadaPor: String, val creadaEn: Long, val incluye: List<String>, val registros: List<CambioDto>)

class JsonCodec @Inject constructor() : Codec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun codificarCambios(a: ArchivoCambios): ByteArray =
        json.encodeToString(ArchivoCambiosDto.serializer(), ArchivoCambiosDto(FORMATO_SYNC, a.dispositivo, a.secuencia, a.cambios.map(::aDto)))
            .toByteArray()

    override fun decodificarCambios(b: ByteArray): ArchivoCambios {
        val dto = json.decodeFromString(ArchivoCambiosDto.serializer(), b.decodeToString())
        if (dto.formato > FORMATO_SYNC) throw FormatoNoSoportado(dto.formato)
        return ArchivoCambios(dto.dispositivo, dto.secuencia, dto.cambios.map(::deDto))
    }

    override fun codificarFoto(f: Foto): ByteArray =
        json.encodeToString(FotoDto.serializer(), FotoDto(FORMATO_SYNC, f.creadaPor, f.creadaEn, f.incluye.sorted(), f.registros.map(::aDto)))
            .toByteArray()

    override fun decodificarFoto(b: ByteArray): Foto {
        val dto = json.decodeFromString(FotoDto.serializer(), b.decodeToString())
        if (dto.formato > FORMATO_SYNC) throw FormatoNoSoportado(dto.formato)
        return Foto(dto.creadaPor, dto.creadaEn, dto.incluye.toSet(), dto.registros.map(::deDto))
    }

    private fun aDto(c: Cambio) = CambioDto(c.tabla, c.id, c.hlc, c.dispositivo, c.eliminado, json.parseToJsonElement(c.datos))
    private fun deDto(d: CambioDto) = Cambio(d.tabla, d.id, d.hlc, d.dispositivo, d.eliminado, d.datos.toString())
}

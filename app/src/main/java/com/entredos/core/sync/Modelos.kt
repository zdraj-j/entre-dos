package com.entredos.core.sync

/** Un registro tal como viaja entre teléfonos. [datos] es el JSON completo de la entidad. */
data class Cambio(
    val tabla: String,
    val id: String,
    val hlc: String,
    val dispositivo: String,
    val eliminado: Boolean,
    val datos: String,
)

/** Contenido de un archivo cambios-<dispositivo>-<secuencia>.json. */
data class ArchivoCambios(
    val dispositivo: String,
    val secuencia: Long,
    val cambios: List<Cambio>,
)

/**
 * Contenido de un archivo foto-<millis>-<dispositivo>.json: el estado completo del hogar,
 * borrados incluidos, y los archivos de cambios que ya resume.
 */
data class Foto(
    val creadaPor: String,
    val creadaEn: Long,
    val incluye: Set<String>,
    val registros: List<Cambio>,
)

data class ArchivoRemoto(val id: String, val nombre: String)

class ArchivoNoEncontrado(nombre: String) : Exception("No existe en Drive: $nombre")

/** Carpeta oculta de la app en Drive (o un sustituto en memoria para pruebas). */
interface AlmacenRemoto {
    suspend fun listar(): List<ArchivoRemoto>
    suspend fun buscar(nombre: String): ArchivoRemoto?
    /** Lanza [ArchivoNoEncontrado] si el archivo ya no existe. */
    suspend fun descargar(archivo: ArchivoRemoto): ByteArray
    suspend fun crear(nombre: String, contenido: ByteArray): ArchivoRemoto
    suspend fun reemplazar(archivo: ArchivoRemoto, contenido: ByteArray)
    suspend fun borrar(archivo: ArchivoRemoto)
}

/** Convierte archivos de cambios y fotos a bytes y de vuelta. */
interface Codec {
    fun codificarCambios(a: ArchivoCambios): ByteArray
    fun decodificarCambios(b: ByteArray): ArchivoCambios
    fun codificarFoto(f: Foto): ByteArray
    fun decodificarFoto(b: ByteArray): Foto
}

/** Lo que el motor necesita de la base local. En Android lo implementa Room. */
interface BaseLocal {
    suspend fun dispositivoId(): String

    /** Cambios por subir, con su número de orden local, del más viejo al más nuevo. */
    suspend fun pendientes(limite: Int): List<Pair<Long, Cambio>>
    suspend fun ultimaSecuenciaSubida(): Long

    /** En una transacción: borra los pendientes hasta [hastaPendiente], guarda la secuencia y marca el archivo como aplicado. */
    suspend fun confirmarSubida(hastaPendiente: Long, secuencia: Long, nombreArchivo: String)

    suspend fun aplicados(): Set<String>

    /**
     * En una transacción: aplica cada cambio solo si gana por LWW y marca como aplicados
     * [nombreArchivo] y [tambienMarcar].
     */
    suspend fun aplicar(nombreArchivo: String, cambios: List<Cambio>, tambienMarcar: Set<String> = emptySet())

    /** Todos los registros sincronizables, eliminados incluidos, para armar una foto. */
    suspend fun registrosParaFoto(): List<Cambio>

    /** En una transacción: aplica cada cambio solo si gana por LWW. Sin archivo (llegan por wifi). */
    suspend fun aplicarCambios(cambios: List<Cambio>)
}

object NombresSync {
    const val HOGAR = "hogar.json"
    private val CAMBIOS = Regex("""^cambios-(.+)-(\d{8})\.json$""")
    private val FOTO = Regex("""^foto-(\d{15})-(.+)\.json$""")

    fun cambios(dispositivo: String, secuencia: Long) = "cambios-$dispositivo-%08d.json".format(secuencia)
    fun foto(millis: Long, dispositivo: String) = "foto-%015d-$dispositivo.json".format(millis)

    fun esCambios(nombre: String) = CAMBIOS.matches(nombre)
    fun esFoto(nombre: String) = FOTO.matches(nombre)

    /** Dispositivo y secuencia de un archivo de cambios, o null si el nombre no corresponde. */
    fun leerCambios(nombre: String): Pair<String, Long>? =
        CAMBIOS.matchEntire(nombre)?.destructured?.let { (d, s) -> d to s.toLong() }
}

package com.entredos.core.sync

import java.util.Base64
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Corre una función suspend en pruebas (las falsas nunca suspenden de verdad). */
fun <T> correr(bloque: suspend () -> T): T {
    var resultado: Result<T>? = null
    bloque.startCoroutine(Continuation(EmptyCoroutineContext) { resultado = it })
    return resultado!!.getOrThrow()
}

/** Drive en memoria. */
class AlmacenEnMemoria : AlmacenRemoto {
    val archivos = linkedMapOf<String, ByteArray>()

    override suspend fun listar() = archivos.keys.map { ArchivoRemoto(it, it) }
    override suspend fun buscar(nombre: String) = if (nombre in archivos) ArchivoRemoto(nombre, nombre) else null
    override suspend fun descargar(archivo: ArchivoRemoto) = archivos[archivo.id] ?: throw ArchivoNoEncontrado(archivo.nombre)
    override suspend fun crear(nombre: String, contenido: ByteArray): ArchivoRemoto {
        archivos[nombre] = contenido; return ArchivoRemoto(nombre, nombre)
    }
    override suspend fun reemplazar(archivo: ArchivoRemoto, contenido: ByteArray) { archivos[archivo.id] = contenido }
    override suspend fun borrar(archivo: ArchivoRemoto) { archivos.remove(archivo.id) ?: throw ArchivoNoEncontrado(archivo.nombre) }

    fun cuantosCambios() = archivos.keys.count { NombresSync.esCambios(it) }
    fun cuantasFotos() = archivos.keys.count { NombresSync.esFoto(it) }
}

/** Codec de texto simple para pruebas: cada campo en Base64. */
object CodecTexto : Codec {
    private fun e(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun d(s: String) = String(Base64.getDecoder().decode(s))
    private fun linea(c: Cambio) = listOf(c.tabla, c.id, c.hlc, c.dispositivo, c.eliminado.toString(), c.datos).joinToString(",") { e(it) }
    private fun cambio(l: String) = l.split(",").map { d(it) }.let { Cambio(it[0], it[1], it[2], it[3], it[4].toBoolean(), it[5]) }

    override fun codificarCambios(a: ArchivoCambios) =
        (listOf(e(a.dispositivo) + "," + a.secuencia) + a.cambios.map(::linea)).joinToString("\n").toByteArray()

    override fun decodificarCambios(b: ByteArray): ArchivoCambios {
        val lineas = String(b).split("\n")
        val (disp, sec) = lineas[0].split(",")
        return ArchivoCambios(d(disp), sec.toLong(), lineas.drop(1).filter { it.isNotEmpty() }.map(::cambio))
    }

    override fun codificarFoto(f: Foto) =
        (listOf(e(f.creadaPor) + "," + f.creadaEn + "," + f.incluye.joinToString(";") { e(it) }) + f.registros.map(::linea))
            .joinToString("\n").toByteArray()

    override fun decodificarFoto(b: ByteArray): Foto {
        val lineas = String(b).split("\n")
        val cab = lineas[0].split(",")
        val incluye = cab[2].split(";").filter { it.isNotEmpty() }.map { d(it) }.toSet()
        return Foto(d(cab[0]), cab[1].toLong(), incluye, lineas.drop(1).filter { it.isNotEmpty() }.map(::cambio))
    }
}

/** Un teléfono con su base local en memoria. */
class TelefonoFalso(private val dispositivo: String, var hora: Long = 1_000) : BaseLocal {
    val reloj = RelojHlc { hora }
    private val registros = mutableMapOf<String, Cambio>()
    private val pendientes = mutableListOf<Pair<Long, Cambio>>()
    private val aplicados = mutableSetOf<String>()
    private var orden = 0L
    private var secuencia = 0L
    var fallarAlConfirmar = false

    fun motor(almacen: AlmacenRemoto, umbralFoto: Int = 200, porArchivo: Int = 500) =
        MotorSync(almacen, this, CodecTexto, reloj, { hora }, porArchivo, umbralFoto)

    fun escribir(tabla: String, id: String, datos: String, eliminado: Boolean = false) {
        val c = Cambio(tabla, id, reloj.ahora().codificar(), dispositivo, eliminado, datos)
        registros["$tabla/$id"] = c
        pendientes += ++orden to c
    }

    fun valor(tabla: String, id: String): String? = registros["$tabla/$id"]?.takeIf { !it.eliminado }?.datos
    fun cuantosPendientes() = pendientes.size

    override suspend fun dispositivoId() = dispositivo
    override suspend fun pendientes(limite: Int) = pendientes.take(limite)
    override suspend fun ultimaSecuenciaSubida() = secuencia

    override suspend fun confirmarSubida(hastaPendiente: Long, secuencia: Long, nombreArchivo: String) {
        if (fallarAlConfirmar) { fallarAlConfirmar = false; error("Se cerró la app justo después de subir") }
        pendientes.removeAll { it.first <= hastaPendiente }
        this.secuencia = secuencia
        aplicados += nombreArchivo
    }

    override suspend fun aplicados(): Set<String> = aplicados.toSet()

    override suspend fun aplicar(nombreArchivo: String, cambios: List<Cambio>, tambienMarcar: Set<String>) {
        for (c in cambios) {
            val local = registros["${c.tabla}/${c.id}"]
            if (Lww.ganaRemoto(local?.hlc, local?.dispositivo, c.hlc, c.dispositivo)) registros["${c.tabla}/${c.id}"] = c
        }
        aplicados += nombreArchivo
        aplicados += tambienMarcar
    }

    override suspend fun registrosParaFoto() = registros.values.toList()

    override suspend fun aplicarCambios(cambios: List<Cambio>) {
        for (c in cambios) {
            val local = registros["${c.tabla}/${c.id}"]
            if (Lww.ganaRemoto(local?.hlc, local?.dispositivo, c.hlc, c.dispositivo)) registros["${c.tabla}/${c.id}"] = c
        }
    }
}

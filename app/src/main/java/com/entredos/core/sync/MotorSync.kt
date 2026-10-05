package com.entredos.core.sync

data class ResultadoSync(
    val archivosSubidos: Int,
    val archivosAplicados: Int,
    val fotoAplicada: Boolean,
    val fotoCreada: Boolean,
)

/**
 * Sincroniza este teléfono con la carpeta del hogar en tres pasos:
 *
 * 1. **Subir:** los cambios pendientes van en archivos propios `cambios-<dispositivo>-<secuencia>.json`
 *    (máximo [porArchivo] cambios cada uno). Si una subida quedó a medias, se reemplaza el mismo
 *    archivo: aplicar dos veces un cambio no hace daño.
 * 2. **Bajar:** si hay una foto que este teléfono no ha aplicado, se aplica solo la más reciente
 *    (resume a las anteriores). Luego, cada archivo de cambios no aplicado, en orden.
 *    Todo pasa por LWW, así que el orden no altera el resultado.
 * 3. **Compactar:** con más de [umbralFoto] archivos de cambios, se crea una foto y se borran
 *    los archivos que resume y las fotos viejas. Un teléfono que no alcanzó a leer un archivo
 *    borrado recibe lo mismo al aplicar la foto.
 */
class MotorSync(
    private val almacen: AlmacenRemoto,
    private val base: BaseLocal,
    private val codec: Codec,
    private val reloj: RelojHlc,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val porArchivo: Int = 500,
    private val umbralFoto: Int = 200,
) {

    suspend fun sincronizar(): ResultadoSync {
        val subidos = subir()
        val listado = almacen.listar()
        val (fotoAplicada, aplicados) = bajar(listado)
        val fotoCreada = compactar(almacen.listar())
        return ResultadoSync(subidos, aplicados, fotoAplicada, fotoCreada)
    }

    private suspend fun subir(): Int {
        val dispositivo = base.dispositivoId()
        var subidos = 0
        while (true) {
            val lote = base.pendientes(porArchivo)
            if (lote.isEmpty()) break
            val secuencia = base.ultimaSecuenciaSubida() + 1
            val nombre = NombresSync.cambios(dispositivo, secuencia)
            val bytes = codec.codificarCambios(ArchivoCambios(dispositivo, secuencia, lote.map { it.second }))
            val existente = almacen.buscar(nombre)
            if (existente != null) almacen.reemplazar(existente, bytes) else almacen.crear(nombre, bytes)
            base.confirmarSubida(lote.last().first, secuencia, nombre)
            subidos++
        }
        return subidos
    }

    private suspend fun bajar(listado: List<ArchivoRemoto>): Pair<Boolean, Int> {
        var aplicados = base.aplicados()
        val fotos = listado.filter { NombresSync.esFoto(it.nombre) }.sortedBy { it.nombre }
        var fotoAplicada = false

        val ultimaFoto = fotos.lastOrNull()
        if (ultimaFoto != null && ultimaFoto.nombre !in aplicados) {
            try {
                val foto = codec.decodificarFoto(almacen.descargar(ultimaFoto))
                recibirMarcas(foto.registros)
                base.aplicar(ultimaFoto.nombre, foto.registros, foto.incluye + fotos.map { it.nombre })
                fotoAplicada = true
            } catch (_: ArchivoNoEncontrado) {
                // Otra foto la reemplazó mientras tanto: se aplicará en la próxima sincronización.
            }
            aplicados = base.aplicados()
        }

        var contador = 0
        val porAplicar = listado
            .filter { NombresSync.esCambios(it.nombre) && it.nombre !in aplicados }
            .sortedWith(compareBy({ NombresSync.leerCambios(it.nombre)!!.first }, { NombresSync.leerCambios(it.nombre)!!.second }))
        for (archivo in porAplicar) {
            val contenido = try {
                almacen.descargar(archivo)
            } catch (_: ArchivoNoEncontrado) {
                continue // Lo borró una compactación: su contenido llega con la foto nueva.
            }
            val cambios = codec.decodificarCambios(contenido).cambios
            recibirMarcas(cambios)
            base.aplicar(archivo.nombre, cambios)
            contador++
        }
        return fotoAplicada to contador
    }

    private suspend fun compactar(listado: List<ArchivoRemoto>): Boolean {
        val archivosCambios = listado.filter { NombresSync.esCambios(it.nombre) }
        if (archivosCambios.size <= umbralFoto) return false

        val aplicados = base.aplicados()
        // Si apareció algo que este teléfono aún no aplicó (por ejemplo, una foto del otro
        // teléfono), se espera a la próxima sincronización: así nunca se borra algo sin leerlo.
        val sinAplicar = listado.any {
            (NombresSync.esCambios(it.nombre) || NombresSync.esFoto(it.nombre)) && it.nombre !in aplicados
        }
        if (sinAplicar) return false
        val resumidos = archivosCambios
        val dispositivo = base.dispositivoId()
        val momento = ahora()
        val nombre = NombresSync.foto(momento, dispositivo)
        val foto = Foto(dispositivo, momento, resumidos.map { it.nombre }.toSet(), base.registrosParaFoto())

        almacen.crear(nombre, codec.codificarFoto(foto))
        base.aplicar(nombre, emptyList())

        resumidos.forEach { borrarSiExiste(it) }
        listado.filter { NombresSync.esFoto(it.nombre) && it.nombre < nombre }.forEach { borrarSiExiste(it) }
        return true
    }

    private suspend fun borrarSiExiste(archivo: ArchivoRemoto) {
        try {
            almacen.borrar(archivo)
        } catch (_: ArchivoNoEncontrado) {
            // Ya lo borró el otro teléfono.
        }
    }

    private fun recibirMarcas(cambios: List<Cambio>) {
        cambios.maxOfOrNull { it.hlc }?.let { reloj.recibir(Hlc.decodificar(it)) }
    }
}

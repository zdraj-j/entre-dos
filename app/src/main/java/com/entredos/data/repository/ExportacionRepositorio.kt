package com.entredos.data.repository

import com.entredos.core.Miembro
import com.entredos.core.Responsable
import com.entredos.core.exportacion.CategoriaExport
import com.entredos.core.exportacion.DeudaExport
import com.entredos.core.exportacion.ExportadorEntreDos
import com.entredos.core.exportacion.GastoExport
import com.entredos.core.exportacion.ParteExport
import com.entredos.data.local.EntreDosDatabase
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Respaldo completo en el formato del importador (entredos-export-AAAAMMDD.zip). */
@Singleton
class ExportacionRepositorio @Inject constructor(private val db: EntreDosDatabase, private val deudas: DeudasRepositorio) {

    data class Respaldo(val nombre: String, val bytes: ByteArray, val gastos: Int)

    suspend fun respaldo(): Respaldo {
        val zona = ZoneId.systemDefault()
        val ahora = System.currentTimeMillis()
        val categorias = db.categoriaDao().todosConEliminados().associateBy { it.id }
        val gastos = db.gastoDao().confirmadosEntre(0, Long.MAX_VALUE).mapNotNull { g ->
            val partes = g.partesActivas.map { p ->
                val c = categorias[p.categoriaId]
                ParteExport(c?.nombre ?: "Sin categoría", c?.responsable ?: Responsable.COMPARTIDA, p.monto, p.cargoA, p.cargoB)
            }
            if (partes.isEmpty()) null
            else GastoExport(g.gasto.id, g.gasto.fecha, g.gasto.pagadoPor, g.gasto.nombre, g.gasto.comercio, partes)
        }
        val archivos = ExportadorEntreDos.archivos(
            gastos = gastos,
            categorias = categorias.values.filter { !it.sync.eliminado }.map { CategoriaExport(it.nombre, it.responsable, it.presupuesto, it.color, it.destacada) },
            deudas = deudas.observar().first().map { DeudaExport(it.id, it.fecha, it.deudor, it.acreedor, it.pendiente, it.concepto) },
            nombres = db.miembroDao().todos().associate { Miembro.valueOf(it.id) to it.nombre },
            ahora = ahora, zona = zona,
        )
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            archivos.forEach { (nombre, texto) ->
                zip.putNextEntry(ZipEntry(nombre)); zip.write(texto.toByteArray()); zip.closeEntry()
            }
        }
        return Respaldo(ExportadorEntreDos.nombreArchivo(ahora, zona), salida.toByteArray(), gastos.size)
    }
}

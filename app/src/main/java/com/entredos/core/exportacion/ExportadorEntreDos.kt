package com.entredos.core.exportacion

import com.entredos.core.Miembro
import com.entredos.core.Reparto
import com.entredos.core.Responsable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ParteExport(val categoria: String, val responsable: Responsable, val monto: Long, val cargoA: Long, val cargoB: Long)

data class GastoExport(
    val id: String,
    val fecha: Long,
    val pagadoPor: Miembro,
    val nombre: String,
    val comercio: String?,
    val transferencia: Boolean,
    val partes: List<ParteExport>,
)

data class CategoriaExport(val nombre: String, val responsable: Responsable, val presupuesto: Long?, val color: String?, val destacada: Boolean)
data class DeudaExport(val id: String, val fecha: Long, val deudor: Miembro, val acreedor: Miembro, val pendiente: Long, val concepto: String)

/**
 * Respaldo en el mismo formato que lee el importador (versión 1): sirve para guardar una copia
 * fuera de Drive o para pasar los datos a otro hogar. Devuelve nombre de archivo → contenido.
 */
object ExportadorEntreDos {

    fun archivos(
        gastos: List<GastoExport>,
        categorias: List<CategoriaExport>,
        deudas: List<DeudaExport>,
        nombres: Map<Miembro, String>,
        ahora: Long,
        zona: ZoneId,
    ): Map<String, String> {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        fun fecha(ms: Long) = Instant.ofEpochMilli(ms).atZone(zona).format(fmt)
        fun nombre(m: Miembro) = nombres[m] ?: "Titular ${m.name}"

        val g = StringBuilder("id_origen,fecha,monto,categoria,pagado_por,descripcion,comercio,tipo,cargo_pagador\n")
        for (gasto in gastos.sortedBy { it.fecha }) for (p in gasto.partes) {
            val real = if (gasto.pagadoPor == Miembro.A) p.cargoA else p.cargoB
            val porRegla = Reparto.repartir(p.monto, p.responsable, gasto.pagadoPor).de(gasto.pagadoPor)
            g.append(Csv.fila(
                gasto.id, fecha(gasto.fecha), p.monto, p.categoria, nombre(gasto.pagadoPor), gasto.nombre, gasto.comercio,
                if (gasto.transferencia) "transferencia" else "gasto", if (real != porRegla) real else null,
            )).append('\n')
        }

        val c = StringBuilder("nombre,responsable,presupuesto,color,destacada\n")
        categorias.forEach {
            val resp = when (it.responsable) { Responsable.COMPARTIDA -> "compartida"; Responsable.A -> nombre(Miembro.A); Responsable.B -> nombre(Miembro.B) }
            c.append(Csv.fila(it.nombre, resp, it.presupuesto, it.color, if (it.destacada) "si" else "no")).append('\n')
        }

        val d = StringBuilder("id_origen,fecha,deudor,acreedor,monto,concepto\n")
        deudas.filter { it.pendiente > 0 }.forEach {
            d.append(Csv.fila(it.id, fecha(it.fecha), nombre(it.deudor), nombre(it.acreedor), it.pendiente, it.concepto)).append('\n')
        }


        val manifiesto = """{"formato": "entredos-import", "version": 1, "app_origen": "Entre dos", "exportado": "${Instant.ofEpochMilli(ahora).atZone(zona).toOffsetDateTime()}"}"""
        return linkedMapOf(
            "gastos.csv" to g.toString(), "categorias.csv" to c.toString(),
            "deudas_abiertas.csv" to d.toString(), "manifest.json" to manifiesto,
        )
    }

    fun nombreArchivo(ahora: Long, zona: ZoneId): String =
        "entredos-export-" + Instant.ofEpochMilli(ahora).atZone(zona).format(DateTimeFormatter.BASIC_ISO_DATE).take(8) + ".zip"
}

/** CSV del resumen de un periodo (secciones separadas por una línea vacía). */
object ReportePeriodoCsv {

    data class FilaPresupuesto(val categoria: String, val asignado: Long?, val gastado: Long)
    data class FilaGastoReporte(val fecha: Long, val nombre: String, val categorias: String, val pagadoPor: String, val monto: Long)

    fun armar(
        inicio: Long, fin: Long, totalPresupuestado: Long, totalGastado: Long,
        presupuestos: List<FilaPresupuesto>, porPersona: List<Pair<String, Long>>, comercios: List<Pair<String, Long>>,
        gastos: List<FilaGastoReporte>, zona: ZoneId,
    ): String {
        val f = DateTimeFormatter.ISO_LOCAL_DATE
        fun dia(ms: Long) = Instant.ofEpochMilli(ms).atZone(zona).format(f)
        val s = StringBuilder()
        s.append("Resumen\n").append(Csv.fila("desde", dia(inicio))).append('\n').append(Csv.fila("hasta", dia(fin))).append('\n')
            .append(Csv.fila("presupuestado", totalPresupuestado)).append('\n').append(Csv.fila("gastado", totalGastado)).append('\n')
            .append(Csv.fila(if (totalPresupuestado >= totalGastado) "sin gastar" else "excedido", kotlin.math.abs(totalPresupuestado - totalGastado))).append("\n\n")
        s.append("Presupuestos\n").append("categoria,asignado,gastado\n")
        presupuestos.forEach { s.append(Csv.fila(it.categoria, it.asignado ?: "sin tope", it.gastado)).append('\n') }
        s.append("\nQuién gastó\n").append("persona,total\n")
        porPersona.forEach { s.append(Csv.fila(it.first, it.second)).append('\n') }
        s.append("\nComercios con más gasto\n").append("comercio,total\n")
        comercios.forEach { s.append(Csv.fila(it.first, it.second)).append('\n') }
        s.append("\nGastos\n").append("fecha,descripcion,categoria,pagado_por,monto\n")
        gastos.sortedBy { it.fecha }.forEach { s.append(Csv.fila(dia(it.fecha), it.nombre, it.categorias, it.pagadoPor, it.monto)).append('\n') }
        return s.toString()
    }
}

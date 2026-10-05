package com.entredos.core.importacion

import com.entredos.core.Movimientos
import java.time.ZoneId

data class ErrorFila(val archivo: String, val linea: Int, val motivo: String)

data class FilaGasto(
    val linea: Int,
    val idOrigen: String?,
    val fecha: Long,
    /** Monto de esta fila: el gasto completo o una parte de un gasto dividido. */
    val monto: Long,
    val categoria: String,
    val pagadoPor: String,
    val descripcion: String?,
    val comercio: String?,
    val transferencia: Boolean,
    val cargoPagador: Long?,
)

data class FilaCategoria(
    val linea: Int,
    val nombre: String,
    /** "compartida" o el nombre de una persona. */
    val responsable: String,
    val presupuesto: Long?,
    val color: String?,
    val destacada: Boolean,
)

data class FilaDeuda(val linea: Int, val idOrigen: String?, val fecha: Long, val deudor: String, val acreedor: String, val monto: Long, val concepto: String?)

/** Filas leídas, errores por fila y filas omitidas a propósito (por ejemplo, ingresos). */
data class Leido<T>(val filas: List<T>, val errores: List<ErrorFila>, val omitidas: Int = 0)

/** Archivos del formato propio de Entre dos (versión 1). */
object FormatoEntreDos {

    const val VERSION = 1
    private val COLUMNAS_GASTOS = setOf("fecha", "monto", "categoria", "pagado_por")

    /** Si el encabezado trae las columnas obligatorias, no hace falta mapear columnas. */
    fun esFormatoPropio(tabla: TablaCsv) = tabla.encabezados.containsAll(COLUMNAS_GASTOS)

    fun gastos(tabla: TablaCsv, zona: ZoneId): Leido<FilaGasto> {
        val archivo = "gastos.csv"
        faltan(tabla, COLUMNAS_GASTOS, archivo)?.let { return Leido(emptyList(), listOf(it)) }
        val c = Indices(tabla)
        val filas = mutableListOf<FilaGasto>()
        val errores = mutableListOf<ErrorFila>()
        for (f in tabla.filas) {
            val error = { motivo: String -> errores += ErrorFila(archivo, f.linea, motivo) }
            val fecha = Valores.fechaIso(tabla.valor(f, c["fecha"]), zona) ?: run { error("Fecha inválida: ${tabla.valor(f, c["fecha"]) ?: "vacía"}"); null }
            val monto = Valores.monto(tabla.valor(f, c["monto"]))
            val categoria = tabla.valor(f, c["categoria"])
            val pagadoPor = tabla.valor(f, c["pagado_por"])
            val tipo = tabla.valor(f, c["tipo"])?.lowercase() ?: "gasto"
            val cargoTexto = tabla.valor(f, c["cargo_pagador"])
            val cargo = cargoTexto?.let { Valores.monto(it) }
            when {
                fecha == null -> Unit
                monto == null || monto <= 0 -> error("El monto debe ser un número mayor que cero: ${tabla.valor(f, c["monto"]) ?: "vacío"}")
                categoria == null -> error("Falta la categoría")
                pagadoPor == null -> error("Falta quién pagó")
                tipo != "gasto" && tipo != "transferencia" -> error("Tipo desconocido: $tipo (debe ser gasto o transferencia)")
                cargoTexto != null && (cargo == null || cargo < 0 || cargo > monto) -> error("cargo_pagador debe estar entre 0 y $monto")
                else -> filas += FilaGasto(
                    linea = f.linea,
                    idOrigen = tabla.valor(f, c["id_origen"]),
                    fecha = fecha, monto = monto,
                    categoria = categoria.take(120), pagadoPor = pagadoPor.take(120),
                    descripcion = tabla.valor(f, c["descripcion"])?.take(120),
                    comercio = tabla.valor(f, c["comercio"])?.take(120),
                    transferencia = tipo == "transferencia",
                    cargoPagador = cargo,
                )
            }
        }
        return Leido(filas, errores)
    }

    fun categorias(tabla: TablaCsv): Leido<FilaCategoria> {
        val archivo = "categorias.csv"
        faltan(tabla, setOf("nombre", "responsable"), archivo)?.let { return Leido(emptyList(), listOf(it)) }
        val c = Indices(tabla)
        val filas = mutableListOf<FilaCategoria>()
        val errores = mutableListOf<ErrorFila>()
        for (f in tabla.filas) {
            val nombre = tabla.valor(f, c["nombre"])
            val responsable = tabla.valor(f, c["responsable"])
            val presupuestoTexto = tabla.valor(f, c["presupuesto"])
            val presupuesto = presupuestoTexto?.let { Valores.monto(it) }
            when {
                nombre == null -> errores += ErrorFila(archivo, f.linea, "Falta el nombre")
                responsable == null -> errores += ErrorFila(archivo, f.linea, "Falta el responsable")
                presupuestoTexto != null && (presupuesto == null || presupuesto <= 0) -> errores += ErrorFila(archivo, f.linea, "Presupuesto inválido: $presupuestoTexto")
                else -> filas += FilaCategoria(
                    f.linea, nombre.take(120), responsable, presupuesto,
                    Valores.color(tabla.valor(f, c["color"])), Valores.booleano(tabla.valor(f, c["destacada"])) ?: false,
                )
            }
        }
        return Leido(filas, errores)
    }

    fun deudas(tabla: TablaCsv, zona: ZoneId): Leido<FilaDeuda> {
        val archivo = "deudas_abiertas.csv"
        faltan(tabla, setOf("fecha", "deudor", "acreedor", "monto"), archivo)?.let { return Leido(emptyList(), listOf(it)) }
        val c = Indices(tabla)
        val filas = mutableListOf<FilaDeuda>()
        val errores = mutableListOf<ErrorFila>()
        for (f in tabla.filas) {
            val fecha = Valores.fechaIso(tabla.valor(f, c["fecha"]), zona)
            val deudor = tabla.valor(f, c["deudor"])
            val acreedor = tabla.valor(f, c["acreedor"])
            val monto = Valores.monto(tabla.valor(f, c["monto"]))
            when {
                fecha == null -> errores += ErrorFila(archivo, f.linea, "Fecha inválida")
                deudor == null || acreedor == null -> errores += ErrorFila(archivo, f.linea, "Faltan deudor o acreedor")
                Movimientos.normalizar(deudor) == Movimientos.normalizar(acreedor) -> errores += ErrorFila(archivo, f.linea, "Deudor y acreedor son la misma persona")
                monto == null || monto <= 0 -> errores += ErrorFila(archivo, f.linea, "El monto debe ser mayor que cero")
                else -> filas += FilaDeuda(f.linea, tabla.valor(f, c["id_origen"]), fecha, deudor, acreedor, monto, tabla.valor(f, c["concepto"])?.take(120))
            }
        }
        return Leido(filas, errores)
    }

    private fun faltan(tabla: TablaCsv, obligatorias: Set<String>, archivo: String): ErrorFila? {
        val faltantes = obligatorias - tabla.encabezados.toSet()
        return if (faltantes.isEmpty()) null else ErrorFila(archivo, 1, "Faltan columnas: ${faltantes.sorted().joinToString(", ")}")
    }

    private class Indices(private val tabla: TablaCsv) {
        operator fun get(columna: String): Int? = tabla.indice(columna)
    }
}

/** Qué columna de un CSV de otra app es cada dato. Índices de columna; null = no viene. */
data class MapeoColumnas(
    val fecha: Int,
    val monto: Int,
    val categoria: Int? = null,
    val pagadoPor: Int? = null,
    val descripcion: Int? = null,
    val comercio: Int? = null,
    val tipo: Int? = null,
)

/** Algunas apps exportan los gastos en positivo y otras en negativo (y los ingresos al revés). */
enum class SignoGastos { POSITIVOS, NEGATIVOS }

/** CSV de otras apps (Wallet, Monefy, Splitwise, la exportación vieja de Budget Couple, etc.). */
object FormatoGenerico {

    const val SIN_CATEGORIA = "Sin categoría"
    const val SIN_PERSONA = "Quien importa"

    private val SINONIMOS = mapOf(
        "fecha" to listOf("fecha", "date", "dia", "fecha_y_hora", "datetime", "created", "fecha_del_gasto"),
        "monto" to listOf("monto", "amount", "valor", "importe", "cantidad", "cost", "costo", "total", "value"),
        "categoria" to listOf("categoria", "category", "rubro", "presupuesto", "category_name"),
        "pagadoPor" to listOf("pagado_por", "quien_pago", "pagado", "paid_by", "persona", "usuario", "user", "quien"),
        "descripcion" to listOf("descripcion", "description", "nota", "note", "notes", "concepto", "detalle", "nombre", "memo"),
        "comercio" to listOf("comercio", "merchant", "payee", "beneficiario", "establecimiento", "tienda"),
        "tipo" to listOf("tipo", "type", "transaction_type", "clase"),
    )

    /** Mapeo sugerido según los encabezados. Null si no hay columnas de fecha y monto. */
    fun sugerirMapeo(tabla: TablaCsv): MapeoColumnas? {
        val usadas = mutableSetOf<Int>()
        fun buscar(clave: String): Int? {
            for (sinonimo in SINONIMOS.getValue(clave)) {
                val i = tabla.encabezados.indexOfFirst { it == sinonimo || it.startsWith(sinonimo + "_") }
                if (i >= 0 && i !in usadas) { usadas += i; return i }
            }
            return null
        }
        val fecha = buscar("fecha") ?: return null
        val monto = buscar("monto") ?: return null
        return MapeoColumnas(fecha, monto, buscar("categoria"), buscar("pagadoPor"), buscar("descripcion"), buscar("comercio"), buscar("tipo"))
    }

    /** Si la mayoría de montos es negativa, los gastos vienen en negativo. */
    fun detectarSigno(tabla: TablaCsv, columnaMonto: Int): SignoGastos {
        val montos = tabla.filas.mapNotNull { Valores.monto(tabla.valor(it, columnaMonto)) }.filter { it != 0L }
        return if (montos.count { it < 0 } > montos.size / 2) SignoGastos.NEGATIVOS else SignoGastos.POSITIVOS
    }

    fun gastos(tabla: TablaCsv, mapeo: MapeoColumnas, formatoFecha: String, signo: SignoGastos, zona: ZoneId): Leido<FilaGasto> {
        val archivo = "archivo"
        val filas = mutableListOf<FilaGasto>()
        val errores = mutableListOf<ErrorFila>()
        var omitidas = 0
        for (f in tabla.filas) {
            val fecha = Valores.fechaConPatron(tabla.valor(f, mapeo.fecha), formatoFecha, zona)
            val bruto = Valores.monto(tabla.valor(f, mapeo.monto))
            val tipo = tabla.valor(f, mapeo.tipo)?.let(Movimientos::normalizar) ?: ""
            when {
                fecha == null -> errores += ErrorFila(archivo, f.linea, "Fecha inválida: ${tabla.valor(f, mapeo.fecha) ?: "vacía"}")
                bruto == null -> errores += ErrorFila(archivo, f.linea, "Monto inválido: ${tabla.valor(f, mapeo.monto) ?: "vacío"}")
                bruto == 0L -> omitidas++
                Regex("""ingreso|income|deposito|abono|reembolso|refund""").containsMatchIn(tipo) -> omitidas++
                else -> {
                    val esGasto = if (signo == SignoGastos.NEGATIVOS) bruto < 0 else bruto > 0
                    val transferencia = Regex("""transfer""").containsMatchIn(tipo)
                    if (!esGasto && !transferencia) { omitidas++; continue }
                    filas += FilaGasto(
                        linea = f.linea, idOrigen = null, fecha = fecha, monto = kotlin.math.abs(bruto),
                        categoria = tabla.valor(f, mapeo.categoria)?.take(120) ?: SIN_CATEGORIA,
                        pagadoPor = tabla.valor(f, mapeo.pagadoPor)?.take(120) ?: SIN_PERSONA,
                        descripcion = tabla.valor(f, mapeo.descripcion)?.take(120),
                        comercio = tabla.valor(f, mapeo.comercio)?.take(120),
                        transferencia = transferencia, cargoPagador = null,
                    )
                }
            }
        }
        return Leido(filas, errores, omitidas)
    }
}

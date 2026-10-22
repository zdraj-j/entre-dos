package com.entredos.core

/**
 * Bancos con lector propio (sección 2.1 del plan). Todos usan las reglas del lector genérico
 * (monto, comercio, OTP, rechazos, ingresos, publicidad) y agregan las suyas.
 *
 * Los nombres de paquete y los textos cambian con las versiones de cada app: verifícalos
 * en Ajustes › Diagnóstico › Notificaciones capturadas y ajústalos aquí.
 */
enum class Banco(val nombre: String, val paquetes: Set<String>) {
    BANCOLOMBIA("Bancolombia", setOf("com.todo1.mobile")),
    BANCO_DE_BOGOTA("Banco de Bogotá", setOf("com.bancodebogota.bancamovil")),
    DAVIVIENDA("Davivienda", setOf("com.davivienda.daviviendaapp")),
    DAVIPLATA("DaviPlata", setOf("com.davivienda.daviplataapp")),
    NEQUI("Nequi", setOf("com.nequi.MobileApp")),
    NUBANK("Nu", setOf("com.nu.production")),
    RAPPI("RappiCard", setOf("com.grability.rappi", "com.rappi.pay")),
}

sealed interface ResultadoLector {
    /** El paquete no es de un banco conocido: le toca al lector genérico (si está activo). */
    data object NoEsBanco : ResultadoLector
    /** Es de un banco pero no es un gasto (OTP, ingreso, envío entre personas, estado de pedido…). */
    data object Descartada : ResultadoLector
    data class Gasto(val banco: Banco, val lectura: LecturaNotificacion) : ResultadoLector
}

object LectoresBancos {

    /** Apps de mensajes: solo cuentan los SMS que mencionan al Banco de Bogotá. */
    val APPS_SMS = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms")

    private val MENCIONA_BOGOTA = Regex("""banco de bogot|bco\.? de bogot|bancodebogota|\bbdb\b""", RegexOption.IGNORE_CASE)

    /** Nequi y DaviPlata: los envíos de plata entre personas no son gastos. */
    private val ENVIO_ENTRE_PERSONAS = Regex(
        """enviaste|le enviaste|env[ií]o de plata|transferiste|pasaste plata|te (envi|pas)[oó]|a otro nequi|a otra daviplata""",
        RegexOption.IGNORE_CASE,
    )

    /** App de domicilios de Rappi: solo los avisos de la tarjeta, nunca los estados del pedido. */
    private val MENCIONA_TARJETA = Regex("""tarjeta|rappicard|rappi card|rappipay""", RegexOption.IGNORE_CASE)
    private val ESTADO_PEDIDO = Regex("""en camino|califica|tu pedido|repartidor|lleg[oó]|preparando|rappitendero|entregado""", RegexOption.IGNORE_CASE)

    /** Nombres de bancos y billeteras que se reconocen en el texto de un SMS o de otra app. */
    private val NOMBRES_BANCOS = listOf(
        "Bancolombia" to "bancolombia",
        "Banco de Bogotá" to MENCIONA_BOGOTA.pattern,
        "Davivienda" to "davivienda",
        "DaviPlata" to "daviplata",
        "Nequi" to "nequi",
        "Nu" to """nubank|\bnu (colombia|financiera)\b""",
        "BBVA" to """\bbbva\b""",
        "Banco de Occidente" to """banco de occidente|bco\.? de occidente|\boccidente:""",
        "Banco Popular" to """banco popular|bco\.? popular""",
        "AV Villas" to """\bav ?villas\b""",
        "Scotiabank Colpatria" to "colpatria|scotiabank",
        "Banco Caja Social" to "caja social",
        "Itaú" to """\bita[uú]\b""",
        "Banco Falabella" to "falabella",
        "Banco Agrario" to "banco agrario",
        "Bancoomeva" to "bancoomeva",
        "GNB Sudameris" to "sudameris",
        "Banco Pichincha" to "pichincha",
        "Lulo Bank" to """lulo ?bank""",
        "RappiCard" to "rappicard|rappi card|rappipay",
        "Movii" to """\bmovii\b""",
        "Banco Finandina" to "finandina",
        "Serfinanza" to "serfinanza",
    ).map { (nombre, patron) -> nombre to Regex(patron, RegexOption.IGNORE_CASE) }

    /** El banco que nombra el texto (el primero que aparece), para SMS y otras apps. */
    fun bancoMencionado(texto: String): String? =
        NOMBRES_BANCOS.mapNotNull { (nombre, patron) -> patron.find(texto)?.let { it.range.first to nombre } }
            .minByOrNull { it.first }?.second

    /**
     * De dónde salió un gasto detectado para mostrarlo en la lista: el banco guardado o, en gastos
     * anteriores que no lo guardaban, el banco de la app que avisó ("SMS" si fue un mensaje).
     */
    fun origen(banco: String?, paquete: String?): String? = banco
        ?: paquete?.let { p -> Banco.entries.firstOrNull { p in it.paquetes }?.nombre ?: if (p in APPS_SMS) "SMS" else null }

    fun bancoDe(paquete: String, texto: String): Banco? = when {
        paquete in APPS_SMS -> if (MENCIONA_BOGOTA.containsMatchIn(texto)) Banco.BANCO_DE_BOGOTA else null
        else -> Banco.entries.firstOrNull { paquete in it.paquetes }
    }

    fun leer(paquete: String, titulo: String?, texto: String?): ResultadoLector {
        val completo = listOfNotNull(titulo, texto).joinToString(". ")
        val banco = bancoDe(paquete, completo) ?: return ResultadoLector.NoEsBanco
        val descartar = when (banco) {
            Banco.NEQUI, Banco.DAVIPLATA -> ENVIO_ENTRE_PERSONAS.containsMatchIn(completo)
            Banco.RAPPI -> paquete == "com.grability.rappi" &&
                (!MENCIONA_TARJETA.containsMatchIn(completo) || ESTADO_PEDIDO.containsMatchIn(completo))
            else -> false
        }
        if (descartar) return ResultadoLector.Descartada
        val lectura = LectorGenerico.leer(titulo, texto, montoSinSigno = banco == Banco.BANCO_DE_BOGOTA)
            ?: return ResultadoLector.Descartada
        return ResultadoLector.Gasto(banco, lectura)
    }
}

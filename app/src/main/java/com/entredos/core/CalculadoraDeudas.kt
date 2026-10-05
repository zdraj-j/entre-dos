package com.entredos.core

/**
 * Una parte de gasto confirmada, con lo necesario para saber si genera deuda.
 * Los nombres coinciden con las columnas de la consulta de Room que la llena.
 */
data class ParteParaDeuda(
    val parteId: String,
    val gastoId: String,
    val concepto: String,
    val fecha: Long,
    val pagadoPor: Miembro,
    val cargoA: Long,
    val cargoB: Long,
    val importado: Boolean,
)

data class DeudaManualInfo(
    val id: String,
    val deudor: Miembro,
    val acreedor: Miembro,
    val monto: Long,
    val concepto: String,
    val fecha: Long,
)

enum class OrigenDeuda { GASTO, MANUAL }

data class Deuda(
    /** Id de la parte de gasto o de la deuda manual. Los abonos apuntan a este id. */
    val id: String,
    val origen: OrigenDeuda,
    val deudor: Miembro,
    val acreedor: Miembro,
    val monto: Long,
    val abonado: Long,
    val concepto: String,
    val fecha: Long,
) {
    val pendiente: Long get() = (monto - abonado).coerceAtLeast(0)
    val saldada: Boolean get() = pendiente == 0L
}

/** Saldo entre la pareja desde el punto de vista de un titular. */
data class Balance(val yoDebo: Long, val meDeben: Long) {
    /** Positivo: a mi favor. Negativo: le debo a mi pareja. Cero: a mano. */
    val neto: Long get() = meDeben - yoDebo
}

object CalculadoraDeudas {

    /**
     * Arma la lista de deudas a partir de las partes de gasto confirmadas, las deudas manuales
     * y el total abonado a cada deuda.
     *
     * - Una parte genera deuda cuando quien no pagó tiene cargo mayor que cero.
     * - Las partes importadas nunca generan deuda (lo pendiente entra como deuda manual).
     * - Sin pareja unida no hay deudas.
     */
    fun calcular(
        partes: List<ParteParaDeuda>,
        manuales: List<DeudaManualInfo>,
        abonadoPorDeuda: Map<String, Long>,
        parejaUnida: Boolean,
    ): List<Deuda> {
        if (!parejaUnida) return emptyList()

        val deGastos = partes
            .asSequence()
            .filter { !it.importado }
            .mapNotNull { p ->
                val deudor = p.pagadoPor.otro
                val monto = Cargos(p.cargoA, p.cargoB).de(deudor)
                if (monto <= 0) null
                else Deuda(
                    id = p.parteId,
                    origen = OrigenDeuda.GASTO,
                    deudor = deudor,
                    acreedor = p.pagadoPor,
                    monto = monto,
                    abonado = abonadoPorDeuda[p.parteId] ?: 0,
                    concepto = p.concepto,
                    fecha = p.fecha,
                )
            }

        val deManuales = manuales
            .asSequence()
            .filter { it.monto > 0 && it.deudor != it.acreedor }
            .map { m ->
                Deuda(
                    id = m.id,
                    origen = OrigenDeuda.MANUAL,
                    deudor = m.deudor,
                    acreedor = m.acreedor,
                    monto = m.monto,
                    abonado = abonadoPorDeuda[m.id] ?: 0,
                    concepto = m.concepto,
                    fecha = m.fecha,
                )
            }

        return (deGastos + deManuales).sortedByDescending { it.fecha }.toList()
    }

    fun balance(deudas: List<Deuda>, yo: Miembro): Balance {
        val abiertas = deudas.filter { !it.saldada }
        return Balance(
            yoDebo = abiertas.filter { it.deudor == yo }.sumOf { it.pendiente },
            meDeben = abiertas.filter { it.acreedor == yo }.sumOf { it.pendiente },
        )
    }

    /** Abonos necesarios para dejar todo saldado (al cerrar un periodo o con "Saldar todo"). */
    fun abonosParaSaldarTodo(deudas: List<Deuda>): Map<String, Long> =
        deudas.filter { !it.saldada }.associate { it.id to it.pendiente }

    /**
     * "Registrar un pago": [deudor] le paga [monto] a su pareja. Se reparte entre sus deudas
     * abiertas, de la más vieja a la más nueva. Devuelve id de deuda → monto del abono.
     * Lo que sobre (si paga más de lo que debe) no se usa.
     */
    fun repartirPago(deudas: List<Deuda>, deudor: Miembro, monto: Long): Map<String, Long> {
        require(monto > 0) { "El pago debe ser mayor que cero" }
        var queda = monto
        val abonos = linkedMapOf<String, Long>()
        for (d in deudas.filter { !it.saldada && it.deudor == deudor }.sortedBy { it.fecha }) {
            if (queda == 0L) break
            val abono = minOf(queda, d.pendiente)
            abonos[d.id] = abono
            queda -= abono
        }
        return abonos
    }
}

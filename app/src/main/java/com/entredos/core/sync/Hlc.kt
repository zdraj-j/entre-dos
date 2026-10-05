package com.entredos.core.sync

/**
 * Marca de un reloj lógico híbrido: milisegundos del reloj físico más un contador.
 * Se guarda como texto de ancho fijo para que el orden alfabético sea el orden real.
 */
data class Hlc(val millis: Long, val contador: Int) : Comparable<Hlc> {

    init {
        require(millis >= 0) { "millis negativo: $millis" }
        require(contador in 0..MAX_CONTADOR) { "contador fuera de rango: $contador" }
    }

    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, Hlc::millis, Hlc::contador)

    fun codificar(): String = "%015d-%05d".format(millis, contador)

    companion object {
        const val MAX_CONTADOR = 99_999
        val CERO = Hlc(0, 0)

        fun decodificar(texto: String): Hlc {
            val partes = texto.split('-')
            require(partes.size == 2) { "Marca HLC inválida: $texto" }
            return Hlc(partes[0].toLong(), partes[1].toInt())
        }
    }
}

/**
 * Reloj lógico híbrido. Garantiza que cada marca nueva sea mayor que todas las que este
 * teléfono ha emitido o recibido, aunque la hora del celular esté atrasada.
 *
 * @param relojFisico hora actual en milisegundos (inyectable para pruebas).
 */
class RelojHlc(private val relojFisico: () -> Long = System::currentTimeMillis) {

    private var ultimo: Hlc = Hlc.CERO

    /** Al iniciar la app se le pasa la marca más alta guardada, para no retroceder. */
    @Synchronized
    fun inicializar(ultimaConocida: Hlc?) {
        if (ultimaConocida != null && ultimaConocida > ultimo) ultimo = ultimaConocida
    }

    /** Marca para un cambio hecho en este teléfono. */
    @Synchronized
    fun ahora(): Hlc {
        val fisico = relojFisico()
        ultimo = if (fisico > ultimo.millis) Hlc(fisico, 0) else siguiente(ultimo.millis, ultimo.contador + 1)
        return ultimo
    }

    /**
     * Ajusta el reloj al recibir un cambio de la pareja. Una marca remota más de
     * [MAX_ADELANTO_MS] en el futuro no adelanta el reloj (celular con la hora mal),
     * aunque el cambio se aplique igual.
     */
    @Synchronized
    fun recibir(remoto: Hlc) {
        val fisico = relojFisico()
        if (remoto.millis - fisico > MAX_ADELANTO_MS) return
        val millis = maxOf(ultimo.millis, remoto.millis, fisico)
        val contador = when {
            millis == ultimo.millis && millis == remoto.millis -> maxOf(ultimo.contador, remoto.contador) + 1
            millis == ultimo.millis -> ultimo.contador + 1
            millis == remoto.millis -> remoto.contador + 1
            else -> 0
        }
        ultimo = siguiente(millis, contador)
    }

    /** Si el contador se desborda, se pasa al milisegundo siguiente. */
    private fun siguiente(millis: Long, contador: Int): Hlc =
        if (contador > Hlc.MAX_CONTADOR) Hlc(millis + 1, 0) else Hlc(millis, contador)

    companion object {
        const val MAX_ADELANTO_MS = 24L * 60 * 60 * 1000
    }
}

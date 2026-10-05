package com.entredos.core.wifi

import com.entredos.core.sync.Cambio

/**
 * Hasta dónde conoce un teléfono los cambios de cada autor: id de dispositivo → marca HLC más alta.
 * Las marcas de un mismo autor solo crecen, así que "todo lo de ese autor con marca mayor"
 * es exactamente lo que le falta al otro.
 */
object VectorVersiones {

    fun de(registros: List<Cambio>): Map<String, String> =
        registros.filter { it.dispositivo.isNotEmpty() && it.hlc.isNotEmpty() }
            .groupBy { it.dispositivo }
            .mapValues { (_, lista) -> lista.maxOf { it.hlc } }

    /** Registros que el dueño de [vectorDelOtro] no tiene. */
    fun faltantes(registros: List<Cambio>, vectorDelOtro: Map<String, String>): List<Cambio> =
        registros.filter { r -> r.hlc.isNotEmpty() && r.hlc > (vectorDelOtro[r.dispositivo] ?: "") }
}

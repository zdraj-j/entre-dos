package com.entredos.core.sync

/**
 * "Gana el cambio más reciente". Compara la marca HLC y, si empatan,
 * gana el dispositivo con id mayor. Así los dos teléfonos llegan al mismo resultado.
 */
object Lww {

    fun ganaRemoto(
        localHlc: String?,
        localDispositivo: String?,
        remotoHlc: String,
        remotoDispositivo: String,
    ): Boolean {
        if (localHlc.isNullOrEmpty()) return true
        val porMarca = Hlc.decodificar(remotoHlc).compareTo(Hlc.decodificar(localHlc))
        if (porMarca != 0) return porMarca > 0
        return remotoDispositivo > (localDispositivo ?: "")
    }
}

package com.entredos.core

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MovimientoResumen(
    val id: String,
    val nombre: String,
    val comercio: String?,
    val pagadoPor: Miembro,
    val categoriaIds: Set<String>,
    val fecha: Long,
    val monto: Long,
)

data class FiltroMovimientos(
    val texto: String = "",
    /** Null = ambos. */
    val persona: Miembro? = null,
    /** Null = todas. */
    val categoriaId: String? = null,
)

object Movimientos {

    fun filtrar(lista: List<MovimientoResumen>, filtro: FiltroMovimientos): List<MovimientoResumen> {
        val buscado = normalizar(filtro.texto.trim())
        return lista.filter { m ->
            (filtro.persona == null || m.pagadoPor == filtro.persona) &&
                (filtro.categoriaId == null || filtro.categoriaId in m.categoriaIds) &&
                (buscado.isEmpty() || normalizar(m.nombre).contains(buscado) || normalizar(m.comercio ?: "").contains(buscado))
        }
    }

    /** Agrupa por día, del más reciente al más antiguo. */
    fun porDia(lista: List<MovimientoResumen>, zona: ZoneId): List<Pair<LocalDate, List<MovimientoResumen>>> =
        lista.sortedByDescending { it.fecha }
            .groupBy { Instant.ofEpochMilli(it.fecha).atZone(zona).toLocalDate() }
            .toList()

    /** Sin tildes ni mayúsculas, para buscar "exito" y encontrar "Éxito". */
    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").lowercase()
}

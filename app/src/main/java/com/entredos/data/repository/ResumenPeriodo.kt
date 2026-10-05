package com.entredos.data.repository

import kotlinx.serialization.Serializable

/** Contenido de PeriodoCerradoEntity.resumenJson. Lo usan el cierre de periodo y el importador. */
@Serializable
data class ResumenPeriodo(
    val porCategoria: List<TotalNombre>,
    val porPersona: List<TotalNombre>,
    val comercios: List<TotalNombre>,
    /** Periodo armado por el importador a partir de fechas viejas. */
    val importado: Boolean = false,
)

@Serializable
data class TotalNombre(val id: String? = null, val nombre: String, val total: Long, val asignado: Long? = null)

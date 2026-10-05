package com.entredos.data.repository

import com.entredos.core.ModoPeriodo
import com.entredos.core.Periodo
import com.entredos.data.local.EntreDosDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodoRepositorio @Inject constructor(private val db: EntreDosDatabase) {

    private val zona: ZoneId get() = ZoneId.systemDefault()

    /** Inicio del periodo actual en epoch millis. Cambia al cerrar un periodo o al editar el hogar. */
    fun observarInicio(): Flow<Long> =
        combine(db.hogarDao().observar(), db.periodoCerradoDao().observarUltimoFin()) { hogar, ultimoFin ->
            Periodo.inicioActual(
                modo = hogar?.modoPeriodo ?: ModoPeriodo.MANUAL,
                diaInicio = hogar?.diaInicio ?: 1,
                ultimoCierre = ultimoFin,
                hoy = LocalDate.now(zona),
                zona = zona,
            )
        }.distinctUntilChanged()
}

package com.entredos.data.repository

import com.entredos.core.CategoriaConTope
import com.entredos.core.Presupuesto
import com.entredos.data.local.EntreDosDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DatosWidget(
    val hayHogar: Boolean,
    val disponible: Long,
    val asignado: Long,
    val gastadoHoy: Long,
    val pendientes: Int,
)

@Singleton
class WidgetRepositorio @Inject constructor(private val db: EntreDosDatabase, private val periodo: PeriodoRepositorio) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observar(): Flow<DatosWidget> = periodo.observarInicio().flatMapLatest { inicio ->
        val hoy = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        combine(
            db.categoriaDao().observar(),
            db.parteGastoDao().observarPartesDesde(inicio),
            db.gastoDao().observarConfirmados(hoy, Long.MAX_VALUE),
            db.gastoDao().contarPendientes(),
            db.hogarDao().observar(),
        ) { cats, partes, deHoy, pendientes, hogar ->
            val p = Presupuesto.calcular(cats.map { CategoriaConTope(it.id, it.presupuesto) }, partes)
            DatosWidget(hogar != null, p.disponible, p.asignado, deHoy.sumOf { it.gasto.monto }, pendientes)
        }
    }.distinctUntilChanged()

    suspend fun actual(): DatosWidget = observar().first()
}

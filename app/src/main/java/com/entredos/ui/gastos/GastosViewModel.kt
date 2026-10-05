package com.entredos.ui.gastos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.CategoriaInfo
import com.entredos.core.Clasificacion
import com.entredos.core.FiltroMovimientos
import com.entredos.core.MovimientoResumen
import com.entredos.core.Movimientos
import com.entredos.core.ReglaInfo
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.notificaciones.AvisosGasto
import com.entredos.data.repository.GastosRepositorio
import com.entredos.data.repository.ParteNueva
import com.entredos.data.repository.PeriodoRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Gasto detectado que espera categoría, con las dos más probables. */
data class PorConfirmar(val gasto: GastoEntity, val sugeridas: List<CategoriaEntity>)

data class EstadoGastos(
    val pendientes: List<PorConfirmar> = emptyList(),
    val dias: List<Pair<LocalDate, List<MovimientoResumen>>> = emptyList(),
    val total: Long = 0,
    val soloPeriodo: Boolean = true,
    val inicioPeriodo: LocalDate? = null,
    val filtro: FiltroMovimientos = FiltroMovimientos(),
    val categorias: List<CategoriaEntity> = emptyList(),
    val miembros: List<MiembroEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GastosViewModel @Inject constructor(
    private val db: EntreDosDatabase,
    periodo: PeriodoRepositorio,
    private val gastos: GastosRepositorio,
    private val avisos: AvisosGasto,
    guardado: SavedStateHandle,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroMovimientos(categoriaId = guardado.get<String>("categoriaId")))
    private val soloPeriodo = MutableStateFlow(guardado.get<Boolean>("todos") != true)
    val error = MutableStateFlow<String?>(null)

    private val confirmados = combine(soloPeriodo, periodo.observarInicio()) { solo, inicio -> solo to inicio }
        .flatMapLatest { (solo, inicio) -> db.gastoDao().observarConfirmados(if (solo) inicio else 0L, Long.MAX_VALUE) }

    private val pendientes = combine(db.gastoDao().observarPendientes(), db.reglaComercioDao().observar(), db.categoriaDao().observar()) { lista, reglas, cats ->
        val infoReglas = reglas.map { ReglaInfo(it.id, it.palabra, it.categoriaId, it.confirmaciones, it.automatica) }
        val infoCats = cats.map { CategoriaInfo(it.id, it.nombre, it.destacada) }
        lista.map { g ->
            val ids = Clasificacion.sugerencias(g.gasto.comercio, infoReglas, infoCats, cuantas = 2).map { it.id }
            PorConfirmar(g.gasto, ids.mapNotNull { id -> cats.firstOrNull { it.id == id } })
        }
    }

    val estado: StateFlow<EstadoGastos> = combine(
        combine(filtro, soloPeriodo, periodo.observarInicio()) { f, s, i -> Triple(f, s, i) },
        confirmados, pendientes, db.categoriaDao().observar(), db.miembroDao().observar(),
    ) { (f, solo, inicio), lista, porConfirmar, cats, miembros ->
        val resumenes = lista.map { g ->
            MovimientoResumen(g.gasto.id, g.gasto.nombre, g.gasto.comercio, g.gasto.pagadoPor, g.partesActivas.map { it.categoriaId }.toSet(), g.gasto.fecha, g.gasto.monto)
        }
        val filtrados = Movimientos.filtrar(resumenes, f)
        EstadoGastos(
            pendientes = porConfirmar,
            dias = Movimientos.porDia(filtrados, ZoneId.systemDefault()),
            total = filtrados.sumOf { it.monto },
            soloPeriodo = solo,
            inicioPeriodo = Instant.ofEpochMilli(inicio).atZone(ZoneId.systemDefault()).toLocalDate(),
            filtro = f, categorias = cats, miembros = miembros,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoGastos())

    /** Para la hoja obligatoria que abre el aviso "Gasto detectado". */
    suspend fun cargarPendiente(id: String): GastoEntity? =
        db.gastoDao().porId(id)?.takeIf { it.estado == com.entredos.core.EstadoGasto.PENDIENTE && !it.sync.eliminado }

    fun buscar(texto: String) = filtro.update { it.copy(texto = texto) }
    fun categoria(id: String?) = filtro.update { it.copy(categoriaId = id) }
    fun fijarSoloPeriodo(solo: Boolean) { soloPeriodo.value = solo }

    /** Un toque en una categoría sugerida. */
    fun confirmar(g: GastoEntity, categoriaId: String) = hacer(g) { gastos.confirmarPendiente(g, g.nombre, listOf(ParteNueva(categoriaId, g.monto))) }

    /** Desde la hoja "Otra": una o varias categorías (partes iguales). */
    fun confirmarVarias(g: GastoEntity, ids: List<String>) = hacer(g) { gastos.confirmarPendiente(g, g.nombre, gastos.partesIguales(g.monto, ids)) }
    fun transferencia(g: GastoEntity) = hacer(g) { gastos.confirmarPendiente(g, g.nombre, emptyList(), transferencia = true) }
    fun noEsGasto(g: GastoEntity) = hacer(g) { gastos.ignorar(g) }

    private fun hacer(g: GastoEntity, accion: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { accion(); avisos.cancelar(g.id) }.onFailure { error.value = it.message }
        }
    }
}

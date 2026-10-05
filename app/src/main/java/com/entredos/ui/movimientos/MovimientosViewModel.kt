package com.entredos.ui.movimientos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.FiltroMovimientos
import com.entredos.core.Miembro
import com.entredos.core.MovimientoResumen
import com.entredos.core.Movimientos
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.MiembroEntity
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class EstadoMovimientos(
    val filtro: FiltroMovimientos = FiltroMovimientos(),
    val soloPeriodo: Boolean = true,
    val categorias: List<CategoriaEntity> = emptyList(),
    val miembros: List<MiembroEntity> = emptyList(),
    val dias: List<Pair<LocalDate, List<MovimientoResumen>>> = emptyList(),
)

/**
 * Lista de gastos confirmados con buscador, periodo, persona y categoría.
 * Desde Presupuesto se abre con el argumento de navegación "categoriaId" ya puesto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovimientosViewModel @Inject constructor(
    db: EntreDosDatabase,
    periodo: PeriodoRepositorio,
    guardado: SavedStateHandle,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroMovimientos(categoriaId = guardado.get<String>("categoriaId")))
    private val soloPeriodo = MutableStateFlow(guardado.get<Boolean>("todos") != true)

    private val gastos = soloPeriodo.flatMapLatest { solo ->
        (if (solo) periodo.observarInicio() else flowOf(0L)).flatMapLatest { desde ->
            db.gastoDao().observarConfirmados(desde, Long.MAX_VALUE)
        }
    }

    val estado: StateFlow<EstadoMovimientos> = combine(
        filtro, soloPeriodo, gastos, db.categoriaDao().observar(), db.miembroDao().observar(),
    ) { f, solo, lista, cats, miembros ->
        val resumenes = lista.map { g ->
            MovimientoResumen(g.gasto.id, g.gasto.nombre, g.gasto.comercio, g.gasto.pagadoPor, g.partesActivas.map { it.categoriaId }.toSet(), g.gasto.fecha, g.gasto.monto)
        }
        EstadoMovimientos(f, solo, cats, miembros, Movimientos.porDia(Movimientos.filtrar(resumenes, f), ZoneId.systemDefault()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMovimientos())

    fun buscar(texto: String) = filtro.update { it.copy(texto = texto) }
    fun persona(m: Miembro?) = filtro.update { it.copy(persona = m) }
    fun categoria(id: String?) = filtro.update { it.copy(categoriaId = id) }
    fun fijarSoloPeriodo(solo: Boolean) { soloPeriodo.value = solo }
}

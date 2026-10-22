package com.entredos.ui.mes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Balance
import com.entredos.core.CalculadoraDeudas
import com.entredos.core.CategoriaConTope
import com.entredos.core.Deuda
import com.entredos.core.EstadoPeriodo
import com.entredos.core.Estadisticas
import com.entredos.core.Miembro
import com.entredos.core.Periodo
import com.entredos.core.Presupuesto
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.local.entity.PeriodoCerradoEntity
import com.entredos.data.repository.CierreRepositorio
import com.entredos.data.repository.DeudasRepositorio
import com.entredos.data.repository.PeriodoRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class EstadoMes(
    val inicio: LocalDate = LocalDate.now(),
    /** Fin exclusivo del periodo (el siguiente día de inicio). */
    val fin: LocalDate = LocalDate.now().plusMonths(1),
    val diaInicio: Int = 1,
    val presupuesto: EstadoPeriodo = EstadoPeriodo(emptyMap(), 0, 0),
    val categorias: List<CategoriaEntity> = emptyList(),
    val seVanAPasar: Boolean = false,
    val porPersona: Map<Miembro, Long> = emptyMap(),
    val deudas: List<Deuda> = emptyList(),
    val balance: Balance = Balance(0, 0),
    val yo: Miembro? = null,
    val miembros: List<MiembroEntity> = emptyList(),
) {
    val abiertas: List<Deuda> get() = deudas.filter { !it.saldada }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MesViewModel @Inject constructor(
    db: EntreDosDatabase,
    periodo: PeriodoRepositorio,
    private val deudasRepo: DeudasRepositorio,
    private val cierre: CierreRepositorio,
) : ViewModel() {

    private val zona = ZoneId.systemDefault()

    private val delPeriodo = combine(periodo.observarInicio(), db.hogarDao().observar()) { inicio, hogar -> inicio to (hogar?.diaInicio ?: 1) }
        .flatMapLatest { (inicioMs, dia) ->
            combine(db.categoriaDao().observar(), db.parteGastoDao().observarPartesDesde(inicioMs), db.gastoDao().observarConfirmados(inicioMs, Long.MAX_VALUE)) { cats, partes, gastos ->
                val inicio = Instant.ofEpochMilli(inicioMs).atZone(zona).toLocalDate()
                val fin = Periodo.finDelPeriodo(dia, Periodo.ultimoDiaDeInicio(dia, inicio))
                val p = Presupuesto.calcular(cats.map { CategoriaConTope(it.id, it.presupuesto) }, partes)
                val stats = Estadisticas.calcular(gastos.map(CierreRepositorio::aResumen))
                EstadoMes(
                    inicio = inicio, fin = fin, diaInicio = dia, presupuesto = p, categorias = cats,
                    seVanAPasar = Periodo.seVanAPasar(p.gastado, p.asignado, inicio, fin, LocalDate.now(zona)),
                    porPersona = stats.porPersona,
                )
            }
        }

    val estado: StateFlow<EstadoMes> = combine(delPeriodo, deudasRepo.observar(), db.dispositivoDao().observar(), db.miembroDao().observar()) { e, deudas, d, miembros ->
        val yo = d?.miembro
        e.copy(deudas = deudas, yo = yo, miembros = miembros, balance = yo?.let { CalculadoraDeudas.balance(deudas, it) } ?: Balance(0, 0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMes())

    val recienCerrado = MutableStateFlow<PeriodoCerradoEntity?>(null)
    val error = MutableStateFlow<String?>(null)

    fun cerrarMes() = hacer { recienCerrado.value = cierre.cerrar() }

    /** El que debe le paga al otro: si mi pareja me debe, paga ella; si yo debo, pago yo. */
    fun registrarPago(monto: Long) = hacer {
        val e = estado.value
        val yo = e.yo ?: error("Este teléfono aún no tiene titular")
        val deudor = if (e.balance.neto > 0) yo.otro else yo
        deudasRepo.registrarPago(e.deudas, deudor, monto, System.currentTimeMillis())
    }

    fun abonar(d: Deuda, monto: Long) = hacer { deudasRepo.abonar(d, monto, System.currentTimeMillis()) }

    fun agregarDeuda(yoDebo: Boolean, monto: Long, concepto: String) = hacer {
        val yo = estado.value.yo ?: error("Este teléfono aún no tiene titular")
        deudasRepo.agregarManual(if (yoDebo) yo else yo.otro, monto, concepto.ifBlank { "Deuda" }, System.currentTimeMillis())
    }

    private fun hacer(accion: suspend () -> Unit) {
        viewModelScope.launch { runCatching { accion() }.onSuccess { error.value = null }.onFailure { error.value = it.message } }
    }
}

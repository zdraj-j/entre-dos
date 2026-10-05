package com.entredos.ui.teclado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Calculadora
import com.entredos.core.CategoriaConTope
import com.entredos.core.Miembro
import com.entredos.core.Presupuesto
import com.entredos.core.Reparto
import com.entredos.core.Responsable
import com.entredos.core.Tecla
import com.entredos.data.local.DispositivoActual
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.GastoEntity
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class CategoriaTecla(
    val id: String,
    val nombre: String,
    /** "#RRGGBB" */
    val color: String,
    val compartida: Boolean,
    /** Null = sin tope. */
    val disponible: Long?,
    val excedida: Boolean,
    val seleccionada: Boolean,
)

/** Lo que la persona está armando y que no viene de la base. */
data class Borrador(
    val calculadora: Calculadora = Calculadora(),
    val fecha: LocalDate = LocalDate.now(),
    val nota: String = "",
    val varias: Boolean = false,
    val seleccion: List<String> = emptyList(),
)

data class EstadoTeclado(
    val borrador: Borrador = Borrador(),
    val categorias: List<CategoriaTecla> = emptyList(),
    val disponiblePeriodo: Long = 0,
    val pendientes: Int = 0,
    val nombrePareja: String? = null,
)

/** Avisos de un solo uso para la barra inferior. */
sealed interface AvisoTeclado {
    data class Registrado(val gasto: GastoEntity, val borradorPrevio: Borrador, val texto: String) : AvisoTeclado
    data class Error(val texto: String) : AvisoTeclado
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TecladoViewModel @Inject constructor(
    db: EntreDosDatabase,
    periodo: PeriodoRepositorio,
    private val gastos: GastosRepositorio,
    private val dispositivo: DispositivoActual,
) : ViewModel() {

    private val borrador = MutableStateFlow(Borrador())
    val aviso = MutableStateFlow<AvisoTeclado?>(null)

    private val hace90Dias: Long get() = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000

    private val estadoPeriodo = periodo.observarInicio().flatMapLatest { inicio ->
        combine(db.categoriaDao().observar(), db.parteGastoDao().observarPartesDesde(inicio)) { cats, partes ->
            cats to Presupuesto.calcular(cats.map { CategoriaConTope(it.id, it.presupuesto) }, partes)
        }
    }

    val estado: StateFlow<EstadoTeclado> = combine(
        borrador,
        estadoPeriodo,
        db.parteGastoDao().observarUso(hace90Dias),
        db.gastoDao().contarPendientes(),
        combine(db.miembroDao().observar(), db.dispositivoDao().observar()) { m, d -> m to d?.miembro },
    ) { b, (cats, presupuesto), uso, pendientes, (miembros, yo) ->
        val veces = uso.associate { it.categoriaId to it.veces }
        val ordenadas = cats.sortedWith(
            compareByDescending<com.entredos.data.local.entity.CategoriaEntity> { it.destacada }
                .thenByDescending { veces[it.id] ?: 0 }
                .thenBy { it.nombre.lowercase() }
        )
        EstadoTeclado(
            borrador = b,
            categorias = ordenadas.map { c ->
                val e = presupuesto.porCategoria[c.id]
                CategoriaTecla(c.id, c.nombre, c.color, c.responsable == Responsable.COMPARTIDA, e?.disponible, e?.excedida == true, c.id in b.seleccion)
            },
            disponiblePeriodo = presupuesto.disponible,
            pendientes = pendientes,
            nombrePareja = miembros.firstOrNull { yo != null && it.id != yo.name }?.nombre,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoTeclado())

    fun tecla(t: Tecla) = borrador.update { it.copy(calculadora = it.calculadora.tecla(t)) }
    fun fijarFecha(fecha: LocalDate) = borrador.update { it.copy(fecha = fecha) }
    fun fijarNota(nota: String) = borrador.update { it.copy(nota = nota.trim()) }
    fun alternarVarias() = borrador.update { it.copy(varias = !it.varias, seleccion = emptyList()) }

    /** Toque normal: guarda de una vez. En "Varias categorías", solo marca o desmarca. */
    fun tocarCategoria(id: String) {
        val b = borrador.value
        if (b.varias) {
            borrador.update { it.copy(seleccion = if (id in it.seleccion) it.seleccion - id else it.seleccion + id) }
        } else {
            registrar { total -> listOf(ParteNueva(id, total)) }
        }
    }

    /** "Ajustar reparto" (toque largo en una categoría compartida). */
    fun registrarConReparto(categoriaId: String, cargoPagador: Long) =
        registrar { total -> listOf(ParteNueva(categoriaId, total, cargoPagador)) }

    /** "Registrar en N": en partes iguales. */
    fun registrarVarias() = registrar { total -> gastos.partesIguales(total, borrador.value.seleccion) }

    /** "Ajustar montos": un monto por categoría seleccionada, en el mismo orden. */
    fun registrarConMontos(montos: List<Long>) =
        registrar { _ -> borrador.value.seleccion.zip(montos) { id, m -> ParteNueva(id, m) } }

    fun deshacer(aviso: AvisoTeclado.Registrado) {
        viewModelScope.launch {
            gastos.eliminar(aviso.gasto)
            borrador.value = aviso.borradorPrevio
            this@TecladoViewModel.aviso.value = null
        }
    }

    fun avisoMostrado() { aviso.value = null }

    fun faltaMonto() { aviso.value = AvisoTeclado.Error("Escribe un monto primero") }

    private fun registrar(armarPartes: (Long) -> List<ParteNueva>) {
        val previo = borrador.value
        val total = previo.calculadora.total
        if (!previo.calculadora.valido) return faltaMonto()
        viewModelScope.launch {
            try {
                val yo = dispositivo.miembro() ?: error("Este teléfono aún no tiene titular")
                val partes = armarPartes(total)
                Reparto.validarDivision(total, partes.map { it.monto })?.let { error(it) }
                val nombres = partes.mapNotNull { p -> estado.value.categorias.firstOrNull { it.id == p.categoriaId }?.nombre }
                val gasto = gastos.registrar(
                    nombre = previo.nota.ifBlank { nombres.joinToString(", ") },
                    fecha = momento(previo.fecha),
                    pagadoPor = yo,
                    partes = partes,
                )
                borrador.value = Borrador()
                aviso.value = AvisoTeclado.Registrado(gasto, previo, "Registrado ${com.entredos.core.FormatoPesos.pesos(total)} en ${nombres.joinToString(", ")}")
            } catch (e: Exception) {
                aviso.value = AvisoTeclado.Error(e.message ?: "No se pudo registrar")
            }
        }
    }

    /** Hoy: la hora actual. Otro día: mediodía, para que no cambie de día por zona horaria. */
    private fun momento(fecha: LocalDate): Long {
        val zona = ZoneId.systemDefault()
        return if (fecha == LocalDate.now(zona)) System.currentTimeMillis()
        else fecha.atTime(LocalTime.NOON).atZone(zona).toInstant().toEpochMilli()
    }
}

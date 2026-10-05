package com.entredos.ui.pendientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.local.entity.CategoriaEntity
import com.entredos.data.local.entity.GastoEntity
import com.entredos.data.notificaciones.AvisosGasto
import com.entredos.data.repository.GastosRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Bandeja "Pendientes por clasificar" y ventana obligatoria al tocar el aviso. */
@HiltViewModel
class PendientesViewModel @Inject constructor(
    private val db: EntreDosDatabase,
    private val gastos: GastosRepositorio,
    private val avisos: AvisosGasto,
) : ViewModel() {

    val pendientes: StateFlow<List<GastoEntity>> = db.gastoDao().observarPendientes()
        .map { lista -> lista.map { it.gasto } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categorias: StateFlow<List<CategoriaEntity>> = db.categoriaDao().observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val error = MutableStateFlow<String?>(null)

    suspend fun cargar(id: String): GastoEntity? = db.gastoDao().porId(id)

    /** Con varias categorías el monto se reparte en partes iguales. */
    fun confirmar(gasto: GastoEntity, nombre: String, monto: Long, categoriaIds: List<String>, alTerminar: () -> Unit) = hacer(gasto, alTerminar) {
        gastos.confirmarPendiente(gasto, nombre.ifBlank { gasto.nombre }, gastos.partesIguales(monto, categoriaIds))
    }

    fun transferencia(gasto: GastoEntity, nombre: String, alTerminar: () -> Unit) = hacer(gasto, alTerminar) {
        gastos.confirmarPendiente(gasto, nombre.ifBlank { gasto.nombre }, emptyList(), transferencia = true)
    }

    fun noRegistrar(gasto: GastoEntity, alTerminar: () -> Unit) = hacer(gasto, alTerminar) { gastos.ignorar(gasto) }

    private fun hacer(gasto: GastoEntity, alTerminar: () -> Unit, accion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
                avisos.cancelar(gasto.id)
                alTerminar()
            } catch (e: Exception) {
                error.value = e.message ?: "No se pudo guardar"
            }
        }
    }
}

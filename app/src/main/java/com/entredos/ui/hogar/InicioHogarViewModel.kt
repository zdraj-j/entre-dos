package com.entredos.ui.hogar

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Miembro
import com.entredos.data.local.entity.MiembroEntity
import com.entredos.data.sync.CuentaHogar
import com.entredos.data.sync.ResultadoConexion
import com.entredos.data.sync.ServicioHogar
import com.entredos.data.sync.SituacionDrive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PasoInicio {
    data object Bienvenida : PasoInicio
    data class Cargando(val mensaje: String) : PasoInicio
    data class AbrirGoogle(val intent: PendingIntent) : PasoInicio
    data object CrearHogar : PasoInicio
    data class ElegirQuienSoy(val nombreHogar: String, val miembros: List<MiembroEntity>, val puedeUnirseComoB: Boolean) : PasoInicio
    data class Error(val mensaje: String) : PasoInicio
    data object Listo : PasoInicio
}

@HiltViewModel
class InicioHogarViewModel @Inject constructor(
    private val cuenta: CuentaHogar,
    private val servicio: ServicioHogar,
) : ViewModel() {

    private val _paso = MutableStateFlow<PasoInicio>(PasoInicio.Bienvenida)
    val paso: StateFlow<PasoInicio> = _paso.asStateFlow()

    fun conectar() = trabajar("Conectando con Google…") {
        when (val r = cuenta.conectar()) {
            is ResultadoConexion.NecesitaPantalla -> _paso.value = PasoInicio.AbrirGoogle(r.intent)
            is ResultadoConexion.Conectada -> revisar()
        }
    }

    /** La pantalla de Google volvió con la cuenta elegida y el permiso aceptado. */
    fun volvioDeGoogle(data: Intent?) = trabajar("Revisando la cuenta…") {
        cuenta.completar(data)
        revisar()
    }

    fun googleCancelado() { _paso.value = PasoInicio.Bienvenida }

    fun crearHogar(nombreHogar: String, miNombre: String, diaInicio: Int) =
        trabajar("Creando el hogar…") { servicio.crearHogar(nombreHogar, miNombre, diaInicio); _paso.value = PasoInicio.Listo }

    fun soy(miembro: Miembro) =
        trabajar("Recuperando tu lugar…") { servicio.soy(miembro); _paso.value = PasoInicio.Listo }

    fun unirmeComoB(miNombre: String) =
        trabajar("Uniéndote al hogar…") { servicio.unirmeComoTitularB(miNombre); _paso.value = PasoInicio.Listo }

    private suspend fun revisar() {
        _paso.value = PasoInicio.Cargando("Descargando los datos del hogar…")
        _paso.value = when (val s = servicio.revisarDrive()) {
            SituacionDrive.SinHogar -> PasoInicio.CrearHogar
            is SituacionDrive.ConHogar -> PasoInicio.ElegirQuienSoy(s.nombreHogar, s.miembros, s.puedeUnirseComoB)
        }
    }

    private fun trabajar(mensaje: String, bloque: suspend () -> Unit) {
        _paso.value = PasoInicio.Cargando(mensaje)
        viewModelScope.launch {
            try {
                bloque()
            } catch (e: Exception) {
                _paso.value = PasoInicio.Error(e.message ?: "Algo salió mal")
            }
        }
    }
}

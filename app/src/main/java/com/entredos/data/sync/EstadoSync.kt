package com.entredos.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Estado(
    /** Correo de la cuenta del hogar conectada en este teléfono. */
    val correo: String? = null,
    val ultimaSyncOk: Long? = null,
    val ultimoError: String? = null,
    /** El permiso venció o se revocó: hay que volver a conectar la cuenta desde la app. */
    val requiereInicio: Boolean = false,
)

/** Estado de la sincronización, guardado solo en este teléfono. Lo muestra Ajustes. */
@Singleton
class EstadoSync @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("estado_sync", Context.MODE_PRIVATE)
    private val _estado = MutableStateFlow(leer())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    val correo: String? get() = _estado.value.correo

    fun guardarCorreo(correo: String) = editar { putString(CORREO, correo).putBoolean(REQUIERE, false) }
    fun registrarExito() = editar { putLong(ULTIMA, System.currentTimeMillis()).remove(ERROR).putBoolean(REQUIERE, false) }
    fun registrarError(mensaje: String?) = editar { putString(ERROR, mensaje ?: "Error desconocido") }
    fun marcarRequiereInicio() = editar { putBoolean(REQUIERE, true) }
    fun limpiar() = editar { clear() }

    private fun editar(cambio: android.content.SharedPreferences.Editor.() -> android.content.SharedPreferences.Editor) {
        prefs.edit().cambio().apply()
        _estado.value = leer()
    }

    private fun leer() = Estado(
        correo = prefs.getString(CORREO, null),
        ultimaSyncOk = prefs.getLong(ULTIMA, 0).takeIf { it > 0 },
        ultimoError = prefs.getString(ERROR, null),
        requiereInicio = prefs.getBoolean(REQUIERE, false),
    )

    private companion object {
        const val CORREO = "correo"; const val ULTIMA = "ultima"; const val ERROR = "error"; const val REQUIERE = "requiere"
    }
}

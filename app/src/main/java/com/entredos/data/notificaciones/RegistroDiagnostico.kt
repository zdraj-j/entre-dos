package com.entredos.data.notificaciones

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class NotificacionCapturada(val cuando: Long, val paquete: String, val titulo: String?, val texto: String?, val resultado: String)

/**
 * Ajustes › Diagnóstico › Notificaciones capturadas: las últimas 100, solo en memoria.
 * Nunca se guardan en la base ni se suben a Drive.
 */
@Singleton
class RegistroDiagnostico @Inject constructor() {
    private val _capturadas = MutableStateFlow<List<NotificacionCapturada>>(emptyList())
    val capturadas: StateFlow<List<NotificacionCapturada>> = _capturadas.asStateFlow()

    fun anotar(n: NotificacionCapturada) = _capturadas.update { (listOf(n) + it).take(100) }
    fun limpiar() { _capturadas.value = emptyList() }
}

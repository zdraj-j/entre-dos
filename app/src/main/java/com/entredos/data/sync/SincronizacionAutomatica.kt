package com.entredos.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.entredos.data.local.EntreDosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Se inicia en Application.onCreate. Pide sincronizar al volver la app a primer plano
 * y cada vez que aparecen cambios por subir (agrupados en 2 segundos).
 */
@Singleton
class SincronizacionAutomatica @Inject constructor(
    private val db: EntreDosDatabase,
    private val estado: EstadoSync,
    private val programador: ProgramadorSync,
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun iniciar() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (estado.correo != null) programador.pedirAhora()
            }
        })
        alcance.launch {
            db.cambioPendienteDao().contar()
                .distinctUntilChanged()
                .filter { it > 0 && estado.correo != null }
                .debounce(2_000)
                .collect { programador.pedirAhora() }
        }
        if (estado.correo != null) programador.iniciarPeriodica()
    }
}

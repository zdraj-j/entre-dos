package com.entredos.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Ajustes(
    /** "Elegir presupuesto siempre a mano": nada se clasifica solo. */
    val clasificacionManual: Boolean = false,
    /** "Leer otras apps (experimental)". */
    val lectorGenericoActivo: Boolean = false,
    /** Paquetes de las apps que el lector genérico vigila. */
    val appsGenericas: Set<String> = emptySet(),
    /** "Sincronizar por wifi en casa". */
    val wifiActivo: Boolean = false,
)

/** Ajustes que son solo de este teléfono (cada uno tiene sus apps instaladas). No se sincronizan. */
@Singleton
class AjustesLocales @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("ajustes_locales", Context.MODE_PRIVATE)
    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<Ajustes> = _ajustes.asStateFlow()
    val actual: Ajustes get() = _ajustes.value

    fun clasificacionManual(activo: Boolean) = guardar { putBoolean(MANUAL, activo) }
    fun lectorGenerico(activo: Boolean) = guardar { putBoolean(GENERICO, activo) }
    fun wifi(activo: Boolean) = guardar { putBoolean(WIFI, activo) }
    fun alternarApp(paquete: String) {
        val apps = actual.appsGenericas.toMutableSet().apply { if (!add(paquete)) remove(paquete) }
        guardar { putStringSet(APPS, apps) }
    }

    private fun guardar(cambio: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(cambio).apply()
        _ajustes.value = leer()
    }

    private fun leer() = Ajustes(
        clasificacionManual = prefs.getBoolean(MANUAL, false),
        lectorGenericoActivo = prefs.getBoolean(GENERICO, false),
        appsGenericas = prefs.getStringSet(APPS, emptySet())!!.toSet(),
        wifiActivo = prefs.getBoolean(WIFI, false),
    )

    private companion object {
        const val MANUAL = "clasificacion_manual"; const val GENERICO = "lector_generico"; const val APPS = "apps_genericas"; const val WIFI = "wifi"
    }
}

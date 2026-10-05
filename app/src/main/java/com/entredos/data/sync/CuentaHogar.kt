package com.entredos.data.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ResultadoConexion {
    /** La persona debe elegir la cuenta del hogar y aceptar el permiso: lanzar este PendingIntent. */
    data class NecesitaPantalla(val intent: PendingIntent) : ResultadoConexion
    data class Conectada(val token: String) : ResultadoConexion
}

/**
 * Permiso de Drive con Google Identity Services (no es Firebase). Pide solo `drive.appdata`:
 * la carpeta oculta de la app. Una vez concedido, los tokens se obtienen sin mostrar nada,
 * también desde el trabajo en segundo plano.
 */
@Singleton
class CuentaHogar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val estado: EstadoSync,
) : ProveedorToken {

    private val cliente = Identity.getAuthorizationClient(context)

    private fun solicitud(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(ALCANCE_DRIVE_APPDATA)))
            .apply { estado.correo?.let { setAccount(Account(it, "com.google")) } }
            .build()

    /** Paso 1 (desde la pantalla). */
    suspend fun conectar(): ResultadoConexion {
        val r = cliente.authorize(solicitud()).await()
        return if (r.hasResolution()) ResultadoConexion.NecesitaPantalla(r.pendingIntent!!)
        else ResultadoConexion.Conectada(r.accessToken!!)
    }

    /** Paso 2: con lo que devolvió la pantalla de Google. */
    fun completar(data: Intent?): String =
        cliente.getAuthorizationResultFromIntent(data).accessToken
            ?: error("Google no entregó el permiso de Drive")

    override suspend fun token(): String {
        val r = cliente.authorize(solicitud()).await()
        if (r.hasResolution()) {
            estado.marcarRequiereInicio()
            throw SinAutorizacion()
        }
        return r.accessToken ?: throw SinAutorizacion()
    }

    override suspend fun invalidar(token: String) = withContext(Dispatchers.IO) {
        GoogleAuthUtil.clearToken(context, token)
    }

    companion object {
        const val ALCANCE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}

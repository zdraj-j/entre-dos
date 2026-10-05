package com.entredos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.sync.EstadoSync
import com.entredos.ui.ajustes.PantallaAjustes
import com.entredos.ui.deteccion.PantallaDeteccion
import com.entredos.ui.deteccion.PantallaDiagnostico
import com.entredos.ui.deteccion.PantallaLectorGenerico
import com.entredos.ui.deudas.PantallaDeudas
import com.entredos.ui.hogar.PantallaInicioHogar
import com.entredos.ui.importacion.PantallaImportaciones
import com.entredos.ui.importacion.PantallaImportar
import com.entredos.ui.movimientos.DialogoEditarGasto
import com.entredos.ui.movimientos.PantallaMovimientos
import com.entredos.ui.pendientes.PantallaPendientes
import com.entredos.ui.pendientes.VentanaDesdeAviso
import com.entredos.ui.periodos.PantallaDetallePeriodo
import com.entredos.ui.periodos.PantallaPeriodos
import com.entredos.ui.permisos.PantallaPermisos
import com.entredos.ui.permisos.recordarPermisos
import com.entredos.ui.presupuesto.PantallaPresupuesto
import com.entredos.ui.recurrentes.PantallaRecurrentes
import com.entredos.ui.resumen.PantallaResumen
import com.entredos.ui.teclado.PantallaTeclado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface Arranque {
    data object Cargando : Arranque
    /** Sin cuenta del hogar conectada o sin titular elegido en este teléfono. */
    data object SinHogar : Arranque
    data class Listo(val pendientes: Int) : Arranque
}

@HiltViewModel
class AppViewModel @Inject constructor(db: EntreDosDatabase, estado: EstadoSync) : ViewModel() {
    val arranque = combine(estado.estado, db.dispositivoDao().observar(), db.gastoDao().contarPendientes()) { e, d, pendientes ->
        if (e.correo == null || d?.miembro == null) Arranque.SinHogar else Arranque.Listo(pendientes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Arranque.Cargando)
}

private data class Pestana(val ruta: String, val texto: String, val icono: ImageVector)

private val PESTANAS = listOf(
    Pestana("registrar", "Registrar", Icons.Filled.Calculate),
    Pestana("movimientos", "Movimientos", Icons.AutoMirrored.Filled.List),
    Pestana("presupuesto", "Presupuesto", Icons.Filled.PieChart),
    Pestana("resumen", "Resumen", Icons.Filled.BarChart),
    Pestana("ajustes", "Ajustes", Icons.Filled.Settings),
)

@Composable
fun AppEntreDos(gastoDesdeAviso: String?, alCerrarAviso: () -> Unit, vm: AppViewModel = hiltViewModel()) {
    val arranque by vm.arranque.collectAsState()
    val permisos = recordarPermisos()
    var permisosVistos by rememberSaveable { mutableStateOf(false) }
    var reconectando by rememberSaveable { mutableStateOf(false) }

    when {
        !permisos.notificaciones || (!permisosVistos && (!permisos.bateria || !permisos.avisos)) ->
            PantallaPermisos(permisos) { permisosVistos = true }
        arranque == Arranque.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        arranque == Arranque.SinHogar || reconectando -> PantallaInicioHogar(alTerminar = { reconectando = false })
        else -> {
            Principal((arranque as Arranque.Listo).pendientes, alReconectar = { reconectando = true })
            if (gastoDesdeAviso != null) VentanaDesdeAviso(gastoDesdeAviso, alTerminar = alCerrarAviso)
        }
    }
}

@Composable
private fun Principal(pendientes: Int, alReconectar: () -> Unit) {
    val nav = rememberNavController()
    val actual = nav.currentBackStackEntryAsState().value?.destination?.route?.substringBefore('?')
    var editando by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            if (actual in PESTANAS.map { it.ruta }) NavigationBar {
                PESTANAS.forEach { p ->
                    NavigationBarItem(
                        selected = actual == p.ruta,
                        onClick = { nav.irAPestana(p.ruta) },
                        icon = { Icon(p.icono, contentDescription = null) },
                        label = { Text(p.texto) },
                    )
                }
            }
        },
    ) { margen ->
        NavHost(nav, startDestination = "registrar", modifier = Modifier.padding(margen)) {
            composable("registrar") { PantallaTeclado(alAbrirPendientes = { nav.navigate("pendientes") }) }
            composable(
                "movimientos?categoriaId={categoriaId}&todos={todos}",
                arguments = listOf(
                    navArgument("categoriaId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("todos") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { PantallaMovimientos(alAbrir = { editando = it }, alPendientes = { nav.navigate("pendientes") }, pendientes = pendientes) }
            composable("presupuesto") { PantallaPresupuesto(alVerCategoria = { nav.verMovimientos(it) }) }
            composable("resumen") {
                PantallaResumen(
                    alVerGastos = { nav.verMovimientos(it) },
                    alDeudas = { nav.navigate("deudas") },
                    alRecurrentes = { nav.navigate("recurrentes") }, alDeteccion = { nav.navigate("deteccion") },
                    alPeriodos = { nav.navigate("periodos") }, alVerPeriodo = { nav.navigate("periodo/$it") },
                )
            }
            composable("ajustes") {
                PantallaAjustes(
                    alImportaciones = { nav.navigate("importaciones") }, alDeteccion = { nav.navigate("deteccion") },
                    alDiagnostico = { nav.navigate("diagnostico") }, alReconectar = alReconectar, alSalir = {},
                )
            }
            composable("pendientes") { PantallaPendientes() }
            composable("deudas") { PantallaDeudas(alVolver = { nav.popBackStack() }) }
            composable("recurrentes") { PantallaRecurrentes(alVolver = { nav.popBackStack() }) }
            composable("deteccion") { PantallaDeteccion(alVolver = { nav.popBackStack() }, alLectorGenerico = { nav.navigate("lectorGenerico") }) }
            composable("lectorGenerico") { PantallaLectorGenerico() }
            composable("diagnostico") { PantallaDiagnostico(alVolver = { nav.popBackStack() }) }
            composable("periodos") { PantallaPeriodos(alVolver = { nav.popBackStack() }, alAbrir = { nav.navigate("periodo/$it") }) }
            composable("periodo/{id}") { entrada -> PantallaDetallePeriodo(entrada.arguments?.getString("id")!!, alVolver = { nav.popBackStack() }) }
            composable("importaciones") { PantallaImportaciones(alImportar = { nav.navigate("importar") }) }
            composable("importar") {
                PantallaImportar(alTerminar = { nav.popBackStack() }, alVerMovimientos = { nav.navigate("movimientos?todos=true") { popUpTo("ajustes") } })
            }
        }
    }
    editando?.let { DialogoEditarGasto(it, alCerrar = { editando = null }) }
}

private fun NavHostController.irAPestana(ruta: String) = navigate(ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.verMovimientos(categoriaId: String?) =
    navigate(if (categoriaId == null) "movimientos" else "movimientos?categoriaId=$categoriaId")

package com.entredos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.entredos.data.local.EntreDosDatabase
import com.entredos.data.sync.EstadoSync
import com.entredos.ui.ajustes.PantallaAjustes
import com.entredos.ui.anotar.PantallaAnotar
import com.entredos.ui.deteccion.PantallaDeteccion
import com.entredos.ui.deteccion.PantallaDiagnostico
import com.entredos.ui.deteccion.PantallaLectorGenerico
import com.entredos.ui.gastos.PantallaGastos
import com.entredos.ui.gastos.VentanaDesdeAviso
import com.entredos.ui.hogar.PantallaCategorias
import com.entredos.ui.hogar.PantallaHogar
import com.entredos.ui.hogar.PantallaInicioHogar
import com.entredos.ui.importacion.PantallaImportaciones
import com.entredos.ui.importacion.PantallaImportar
import com.entredos.ui.mes.PantallaDeudasMes
import com.entredos.ui.mes.PantallaMes
import com.entredos.ui.movimientos.DialogoEditarGasto
import com.entredos.ui.periodos.PantallaDetallePeriodo
import com.entredos.ui.periodos.PantallaPeriodos
import com.entredos.ui.permisos.PantallaPermisos
import com.entredos.ui.permisos.recordarPermisos
import com.entredos.ui.recurrentes.PantallaRecurrentes
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

/** Las cuatro pestañas. Cada una es un grafo con sus vistas internas, así la barra marca la pestaña correcta. */
private enum class Pestana(val ruta: String, val inicio: String, val texto: String, val icono: ImageVector) {
    ANOTAR("pestana_anotar", "anotar", "Anotar", Icons.Filled.Edit),
    GASTOS("pestana_gastos", "gastos?categoriaId={categoriaId}&todos={todos}", "Gastos", Icons.AutoMirrored.Filled.ReceiptLong),
    MES("pestana_mes", "mes", "Mes", Icons.Filled.DonutLarge),
    HOGAR("pestana_hogar", "hogar", "Hogar", Icons.Filled.Home),
}

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
    val destino = nav.currentBackStackEntryAsState().value?.destination
    var editando by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(
                        selected = destino?.hierarchy?.any { it.route == p.ruta } == true,
                        onClick = { nav.irAPestana(p.ruta) },
                        icon = {
                            if (p == Pestana.GASTOS && pendientes > 0) BadgedBox(badge = { Badge { Text("$pendientes") } }) { Icon(p.icono, contentDescription = null) }
                            else Icon(p.icono, contentDescription = null)
                        },
                        label = { Text(p.texto) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { margen ->
        NavHost(nav, startDestination = Pestana.ANOTAR.ruta, modifier = Modifier.padding(margen)) {
            navigation(startDestination = Pestana.ANOTAR.inicio, route = Pestana.ANOTAR.ruta) {
                composable("anotar") { PantallaAnotar() }
            }
            navigation(startDestination = Pestana.GASTOS.inicio, route = Pestana.GASTOS.ruta) {
                composable(
                    Pestana.GASTOS.inicio,
                    arguments = listOf(
                        navArgument("categoriaId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("todos") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { PantallaGastos(alEditar = { editando = it }) }
            }
            navigation(startDestination = Pestana.MES.inicio, route = Pestana.MES.ruta) {
                composable("mes") {
                    PantallaMes(
                        alVerCategoria = { nav.navigate("gastos?categoriaId=$it") },
                        alDeudas = { nav.navigate("mes/deudas") },
                        alCategorias = { nav.navigate("hogar/categorias") },
                        alVerPeriodo = { nav.navigate("hogar/meses/$it") },
                    )
                }
                composable("mes/deudas") { PantallaDeudasMes(alVolver = { nav.popBackStack() }) }
            }
            navigation(startDestination = Pestana.HOGAR.inicio, route = Pestana.HOGAR.ruta) {
                hogar(nav, alReconectar)
            }
        }
    }
    editando?.let { DialogoEditarGasto(it, alCerrar = { editando = null }) }
}

private fun NavGraphBuilder.hogar(nav: NavHostController, alReconectar: () -> Unit) {
    val volver: () -> Unit = { nav.popBackStack() }
    composable("hogar") {
        PantallaHogar(
            alCategorias = { nav.navigate("hogar/categorias") }, alFijos = { nav.navigate("hogar/fijos") },
            alMeses = { nav.navigate("hogar/meses") }, alAjustes = { nav.navigate("hogar/ajustes") },
        )
    }
    composable("hogar/categorias") { PantallaCategorias(alVolver = volver) }
    composable("hogar/fijos") { PantallaRecurrentes(alVolver = volver) }
    composable("hogar/meses") { PantallaPeriodos(alVolver = volver, alAbrir = { nav.navigate("hogar/meses/$it") }) }
    composable("hogar/meses/{id}") { entrada -> PantallaDetallePeriodo(entrada.arguments?.getString("id")!!, alVolver = volver) }
    composable("hogar/ajustes") {
        PantallaAjustes(
            alVolver = volver, alReglas = { nav.navigate("hogar/ajustes/reglas") }, alOtrasApps = { nav.navigate("hogar/ajustes/otras-apps") },
            alImportar = { nav.navigate("hogar/ajustes/importaciones") }, alDiagnostico = { nav.navigate("hogar/ajustes/diagnostico") },
            alReconectar = alReconectar,
        )
    }
    composable("hogar/ajustes/reglas") { PantallaDeteccion(alVolver = volver, alLectorGenerico = { nav.navigate("hogar/ajustes/otras-apps") }) }
    composable("hogar/ajustes/otras-apps") { PantallaLectorGenerico() }
    composable("hogar/ajustes/diagnostico") { PantallaDiagnostico(alVolver = volver) }
    composable("hogar/ajustes/importaciones") { PantallaImportaciones(alImportar = { nav.navigate("hogar/ajustes/importar") }) }
    composable("hogar/ajustes/importar") {
        PantallaImportar(alTerminar = volver, alVerMovimientos = { nav.irAPestana(Pestana.GASTOS.ruta) })
    }
}

private fun NavHostController.irAPestana(ruta: String) = navigate(ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

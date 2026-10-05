package com.entredos.ui.gastos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.data.local.entity.GastoEntity

/**
 * Al tocar el cuerpo del aviso "Gasto detectado": la hoja de categorías, que no se puede cerrar
 * sin decidir. Si el gasto ya se clasificó (desde un botón del aviso o en el otro teléfono), no muestra nada.
 */
@Composable
fun VentanaDesdeAviso(gastoId: String, alTerminar: () -> Unit, vm: GastosViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsState()
    var gasto by remember { mutableStateOf<GastoEntity?>(null) }
    LaunchedEffect(gastoId) { gasto = vm.cargarPendiente(gastoId) ?: run { alTerminar(); null } }
    gasto?.let { g ->
        HojaCategoria(
            gasto = g, categorias = e.categorias, obligatoria = true,
            alGuardar = { vm.confirmarVarias(g, it); alTerminar() },
            alTransferencia = { vm.transferencia(g); alTerminar() },
            alNoEsGasto = { vm.noEsGasto(g); alTerminar() },
            alCerrar = alTerminar,
        )
    }
}

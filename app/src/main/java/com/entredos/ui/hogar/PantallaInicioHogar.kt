package com.entredos.ui.hogar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.Miembro

/**
 * Primer uso y recuperación. Funcional y sin estilo: el diseño final llega en la entrega 6.
 * [alTerminar] lleva a la pantalla del teclado.
 */
@Composable
fun PantallaInicioHogar(alTerminar: () -> Unit, vm: InicioHogarViewModel = hiltViewModel()) {
    val paso by vm.paso.collectAsState()
    val google = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.volvioDeGoogle(r.data) else vm.googleCancelado()
    }

    LaunchedEffect(paso) {
        when (val p = paso) {
            is PasoInicio.AbrirGoogle -> google.launch(IntentSenderRequest.Builder(p.intent.intentSender).build())
            PasoInicio.Listo -> alTerminar()
            else -> Unit
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (val p = paso) {
            PasoInicio.Bienvenida, is PasoInicio.AbrirGoogle -> {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                    LogoEntreDos()
                    Text("Entre dos", style = MaterialTheme.typography.displaySmall)
                    Text("El presupuesto de la casa, claro para los dos.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PuntoBienvenida("Los gastos del banco se anotan solos")
                    PuntoBienvenida("Saben cuánto les queda en el mes")
                    PuntoBienvenida("Saben quién le debe a quién")
                }
                Button(onClick = vm::conectar, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Conectar la cuenta del hogar") }
                Text("Es una cuenta de Google que comparten los dos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            is PasoInicio.Cargando -> {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Text(p.mensaje, Modifier.align(Alignment.CenterHorizontally))
            }
            PasoInicio.CrearHogar -> FormularioCrear(vm::crearHogar)
            is PasoInicio.ElegirQuienSoy -> {
                Text(p.nombreHogar, style = MaterialTheme.typography.headlineMedium)
                Text("¿Quién eres? Así recuperas tu lugar y todos tus datos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                p.miembros.forEach { m ->
                    Button(onClick = { vm.soy(Miembro.valueOf(m.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Soy ${m.nombre}") }
                }
                if (p.puedeUnirseComoB) FormularioUnirse(vm::unirmeComoB)
            }
            is PasoInicio.Error -> {
                Text(p.mensaje, color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::conectar) { Text("Intentar de nuevo") }
            }
            PasoInicio.Listo -> Unit
        }
    }
}

@Composable
private fun FormularioCrear(crear: (String, String, Int) -> Unit) {
    var hogar by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("1") }
    val diaValido = dia.toIntOrNull()?.let { it in 1..28 } == true
    Text("Crear el hogar", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(nombre, { nombre = it }, label = { Text("Tu nombre") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(hogar, { hogar = it }, label = { Text("Nombre del hogar (opcional)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        dia, { dia = it.filter(Char::isDigit).take(2) }, label = { Text("Día de inicio del periodo (1 a 28)") },
        isError = !diaValido, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { crear(hogar, nombre, dia.toInt()) }, enabled = nombre.isNotBlank() && diaValido, modifier = Modifier.fillMaxWidth()) {
        Text("Crear hogar")
    }
}

@Composable
private fun FormularioUnirse(unirse: (String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    Text("¿Es tu primera vez? Únete como segundo titular.")
    OutlinedTextField(nombre, { nombre = it }, label = { Text("Tu nombre") }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { unirse(nombre) }, enabled = nombre.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Unirme al hogar")
    }
}


/** Dos círculos que se cruzan: dos personas, una cuenta. */
@Composable
private fun LogoEntreDos() {
    val acento = MaterialTheme.colorScheme.primary
    val relleno = MaterialTheme.colorScheme.primaryContainer
    androidx.compose.foundation.Canvas(Modifier.size(width = 120.dp, height = 84.dp)) {
        val r = size.height / 2 - 3.dp.toPx()
        val izquierda = androidx.compose.ui.geometry.Offset(size.width / 2 - r / 2, size.height / 2)
        val derecha = androidx.compose.ui.geometry.Offset(size.width / 2 + r / 2, size.height / 2)
        drawCircle(relleno, r, izquierda); drawCircle(relleno, r, derecha)
        val trazo = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx())
        drawCircle(acento, r, izquierda, style = trazo); drawCircle(acento, r, derecha, style = trazo)
    }
}

@Composable
private fun PuntoBienvenida(texto: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

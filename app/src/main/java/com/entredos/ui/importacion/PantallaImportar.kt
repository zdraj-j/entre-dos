package com.entredos.ui.importacion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.entredos.core.FormatoPesos
import com.entredos.core.Miembro
import com.entredos.core.Responsable
import com.entredos.core.importacion.DestinoCategoria
import com.entredos.core.importacion.SignoGastos
import com.entredos.core.importacion.Valores
import com.entredos.data.importacion.OpcionesImportacion
import com.entredos.data.importacion.nombreTitular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Ajustes › Importar datos. [alTerminar] vuelve a Ajustes; [alVerMovimientos] abre Movimientos en "Todos". */
@Composable
fun PantallaImportar(alTerminar: () -> Unit, alVerMovimientos: () -> Unit, vm: ImportarViewModel = hiltViewModel()) {
    val paso by vm.paso.collectAsState()
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::elegir) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Importar datos", style = MaterialTheme.typography.headlineSmall)
        when (val p = paso) {
            PasoImportar.Elegir -> {
                Text("Elige el archivo .zip exportado para Entre dos, o un CSV de otra app de gastos. Antes de guardar nada verás una vista previa.")
                Button(onClick = { elegir.launch(arrayOf("application/zip", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }) {
                    Text("Elegir archivo")
                }
            }
            is PasoImportar.Cargando -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(); Text(p.mensaje)
            }
            is PasoImportar.Columnas -> PasoColumnas(p, vm::confirmarColumnas)
            is PasoImportar.Personas -> PasoPersonas(p, vm, vm::confirmarPersonas)
            is PasoImportar.Categorias -> PasoCategorias(p, vm, vm::confirmarCategorias)
            is PasoImportar.Revisar -> PasoRevisar(p, vm)
            is PasoImportar.Listo -> {
                val r = p.resultado
                Text("Listo", style = MaterialTheme.typography.titleLarge)
                Text("Se importaron ${r.lote.gastos} gastos, ${r.lote.categorias} categorías nuevas, ${r.lote.deudas} deudas y ${r.lote.metas} metas. " +
                    "También se crearon ${r.periodos} periodos anteriores y ${r.reglas} reglas de comercio.")
                Text("Si algo no quedó bien, puedes deshacer esta importación desde Ajustes › Importaciones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = alVerMovimientos) { Text("Ver en Movimientos") }
                TextButton(onClick = alTerminar) { Text("Cerrar") }
            }
            is PasoImportar.Error -> {
                Text(p.mensaje, color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::reiniciar) { Text("Elegir otro archivo") }
            }
        }
    }
}

@Composable
private fun <T> Selector(etiqueta: String, opciones: List<Pair<String, T>>, actual: T, alElegir: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
                Text(opciones.firstOrNull { it.second == actual }?.first ?: "Elegir")
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { (texto, valor) -> DropdownMenuItem(text = { Text(texto) }, onClick = { alElegir(valor); abierto = false }) }
            }
        }
    }
}

@Composable
private fun PasoColumnas(p: PasoImportar.Columnas, confirmar: (PasoImportar.Columnas) -> Unit) {
    var mapeo by remember { mutableStateOf(p.mapeo) }
    var formato by remember { mutableStateOf(p.formatoFecha) }
    var signo by remember { mutableStateOf(p.signo) }
    val columnas = p.tabla.encabezadosOriginales.mapIndexed { i, n -> n.ifBlank { "Columna ${i + 1}" } to i }
    val opcionales = listOf<Pair<String, Int?>>("No viene" to null) + columnas

    Text("Paso 1 de 4 · Columnas", style = MaterialTheme.typography.titleMedium)
    Text("Este archivo es de otra app. Indica qué columna es cada dato.")
    Selector("Fecha", columnas, mapeo.fecha) { mapeo = mapeo.copy(fecha = it) }
    Selector("Formato de la fecha", Valores.FORMATOS_FECHA.map { it to it }, formato) { formato = it }
    Selector("Monto", columnas, mapeo.monto) { mapeo = mapeo.copy(monto = it) }
    Selector("Categoría", opcionales, mapeo.categoria) { mapeo = mapeo.copy(categoria = it) }
    Selector("Quién pagó", opcionales, mapeo.pagadoPor) { mapeo = mapeo.copy(pagadoPor = it) }
    Selector("Descripción", opcionales, mapeo.descripcion) { mapeo = mapeo.copy(descripcion = it) }
    Selector("Comercio", opcionales, mapeo.comercio) { mapeo = mapeo.copy(comercio = it) }
    Selector("Tipo (gasto, ingreso, transferencia)", opcionales, mapeo.tipo) { mapeo = mapeo.copy(tipo = it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Los gastos vienen en negativo", Modifier.weight(1f))
        Switch(signo == SignoGastos.NEGATIVOS, { signo = if (it) SignoGastos.NEGATIVOS else SignoGastos.POSITIVOS })
    }
    Text("Primeras filas:", style = MaterialTheme.typography.labelLarge)
    p.tabla.filas.take(3).forEach { f ->
        val valor = { i: Int? -> i?.let { f.valores.getOrNull(it) } ?: "—" }
        Text("${valor(mapeo.fecha)} · ${valor(mapeo.monto)} · ${valor(mapeo.categoria)} · ${valor(mapeo.descripcion)}", style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = { confirmar(p.copy(mapeo = mapeo, formatoFecha = formato, signo = signo)) }) { Text("Siguiente") }
}

@Composable
private fun PasoPersonas(p: PasoImportar.Personas, vm: ImportarViewModel, confirmar: (Map<String, Miembro>) -> Unit) {
    val asignacion = remember { mutableStateMapOf<String, Miembro>().apply { putAll(p.asignacion) } }
    Text("Paso 2 de 4 · Personas", style = MaterialTheme.typography.titleMedium)
    Text("¿A qué titular corresponde cada nombre del archivo?")
    p.nombres.forEach { nombre ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(nombre, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Miembro.entries.forEach { m ->
                    FilterChip(asignacion[nombre] == m, { asignacion[nombre] = m }, { Text(nombreTitular(vm.ctx.miembros, m)) })
                }
            }
        }
    }
    Button(onClick = { confirmar(asignacion.toMap()) }, enabled = p.nombres.all { it in asignacion }) { Text("Siguiente") }
}

@Composable
private fun PasoCategorias(p: PasoImportar.Categorias, vm: ImportarViewModel, confirmar: (Map<String, DestinoCategoria>) -> Unit) {
    val destinos = remember { mutableStateMapOf<String, DestinoCategoria>().apply { putAll(p.destinos) } }
    val miembros = vm.ctx.miembros
    Text("Paso 3 de 4 · Categorías", style = MaterialTheme.typography.titleMedium)
    Text("Enlaza cada categoría del archivo con una tuya o créala. Las que coinciden por nombre ya están enlazadas.")
    p.nombres.forEach { nombre ->
        val actual = destinos[nombre]
        val opciones = listOf<Pair<String, String?>>("Crear nueva" to null) + vm.ctx.categorias.map { it.nombre to it.id }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Selector(nombre, opciones, (actual as? DestinoCategoria.Existente)?.id) { id ->
                destinos[nombre] = if (id != null) DestinoCategoria.Existente(id)
                else (p.destinos[nombre] as? DestinoCategoria.Nueva) ?: DestinoCategoria.Nueva(nombre, Responsable.COMPARTIDA)
            }
            if (actual is DestinoCategoria.Nueva) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Responsable.COMPARTIDA to "Compartida", Responsable.A to nombreTitular(miembros, Miembro.A), Responsable.B to nombreTitular(miembros, Miembro.B))
                        .forEach { (r, texto) -> FilterChip(actual.responsable == r, { destinos[nombre] = actual.copy(responsable = r) }, { Text(texto) }) }
                }
            }
        }
        HorizontalDivider()
    }
    Button(onClick = { confirmar(destinos.toMap()) }) { Text("Ver vista previa") }
}

@Composable
private fun PasoRevisar(p: PasoImportar.Revisar, vm: ImportarViewModel) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val guardarErrores = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        alcance.launch(Dispatchers.IO) {
            contexto.contentResolver.openOutputStream(uri)?.use { it.write(vm.reporteErrores(p.errores).toByteArray()) }
        }
    }
    val plan = p.plan
    Text("Paso 4 de 4 · Vista previa", style = MaterialTheme.typography.titleMedium)
    Text("Gastos que entran: ${plan.gastos.size} (${FormatoPesos.pesos(plan.gastos.filter { !it.transferencia }.sumOf { it.monto })})", fontWeight = FontWeight.SemiBold)
    Text("Ya importados antes (se omiten): ${plan.duplicados}")
    Text("Posibles duplicados sin id: ${plan.posiblesDuplicados}")
    if (p.omitidas > 0) Text("Ingresos o filas en cero omitidos: ${p.omitidas}")
    Text("Categorías nuevas: ${plan.categoriasNuevas.size} · Deudas abiertas: ${plan.deudas.size} · Metas: ${plan.metas.size}")
    Text("Periodos anteriores que se crearían: ${p.periodosPrevistos}")
    Text("Los gastos importados no generan deudas: lo pendiente entra solo por las deudas abiertas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (p.errores.isNotEmpty()) {
        Text("Filas con error: ${p.errores.size}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        p.errores.take(20).forEach { Text("${it.archivo}, línea ${it.linea}: ${it.motivo}", style = MaterialTheme.typography.bodySmall) }
        if (p.errores.size > 20) Text("… y ${p.errores.size - 20} más")
        OutlinedButton(onClick = { guardarErrores.launch("errores-importacion.csv") }) { Text("Descargar errores") }
    }

    Opcion("Omitir posibles duplicados", p.omitirPosibles) { vm.cambiarOpciones(p.opciones, it) }
    Opcion("Crear periodos anteriores", p.opciones.crearPeriodos) { vm.cambiarOpciones(p.opciones.copy(crearPeriodos = it), p.omitirPosibles) }
    Opcion("Aprender reglas de comercio", p.opciones.aprenderReglas) { vm.cambiarOpciones(p.opciones.copy(aprenderReglas = it), p.omitirPosibles) }

    val hayAlgo = plan.gastos.isNotEmpty() || plan.deudas.isNotEmpty() || plan.metas.isNotEmpty()
    Button(onClick = { vm.importar(p) }, enabled = hayAlgo) { Text(if (hayAlgo) "Importar" else "No hay nada nuevo para importar") }
    TextButton(onClick = vm::reiniciar) { Text("Elegir otro archivo") }
}

@Composable
private fun Opcion(texto: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f)); Switch(activo, alCambiar)
    }
}

package com.entredos.ui.importacion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entredos.core.Miembro
import com.entredos.core.Responsable
import com.entredos.core.importacion.DestinoCategoria
import com.entredos.core.importacion.EntradaPlan
import com.entredos.core.importacion.ErrorFila
import com.entredos.core.importacion.FilaCategoria
import com.entredos.core.importacion.FilaDeuda
import com.entredos.core.importacion.FilaGasto
import com.entredos.core.importacion.FormatoEntreDos
import com.entredos.core.importacion.FormatoGenerico
import com.entredos.core.importacion.LectorCsv
import com.entredos.core.importacion.MapeoColumnas
import com.entredos.core.importacion.PlanImportacion
import com.entredos.core.importacion.PlanImportador
import com.entredos.core.importacion.SignoGastos
import com.entredos.core.importacion.TablaCsv
import com.entredos.core.importacion.Valores
import com.entredos.data.importacion.ArchivosImportacion
import com.entredos.data.importacion.ContextoImportacion
import com.entredos.data.importacion.ImportadorRepositorio
import com.entredos.data.importacion.LectorArchivo
import com.entredos.data.importacion.OpcionesImportacion
import com.entredos.data.importacion.ResultadoImportacion
import com.entredos.data.local.DispositivoActual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import javax.inject.Inject

sealed interface PasoImportar {
    data object Elegir : PasoImportar
    data class Cargando(val mensaje: String) : PasoImportar
    data class Columnas(val tabla: TablaCsv, val mapeo: MapeoColumnas, val formatoFecha: String, val signo: SignoGastos) : PasoImportar
    data class Personas(val nombres: List<String>, val asignacion: Map<String, Miembro>) : PasoImportar
    data class Categorias(val nombres: List<String>, val destinos: Map<String, DestinoCategoria>) : PasoImportar
    data class Revisar(val plan: PlanImportacion, val errores: List<ErrorFila>, val omitidas: Int, val periodosPrevistos: Int, val opciones: OpcionesImportacion, val omitirPosibles: Boolean) : PasoImportar
    data class Listo(val resultado: ResultadoImportacion) : PasoImportar
    data class Error(val mensaje: String) : PasoImportar
}

/** Asistente: elegir → columnas (solo otras apps) → personas → categorías → revisar → importar. */
@HiltViewModel
class ImportarViewModel @Inject constructor(
    private val lector: LectorArchivo,
    private val repo: ImportadorRepositorio,
    private val dispositivo: DispositivoActual,
) : ViewModel() {

    private val zona = ZoneId.systemDefault()
    private val _paso = MutableStateFlow<PasoImportar>(PasoImportar.Elegir)
    val paso: StateFlow<PasoImportar> = _paso.asStateFlow()

    lateinit var ctx: ContextoImportacion
        private set
    private lateinit var archivos: ArchivosImportacion
    private var filasGastos: List<FilaGasto> = emptyList()
    private var erroresLectura: List<ErrorFila> = emptyList()
    private var omitidas = 0
    private var categoriasCsv: List<FilaCategoria> = emptyList()
    private var deudas: List<FilaDeuda> = emptyList()
    private var personas: Map<String, Miembro> = emptyMap()
    private var destinos: Map<String, DestinoCategoria> = emptyMap()

    fun elegir(uri: Uri) = trabajar("Leyendo el archivo…") {
        ctx = repo.contexto()
        archivos = lector.leer(uri)
        archivos.manifiesto?.let { revisarVersion(it) }
        val tabla = withContext(Dispatchers.Default) { LectorCsv.leer(archivos.gastos!!) }
        if (tabla.filas.isEmpty()) error("El archivo no tiene filas de gastos")

        val errores = mutableListOf<ErrorFila>()
        archivos.categorias?.let { FormatoEntreDos.categorias(LectorCsv.leer(it)) }?.also { categoriasCsv = it.filas; errores += it.errores }
        archivos.deudas?.let { FormatoEntreDos.deudas(LectorCsv.leer(it), zona) }?.also { deudas = it.filas; errores += it.errores }
        erroresLectura = errores

        if (FormatoEntreDos.esFormatoPropio(tabla)) {
            val leido = FormatoEntreDos.gastos(tabla, zona)
            filasGastos = leido.filas; erroresLectura = leido.errores + erroresLectura
            irAPersonas()
        } else {
            val mapeo = FormatoGenerico.sugerirMapeo(tabla) ?: MapeoColumnas(fecha = 0, monto = minOf(1, tabla.encabezados.size - 1))
            val formato = Valores.detectarFormatoFecha(tabla.filas.mapNotNull { it.valores.getOrNull(mapeo.fecha) }, zona) ?: Valores.FORMATOS_FECHA.first()
            _paso.value = PasoImportar.Columnas(tabla, mapeo, formato, FormatoGenerico.detectarSigno(tabla, mapeo.monto))
        }
    }

    fun confirmarColumnas(p: PasoImportar.Columnas) = trabajar("Leyendo los gastos…") {
        val leido = withContext(Dispatchers.Default) { FormatoGenerico.gastos(p.tabla, p.mapeo, p.formatoFecha, p.signo, zona) }
        filasGastos = leido.filas; erroresLectura = leido.errores + erroresLectura; omitidas = leido.omitidas
        if (filasGastos.isEmpty()) error("Con esas columnas no se pudo leer ningún gasto. Revisa la fecha y el monto.")
        irAPersonas()
    }

    fun confirmarPersonas(asignacion: Map<String, Miembro>) {
        personas = asignacion.mapKeys { PlanImportador.clave(it.key) }
        val nombres = filasGastos.map { it.categoria }.distinctBy(PlanImportador::clave)
        val sugeridos = nombres.associateWith { nombre ->
            val k = PlanImportador.clave(nombre)
            ctx.categorias.firstOrNull { PlanImportador.clave(it.nombre) == k }?.let { DestinoCategoria.Existente(it.id) }
                ?: categoriasCsv.firstOrNull { PlanImportador.clave(it.nombre) == k }?.let { c ->
                    val resp = if (PlanImportador.clave(c.responsable) == "compartida") Responsable.COMPARTIDA
                    else when (personas[PlanImportador.clave(c.responsable)]) { Miembro.A -> Responsable.A; Miembro.B -> Responsable.B; null -> Responsable.COMPARTIDA }
                    DestinoCategoria.Nueva(c.nombre, resp, c.presupuesto, c.color, c.destacada)
                }
                ?: DestinoCategoria.Nueva(nombre, Responsable.COMPARTIDA)
        }
        _paso.value = PasoImportar.Categorias(nombres, sugeridos)
    }

    fun confirmarCategorias(asignacion: Map<String, DestinoCategoria>) = trabajar("Preparando la vista previa…") {
        destinos = asignacion.mapKeys { PlanImportador.clave(it.key) }
        revisar(OpcionesImportacion(), omitirPosibles = true)
    }

    fun cambiarOpciones(opciones: OpcionesImportacion, omitirPosibles: Boolean) = trabajar("Actualizando…") { revisar(opciones, omitirPosibles) }

    fun importar(p: PasoImportar.Revisar) = trabajar("Importando ${p.plan.gastos.size} gastos…") {
        val r = repo.aplicar(p.plan, destinos, p.opciones, archivos.nombre, ctx)
        _paso.value = PasoImportar.Listo(r)
    }

    fun reiniciar() { _paso.value = PasoImportar.Elegir }

    /** CSV con las filas que no se pudieron importar, para revisarlas en la otra app. */
    fun reporteErrores(errores: List<ErrorFila>): String =
        "archivo,linea,motivo\n" + errores.joinToString("\n") { e -> listOf(e.archivo, e.linea.toString(), e.motivo).joinToString(",") { campo(it) } }

    private fun campo(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private suspend fun irAPersonas() {
        val yo = dispositivo.miembro()
        val nombres = (filasGastos.map { it.pagadoPor } + deudas.flatMap { listOf(it.deudor, it.acreedor) } +
            categoriasCsv.map { it.responsable }.filter { PlanImportador.clave(it) != "compartida" })
            .distinctBy(PlanImportador::clave)
        val sugeridos = nombres.mapNotNull { n ->
            val k = PlanImportador.clave(n)
            val m = ctx.miembros.firstOrNull { PlanImportador.clave(it.nombre) == k }?.let { Miembro.valueOf(it.id) }
                ?: if (n == FormatoGenerico.SIN_PERSONA) yo else null
            m?.let { n to it }
        }.toMap()
        _paso.value = PasoImportar.Personas(nombres, sugeridos)
    }

    private suspend fun revisar(opciones: OpcionesImportacion, omitirPosibles: Boolean) {
        val (plan, errores, periodos) = withContext(Dispatchers.Default) {
            val agrupado = PlanImportador.agrupar(filasGastos)
            val plan = PlanImportador.armar(
                EntradaPlan(
                    gastos = agrupado.filas, deudas = deudas, personas = personas, categorias = destinos,
                    idsOrigenGastos = ctx.idsOrigenGastos, idsOrigenDeudas = ctx.idsOrigenDeudas,
                    huellasExistentes = ctx.huellas, omitirPosiblesDuplicados = omitirPosibles, zona = zona,
                )
            )
            val periodos = ctx.hogar?.let {
                PlanImportador.periodosHistoricos(plan.gastos, it.diaInicio, ctx.inicioPeriodoActual, ctx.rangosCerrados, zona).size
            } ?: 0
            Triple(plan, erroresLectura + agrupado.errores + plan.errores, periodos)
        }
        _paso.value = PasoImportar.Revisar(plan, errores, omitidas, periodos, opciones, omitirPosibles)
    }

    private fun revisarVersion(manifiesto: String) {
        val version = runCatching { Json.parseToJsonElement(manifiesto).jsonObject["version"]?.jsonPrimitive?.intOrNull }.getOrNull()
        if (version != null && version > FormatoEntreDos.VERSION) {
            error("El archivo usa el formato versión $version y esta app lee hasta la ${FormatoEntreDos.VERSION}. Actualiza Entre dos.")
        }
    }

    private fun trabajar(mensaje: String, bloque: suspend () -> Unit) {
        _paso.value = PasoImportar.Cargando(mensaje)
        viewModelScope.launch {
            try { bloque() } catch (e: Exception) { _paso.value = PasoImportar.Error(e.message ?: "No se pudo importar") }
        }
    }
}

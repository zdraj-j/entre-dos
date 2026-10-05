package com.entredos.core

import com.entredos.core.exportacion.CategoriaExport
import com.entredos.core.exportacion.DeudaExport
import com.entredos.core.exportacion.ExportadorEntreDos
import com.entredos.core.exportacion.GastoExport
import com.entredos.core.exportacion.ParteExport
import com.entredos.core.importacion.FormatoEntreDos
import com.entredos.core.importacion.LectorCsv
import com.entredos.core.importacion.PlanImportador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EstadisticasYExportacionTest {
    private val bogota = ZoneId.of("America/Bogota")
    private fun ms(m: Int, d: Int) = LocalDate.of(2026, m, d).atStartOfDay(bogota).toInstant().toEpochMilli()

    @Test fun estadisticas() {
        val r = Estadisticas.calcular(listOf(
            GastoResumen(85_000, Miembro.A, "Éxito", listOf("mercado" to 60_000L, "aseo" to 25_000L)),
            GastoResumen(35_000, Miembro.B, "EXITO", listOf("mercado" to 35_000L)),
            GastoResumen(12_000, Miembro.B, null, listOf("transporte" to 12_000L)),
        ))
        assertEquals(132_000L, r.total)
        assertEquals(listOf("mercado" to 95_000L, "aseo" to 25_000L, "transporte" to 12_000L), r.porCategoria)
        assertEquals(mapOf(Miembro.A to 85_000L, Miembro.B to 47_000L), r.porPersona)
        assertEquals(listOf("Éxito" to 120_000L), r.comercios)
    }

    @Test fun archivoAutomatico() {
        val hoy = LocalDate.of(2026, 10, 5)
        assertNull(Periodo.pendienteDeArchivar(ModoPeriodo.MANUAL, 1, null, hoy, bogota))
        assertEquals(ms(9, 1) to ms(10, 1), Periodo.pendienteDeArchivar(ModoPeriodo.AUTOMATICO, 1, null, hoy, bogota))
        assertNull(Periodo.pendienteDeArchivar(ModoPeriodo.AUTOMATICO, 1, ms(10, 1), hoy, bogota))
        assertEquals(ms(7, 1) to ms(10, 1), Periodo.pendienteDeArchivar(ModoPeriodo.AUTOMATICO, 1, ms(7, 1), hoy, bogota))
    }

    @Test fun elRespaldoSeVuelveAImportarIgual() {
        val gastos = listOf(
            GastoExport("g1", ms(9, 5), Miembro.A, "Compra, Éxito", "Éxito", false, listOf(
                ParteExport("Mercado", Responsable.COMPARTIDA, 60_000, 30_000, 30_000),
                ParteExport("Aseo", Responsable.COMPARTIDA, 25_001, 12_501, 12_500),
            )),
            GastoExport("g2", ms(9, 10), Miembro.B, "Internet", "Claro", false, listOf(
                ParteExport("Servicios", Responsable.COMPARTIDA, 100_000, 40_000, 60_000),
            )),
            GastoExport("g3", ms(9, 12), Miembro.A, "Al ahorro", null, true, listOf(ParteExport("Ahorro", Responsable.A, 200_000, 200_000, 0))),
        )
        val archivos = ExportadorEntreDos.archivos(
            gastos,
            listOf(CategoriaExport("Mercado", Responsable.COMPARTIDA, 900_000, "#2E7D5B", true), CategoriaExport("Ahorro", Responsable.A, null, null, false)),
            listOf(DeudaExport("m1", ms(9, 20), Miembro.B, Miembro.A, 45_000, "Arriendo"), DeudaExport("m2", ms(9, 1), Miembro.A, Miembro.B, 0, "Saldada")),
            mapOf(Miembro.A to "Zdraj", Miembro.B to "Pareja"), ms(10, 4), bogota,
        )
        val leido = FormatoEntreDos.gastos(LectorCsv.leer(archivos.getValue("gastos.csv")), bogota)
        assertEquals(0, leido.errores.size)
        val agrupado = PlanImportador.agrupar(leido.filas).filas
        assertEquals(3, agrupado.size)
        assertEquals("Compra, Éxito", agrupado[0].descripcion)
        assertEquals(listOf(null, null), agrupado[0].partes.map { it.cargoPagador }) // 50/50: el peso impar lo asume quien pagó
        assertEquals(60_000L, agrupado[1].partes.single().cargoPagador)            // reparto ajustado: se conserva
        assertEquals(true, agrupado[2].transferencia)
        assertEquals("compartida", FormatoEntreDos.categorias(LectorCsv.leer(archivos.getValue("categorias.csv"))).filas[0].responsable)
        assertEquals(1, FormatoEntreDos.deudas(LectorCsv.leer(archivos.getValue("deudas_abiertas.csv")), bogota).filas.size)
        assertEquals(false, archivos.containsKey("metas.csv"))
        assertEquals("entredos-export-20261004.zip", ExportadorEntreDos.nombreArchivo(ms(10, 4), bogota))
    }
}

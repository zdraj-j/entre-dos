package com.entredos.core.importacion

import com.entredos.core.Miembro
import com.entredos.core.Responsable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ImportacionTest {
    private val bogota = ZoneId.of("America/Bogota")
    private fun dia(m: Int, d: Int) = LocalDate.of(2026, m, d).atTime(LocalTime.NOON).atZone(bogota).toInstant().toEpochMilli()

    private val ejemplo = "\uFEFFid_origen,fecha,monto,categoria,pagado_por,descripcion,comercio,tipo,cargo_pagador\r\n" +
        "g-1001,2026-09-02,35000,Mercado,Pareja,Mercado de la semana,D1,gasto,\r\n" +
        "g-1002,2026-09-03T19:30,48000,Restaurantes,Zdraj,\"Pizza, viernes\",Domino's,gasto,\r\n" +
        "g-1003,2026-09-05,60000,Mercado,Zdraj,Compra Éxito,Éxito,gasto,\r\n" +
        "g-1003,2026-09-05,25000,Aseo,Zdraj,Compra Éxito,Éxito,gasto,\r\n" +
        "g-1004,2026-09-10,100000,Servicios,Pareja,Internet,Claro,gasto,60000\r\n" +
        "g-1005,2026-09-12,200000,Ahorro,Zdraj,Paso a la cuenta de ahorros,,transferencia,\r\n"

    @Test fun csvConComillasSaltosYPuntoYComa() {
        val t = LectorCsv.leer("Nombre;Nota\n\"Ana\";\"dijo \"\"hola\"\"\nen dos líneas\"\n\n;sola\n")
        assertEquals(';', t.separador)
        assertEquals(listOf("nombre", "nota"), t.encabezados)
        assertEquals(2, t.filas.size)
        assertEquals("dijo \"hola\"\nen dos líneas", t.filas[0].valores[1])
        assertEquals(5, t.filas[1].linea)
    }

    @Test fun montos() {
        assertEquals(35_000L, Valores.monto("35000"))
        assertEquals(35_000L, Valores.monto("35.000"))
        assertEquals(1_234_568L, Valores.monto("$ 1.234.567,89"))
        assertEquals(12_501L, Valores.monto("12,500.50"))
        assertEquals(-12_000L, Valores.monto("(12.000)"))
        assertEquals(-45_900L, Valores.monto("-45900.00"))
        assertEquals(35L, Valores.monto("35.00"))
        assertNull(Valores.monto("treinta"))
        assertNull(Valores.monto("1.2345"))
    }

    @Test fun fechasIso() {
        assertEquals(dia(9, 2), Valores.fechaIso("2026-09-02", bogota))
        val conHora = LocalDate.of(2026, 9, 3).atTime(19, 30).atZone(bogota).toInstant().toEpochMilli()
        assertEquals(conHora, Valores.fechaIso("2026-09-03T19:30", bogota))
        assertEquals(conHora, Valores.fechaIso("2026-09-03 19:30:00", bogota))
        assertEquals(conHora, Valores.fechaIso("2026-09-04T00:30:00Z", bogota))
        assertEquals(conHora, Valores.fechaIso("2026-09-03T19:30-05:00", bogota))
        assertNull(Valores.fechaIso("03/09/2026", bogota))
    }

    @Test fun formatoPropioYAgrupacion() {
        val tabla = LectorCsv.leer(ejemplo)
        assertTrue(FormatoEntreDos.esFormatoPropio(tabla))
        val leido = FormatoEntreDos.gastos(tabla, bogota)
        assertEquals(emptyList<ErrorFila>(), leido.errores)
        assertEquals(5, leido.filas.size)
        assertEquals(1, leido.omitidas) // la transferencia de un respaldo viejo no entra
        assertEquals("Pizza, viernes", leido.filas[1].descripcion)
        assertEquals(60_000L, leido.filas[4].cargoPagador)

        val agrupado = PlanImportador.agrupar(leido.filas)
        assertEquals(4, agrupado.filas.size)
        val dividido = agrupado.filas.first { it.idOrigen == "g-1003" }
        assertEquals(85_000L, dividido.monto)
        assertEquals(listOf("Mercado", "Aseo"), dividido.partes.map { it.categoria })
    }

    @Test fun erroresPorFila() {
        val csv = "fecha,monto,categoria,pagado_por,tipo,cargo_pagador\n" +
            "2026-13-01,1000,A,Ana,gasto,\n" +
            "2026-09-01,0,A,Ana,gasto,\n" +
            "2026-09-01,1000,,Ana,gasto,\n" +
            "2026-09-01,1000,A,Ana,regalo,\n" +
            "2026-09-01,1000,A,Ana,gasto,2000\n" +
            "2026-09-01,1000,A,Ana,gasto,\n"
        val leido = FormatoEntreDos.gastos(LectorCsv.leer(csv), bogota)
        assertEquals(1, leido.filas.size)
        assertEquals(listOf(2, 3, 4, 5, 6), leido.errores.map { it.linea })
        assertEquals("Faltan columnas: pagado_por", FormatoEntreDos.gastos(LectorCsv.leer("fecha,monto,categoria\n"), bogota).errores.single().motivo)
    }

    @Test fun filasDivididasQueNoCoinciden() {
        val f1 = FilaGasto(2, "x", dia(9, 1), 1000, "A", "Ana", null, null, null)
        val f2 = f1.copy(linea = 3, fecha = dia(9, 2), categoria = "B")
        val r = PlanImportador.agrupar(listOf(f1, f2))
        assertTrue(r.filas.isEmpty())
        assertTrue(r.errores.single().motivo.contains("no coinciden"))
    }

    private fun plan(ids: Set<String> = emptySet(), huellas: List<HuellaGasto> = emptyList(), omitir: Boolean = true): PlanImportacion {
        val leido = FormatoEntreDos.gastos(LectorCsv.leer(ejemplo), bogota)
        val gastos = PlanImportador.agrupar(leido.filas).filas
        return PlanImportador.armar(
            EntradaPlan(
                gastos = gastos,
                personas = mapOf("zdraj" to Miembro.A, "pareja" to Miembro.B),
                categorias = mapOf(
                    "mercado" to DestinoCategoria.Existente("cat-mercado"),
                    "restaurantes" to DestinoCategoria.Nueva("Restaurantes", Responsable.COMPARTIDA),
                    "aseo" to DestinoCategoria.Nueva("Aseo", Responsable.COMPARTIDA),
                    "servicios" to DestinoCategoria.Existente("cat-servicios"),
                    "ahorro" to DestinoCategoria.Nueva("Ahorro", Responsable.A),
                    "sin_uso" to DestinoCategoria.Nueva("Sin uso", Responsable.B),
                ),
                idsOrigenGastos = ids,
                huellasExistentes = huellas,
                omitirPosiblesDuplicados = omitir,
                zona = bogota,
            )
        )
    }

    @Test fun planCompleto() {
        val p = plan()
        assertEquals(4, p.gastos.size)
        assertEquals(0, p.duplicados)
        assertEquals(setOf("restaurantes", "aseo"), p.categoriasNuevas.keys) // "ahorro" solo lo usaba la transferencia
        assertEquals("Mercado de la semana", p.gastos[0].nombre)
        assertEquals(Miembro.B, p.gastos[0].pagadoPor)
        assertEquals("Internet", p.gastos[3].nombre)
    }

    @Test fun reimportarNoDuplica() {
        val p = plan(ids = setOf("g-1001", "g-1002", "g-1003", "g-1004"))
        assertEquals(0, p.gastos.size)
        assertEquals(4, p.duplicados)
    }

    @Test fun personaOCategoriaSinAsignarEsError() {
        val gasto = GastoImportado(null, listOf(2), dia(9, 1), "Desconocido", null, null, listOf(ParteImportada("Mercado", 1000, null)))
        val p = PlanImportador.armar(EntradaPlan(listOf(gasto), personas = emptyMap(), categorias = emptyMap(), zona = bogota))
        assertEquals("No se asignó a ningún titular: Desconocido", p.errores.single().motivo)
    }

    @Test fun posiblesDuplicadosSinIdOrigen() {
        val g = GastoImportado(null, listOf(2), dia(9, 1), "Ana", null, "D1", listOf(ParteImportada("Mercado", 35_000, null)))
        val entrada = EntradaPlan(
            listOf(g, g.copy(lineas = listOf(3))), personas = mapOf("ana" to Miembro.A),
            categorias = mapOf("mercado" to DestinoCategoria.Existente("m")), zona = bogota,
            huellasExistentes = emptyList(),
        )
        val p = PlanImportador.armar(entrada)
        assertEquals(1, p.gastos.size)
        assertEquals(1, p.posiblesDuplicados)
        val conExistente = PlanImportador.armar(entrada.copy(gastos = listOf(g), huellasExistentes = listOf(HuellaGasto(dia(9, 1) + 3_600_000, 35_000, "d1"))))
        assertEquals(0, conExistente.gastos.size)
        assertEquals(1, PlanImportador.armar(entrada.copy(gastos = listOf(g), omitirPosiblesDuplicados = false, huellasExistentes = listOf(HuellaGasto(dia(9, 1), 35_000, "D1")))).gastos.size)
    }

    @Test fun deudasAbiertas() {
        val deudas = FormatoEntreDos.deudas(LectorCsv.leer("id_origen,fecha,deudor,acreedor,monto,concepto\nd1,2026-09-20,Pareja,Zdraj,45000,Arriendo\nd2,2026-09-20,Zdraj,zdraj,1000,\n"), bogota)
        assertEquals(1, deudas.filas.size)
        assertEquals(1, deudas.errores.size)
        val p = PlanImportador.armar(
            EntradaPlan(emptyList(), deudas.filas, mapOf("zdraj" to Miembro.A, "pareja" to Miembro.B), emptyMap(), zona = bogota)
        )
        assertEquals(DeudaLista("d1", dia(9, 20), Miembro.B, Miembro.A, 45_000, "Arriendo"), p.deudas.single())
    }

    @Test fun periodosAnteriores() {
        val g = { m: Int, d: Int, monto: Long, comercio: String? -> GastoListo(null, dia(m, d), Miembro.A, "x", comercio, listOf(ParteLista("mercado", monto, null))) }
        val gastos = listOf(g(7, 3, 10_000, "D1"), g(7, 28, 5_000, "D1"), g(8, 15, 20_000, "Ara"), g(9, 2, 1_000, null), g(10, 2, 7_000, null))
        val inicioActual = LocalDate.of(2026, 10, 1).atStartOfDay(bogota).toInstant().toEpochMilli()
        val existente = LocalDate.of(2026, 9, 1).atStartOfDay(bogota).toInstant().toEpochMilli() to inicioActual
        val periodos = PlanImportador.periodosHistoricos(gastos, 1, inicioActual, listOf(existente), bogota)
        assertEquals(2, periodos.size) // julio y agosto; septiembre ya existe; octubre es el actual
        assertEquals(15_000L, periodos[0].total)
        assertEquals(listOf("D1" to 15_000L), periodos[0].comercios)
    }

    @Test fun reglasDesdeLaImportacion() {
        val g = { c: String, cat: String -> GastoListo(null, dia(9, 1), Miembro.A, "x", c, listOf(ParteLista(cat, 1000, null))) }
        val gastos = List(3) { g("D1 SUCURSAL $it", "mercado") } + List(2) { g("Rappi", "antojos") } + g("D1 CENTRO 4", "aseo")
        assertEquals(mapOf("d1" to "mercado"), PlanImportador.reglas(gastos))
    }

    @Test fun otraAppConGastosNegativosYFechaColombiana() {
        val csv = "Date;Amount;Category;Note;Type\n" +
            "05/09/2026;-45.900;Comida;Almuerzo;Expense\n" +
            "13/09/2026;2.500.000;Salario;Pago;Income\n" +
            "14/09/2026;-12.000;Transporte;Taxi;Expense\n" +
            "15/09/2026;-500.000;;Al ahorro;Transfer\n"
        val t = LectorCsv.leer(csv)
        val mapeo = FormatoGenerico.sugerirMapeo(t)!!
        assertEquals(MapeoColumnas(fecha = 0, monto = 1, categoria = 2, descripcion = 3, tipo = 4), mapeo)
        val formato = Valores.detectarFormatoFecha(t.filas.map { it.valores[0] }, bogota)
        assertEquals("dd/MM/yyyy", formato)
        assertEquals(SignoGastos.NEGATIVOS, FormatoGenerico.detectarSigno(t, mapeo.monto))
        val leido = FormatoGenerico.gastos(t, mapeo, formato!!, SignoGastos.NEGATIVOS, bogota)
        assertEquals(listOf(45_900L, 12_000L), leido.filas.map { it.monto })
        assertEquals(2, leido.omitidas) // el ingreso y la transferencia
        assertEquals(FormatoGenerico.SIN_PERSONA, leido.filas[0].pagadoPor)
    }

    @Test fun fechaAmericanaSeDetectaCuandoElDiaPasaDe12() {
        assertEquals("MM/dd/yyyy", Valores.detectarFormatoFecha(listOf("09/05/2026", "09/23/2026", "10/01/2026"), bogota))
    }
}

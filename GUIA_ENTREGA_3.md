# Entre dos — Entrega 3: teclado y pendientes de la fase 1

Al terminar esta entrega la app abre en el teclado y registra un gasto en dos toques,
Movimientos se filtra por categoría, la división con montos distintos y el reparto ajustado
funcionan, y el lector genérico de otras apps está disponible en Detección.

## Cómo se usa el teclado

- Escribir el monto (con **000**, **+** y **−** si hace falta) y tocar una categoría: queda
  guardado con fecha de hoy y a tu nombre. Aparece "Registrado …" con **Deshacer**.
- **Nota** y **Fecha** (Hoy, Ayer o calendario; no permite fechas futuras) se aplican al
  siguiente gasto y luego vuelven a lo normal.
- **Varias categorías**: tocar las categorías y luego "Registrar en N" (partes iguales) o
  "Ajustar montos" (la última se calcula sola).
- **Toque largo en una categoría compartida**: "Ajustar reparto" (mitad, todo yo, todo mi
  pareja o un monto). Lo que asume la pareja queda como deuda.
- Las categorías se ordenan: destacadas, luego las más usadas en los últimos 90 días, luego alfabético.
  Cada una muestra lo que le queda en el periodo, o "excedida" en rojo.

## Qué trae este paquete (además de las entregas 1 y 2)

| Archivo | Para qué |
| --- | --- |
| `core/Calculadora.kt` | Lógica del teclado y formato de pesos. |
| `core/Periodo.kt` | Inicio del periodo actual (manual o automático). |
| `core/Presupuesto.kt` | Gastado y disponible por categoría y del periodo. |
| `core/LectorGenerico.kt` | Lector conservador de montos y comercios para otros bancos. |
| `core/Clasificacion.kt` | Recurrentes, reglas aprendidas (3 confirmaciones) y sugerencias. |
| `core/Movimientos.kt` | Filtros (texto sin tildes, persona, categoría) y agrupación por día. |
| `data/local/AjustesLocales.kt` | Ajustes solo de este teléfono: clasificación manual y lector genérico. |
| `data/repository/PeriodoRepositorio.kt` | Inicio del periodo en vivo. |
| `data/repository/GastosRepositorio.kt` | Ahora también: pendientes, confirmar, ignorar y aprendizaje de reglas. |
| `data/notificaciones/IngresoNotificacion.kt` | Sección 2.3 del plan completa, más el receptor genérico. |
| `ui/teclado/...` | Pantalla principal y sus diálogos (nota, fecha, montos, reparto). |
| `ui/movimientos/...` | Movimientos con buscador, periodo, persona y fichas de categoría. |
| `ui/deteccion/PantallaLectorGenerico.kt` | Interruptor y elección de apps. |

**Probado aquí:** 55 pruebas de lógica pura en total (incluye las de las entregas anteriores).
**No probado aquí:** las pantallas de Compose ni las consultas de Room.

## Pasos

1. **Navegación.** El destino inicial (con hogar listo) es `PantallaTeclado`. Barra inferior:
   Registrar, Movimientos, Presupuesto, Resumen y Ajustes. Ruta de Movimientos:
   `movimientos?categoriaId={categoriaId}` (argumento opcional, tipo String, nullable).
   En Presupuesto, tocar una categoría navega a `movimientos?categoriaId=<id>`; borra la lista
   de gastos por categoría que había aparte.

2. **Reemplazos.** `PantallaTeclado` reemplaza a la pantalla de registro rápido vieja.
   `DialogoMontos` reemplaza al `SplitExpenseDialog` viejo: bórralo.

3. **Notificaciones de bancos.** En el `NotificationListenerService`:
   - Si un lector propio (Bancolombia, Nequi, etc.) reconoce la notificación:
     `ingreso.registrar(monto, comercio, paquete, sbn.postTime)`.
   - Si ningún lector propio conoce el paquete: `receptorGenerico.alRecibir(paquete, titulo, texto, sbn.postTime)`.
   - Con el resultado: `Pendiente` → aviso "Gasto detectado" con `sugerencias` como botones;
     `Confirmado` → aviso breve "Gasto registrado"; `Duplicado` o null → nada.
   - Un botón del aviso hace `gastos.confirmarPendiente(gasto, gasto.nombre, listOf(ParteNueva(categoriaId, gasto.monto)))`.
   - Usa un `CoroutineScope(SupervisorJob() + Dispatchers.Default)` del servicio y cancélalo en `onDestroy`.

4. **Bandeja de pendientes y ventana obligatoria.** Al clasificar:
   `confirmarPendiente(gasto, nombre, partes)` (con varias categorías, `gastos.partesIguales(monto, ids)`),
   `confirmarPendiente(..., transferencia = true)` para transferencias, e `ignorar(gasto)` para "No registrar".

5. **Ajustes.** El interruptor "Elegir presupuesto siempre a mano" usa
   `AjustesLocales.clasificacionManual`. En Detección, agrega la entrada a `PantallaLectorGenerico`.

6. **Manifiesto.** Para poder listar las apps instaladas (Android 11 o superior), dentro de `<manifest>`:
   ```xml
   <queries>
       <intent>
           <action android:name="android.intent.action.MAIN" />
           <category android:name="android.intent.category.LAUNCHER" />
       </intent>
   </queries>
   ```

7. **Versiones.** Las pantallas usan colores `surfaceContainerLowest` de Material 3 1.2 o superior.

## Pruebas a mano

1. Escribir 12000 + 8500, tocar una categoría: se guarda $ 20.500 y baja lo disponible.
2. Tocar Deshacer: el gasto desaparece y vuelve el monto al teclado.
3. Toque largo en una compartida, "Todo yo": no se crea deuda. "Mitad" con $ 2.001: la pareja debe $ 1.000.
4. Varias categorías con $ 100.000 en tres: 33.334, 33.333 y 33.333. Ajustar montos 60.000 / 25.000 / resto.
5. Cambiar la fecha a ayer y registrar: aparece en Movimientos bajo el día de ayer.
6. Desde Presupuesto, tocar una categoría: Movimientos se abre filtrado por ella (incluye gastos divididos).
7. Activar el lector genérico con una app de prueba que envíe "Compraste $35.000 en D1": queda pendiente.

# Entre dos — Entrega 4: importador, y lo que faltaba conectar de la fase 1

## Qué trae (además de las entregas 1 a 3)

**Lo que quedaba de la fase 1**

| Archivo | Para qué |
| --- | --- |
| `data/notificaciones/AvisosGasto.kt` | Aviso "Gasto detectado" con hasta 3 botones y aviso breve "Gasto registrado" (se quita a los 4 segundos). |
| `data/notificaciones/ReceptorAccionesAviso.kt` | Un botón del aviso clasifica el gasto sin abrir la app. |
| `ui/pendientes/...` | Bandeja "Pendientes por clasificar" y ventana de clasificación (obligatoria si se abre desde el aviso). |
| `ui/ajustes/SeccionClasificacion.kt` | Interruptor "Elegir presupuesto siempre a mano". |

**Importador**

| Archivo | Para qué |
| --- | --- |
| `core/importacion/LectorCsv.kt` | CSV RFC 4180: comillas, saltos de línea internos, BOM, coma, punto y coma o tabulación. |
| `core/importacion/Valores.kt` | Montos en cualquier formato colombiano o inglés, fechas ISO y detección del formato de fecha de otras apps. |
| `core/importacion/Formatos.kt` | Formato propio (los cuatro CSV) y CSV de otras apps con mapeo de columnas sugerido. |
| `core/importacion/Plan.kt` | Agrupa gastos divididos, asigna personas y categorías, detecta duplicados, arma periodos anteriores y reglas. |
| `data/importacion/LectorArchivo.kt` | Lee el .zip o .csv elegido (UTF-8 o Windows-1252), con límite de tamaño. |
| `data/importacion/ImportadorRepositorio.kt` | Guarda por bloques de 100 bajo un lote, y deshace un lote completo. |
| `data/repository/ResumenPeriodo.kt` | Formato JSON del resumen de un periodo cerrado (úsalo también al cerrar periodos). |
| `ui/importacion/...` | Asistente de importación y lista de importaciones con Deshacer. |
| `ejemplos/` | `entredos-export-ejemplo.zip` (formato propio) y `otra-app-ejemplo.csv` (otra app, gastos en negativo). |

**Probado aquí:** 70 pruebas de lógica pura (14 nuevas del importador). **No probado aquí:** Room, Compose,
avisos y lectura de archivos en Android.

No hay cambios en las tablas: la base sigue en la versión 2.

## Pasos

1. **Manifiesto.**
   ```xml
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

   <receiver
       android:name=".data.notificaciones.ReceptorAccionesAviso"
       android:exported="false" />
   ```
   La actividad principal con `android:launchMode="singleTop"`. En Android 13 o superior, pide
   POST_NOTIFICATIONS desde la pantalla de permisos (ya existe en la app).

2. **Servicio de notificaciones.** Donde antes armabas el aviso a mano:
   ```kotlin
   alcance.launch {
       val resultado = if (lectorPropio != null) ingreso.registrar(monto, comercio, paquete, sbn.postTime)
                       else receptorGenerico.alRecibir(paquete, titulo, texto, sbn.postTime)
       avisos.mostrar(resultado)
   }
   ```
   Borra el constructor de avisos y el receptor de botones viejos.

3. **Abrir la ventana obligatoria desde el aviso.** En `MainActivity`, lee
   `intent.getStringExtra(AvisosGasto.EXTRA_GASTO_PENDIENTE)` en `onCreate` y en `onNewIntent`,
   guárdalo en un estado y, mientras no sea null, muestra `VentanaDesdeAviso(gastoId) { estado = null }`
   encima de la navegación.

4. **Navegación.**
   - El aviso "por clasificar" del teclado y la entrada de Movimientos llevan a `PantallaPendientes`.
   - Ajustes › Clasificación de gastos: `SeccionClasificacion`.
   - Ajustes › Importaciones: `PantallaImportaciones`; su botón lleva a `PantallaImportar`;
     "Ver en Movimientos" abre Movimientos con el filtro "Todos".
   - Borra la bandeja de pendientes vieja.

5. **Cierre de periodo.** Cuando lo conectes al modelo nuevo, guarda el resumen con `ResumenPeriodo`,
   para que los periodos importados y los cerrados a mano se vean igual en Periodos anteriores.

## Pruebas a mano

1. Copia `ejemplos/entredos-export-ejemplo.zip` al teléfono e impórtalo: no pide columnas; en Personas
   asigna "Zdraj" y "Pareja"; en Categorías deben venir prellenadas desde `categorias.csv`.
2. Vista previa: 7 gastos (uno dividido en dos categorías), 0 errores, 1 deuda, 1 meta, 5 categorías
   nuevas (si aún no las tienes) y 3 periodos anteriores: julio ($ 87.000), agosto ($ 226.000) y
   septiembre ($ 48.000; la transferencia no cuenta), con día de inicio 1 y el periodo actual desde octubre.
3. Tras importar: la deuda de $ 45.000 aparece en Deudas; ningún gasto importado crea deuda; Metas muestra
   $ 450.000 ahorrados; D1 queda como regla automática en Detección (tiene 3 gastos en Mercado).
4. Importa el mismo zip otra vez: "Ya importados antes: 7" y el botón dice que no hay nada nuevo.
5. Importa `ejemplos/otra-app-ejemplo.csv`: pide columnas ya sugeridas, formato dd/MM/yyyy y "gastos en
   negativo" activado. Entran 3 (taxi, almuerzo y la transferencia) y se omite el salario.
6. Ajustes › Importaciones › Deshacer el primero: desaparece todo lo suyo en los dos teléfonos.
7. Simula una notificación de banco: el aviso trae 3 botones; uno clasifica sin abrir la app; tocar el
   cuerpo abre la ventana y no se puede cerrar sin decidir.

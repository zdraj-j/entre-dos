# Entre dos

App Android para que dos personas que viven juntas lleven un presupuesto compartido en pesos colombianos.
Registra gastos en dos toques, los detecta solos leyendo los avisos de los bancos, calcula quién le debe
a quién y guarda el historial de cada periodo. Funciona sin conexión y se sincroniza entre los dos
teléfonos por la carpeta oculta de la app en el Google Drive de una cuenta del hogar: sin Firebase ni
servidores de terceros.

## Qué hace

- **Registrar:** la pantalla de inicio es un teclado tipo calculadora. Monto + categoría y queda guardado, con Deshacer.
- **Detección automática:** lee notificaciones de Bancolombia, Banco de Bogotá, Davivienda, DaviPlata, Nequi, Nu y RappiCard, y de otras apps que elijas (lector genérico, siempre queda pendiente).
- **Presupuesto** por categoría con tope o sin tope, y categorías personales o compartidas.
- **Deudas automáticas:** categoría de una persona pagada por la otra = deuda completa; compartida = la mitad o el reparto que se ajuste.
- **Periodos:** cierre manual o automático, historial con estadísticas y descarga en CSV.
- **Metas de ahorro, gastos recurrentes y reglas de comercio** que se aprenden con 3 confirmaciones.
- **Widget de inicio** con lo disponible del periodo, lo gastado hoy y un botón para registrar; y acceso rápido "Nuevo gasto" al mantener presionado el ícono.
- **Wifi en casa:** con la app abierta, los dos teléfonos se pasan los cambios directamente aunque no haya internet.
- **Importar** desde otras apps (formato propio o cualquier CSV) y **exportar** un respaldo en el mismo formato.

## Tecnología

Kotlin 2.0 · Jetpack Compose (Material 3) · Glance (widget) · Room · Hilt · WorkManager · kotlinx.serialization ·
Google Identity Services (permiso `drive.appdata`) · API REST de Drive v3 con `HttpURLConnection`.
`minSdk 26`, `targetSdk 35`.

## Estructura

```
app/src/main/java/com/entredos/
├── core/            Lógica pura, sin Android y con pruebas: reparto, deudas, periodo, presupuesto,
│   ├── sync/        reloj lógico híbrido, LWW y motor de sincronización
│   ├── wifi/        vector de versiones, cifrado y protocolo del intercambio por wifi
│   ├── importacion/ lector CSV, formatos y plan de importación
│   └── exportacion/ respaldo y reporte de periodo
├── data/
│   ├── local/       Room (entidades, DAO), Escritor (única puerta de escritura), ajustes locales
│   ├── sync/        Drive, cuenta del hogar, trabajos en segundo plano, servicio del hogar
│   ├── notificaciones/ servicio que lee avisos, avisos de la app, diagnóstico
│   ├── wifi/        anuncio y búsqueda en la red (NSD) y conexiones
│   ├── importacion/ lectura de archivos y guardado por lotes
│   └── repository/  gastos, deudas, periodos, cierre, categorías, metas, etc.
├── di/              módulos de Hilt
└── ui/              pantallas de Compose
```

Regla de oro: toda escritura pasa por `Escritor`, que sella el cambio con el reloj y lo deja en la
lista de cambios por subir. Si se escribe directo en un DAO, ese cambio no llega a la pareja.

## Primeros pasos

1. Abre la carpeta en Android Studio (Ladybug o más reciente) y deja que sincronice Gradle.
2. Configura Google Cloud (proyecto, API de Drive, permiso `drive.appdata`, cliente Android con el
   paquete `com.entredos` y tu SHA-1). Paso a paso en [`docs/CONFIGURACION.md`](docs/CONFIGURACION.md).
3. Ejecuta las pruebas: `./gradlew test`.
4. Instala en los dos teléfonos y sigue [`docs/PRUEBAS.md`](docs/PRUEBAS.md).

## Estado

| Entrega | Contenido | Estado |
| --- | --- | --- |
| 1 | Modelo de datos local, reparto y deudas | Hecha |
| 2 | Sincronización por Drive y recuperación al reinstalar | Hecha |
| 3 | Teclado, periodo, presupuesto, lector genérico | Hecha |
| 4 | Importador, avisos de gasto, pendientes | Hecha |
| 5 | Proyecto completo: Gradle, navegación, todas las pantallas, cierre, exportación, lectura de notificaciones | Hecha |
| 6 | Sincronización por wifi en casa, cifrada y solo entre teléfonos del hogar | Hecha |
| 7 | Pulido visual (tarjetas, montos alineados, estados vacíos) y widget de inicio | Hecha |

**Probado:** 85 pruebas de la lógica pura (`core/`). **Sin probar en un dispositivo todavía:** todo lo de Android.
Los nombres de paquete y textos de los bancos (`core/LectoresBancos.kt`) deben confirmarse con
Ajustes › Diagnóstico en los teléfonos reales.

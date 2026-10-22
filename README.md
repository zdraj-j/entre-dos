# Entre dos

App Android para que dos personas que viven juntas lleven el presupuesto de la casa en pesos colombianos.
Los gastos del banco se anotan solos, saben cuánto les queda en el mes y quién le debe a quién.
Funciona sin conexión y se sincroniza entre los dos teléfonos por la carpeta oculta de la app en el
Google Drive de una cuenta del hogar: sin Firebase ni servidores de terceros.

## Las cuatro pestañas

| Pestaña | Para qué | Qué tiene |
| --- | --- | --- |
| **Anotar** | Registrar un gasto | Monto, "Hoy / Nota / Dividir", categorías y teclado. Tocar una categoría guarda. Arriba, cuánto queda del mes. |
| **Gastos** | Ver y confirmar lo que pasó | Arriba, "Por confirmar" (a quién le llegó, fecha y banco; al tocar uno aparecen las categorías). Debajo, los gastos del mes por día. Búsqueda y cambio de mes. |
| **Mes** | Saber cómo van | Lo que queda (con aviso si a este ritmo se pasan), Deudas en una fila, todas las categorías, quién pagó y "Cerrar el mes". |
| **Hogar** | Lo que se configura una vez | Los dos titulares, categorías y topes, gastos fijos, meses anteriores y Ajustes. |

Diseño aprobado (claro y oscuro): ver `docs/DISENO.md`.

## Qué hace

- **Detección automática:** lee los avisos de Bancolombia, Banco de Bogotá, Davivienda, DaviPlata, Nequi, Nu y RappiCard; y, si se activa, de otras apps elegidas (siempre quedan por confirmar). Descarta publicidad, recordatorios y avisos que no dicen que salió plata.
- **Clasificación que aprende:** con 3 confirmaciones del mismo comercio en la misma categoría, se clasifica solo. Gastos fijos con monto parecido se confirman solos.
- **Deudas automáticas:** categoría de una persona pagada por la otra = deuda completa; compartida = la mitad o el reparto ajustado. "Registrar un pago" abona a las deudas más viejas primero.
- **Mes:** cierre manual o automático, historial con resumen y descarga en CSV.
- **Sincronización:** Google Drive (cuenta del hogar) y, opcional, wifi en casa cifrado entre los dos teléfonos.
- **Importar y exportar:** formato propio (respaldo) o CSV de cualquier otra app; cada importación se puede deshacer.
- **Widget** con lo que queda y un botón para anotar; acceso rápido "Nuevo gasto" en el ícono.

## Tecnología

Kotlin 2.0 · Jetpack Compose (Material 3) · Room · Hilt · WorkManager · Glance · kotlinx.serialization ·
Google Identity Services (permiso `drive.appdata`) · API REST de Drive v3 · NSD y AES-GCM para el wifi.
Letra Manrope incluida (OFL). `minSdk 26`, `targetSdk 35`.

## Estructura

```
app/src/main/java/com/entredos/
├── core/              Lógica pura con pruebas: reparto, deudas, periodo y ritmo, presupuesto, calculadora,
│   ├── sync/          lectores de bancos, clasificación · reloj híbrido, LWW, motor de sincronización
│   ├── wifi/          vector de versiones, cifrado, protocolo
│   ├── importacion/   CSV, formatos, plan de importación
│   └── exportacion/   respaldo y reporte de mes
├── data/              Room, Escritor, Drive, notificaciones, wifi, importación, repositorios
├── di/                Hilt
└── ui/
    ├── anotar/ gastos/ mes/ hogar/ ajustes/     las cuatro pestañas y Ajustes
    ├── teclado/       lógica y diálogos del teclado (los usa Anotar)
    ├── movimientos/   edición de un gasto
    ├── periodos/ recurrentes/ deteccion/ importacion/ permisos/ widget/
    └── comun/ tema/   componentes y tema (violeta, claro y oscuro)
```

Regla de oro: toda escritura pasa por `Escritor`, que sella el cambio con el reloj y lo deja por subir.

## Primeros pasos

1. Abre la carpeta en Android Studio y deja que sincronice Gradle.
2. Configura la cuenta de Google del hogar y Google Cloud: [`docs/CONFIGURACION.md`](docs/CONFIGURACION.md).
3. `./gradlew test` (97 pruebas de la lógica pura).
4. Instala en los dos teléfonos y recorre [`docs/PRUEBAS.md`](docs/PRUEBAS.md).

## Documentos

| Documento | Para quién |
| --- | --- |
| [`docs/GUIA_DE_USO.md`](docs/GUIA_DE_USO.md) | Para los dos: cómo se usa la app día a día |
| [`docs/CONFIGURACION.md`](docs/CONFIGURACION.md) | Cuenta del hogar, Google Cloud y conexión de los teléfonos |
| [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) | Modelo de datos, sincronización, wifi y decisiones técnicas |
| [`docs/DISENO.md`](docs/DISENO.md) | Pantallas, colores, letra y componentes |
| [`docs/IMPORTAR_DESDE_OTRA_APP.md`](docs/IMPORTAR_DESDE_OTRA_APP.md) | Formato de importación y prompt para agregar la exportación a otra app |
| [`docs/PRUEBAS.md`](docs/PRUEBAS.md) | Pruebas a mano con los dos teléfonos |

## Estado

Las 7 entregas del plan y el rediseño están en el código (versión 1.0.0). **Probado:** 97 pruebas de la lógica
pura. **Sin probar todavía en un dispositivo:** todo lo de Android (compilación, pantallas, Drive, avisos, wifi).
Los nombres de paquete y los textos de los bancos (`core/LectoresBancos.kt`) deben confirmarse con
Hogar › Ajustes › Diagnóstico en los teléfonos reales.

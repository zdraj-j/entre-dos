# Entre dos — Arquitectura

## Principios

- **La base local manda.** Cada teléfono guarda todo en Room y funciona sin conexión.
- **Una sola puerta de escritura.** `Escritor` sella cada cambio con el reloj lógico híbrido y el id del teléfono, lo
  guarda y lo deja en `cambio_pendiente`. Nada se escribe directo en un DAO.
- **Solo se sincronizan hechos.** Gastos, partes, abonos, deudas manuales, categorías, reglas, gastos fijos, cierres y
  lotes de importación. Las deudas automáticas se **calculan** en cada teléfono a partir de las partes de cada gasto.
- **Lógica pura en `core/`**, sin Android, con pruebas (88).

## Modelo de datos (Room, versión 3)

| Tabla | Qué guarda |
| --- | --- |
| `hogar` | Nombre, día de inicio, modo del periodo (manual o automático) |
| `miembro` | A o B y su nombre |
| `categoria` | Nombre, color, responsable (A, B o COMPARTIDA), tope o sin tope, destacada, orden |
| `gasto` | Monto, nombre, comercio, fecha, quién pagó, estado (PENDIENTE, CONFIRMADO, IGNORADO), origen, banco, lote |
| `parte_gasto` | Categoría, monto y cargo de A y de B (suman el monto). Su id es el de su deuda |
| `deuda_manual` · `abono` | Deudas agregadas a mano y abonos a cualquier deuda |
| `periodo_cerrado` | Inicio, fin, totales y resumen (JSON) |
| `recurrente` · `regla_comercio` | Gastos fijos y reglas comercio → categoría |
| `lote_importacion` | Cada importación, para poder deshacerla |
| `dispositivo_local` · `cambio_pendiente` · `archivo_aplicado` | Solo locales: identidad del teléfono y control de la sincronización |

Todas las sincronizables llevan `sync_actualizadoEn` (HLC), `sync_actualizadoPor` (dispositivo) y `sync_eliminado`.

**Deudas:** por cada parte confirmada de tipo GASTO, quien no pagó debe su cargo a quien pagó. Los gastos importados
nunca generan deuda. "Registrar un pago" reparte el monto entre las deudas del que paga, de la más vieja a la más nueva.

## Sincronización por Drive

Carpeta oculta `appDataFolder` de la cuenta del hogar (permiso `drive.appdata`):

- `hogar.json`: marca que el hogar existe.
- `cambios-<dispositivo>-<secuencia>.json`: cada teléfono escribe solo los suyos, nunca los modifica.
- `foto-<millis>-<dispositivo>.json`: estado completo cuando hay más de 200 archivos de cambios; los que resume se borran.

Conflictos: gana la marca HLC mayor; en empate, el id de dispositivo mayor. Se sincroniza al abrir la app, tres segundos
después de cada cambio y cada 15 minutos (WorkManager). Al reinstalar: conectar la cuenta → baja la foto y los cambios →
"Soy …" → el teléfono queda ligado al mismo titular con un id de dispositivo nuevo.

## Wifi en casa

Con la app abierta: anuncio y búsqueda NSD (`_entredos._tcp.`) con una huella del hogar que no revela su id. Saludo con
números aleatorios y prueba HMAC del secreto del hogar; luego todo va cifrado con AES-256-GCM. Cada uno manda su
**vector de versiones** (marca más alta conocida de cada autor) y recibe lo que le falta; se aplica con LWW, igual que
lo de Drive.

## Notificaciones

`ServicioNotificaciones` → `LectoresBancos` (reglas por banco sobre el lector genérico) → `IngresoNotificacion`:
duplicado (±5 min) → gasto fijo parecido → regla automática → si nada aplica, PENDIENTE y aviso "Gasto detectado" con
tres botones. El texto de los avisos solo vive en memoria (Diagnóstico) y nunca se sube.

## Importación y exportación

Formato v1 (`docs/IMPORTAR_DESDE_OTRA_APP.md`): `gastos.csv` (obligatorio), `categorias.csv`, `deudas_abiertas.csv`,
`manifest.json` en un zip. El respaldo de Ajustes usa el mismo formato. Cada importación es un lote que se puede deshacer.

## Decisiones

- Sin Firebase ni servidores: Drive de una cuenta compartida y wifi local.
- Sin ahorros ni metas: la app es de gastos y deudas.
- Cuatro pestañas con un propósito cada una; lo ocasional vive dentro de Hogar.

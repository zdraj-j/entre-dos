# Entre dos — Diseño

Diseño aprobado, claro y oscuro: lienzo "Entre dos – Diseño aprobado" (https://claude.ai/artifact/8wpGFnGbmMG6QXjpubbQHe).

## Principios

- Cada pantalla tiene un propósito y muestra solo lo necesario para él.
- Lo frecuente queda a un toque; lo ocasional, dentro de Hogar.
- El naranja solo aparece cuando algo requiere atención.

## Pantallas

| # | Pantalla | Contenido |
| --- | --- | --- |
| 0 | Bienvenida | Logo de dos círculos, "Entre dos", tres puntos de para qué sirve, "Conectar la cuenta del hogar" |
| 1 | Anotar | "Anotar gasto" + "Quedan $ …"; monto; Hoy · Nombre · Dividir; categorías (hasta 9, luego "Más…"); teclado |
| 2 | Gastos | Título + "Mes · total ▾" + buscar; "Por confirmar · N" en recuadro naranja (quién, fecha, banco; al tocar, las categorías); gastos por día |
| 2b | Elegir categoría | Hoja desde abajo: categorías en dos columnas, Guardar, No es un gasto |
| 3 | Mes | Anillo + "Quedan"; aviso de ritmo; Deudas en una fila; Categorías completas; Quién pagó; Cerrar el mes |
| 3b | Deudas | Saldo, Registrar un pago, Agregar deuda, De dónde sale |
| 4 | Hogar | Los dos titulares; Categorías y topes, Gastos fijos, Meses anteriores, Ajustes |
| 4b | Ajustes | El mes · Gastos detectados · Sincronización · Datos · Salir del hogar |

## Color

| Uso | Claro | Oscuro |
| --- | --- | --- |
| Fondo | `#F6F4FA` | `#141219` |
| Tarjetas | `#FFFFFF` | `#1F1C26` |
| Texto | `#1C1A24` | `#ECE9F3` |
| Texto secundario | `#5E5A6B` | `#B4AFC2` |
| Acento (violeta) | `#5B3FB0` | `#B9A3FF` |
| Fondo de acento | `#ECE6FA` | `#2D2545` |
| Alerta (naranja) | `#B5541C` | `#FFB08A` |
| Titular A / B | `#5B3FB0` / `#2F7F8F` | `#B9A3FF` / `#7FC8D6` |

Colores para categorías: verde `#2E7D5B`, azul `#3A5BA0`, ámbar `#C77A12`, terracota `#B4472B`, café `#8A5A2E`,
turquesa `#1F8A8A`, fucsia `#A23B72`, oliva `#4F6D2E`, gris `#5C6670`, mostaza `#B08A1E`. Ninguno es violeta, para no
confundirse con el acento.

## Letra y formas

Manrope (incluida en `res/font`, licencia en `docs/licencias`). Títulos de pestaña 26 sp ExtraBold; montos con cifras
de ancho fijo. Esquinas: 22 dp en tarjetas, 14–16 dp en teclas y casillas, píldoras en botones. Toques de 44 dp o más.

## Código

`ui/tema/Tema.kt` (colores, letra, formas) y `ui/comun/Comunes.kt` (Tarjeta, Monto, EncabezadoPantalla,
EncabezadoVolver, FilaOpcion, GrupoAjustes, FilaAjuste, IconoCategoria, BarraProgreso, EstadoVacio, AvatarTitular).

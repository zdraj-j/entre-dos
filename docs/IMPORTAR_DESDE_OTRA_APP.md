# Importar desde otra app

Entre dos importa un `entredos-export-AAAAMMDD.zip` (formato propio, versión 1) o un CSV suelto de cualquier app.
Este documento es para agregar a otra app la función "Exportar para Entre dos". Al final está el prompt para hacerlo
con Claude Code.

## Archivos del zip

| Archivo | Una fila por | Columnas |
| --- | --- | --- |
| `gastos.csv` (obligatorio) | Gasto, o parte de un gasto dividido | `id_origen`, `fecha`, `monto`, `categoria`, `pagado_por`, `descripcion`, `comercio`, `cargo_pagador` |
| `categorias.csv` | Categoría | `nombre`, `responsable`, `presupuesto`, `color`, `destacada` |
| `deudas_abiertas.csv` | Deuda con saldo pendiente | `id_origen`, `fecha`, `deudor`, `acreedor`, `monto`, `concepto` |
| `manifest.json` | — | `formato`, `version`, `app_origen`, `exportado` |

Entre dos solo lleva gastos: no se exportan metas de ahorro ni transferencias.

## Reglas

- UTF-8; separador coma; comillas dobles cuando haga falta (RFC 4180); encabezados en minúscula y sin tildes.
- Montos enteros en pesos, sin símbolo ni puntos (`35000`), siempre mayores que cero.
- Fechas `AAAA-MM-DD` o `AAAA-MM-DDTHH:MM` en hora de Colombia. Booleanos `si` o `no`.
- Personas: el nombre tal como aparece en la otra app; Entre dos pregunta a qué titular corresponde.
- Un gasto dividido: varias filas con el mismo `id_origen` y los mismos fecha, pagado_por, descripción y comercio.
- `cargo_pagador`: solo si el reparto fue distinto al normal (personal: todo su dueño; compartida: 50/50).
- No se exportan ingresos, transferencias, devoluciones, gastos descartados ni deudas ya saldadas.
- Respaldos viejos con la columna `tipo`: las filas `transferencia` se omiten al importar.

## Qué hace Entre dos al importar

- Los gastos importados **no generan deudas**: lo pendiente entra solo por `deudas_abiertas.csv`.
- Un `id_origen` ya importado se omite: importar dos veces no duplica.
- Arma los meses anteriores y aprende reglas de comercio (opcional).
- Cada importación se puede deshacer en Hogar › Ajustes › Importar.

## Cómo probar la exportación

- [ ] El zip abre en el computador y `gastos.csv` se ve bien en una hoja de cálculo, con tildes correctas.
- [ ] En Entre dos, Importar no pide mapear columnas (reconoce el formato).
- [ ] La vista previa muestra 0 errores y la cantidad de gastos esperada.
- [ ] El total de un mes conocido coincide con el de la otra app.
- [ ] Importar el mismo zip otra vez: todo sale como ya importado.

## Prompt para Claude Code (en el proyecto de la otra app)

```markdown
Necesito agregar a esta app una función "Exportar para Entre dos". Entre dos es otra app de presupuesto en pareja que importa un archivo con un formato fijo (versión 1). Tu trabajo es generar ese archivo a partir de los datos de esta app.

## Cómo quiero que trabajes
1. Primero explora el proyecto: dónde y cómo se guardan los gastos, las categorías, las deudas entre personas, las personas, los gastos divididos en varias categorías, y cómo se distinguen las transferencias (para no exportarlas).
2. Antes de escribir código, muéstrame una tabla con cómo vas a traducir cada dato de esta app a cada columna del formato, y lista lo que no tenga equivalente o sea ambiguo. Espera a que te confirme.
3. Implementa siguiendo el estilo y la arquitectura que ya usa el proyecto. No agregues librerías para CSV ni para ZIP.
4. Agrega pruebas automáticas y una entrada visible en la app (por ejemplo en Ajustes) que diga "Exportar para Entre dos".
5. Al final, resume qué archivos cambiaste y cómo probarlo.

## Formato de salida
Un archivo `entredos-export-AAAAMMDD.zip` con estos archivos en la raíz (sin carpetas). Solo `gastos.csv` es obligatorio; incluye los demás si esta app tiene esos datos. No exportes metas ni ahorros: Entre dos no los maneja.

### Reglas comunes a todos los CSV
- UTF-8 sin BOM. Saltos de línea LF.
- Separador coma. Un campo con coma, comillas o salto de línea va entre comillas dobles y las comillas internas se duplican (RFC 4180).
- Primera línea: encabezado con los nombres exactos de columna, en minúscula y sin tildes.
- Montos: enteros en pesos colombianos, sin símbolo y sin separadores de miles. Ejemplo: 35000. Siempre mayores que cero. Si esta app guarda decimales, redondea al peso.
- Fechas: AAAA-MM-DD, o AAAA-MM-DDTHH:MM si hay hora, en hora de Colombia (America/Bogota).
- Booleanos: si o no.
- Personas: el nombre tal como aparece en esta app.
- Textos de máximo 120 caracteres.
- No exportes ingresos, transferencias (entre cuentas, al ahorro o a otras personas), recargas, devoluciones, gastos descartados o rechazados, ni gastos sin categoría.

### manifest.json
{"formato": "entredos-import", "version": 1, "app_origen": "<nombre de esta app>", "exportado": "<fecha y hora ISO 8601 con zona>"}

### gastos.csv (obligatorio)
Columnas: id_origen,fecha,monto,categoria,pagado_por,descripcion,comercio,cargo_pagador
- id_origen: id interno del gasto en esta app. Obligatorio en la práctica: evita duplicados al reimportar.
- fecha, monto, categoria (nombre), pagado_por (nombre de quien pagó): obligatorias.
- descripcion, comercio: opcionales.
- cargo_pagador: opcional. Solo si el gasto se repartió distinto a lo normal: cuánto de esa fila asumió quien pagó. Vacío = regla normal (categoría personal: todo su dueño; compartida: 50/50).
- Un gasto dividido en varias categorías va en varias filas con el mismo id_origen, cada una con el monto de su parte. Todas esas filas deben tener igual fecha, pagado_por, descripcion y comercio, y la suma de sus montos debe ser el total del gasto.

### categorias.csv (opcional)
Columnas: nombre,responsable,presupuesto,color,destacada
- nombre: igual al usado en gastos.csv.
- responsable: compartida, o el nombre de la persona dueña de la categoría.
- presupuesto: monto por periodo; vacío = sin tope.
- color: #RRGGBB; vacío si no hay.
- destacada: si o no.

### deudas_abiertas.csv (opcional)
Columnas: id_origen,fecha,deudor,acreedor,monto,concepto
- Solo deudas con saldo pendiente. monto = lo que falta, ya descontados los abonos. deudor y acreedor son nombres y deben ser distintos.

### Ejemplo de gastos.csv
id_origen,fecha,monto,categoria,pagado_por,descripcion,comercio,cargo_pagador
g-1001,2026-09-02,35000,Mercado,Pareja,Mercado de la semana,D1,
g-1002,2026-09-03T19:30,48000,Restaurantes,Zdraj,"Pizza, viernes",Domino's,
g-1003,2026-09-05,60000,Mercado,Zdraj,Compra Éxito,Éxito,
g-1003,2026-09-05,25000,Aseo,Zdraj,Compra Éxito,Éxito,
g-1004,2026-09-10,100000,Servicios,Pareja,Internet,Claro,60000

## Entrega del archivo
Un botón "Exportar para Entre dos" que genere el zip y permita guardarlo donde la persona elija o compartirlo. Muestra al final cuántos gastos, categorías y deudas se exportaron.

## Criterios de aceptación
- El zip contiene los CSV en la raíz y el manifest.json, y no contiene metas ni ahorros.
- Ningún monto es cero, negativo ni tiene separadores.
- Los gastos divididos comparten id_origen y sus partes suman el total.
- No aparecen ingresos, transferencias ni gastos descartados.
- Las pruebas cubren: escape de campos, gasto dividido, deuda con abono parcial y exclusión de ingresos y transferencias.
```

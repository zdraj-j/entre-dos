# Pruebas a mano con los dos teléfonos

Antes: `./gradlew test` (97 pruebas de la lógica pura).

## Primer uso y sincronización
1. Teléfono 1: permisos, conectar la cuenta del hogar, Crear hogar; en Hogar › Categorías y topes crear una compartida con tope.
2. Teléfono 2: la misma cuenta › Unirme al hogar. Debe ver la categoría.
3. Anotar un gasto en la compartida en el 1: en menos de un minuto aparece en Gastos del 2, y en Mes › Deudas la mitad.
4. Modo avión en los dos, anotar en ambos, quitar el modo avión: los dos ven todo.
5. Editar el mismo gasto en los dos sin red: queda la última edición.
6. Desinstalar en el 2, reinstalar, conectar, "Soy …": todo vuelve.

## Anotar
1. 12000 + 8500 y tocar una categoría: guarda $ 20.500 y baja "Quedan". Deshacer lo quita.
2. Toque largo en una compartida › "Todo yo": no hay deuda. "Mitad" con $ 2.001: la pareja debe $ 1.000.
3. Dividir: $ 100.000 en tres categorías → 33.334 / 33.333 / 33.333. Ajustar montos 60.000 / 25.000 / resto.
4. Hoy › ayer: el gasto aparece en Gastos bajo "Ayer".
5. Con más de nueve categorías aparece "Más…".

## Gastos detectados
1. Hogar › Ajustes › Diagnóstico: hacer una compra y ver el aviso del banco y su resultado.
2. El gasto aparece en Gastos › Por confirmar (recuadro naranja, con número en la pestaña), sin botones debajo. Muestra a quién le llegó, la fecha y hora, y el banco. Tocarlo abre las categorías (las sugeridas primero); tocar una lo confirma.
3. "Varias categorías o no es un gasto": elegir dos categorías → se reparte en partes iguales. Probar "No es un gasto": desaparece y no cuenta.
4. Un SMS de publicidad (oferta, crédito preaprobado, "compra hoy y gana…", con enlace o con %) no crea gasto; en Diagnóstico sale "Ignorada" o "Banco: no es gasto".
5. Después de actualizar, los gastos que ya estaban por confirmar siguen ahí (la base se migra, no se borra) y las transferencias viejas ya no aparecen ni suman.
6. Aviso del teléfono: un botón confirma sin abrir; tocar el cuerpo abre la hoja y no se cierra sin decidir.
7. Confirmar 3 veces el mismo comercio en la misma categoría: queda en Ajustes › Reglas aprendidas y la próxima vez se guarda solo.

## Mes y deudas
1. Con más del 50 % gastado en los primeros días, aparece "A este ritmo se pasarán…".
2. Tocar una categoría abre Gastos filtrado (con la ficha para quitar el filtro).
3. Mes › Deudas › Registrar un pago por menos del total: se abona a la deuda más vieja primero.
4. Cerrar el mes con deudas abiertas: avisa, salda y cierra; el otro teléfono ve el reinicio. Hogar › Meses anteriores › Descargar CSV.
5. Ajustes › El mes › Automático con el día de hoy: al día siguiente el mes se archiva solo.

## Importar y exportar
1. Importar `ejemplos/entredos-export-ejemplo.zip`: 6 gastos, 1 deuda, 3 meses anteriores (con día de inicio 1).
2. Importarlo otra vez: todo sale como ya importado.
3. Importar `ejemplos/otra-app-ejemplo.csv`: entran 2 y se omiten el salario y la transferencia.
4. Ajustes › Importar › Deshacer: desaparece en los dos teléfonos.
5. Exportar respaldo e importarlo en un hogar de prueba: queda igual.

## Wifi en casa
1. Activarlo en los dos, app abierta, misma red; desconectar el módem de internet.
2. Anotar en uno: aparece en el otro en segundos; Ajustes muestra "El otro teléfono está en la red".
3. Un teléfono de otro hogar en la misma red no recibe nada.

## Aspecto y widget
1. Revisar todas las pestañas en modo claro y oscuro; sin datos, cada pantalla dice qué hacer.
2. Widget "Entre dos": muestra lo que queda y lo de hoy; con pendientes, "N por confirmar" en naranja; "Nuevo gasto" abre Anotar.
3. Mantener presionado el ícono › "Nuevo gasto".

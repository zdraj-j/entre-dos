# Pruebas a mano con los dos teléfonos

## Primer uso y sincronización
1. Teléfono 1: dar acceso a notificaciones, conectar la cuenta del hogar, crear hogar y una categoría compartida.
2. Teléfono 2: la misma cuenta › Unirme al hogar. Debe ver la categoría.
3. Registrar un gasto compartido en el 1: en menos de un minuto aparece en el 2 con la deuda del 50 %.
4. Modo avión en ambos, registrar en los dos, quitar el modo avión: los dos ven todo.
5. Editar el mismo gasto en ambos sin red: queda la última edición.
6. Desinstalar en el 2, reinstalar, conectar, "Soy …": todo vuelve.

## Teclado
1. 12000 + 8500 y tocar una categoría: guarda $ 20.500. Deshacer lo quita.
2. Toque largo en una compartida › "Todo yo": sin deuda. "Mitad" con $ 2.001: la pareja debe $ 1.000.
3. Varias categorías con $ 100.000 en tres: 33.334 / 33.333 / 33.333. Ajustar montos 60.000 / 25.000 / resto.

## Detección
1. Ajustes › Diagnóstico: hacer una compra y revisar que el aviso del banco aparezca y su resultado.
2. Aviso "Gasto detectado": un botón clasifica sin abrir; tocar el cuerpo abre la ventana obligatoria.
3. Confirmar 3 veces el mismo comercio en la misma categoría: la regla queda automática en Detección.

## Periodos y deudas
1. Resumen › Cerrar y archivar con deudas abiertas: avisa, cierra y salda; el otro teléfono ve el reinicio.
2. Periodos anteriores › abrir el cerrado › Descargar CSV.
3. Modo automático: cambiar el día de inicio a hoy y reabrir la app al día siguiente: se archiva solo.

## Importar y exportar
1. Importar `ejemplos/entredos-export-ejemplo.zip`: 7 gastos, 1 deuda, 1 meta, 3 periodos anteriores (julio, agosto y septiembre, con día de inicio 1).
2. Importarlo otra vez: todo sale como ya importado.
3. Importar `ejemplos/otra-app-ejemplo.csv`: entran 3 y se omite el salario.
4. Ajustes › Importaciones › Deshacer: desaparece en los dos teléfonos.
5. Ajustes › Exportar respaldo e importarlo en un hogar de prueba: debe quedar igual.

## Wifi en casa
1. Activar "Sincronizar por wifi en casa" en los dos teléfonos, con la app abierta y en la misma red.
2. Desconectar el módem de internet (dejando el wifi encendido) o activar el modo avión y volver a encender solo el wifi.
3. Registrar un gasto en uno: en unos segundos aparece en el otro. Ajustes muestra "Último intercambio".
4. "Intercambiar ahora" funciona desde cualquiera de los dos.
5. Con un tercer teléfono de otro hogar en la misma red: no intercambia nada (Ajustes no muestra pareja visible o muestra el error de autenticación).
6. Al volver internet, todo sube a Drive igual: reinstalar en un teléfono y recuperar con "Soy …" trae también lo que se pasó por wifi.

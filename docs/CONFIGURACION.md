# Configuración de la cuenta de Google y de Google Cloud

La guía completa, con capturas de los pasos y solución de problemas, está en el documento
"Entre dos — Cuenta de Google y exportación desde la otra app". Este es el resumen.

## Cuenta del hogar

1. Crea una cuenta de Gmail solo para la app (`entredos.<apellidos>@gmail.com`).
2. Correo de recuperación de uno, teléfono del otro; verificación en dos pasos con códigos de respaldo impresos.
3. No la uses para nada más e inicia sesión al menos una vez al año.

## Google Cloud (con la sesión de la cuenta del hogar)

1. `./gradlew signingReport` → copia la SHA-1 de `Variant: debug`.
2. console.cloud.google.com → proyecto "Entre dos".
3. APIs y servicios › Biblioteca › Google Drive API › Habilitar.
4. Google Auth Platform › Comenzar: nombre "Entre dos", público Externo.
5. Acceso a los datos: agrega `https://www.googleapis.com/auth/drive.appdata` (no sensible).
6. Clientes › Crear cliente › Android: paquete `com.entredos`, la SHA-1.
7. Público › Publicar app (en estado "Prueba" el permiso vence cada 7 días).

No hay archivo que copiar al proyecto: Google reconoce la app por paquete y firma.

## En los teléfonos

- Teléfono 1: Conectar cuenta del hogar › Crear hogar.
- Teléfono 2: la misma cuenta › Unirme al hogar.
- Reinstalar: la misma cuenta › "Soy …".

No borres los "datos ocultos de la app" en la configuración de Drive: ahí está todo el historial.

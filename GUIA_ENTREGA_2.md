# Entre dos — Entrega 2: sincronización por Drive y recuperación

Al terminar esta entrega los dos teléfonos comparten todo por la carpeta oculta de la app en
el Drive de la cuenta del hogar, y quien reinstala recupera su lugar tocando "Soy …".
No hay códigos de invitación: lo que comparten es la cuenta de Google del hogar.

## Qué trae este paquete (además de la entrega 1)

| Archivo | Para qué |
| --- | --- |
| `core/sync/Modelos.kt` | Cambio, archivo de cambios, foto, nombres de archivo e interfaces del motor. |
| `core/sync/MotorSync.kt` | Subir, bajar y compactar. Lógica pura, probada con dos teléfonos simulados. |
| `data/sync/RoomBaseLocal.kt` | El motor visto desde Room: pendientes, aplicar con LWW, foto. |
| `data/sync/JsonCodec.kt` | Formato JSON de los archivos en Drive (versión 1). |
| `data/sync/DriveAlmacen.kt` | API REST de Drive v3 con HttpURLConnection, sin librerías extra. |
| `data/sync/CuentaHogar.kt` | Permiso `drive.appdata` con Google Identity Services. |
| `data/sync/EstadoSync.kt` | Correo conectado, última sincronización, último error. |
| `data/sync/Sincronizador.kt` | Evita dos sincronizaciones a la vez y registra el resultado. |
| `data/sync/TrabajoSync.kt` | WorkManager: cada 15 minutos y 3 segundos después de cada cambio. |
| `data/sync/SincronizacionAutomatica.kt` | Dispara la sincronización al abrir la app y al haber cambios. |
| `data/sync/ServicioHogar.kt` | Crear hogar, "Soy …", unirse como titular B y salir del hogar. |
| `ui/hogar/...` | Pantalla de primer uso y bloque de sincronización para Ajustes (sin estilo final). |
| `di/SyncModule.kt` | Hilt. |

Cambios sobre la entrega 1: la base pasa a la versión 2 (borra los datos de prueba),
`cambio_pendiente` guarda dispositivo y borrado, `dispositivo_local` guarda la última secuencia
subida, y cada DAO tiene `todosConEliminados()` para armar fotos.

**Probado aquí:** 29 pruebas de la lógica pura, incluidas reinstalar, editar lo mismo sin red en
los dos teléfonos, un celular con la hora atrasada, borrados, compactación con un teléfono que
estuvo sin red y una subida cortada a la mitad. **No probado aquí:** Room, Drive real, permisos
de Google y WorkManager.

## 1. Google Cloud (una sola vez, unos 15 minutos)

1. En [console.cloud.google.com](https://console.cloud.google.com) crea un proyecto "Entre dos".
2. APIs y servicios › Biblioteca › **Google Drive API** › Habilitar.
3. Pantalla de consentimiento de OAuth: tipo **Externo**, nombre "Entre dos", tu correo de soporte.
   En alcances agrega solo `.../auth/drive.appdata` (no es sensible).
4. Estado de publicación: **En producción**. Si lo dejas "En prueba", agrega los correos como
   usuarios de prueba; ojo: en ese modo Google suele vencer el permiso cada 7 días y la app
   pedirá "Volver a conectar".
5. Credenciales › Crear ID de cliente de OAuth › tipo **Android**: paquete `com.entredos` y la
   huella SHA-1. La de depuración sale con `./gradlew signingReport`. Cuando firmes la versión
   final, crea otro ID de cliente con la SHA-1 de esa firma.

No hay que copiar ningún archivo al proyecto: Google reconoce la app por paquete y firma.

## 2. Cuenta del hogar

Crea la cuenta de Gmail del hogar. En cada teléfono, al tocar "Conectar cuenta del hogar",
Google muestra sus cuentas y permite agregar otra: ahí se elige o se agrega la del hogar.

## 3. Dependencias

```kotlin
implementation("com.google.android.gms:play-services-auth:21.2.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
implementation("androidx.work:work-runtime-ktx:2.9.1")
implementation("androidx.hilt:hilt-work:1.2.0")
ksp("androidx.hilt:hilt-compiler:1.2.0")            // o kapt(...) si el proyecto usa KAPT
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
implementation("androidx.lifecycle:lifecycle-process:2.8.4")
```
Alinea las versiones con tu catálogo. Si `AuthorizationRequest.Builder.setAccount` no aparece,
sube `play-services-auth` a la versión más reciente.

## 4. Application y manifiesto

```kotlin
@HiltAndroidApp
class EntreDosApp : Application(), Configuration.Provider {
    @Inject lateinit var fabricaTrabajos: HiltWorkerFactory
    @Inject lateinit var sincronizacion: SincronizacionAutomatica

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()

    override fun onCreate() {
        super.onCreate()
        sincronizacion.iniciar()
    }
}
```

En `AndroidManifest.xml`: permiso `android.permission.INTERNET` y, dentro de `<application>`,
quitar el inicializador automático de WorkManager (lo hace Hilt):

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

## 5. Navegación

- Al abrir la app: si `EstadoSync.correo` es null o `DispositivoActual.miembro()` es null,
  se muestra `PantallaInicioHogar`; si no, el teclado.
- En Ajustes, agrega `SeccionSincronizacion`. "Volver a conectar" y "Salir del hogar" llevan a
  `PantallaInicioHogar`.
- Quita de la app todo lo del código de invitación y el "Crear hogar" temporal de la entrega 1.

## 6. Pruebas con los dos teléfonos

1. Teléfono 1: conectar la cuenta del hogar, crear hogar, registrar un gasto compartido.
2. Teléfono 2: conectar la misma cuenta, "Unirme al hogar". Debe ver el gasto y la deuda del 50 %.
3. Modo avión en los dos, registrar gastos en ambos, quitar el modo avión: en menos de un
   minuto los dos deben ver todo.
4. Editar el mismo gasto en los dos sin red: al sincronizar queda la última edición.
5. Desinstalar la app del teléfono 2, reinstalar, conectar y tocar "Soy …": todo debe volver.
6. Salir del hogar con modo avión y un gasto sin subir: debe salir el aviso.

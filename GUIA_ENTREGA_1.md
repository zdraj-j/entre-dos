# Entre dos — Entrega 1: base local sin Firebase

Al terminar esta entrega la app funciona 100 % en el teléfono, sin Firebase, con el modelo
de datos nuevo y el cálculo de deudas por partes. La sincronización por Drive llega en la entrega 2.

## Qué trae este paquete

| Archivo | Para qué |
| --- | --- |
| `core/Tipos.kt` | Enums: Miembro, Responsable, tipo, estado y origen del gasto, etc. |
| `core/Reparto.kt` | Reparto de cada parte entre A y B (personal, 50/50 o ajustado) y división en partes. |
| `core/CalculadoraDeudas.kt` | Deudas a partir de las partes, deudas manuales y abonos; balance; saldar todo. |
| `core/sync/Hlc.kt`, `Lww.kt` | Reloj lógico híbrido y regla "gana el más reciente". |
| `data/local/entity/Entidades.kt` | Las 16 tablas de Room (13 sincronizables, 3 locales). |
| `data/local/dao/Daos.kt` | Consultas. |
| `data/local/EntreDosDatabase.kt` | Base `entre_dos.db`, versión 1. |
| `data/local/Escritor.kt` | Única puerta de escritura: sella con el reloj y deja el cambio por subir. |
| `data/local/Tablas.kt` | Registro de tablas (nombre, serializador, upsert, porId). |
| `data/local/DispositivoActual.kt` | Id anónimo del teléfono y titular que lo usa (reemplaza Firebase Auth). |
| `data/repository/GastosRepositorio.kt` | Registrar, editar y eliminar gastos con sus partes. |
| `data/repository/DeudasRepositorio.kt` | Deudas en vivo, abonar, saldar todo, deuda manual. |
| `di/BaseDatosModule.kt` | Hilt: base, reloj y DAO. |
| `res/xml/reglas_respaldo.xml`, `respaldo_antiguo.xml` | Sacan la base de la copia automática de Android. |
| `test/...` | 20 pruebas de la lógica pura (pasan). |

La lógica pura (`core/`) se compiló y probó con Kotlin 2.0. La parte de Android (Room, Hilt)
no se pudo compilar fuera de Android Studio: es lo primero que hay que revisar.

## Pasos

1. **Respaldo.** En la app actual: Movimientos › Exportar CSV. Guarda el archivo; se cargará
   con el importador en la entrega 4. Haz commit de todo y crea una rama: `git checkout -b entre-dos`.

2. **Nombre y paquete.** En Android Studio: clic derecho en el paquete `com.budgetcouple` ›
   Refactor › Rename › `com.entredos` (todo el directorio). En `app/build.gradle.kts`:
   `namespace = "com.entredos"` y `applicationId = "com.entredos"`. En `strings.xml`:
   `<string name="app_name">Entre dos</string>`. Como cambia el applicationId, se instala
   como app nueva y la vieja sigue ahí con sus datos.

3. **Quitar Firebase por completo.**
   - En el `build.gradle.kts` raíz y en el de `app`: borra el plugin `com.google.gms.google-services`.
   - En `app/build.gradle.kts` (o en `libs.versions.toml`): borra `firebase-bom`, `firebase-auth`,
     `firebase-firestore` y cualquier otra `firebase-*`.
   - Borra `app/google-services.json`.
   - Borra `AuthManager`, `SyncManager`, el `provideFirestore` de `DataModule` y todo el código
     Firestore de `HouseholdRepository`.
   - Comprobación: buscar `com.google.firebase` y `google-services` en el proyecto no debe dar resultados.

4. **Dependencias nuevas** (alinea las versiones con tu catálogo; la de serialization debe ser
   la misma de tu Kotlin):
   ```kotlin
   plugins { kotlin("plugin.serialization") version "<tu versión de Kotlin>" }
   dependencies {
       implementation("androidx.room:room-runtime:2.6.1")
       implementation("androidx.room:room-ktx:2.6.1")
       ksp("androidx.room:room-compiler:2.6.1")      // o kapt(...) si el proyecto usa KAPT
       implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
       testImplementation("junit:junit:4.13.2")
   }
   ksp { arg("room.schemaLocation", "$projectDir/schemas") }   // con KAPT: kapt { arguments { ... } }
   ```

5. **Copiar los archivos** de este paquete dentro de `app/src/` respetando las carpetas.

6. **Reemplazar la capa de datos vieja.** Borra `AppDatabase`, las entidades y DAO viejos y su
   `DataModule`, y apunta los ViewModels y casos de uso a lo nuevo:

   | Antes | Ahora |
   | --- | --- |
   | `MovementEntity` + splits en JSON | `GastoEntity` + `ParteGastoEntity` (una fila por categoría) |
   | `DebtEntity` | Se calcula: `DeudasRepositorio.observar()`; las manuales son `DeudaManualEntity` |
   | `CategoryEntity` + `BudgetEntity` | `CategoriaEntity` (el presupuesto es un campo) |
   | `MerchantRuleEntity` | `ReglaComercioEntity` |
   | `RecurringExpenseEntity` | `RecurrenteEntity` |
   | `PeriodSummaryEntity` | `PeriodoCerradoEntity` |
   | uid de Firebase | `DispositivoActual.id()` y `DispositivoActual.miembro()` |

   Regla de oro: toda escritura pasa por `Escritor` (o por los repositorios que lo usan),
   nunca por `upsert` directo. Si no, ese cambio no llegará a la pareja en la entrega 2.

7. **Crear hogar (temporal, hasta la entrega 2).** El flujo de "Crear hogar" guarda con el
   `Escritor` un `HogarEntity`, un `MiembroEntity(id = "A", nombre = ...)` y llama
   `DispositivoActual.asignarMiembro(Miembro.A)`. Deja ocultos "Unirse con código" y
   "Recuperar acceso": vuelven con Drive.

8. **Copia automática de Android.** En el `<application>` del `AndroidManifest.xml`:
   ```xml
   android:dataExtractionRules="@xml/reglas_respaldo"
   android:fullBackupContent="@xml/respaldo_antiguo"
   ```

9. **Probar.** `./gradlew test` (deben pasar las 20 pruebas) y luego compilar e instalar.
   Prueba a mano: registrar un gasto en categoría compartida y ver la deuda del 50 %, abonar,
   saldar todo, editar el nombre de un gasto (la deuda sigue) y editar el monto (la deuda cambia).

## Pendiente para la entrega 2

Inicio de sesión con la cuenta del hogar (permiso `drive.appdata`), subida de
`cambio_pendiente` a archivos `cambios-<dispositivo>-<secuencia>.json`, aplicador de cambios
remotos con `Lww` y `Tablas.porNombre`, fotos y recuperación con "Soy …".

# PixelPals

[Español](#español) | [English](#english)

Privacy Policy / Politica de Privacidad: [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

---

## Español

PixelPals es una app Android de mascotas virtuales flotantes que viven sobre la pantalla mediante una superposición (overlay).  
Cada mascota tiene lógica de movimiento propia, animaciones, interacción táctil y progreso persistente.

### Características principales

- Mascotas flotantes con comportamientos personalizados por personaje.
- Servicio en primer plano para mantener el pet activo sobre otras apps.
- Sistema de interacción táctil (tap, arrastre y reacción).
- Álbum de tesoros con persistencia local (Room + `SharedPreferences`).
- Progreso de mascota (XP, minutos activos, interacciones y eventos).

### Stack técnico

- Kotlin
- `compileSdk 36`, `targetSdk 36`, `minSdk 26`
- Room
- Coroutines
- Foreground Service + Overlay (`SYSTEM_ALERT_WINDOW`)
- Release con R8; `shrinkResources` desactivado por frames cargados dinámicamente (`getIdentifier()`)

### Estructura del proyecto

- `app/src/main/java/com/PixelPals/app/`: Activities, `PetService`, `PetView`, progreso y flujo principal.
- `app/src/main/java/com/PixelPals/app/behavior/`: lógica por mascota (`Bloop`, `NubeMichi`, `Ginger`, `Jelly`, `Duck`, etc.).
- `app/src/main/java/com/PixelPals/app/database/`: entidades Room y DAOs del álbum de tesoros.
- `app/src/main/java/com/PixelPals/app/launcher/`: utilidades de movimiento y soporte auxiliar del launcher.
- `app/src/main/res/`: recursos visuales, drawables y layouts.

### Versiones publicadas (referencia)

Definidas en [`app/build.gradle.kts`](app/build.gradle.kts):

- `versionName`: `1.0.1`
- `versionCode`: `2` (Play exige subir el código en cada nuevo envío)

### Requisitos

- JDK 17
- Android SDK (instalar la plataforma de `compileSdk`, p. ej. Android API 36)
- Gradle Wrapper incluido (`./gradlew`)
- Dispositivo físico o emulador Android 8.0+ (`minSdk` 26).

### Configuración local

Crea o verifica `local.properties`:

```properties
sdk.dir=/ruta/a/Android/Sdk
```

### Build e instalación

Debug:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Release:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Salidas típicas:

- APK: `app/build/outputs/apk/release/`
- AAB: `app/build/outputs/bundle/release/`

### Permisos sensibles

- `SYSTEM_ALERT_WINDOW`: necesario para mostrar la mascota flotante.
- `POST_NOTIFICATIONS`: para notificaciones (Android 13+).
La app no está diseñada para capturar contraseñas, texto sensible ni contenido privado de otras apps.

### Google Play Submission Notes (ES)

Texto recomendado para la ficha o formulario de revisión:

**Uso de superposición (`SYSTEM_ALERT_WINDOW`)**
- La función principal de la app es mostrar una mascota virtual flotante en pantalla.
- Este permiso se usa exclusivamente para dibujar la mascota y sus animaciones sobre otras apps.
- No se utiliza para leer contenido de otras apps, capturar texto ni interactuar con contraseñas.

**Declaración de privacidad**
- No se venden datos personales.
- El usuario puede desactivar permisos en cualquier momento desde Ajustes.
- Si no se concede overlay, la app puede funcionar con experiencia limitada.

### Estado del proyecto

V1 estable; versiones recientes alineadas con **Google Play** (`targetSdk 36`, documentación y política de privacidad).
Mejoras incrementales en V1.1+.

---

## English

PixelPals is an Android virtual pet app that displays floating pets on top of other apps using an overlay.  
Each pet has its own movement logic, animation set, touch interactions, and persistent progression.

### Main features

- Floating pets with character-specific behavior and animations.
- Foreground service to keep pets active above other apps.
- Touch interactions (tap, drag, and reaction states).
- Treasure album with local persistence (Room + `SharedPreferences`).
- Pet progression (XP, active minutes, interactions, and events).

### Tech stack

- Kotlin
- `compileSdk 36`, `targetSdk 36`, `minSdk 26`
- Room
- Coroutines
- Foreground Service + Overlay (`SYSTEM_ALERT_WINDOW`)
- Release with R8; `shrinkResources` disabled for dynamically loaded frames (`getIdentifier()`)

### Project structure

- `app/src/main/java/com/PixelPals/app/`: Activities, `PetService`, `PetView`, progress, and main flow.
- `app/src/main/java/com/PixelPals/app/behavior/`: per-pet behavior logic (`Bloop`, `NubeMichi`, `Ginger`, `Jelly`, `Duck`, etc.).
- `app/src/main/java/com/PixelPals/app/database/`: Room entities and DAOs for treasure album.
- `app/src/main/java/com/PixelPals/app/launcher/`: launcher helpers and auxiliary movement support.
- `app/src/main/res/`: visual resources, drawables, and layouts.

### Published versions (reference)

Defined in [`app/build.gradle.kts`](app/build.gradle.kts):

- `versionName`: `1.0.1`
- `versionCode`: `2` (Play requires a higher code for each new upload)

### Requirements

- JDK 17
- Android SDK (install the `compileSdk` platform, e.g. Android API 36)
- Included Gradle Wrapper (`./gradlew`)
- Android 8.0+ device or emulator (`minSdk` 26).

### Local setup

Create or verify `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

### Build and install

Debug:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Release:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Typical outputs:

- APK: `app/build/outputs/apk/release/`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

### Google Play (AAB)

Current Play policy requires **minimum target API 36**; this project uses `targetSdk 36`.

- Build: `./gradlew :app:bundleRelease`
- Unsigned output is usually `app-release.aab` in the folder above.
- **Do not upload** an AAB signed only with the **debug** key; Play will reject it.
- Sign with your **release keystore** (or configure `signingConfigs.release` in Gradle and use Play App Signing).
- Each upload needs a **higher `versionCode`** than the last one accepted in the console.

### Sensitive permissions

- `SYSTEM_ALERT_WINDOW`: required to render floating pets.
- `POST_NOTIFICATIONS`: notifications support (Android 13+).
The app is not intended to capture passwords, sensitive text, or private content from other apps.

### Google Play Submission Notes (EN)

Suggested wording for store listing or review forms:

**Overlay permission (`SYSTEM_ALERT_WINDOW`)**
- The app's core feature is displaying a floating virtual pet on top of other apps.
- This permission is used strictly to render the pet and its animations.
- It is not used to read other apps' content, capture text, or interact with passwords.

**Privacy statement**
- No personal data is sold.
- Users can revoke permissions at any time in system settings.
- If overlay permission is denied, the app can still run with limited experience.

### Project status

V1 stable; recent releases aligned with **Google Play** (`targetSdk 36`, docs and privacy policy).
Incremental improvements in V1.1+.

# PixelPals (ScreenPets)

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
- Integración opcional con `AccessibilityService` para movimientos avanzados en launcher.

### Stack técnico

- Kotlin
- Android SDK 34 (`minSdk 26`)
- Room
- Coroutines
- Foreground Service + Overlay (`SYSTEM_ALERT_WINDOW`)

### Estructura del proyecto

- `app/src/main/java/com/pixelpals/app/`: Activities, `PetService`, `PetView`, progreso y flujo principal.
- `app/src/main/java/com/pixelpals/app/behavior/`: lógica por mascota (`Bloop`, `NubeMichi`, `Ginger`, `Jelly`, `Duck`, etc.).
- `app/src/main/java/com/pixelpals/app/database/`: entidades Room y DAOs del álbum de tesoros.
- `app/src/main/java/com/pixelpals/app/launcher/`: soporte de accesibilidad para plataformas del launcher.
- `app/src/main/res/`: recursos visuales, drawables y layouts.

### Requisitos

- JDK 17
- Android SDK (platform 34)
- Gradle Wrapper incluido (`./gradlew`)
- Dispositivo físico o emulador Android 8.0+.

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
- `BIND_ACCESSIBILITY_SERVICE` (opcional): usado para funciones avanzadas de movimiento en launcher.

La app no está diseñada para capturar contraseñas, texto sensible ni contenido privado de otras apps.

### Google Play Submission Notes (ES)

Texto recomendado para la ficha o formulario de revisión:

**Uso de superposición (`SYSTEM_ALERT_WINDOW`)**
- La función principal de la app es mostrar una mascota virtual flotante en pantalla.
- Este permiso se usa exclusivamente para dibujar la mascota y sus animaciones sobre otras apps.
- No se utiliza para leer contenido de otras apps, capturar texto ni interactuar con contraseñas.

**Uso de accesibilidad (si está habilitado)**
- El servicio de accesibilidad es opcional y se usa solo para detectar posiciones de iconos del launcher y mejorar trayectorias de movimiento.
- No se lee ni se almacena contenido sensible del usuario.

**Declaración de privacidad**
- No se venden datos personales.
- El usuario puede desactivar permisos en cualquier momento desde Ajustes.
- Si no se concede overlay, la app puede funcionar con experiencia limitada.

### Estado del proyecto

V1 completada y estabilizada.  
Actualmente se trabaja en mejoras incrementales (V1.1+) y preparación para publicación en Google Play.

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
- Optional `AccessibilityService` integration for advanced launcher-based movement.

### Tech stack

- Kotlin
- Android SDK 34 (`minSdk 26`)
- Room
- Coroutines
- Foreground Service + Overlay (`SYSTEM_ALERT_WINDOW`)

### Project structure

- `app/src/main/java/com/pixelpals/app/`: Activities, `PetService`, `PetView`, progress, and main flow.
- `app/src/main/java/com/pixelpals/app/behavior/`: per-pet behavior logic (`Bloop`, `NubeMichi`, `Ginger`, `Jelly`, `Duck`, etc.).
- `app/src/main/java/com/pixelpals/app/database/`: Room entities and DAOs for treasure album.
- `app/src/main/java/com/pixelpals/app/launcher/`: accessibility support for launcher platform detection.
- `app/src/main/res/`: visual resources, drawables, and layouts.

### Requirements

- JDK 17
- Android SDK (platform 34)
- Included Gradle Wrapper (`./gradlew`)
- Android 8.0+ device or emulator.

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
- AAB: `app/build/outputs/bundle/release/`

### Sensitive permissions

- `SYSTEM_ALERT_WINDOW`: required to render floating pets.
- `POST_NOTIFICATIONS`: notifications support (Android 13+).
- `BIND_ACCESSIBILITY_SERVICE` (optional): used for advanced launcher movement features.

The app is not intended to capture passwords, sensitive text, or private content from other apps.

### Google Play Submission Notes (EN)

Suggested wording for store listing or review forms:

**Overlay permission (`SYSTEM_ALERT_WINDOW`)**
- The app's core feature is displaying a floating virtual pet on top of other apps.
- This permission is used strictly to render the pet and its animations.
- It is not used to read other apps' content, capture text, or interact with passwords.

**Accessibility usage (if enabled)**
- Accessibility Service is optional and only used to detect launcher icon positions for improved movement trajectories.
- No sensitive user content is read or stored.

**Privacy statement**
- No personal data is sold.
- Users can revoke permissions at any time in system settings.
- If overlay permission is denied, the app can still run with limited experience.

### Project status

V1 is completed and stabilized.  
Current focus is incremental hardening (V1.1+) and Google Play readiness.

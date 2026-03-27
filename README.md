# PixelPals

PixelPals es una app Android de mascotas virtuales flotantes que viven sobre el escritorio mediante una superposición. Cada pet tiene su propia lógica de movimiento, animaciones, interacción táctil y progreso persistente.

## Características

- Mascotas flotantes con comportamientos y animaciones por personaje.
- Servicio en primer plano para mantener el pet activo sobre otras apps.
- Álbum de tesoros con persistencia local usando Room y `SharedPreferences`.
- Sistema de progreso con XP, tiempo activo e interacciones.
- Soporte para detección de iconos del launcher mediante `AccessibilityService` para movimientos especiales.

## Requisitos

- Android Studio o entorno con Gradle
- JDK 17
- Android SDK 34
- Dispositivo o emulador con Android 8.0+ (`minSdk 26`)

## Ejecutar en desarrollo

1. Crea o verifica `local.properties` con tu SDK:

```properties
sdk.dir=/ruta/a/Android/Sdk
```

2. Compila el proyecto:

```bash
./gradlew :app:assembleDebug
```

3. Instala en un dispositivo conectado:

```bash
./gradlew :app:installDebug
```

## Estructura general

- `app/src/main/java/com/pixelpals/app/`: actividades, servicio principal y vista del pet.
- `app/src/main/java/com/pixelpals/app/behavior/`: lógicas individuales de cada mascota.
- `app/src/main/java/com/pixelpals/app/database/`: entidades y acceso a datos del álbum de tesoros.
- `app/src/main/java/com/pixelpals/app/launcher/`: integración con accesibilidad para detectar plataformas del launcher.

## Estado actual

El proyecto está en desarrollo activo, con ajustes frecuentes en animaciones, movimiento de mascotas y funcionamiento del álbum de tesoros.

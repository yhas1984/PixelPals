# PixelPals 2.5.0 (22): candidata de pruebas internas

Esta entrega reúne el compañero local, hogar y decoración táctil, aventuras y recuerdos, tienda y cosméticos, y las correcciones actuales de anatomía, objetos, cámara y movimiento. Es una candidata para pruebas internas de Google Play; el alcance artístico y la aceptación integral pendientes se mantienen en `CLOSURE-2026-09-12.md`.

## Artefactos y configuración

- AAB: `artifacts/internal/2.5.0/PixelPals-2.5.0-22-internal.aab`.
- SHA-256 del AAB: `6ff89a5cdc7032c75afa5fa1a3793611137d5e805d74c743e6bb9266c99c08b8`.
- Aplicación release: `com.pixelpals.app`, versión `2.5.0`, código `22`, sin depuración y firmada con el almacén release existente.
- La candidata se compila con `-Ppixelpals.companion.releaseCandidate=true`: incluye los 40 archivos actuales de `carePreview` y activa sus cuidados. Los PNG/JSON del AAB coinciden byte a byte con esas fuentes.
- Conserva anuncios de apertura, banner y compras de Google Play. El simulador de compras sigue limitado a debug.
- La instalación física realizada corresponde a `com.pixelpals.app.debug`, versión `2.5.0-debug`/22; conserva los 16 archivos de bases de datos y preferencias comparados antes y después de instalar. La pantalla de hogar se abrió con Jelly seleccionada.
- La rama `legacy/pixelpals-classic` conserva el punto anterior. La entrega actual se propone mediante PR y no se publica en producción.

## Verificación de esta compilación

- Compilación de APK debug, APK release y AAB release: correcta.
- JVM: 335 pruebas, sin fallos ni omisiones.
- Android: 24 pruebas de movimiento y descanso; 11 de navegación, migraciones y compra local de cosmético. Todas correctas en emulador API 26.
- Python: 57 casos únicos. La primera ejecución detectó una expectativa antigua de Taro; después se repitieron correctamente los seis casos del módulo afectado. El test exige conservar íntegro el arte aprobado y permite únicamente el cambio intencional `sleep.loop=false` en los metadatos de ejecución.
- Validador de assets: 15 mascotas y 280 frames de producción; esta cifra no incluye todos los recursos optativos ni acredita aceptación artística.
- Lint debug: cero errores, 247 advertencias. Lint release: cero errores, 221 advertencias.
- Firma del AAB: `jarsigner` informa `jar verified`; firma del APK release verificada con `apksigner`.
- Ginger: las expectativas de levantarse/sentarse vuelven a usar los frames originales de esas transiciones; el descanso y despertar mantienen sus nuevas pruebas independientes. No se alteró el comportamiento para ajustarlo a una expectativa de sueño equivocada.

La evidencia detallada y los volcados del dispositivo se conservan localmente en `docs/companion/evidence/internal-2.5.0/`. Las copias de datos, claves, capturas privadas y artefactos de compilación no se versionan.

## Reproducción

Con la configuración de firma del proyecto cargada en el entorno, ejecutar:

```bash
GRADLE_USER_HOME="$PWD/.gradle-local" ./gradlew --no-daemon --max-workers=2 \
  :app:assembleRelease :app:bundleRelease :app:lintRelease \
  -Ppixelpals.companion.releaseCandidate=true
```

La firma usa `PIXELPALS_KEYSTORE_FILE`, `PIXELPALS_KEYSTORE_PASSWORD`, `PIXELPALS_KEYSTORE_ALIAS` y `PIXELPALS_KEY_PASSWORD`. No guardar valores en Git ni pasarlos como argumentos visibles. Los APK debug usan el identificador `.debug`; el AAB mantiene el identificador existente de Play.

## Pendientes de aceptación

Compras y restauración desde una instalación de la pista de Play, revisión completa de las 15 mascotas, combinaciones restantes de navegación/permisos/accesibilidad, y pruebas físicas de batería, bloqueo y orientación. El ensayo físico previo de 30 minutos pertenece a una candidata anterior y no se presenta como validación de esta compilación. La subida a Play y la publicación en producción no se han realizado.

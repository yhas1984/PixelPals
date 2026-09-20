# PixelPals 2.5.1 (23): candidata de continuidad

Continúa la PR #17, ya integrada en `main`. La rama de esta entrega es
`codex/pixelpals-2.5.1-continuity`; `legacy/pixelpals-classic` conserva la versión
anterior de la experiencia. No se realiza una publicación de producción.

## Cambios incluidos

- Ginger tiene dos nuevos dibujos para girar sobre sus apoyos, con la misma
  secuencia en hogar y escritorio. El descanso de cuidados reutiliza sus poses
  nativas de ovillo; la cámara conserva la anatomía y la cama mantiene su tamaño
  al despertar. Con movimiento reducido se mantiene la pose dormida.
- Menta recupera la paleta menta de su retrato. Ángel usa una calibración fija
  para aproximar la cabeza y el halo al tamaño de su movimiento normal.
- Patito anticipa el despegue desde el suelo; al estar en el aire conserva su
  posición antes de abrir las alas. Al terminar cuidados, las mascotas conservan
  su orientación en el escritorio.
- El idioma elegido se conserva al consultar el repositorio sin pantallas abiertas
  en Android 13 o posterior.
- El repositorio comprueba que el cosmético existe y pertenece al usuario antes
  de equiparlo. Comprar y equipar conserva la mascota seleccionada.
- Las pruebas de catálogo exportan las seis acciones de las 15 especies en
  modo normal y reducido, incluyendo la carrera nativa y pelota de Corgi.

No se añaden productos de dinero real, suscripciones ni permisos obligatorios.
Se conservan el monedero, las compras, las migraciones y los anuncios existentes.

## Artefactos

| Archivo local | SHA-256 |
| --- | --- |
| `artifacts/internal/2.5.1/PixelPals-2.5.1-23-internal.aab` | `db458836ad7b722e8e5a748953a57c1d822f0b2a8bf3a0b010f5947e16bcb375` |
| `artifacts/internal/2.5.1/PixelPals-2.5.1-23-release.apk` | `0d9eb68f75886c16f39143f0052b0a37f005f8ff0efc7d77bafcbe361d776cc6` |
| `artifacts/internal/2.5.1/PixelPals-2.5.1-23-debug.apk` | `e294540f8da78ad1c3fe386341b8f89808c885bd5d5ad15af2f30698b737511a` |

El AAB y el APK release usan `com.pixelpals.app`, versión 2.5.1/código 23, sin
depuración. `jarsigner` verifica el AAB; `apksigner` verifica el APK. El
certificado del AAB coincide con el de 2.5.0. Los 42 archivos de `carePreview`
incluidos en el AAB coinciden byte a byte con las fuentes. El APK debug usa
`com.pixelpals.app.debug` y anuncios de prueba.

## Validación

- APK debug, APK release y AAB release compilados correctamente.
- JVM: 336 pruebas, cero fallos, errores u omisiones.
- Herramientas Python: 59 pruebas correctas.
- Assets base: 15 mascotas, 280 frames, sin duplicados exactos ni huérfanos.
- Lint debug: cero errores, 249 advertencias; release: cero errores, 223 advertencias.
- Android API 35: 361 casos únicos correctos, consolidados tras repeticiones
  focalizadas; API 26: 15 casos focalizados correctos con el APK final.
- Resultados Android y revisión de pantallas: se consolidan en
  [VISUAL-CLOSURE-2026-09-20.md](VISUAL-CLOSURE-2026-09-20.md).

Las evidencias se conservan localmente en `docs/companion/evidence/final-2.5.1/`.
Los artefactos, claves y capturas de dispositivos no se versionan.

## Reproducción

Con la configuración de firma existente cargada en el entorno:

```bash
GRADLE_USER_HOME="$PWD/.gradle-local" ./gradlew --no-daemon --max-workers=2 \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:assembleRelease :app:bundleRelease :app:lintDebug :app:lintRelease \
  -Ppixelpals.companion.releaseCandidate=true -Ppixelpals.ads.debugAppOpen=true
```

La opción de candidata incluye el arte revisado; los controles predeterminados
de producción no se cambian. La opción de anuncios afecta únicamente a debug.
Notas listas para Play: [RELEASE-NOTES-2.5.1.txt](RELEASE-NOTES-2.5.1.txt).

## Aceptación externa

Esta candidata aún necesita instalación y ensayo prolongado en el móvil físico,
incluyendo bloqueo, orientación, consumo y revisión temporal de las mascotas.
La prueba física previa de 30 minutos pertenece a otra compilación y no se
presenta como validación de 2.5.1. Las compras y restauración reales deben
comprobarse desde una instalación de Google Play con cuenta de pruebas.

La consola consultada el 20 de septiembre mostraba 2.5.0/código 22 disponible
en pruebas internas y producción; por eso esta candidata aumenta a código 23.
2.5.1 no se ha subido ni publicado. La publicación en producción requiere
autorización explícita.

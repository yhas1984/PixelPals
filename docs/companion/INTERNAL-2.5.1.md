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
- El inicio presenta a Corgi sin obligar a renombrarlo. El nombre del usuario es
  opcional, editable en Ajustes y se usa en saludos del hogar y escritorio.
- El dashboard aplica el ambiente guardado de la mascota seleccionada.
- Diablillo estrena 24 poses de cuidado, con paleta basada en el original,
  transparencia limpia y contactos recalibrados. Se conserva la temporización
  y una escala común. El tridente se ajusta a las manos y los globos mantienen
  margen para sus estallidos.
- Las pruebas de catálogo exportan las seis acciones de las 15 especies en
  modo normal y reducido, incluyendo la carrera nativa y pelota de Corgi.

No se añaden productos de dinero real, suscripciones ni permisos obligatorios.
Se conservan el monedero, las compras, las migraciones y los anuncios existentes.

## Artefactos

| Archivo local | SHA-256 |
| --- | --- |
| `artifacts/internal/2.5.1/PixelPals-2.5.1-23-internal.aab` | `70856cb59014131669f0bb003e0ddddbc262d7b71dc4af24c34af4c54a85dc6e` |
| `artifacts/internal/2.5.1/PixelPals-2.5.1-23-release.apk` | `e6f8f94c6558bee609aaedca63639b6d786a7b8b5af34b83e360d0248e2e8968` |
| `artifacts/internal/2.5.1/PixelPals-2.5.1-23-debug.apk` | `45d222b7b85e746a09fcf2824c6601db6ab9d524ac3587a7102edba210e21572` |

El AAB y el APK release usan `com.pixelpals.app`, versión 2.5.1/código 23, sin
depuración. `jarsigner` verifica el AAB; `apksigner` verifica el APK. El
certificado del AAB coincide con el de 2.5.0. Los 42 archivos de `carePreview`
incluidos en el AAB coinciden byte a byte con las fuentes. El APK debug usa
`com.pixelpals.app.debug` y anuncios de prueba.

## Validación

- APK debug, APK release y AAB release compilados correctamente.
- JVM: 340 pruebas, cero fallos, errores u omisiones.
- Herramientas Python: 62 pruebas correctas.
- Assets base: 15 mascotas, 280 frames, sin duplicados exactos ni huérfanos.
- Lint debug: cero errores, 250 advertencias; release: cero errores, 224 advertencias.
- Base anterior `13e56de`: 361 casos únicos API 35 y 15 casos API 26. Estos
  resultados corresponden a esa compilación, no al nuevo atlas de Diablillo.
- Esta revisión: 10 pruebas API 35 de perfil/dashboard y 19 de física, tacto y
  permisos pasaron antes del reemplazo del atlas. La validación USB posterior
  del nuevo arte se detalla en [USB-IDENTITY-2.5.1.md](USB-IDENTITY-2.5.1.md).
- Resultados Android y revisión de pantallas: se consolidan en
  [VISUAL-CLOSURE-2026-09-20.md](VISUAL-CLOSURE-2026-09-20.md).

Las evidencias se conservan localmente en `docs/companion/evidence/final-2.5.1/`.
La revisión de perfil/Diablillo tiene evidencias en
`docs/companion/evidence/identity-2.5.1/`. Los artefactos, claves y capturas de
dispositivos no se versionan.

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

Se ha instalado y revisado la candidata por USB en el OnePlus 10 Pro. Sigue pendiente
un ensayo prolongado de esta compilación, incluyendo bloqueo, orientación,
consumo y revisión temporal del catálogo completo.
La prueba física previa de 30 minutos pertenece a otra compilación y no se
presenta como validación de 2.5.1. Las compras y restauración reales deben
comprobarse desde una instalación de Google Play con cuenta de pruebas.

La consola consultada el 20 de septiembre mostraba 2.5.0/código 22 disponible
en pruebas internas y producción; por eso esta candidata aumenta a código 23.
2.5.1 no se ha subido ni publicado. La publicación en producción requiere
autorización explícita.

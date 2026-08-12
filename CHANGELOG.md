# Changelog

Todas las versiones notables se documentan aquí. El formato sigue [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).

## [1.9.0] — 2026-08-12

### Added
- Controles persistentes para detener, ocultar y mostrar la mascota.
- Persistencia explícita del estado activo de la mascota.
- Pruebas de detección de foreground y persistencia del estado.

### Fixed
- La mascota ya no se inicia automáticamente al volver a abrir PixelPals.
- La mascota se detiene al quitar PixelPals de recientes y no se reinicia sola.
- El modo "Solo en el escritorio" conserva el último foreground conocido y oculta el overlay si el estado es desconocido.

## [Unreleased]

### Planned
- Migración de marca a PixelPals (strings, theme, README)
- Eliminación de doble fuente de verdad del tesoro (PetProgress → Room)
- Atomicidad en PixelPalsRepository (`db.withTransaction`)
- Fix bug timezone en `getMemories`
- Cache de screen metrics en PetView
- Polling de foreground a 15 s
- Modularización Gradle (`:core`, `:data`, `:feature`)
- Hilt para inyección de dependencias
- Split de PixelPalsRepository en sub-repos
- Nuevo modelo de accesorios (slot + PetModifier)
- Catálogo de 18 accesorios (5 migrados + 13 nuevos)
- Tienda nueva con TabLayout (Monedas / Accesorios / Packs)
- IAPs one-time (coins_small, coins_medium, coins_large, coins_mega, 3 packs premium)
- Gadgets funcionales (alas celestiales, alas demoníacas, jetpack para Patito)

## [1.4.0] — 2026-08-03

### Changed
- `versionCode` 7. Stores listing.

## [1.0.0..1.3.x]

Releases incrementales no documentadas. Ver `app/build.gradle.kts` para los códigos históricos.

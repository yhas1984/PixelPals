# Alimento por especie y tamaño de Tela

## Cambios

- El hogar reutiliza el alimento de CarePropPainter para las 15 mascotas. Tela dispone de un pequeño soporte de seda con grillo; desaparece su cuenco de croquetas. El soporte mantiene el color de cada variante adquirida.
- Los otros alimentos tienen una base decorativa de color. Corgi comparte el mismo dibujo de alimento/cuenco que los cuidados. Los identificadores de inventario y las posiciones se conservan.
- Nombres de alimentos y variantes por especie en español e inglés.
- Tela utiliza un factor de especie de 0.60 en el hogar, en la cámara del escritorio y en los cuidados de ambas superficies. Es independiente de la calibración del atlas. Se retiraron los estiramientos corporales de su comportamiento de escritorio y la deformación global en el hogar.

## Evidencia

- `assembleDebug`, `assembleDebugAndroidTest` y `lintDebug`: BUILD SUCCESSFUL en 18 segundos.
- HomeFoodArtworkReviewTest, CompanionSceneTest y SpeciesCareRenderingTest: 15 pruebas pasaron en 7.344 segundos en emulador. La última suite recorre los cuidados de las 14 especies del renderer compartido; no se confunde con aceptación de todas las animaciones de escritorio.
- Hoja de alimentos de las 15 especies y cuatro variantes de Tela inspeccionada visualmente después del ajuste final. Se descartó la primera presentación por alimentos demasiado pequeños y diferencia de comida de Corgi.
- Archivos en `evidence/species-food-size/`; `git diff --check` limpio.

## Pendiente

La reducción global de Tela no resuelve sus diferencias de cámara entre frames. Falta calibrar con referencias anatómicas y revisar secuencias completas, también al cambiar entre locomoción, descanso y cuidados. Tampoco se declara que los 24 objetos adquiribles sean ya apropiados para todas las anatomías: esa revisión sigue en el plan integral.

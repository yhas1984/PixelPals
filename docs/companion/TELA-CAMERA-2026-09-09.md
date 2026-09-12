# Tela: primera calibración anatómica

Se corrigieron los frames 20–23 (descenso por seda, factor 1.25) y 24 (primera pose colgada, factor 0.90). Los otros frames mantienen factor 1. Los factores se comparten entre TelaBehavior y la carga del atlas de 40 frames del hogar; esto no añade clips nuevos al hogar ni declara que todas esas poses se reproduzcan allí actualmente.

La escala de especie de 0.60 se mantiene separada. El dibujo, los toques y la baseline de cuidados utilizan la corrección de cámara compartida de BaseBehavior.

## Referencia y revisión

Anotaciones manuales aproximadas de los centros de ojos en las imágenes fuente de 384 píxeles, guardadas en `evidence/tela-camera/landmarks.json`. Se midieron 0 y 20–27. El rango de separación pasa de 60.21–84.17 píxeles a 73.76–77.59 tras calibración. La perspectiva y la precisión manual limitan esta medida; no es una prueba de conservación exacta del volumen ni una segmentación automática.

La hoja `evidence/tela-camera/rendered.png` se generó con TelaBehavior real y la escala de escritorio; se revisaron las nueve poses y sus líneas de apoyo calculadas. La cabeza se mantiene más uniforme sin normalizar el ancho de patas. La prueba carga el atlas real y exporta la hoja; la aceptación visual no se deduce únicamente de sus asserts.

Compilación debug/test y lint pasaron en 2m 19s. TelaCameraArtworkReviewTest y BaseBehaviorCameraScaleTest: dos pruebas pasaron en 0.197s. Logs junto a la hoja.

## Pendiente

Calibración y revisión de caminar, trepar, techo, contacto, ascenso, sueño y cuidados. Revisar reproducción continua, cambios de clip y contacto con la seda en el dispositivo. Esta hoja estática no demuestra fluidez ni completa la aceptación de Tela o de las 15 mascotas.

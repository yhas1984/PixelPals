# Jelly: retirar el contorno blanco

La pasada final de alfa incluye una apertura de silueta de un píxel para retirar los restos del antiguo trazo que siguen unidos al cuerpo por puentes finos. Se conserva el umbral de color: ampliarlo dañaría reflejos interiores. La revisión de esta mejora está en `docs/companion/evidence/jelly-outline-refinement-2026-09-12/`.

Los originales de `source/` son inmutables. `cleanup_outline.py` elimina alfa de los píxeles pálidos conectados al exterior y las islas que deja el antiguo borde. Mantiene el cuerpo conectado, sus reflejos interiores y las dos burbujas de sueño del frame19 de cuidados. La limpieza no modifica RGB, lienzos ni proporciones. El pipeline recalibra ground.y a la base visible. Las veinte primeras poses mantienen sus otros anchors. Las cuatro poses de medicina se sustituyen después por los dibujos revisados de `clean/medicine_0.png` a `medicine_3.png`, con cámara común y contactos propios de boca.

```sh
python3 -m tools.jelly.build_outline
python3 -m tools.jelly.build_outline --apply
python3 -m unittest tools.jelly.test_outline_cleanup tools.jelly.test_ground_anchor_cleanup tools.jelly.test_medicine_artwork tools.care.test_atlases
```

`--apply` actualiza los ocho recursos de animación, el retrato y las 30 poses de cuidados. Regenera únicamente Jelly con el pipeline compartido y conserva los informes de las demás mascotas. Los cuidados mantienen su ruta optativa `carePreview`.

El apoyo del cuerpo limpio del recurso0 termina en y=718/768. `JellyElasticMotion.GROUND` usa esa medida para evitar desplazamientos al comprimirse. `JellyArtworkBounds` conserva la cámara original de las poses del descanso del hogar; retirar el borde no reajusta la escala automáticamente. HomeRestArtwork usa también el ground.y recalibrado para alinear la gelatina con la cama, sin utilizar el antiguo borde como apoyo.

`review/legacy-review.png` y `review/care-review.png` muestran antes/después sobre fondo oscuro. `raw/air-expression-2026-09-12/` contiene un candidato de expresión que todavía no se empaqueta.

La preparación reproducible de medicina está en `prepare_medicine.py`; su fuente generada, prompt y revisión se conservan en `raw/medicine-expressions-2026-09-12/`. `medicine_artwork.py` coloca únicamente las celdas20–23 y sus anchors. El informe compartido identifica esos cuatro PNG como fuentes, sin alterar el resto de las mascotas.

REST dispone ahora de seis expresiones adicionales (`clean/rest_0.png` a `rest_5.png`) en las celdas24–29; el atlas mide1024×2048 con dos celdas finales vacías. `prepare_rest.py` compone las facciones generadas sobre el mismo cuerpo, con alfa idéntico; `rest_artwork.py` añade recursos, anchors y trazabilidad al pipeline. Los primeros24frames permanecen intactos. Revisión y prompt en `raw/rest-expressions-2026-09-12/`; validación en `docs/companion/evidence/jelly-rest-art-2026-09-12/`.

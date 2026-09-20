# Jelly: contacto y pendientes de continuidad

Medición del renderer Android en ventana320×320 y sprite160; fuentes `jelly_0.png` y `care_v1.png` ya sin contorno blanco. Ver `docs/companion/evidence/jelly-care-contact-2026-09-12/`.

El cambio de apoyo vertical al entrar/salir de las seis acciones era de -7 a -9px. Corregido mediante los 24 ground.y del atlas, conservando los otros anchors y todos los PNG. Los 24 endpoints (normal/reducido) quedan a -1px del apoyo normal por rasterización. El descanso del hogar compensa el margen del antiguo borde sin reajustar la escala.

El panel también conserva ahora la pose REST completada de Jelly al liberar la sesión; antes sustituía ese frame por PET en otra línea de apoyo. La corrección se verificó en un descanso real del OnePlus (`docs/companion/evidence/jelly-rest-result-2026-09-12/`).

La corrección no cierra el arte ni la transición entre dibujos:

- Comida de entrada conserva anchura/altura cercanas al original (168×115 frente a171×115). No aumentar globalmente todo el banco: arreglaría una acción encogiendo o agrandando otras.
- Medicina: se sustituyeron los cuatro dibujos que encogían el cuerpo. La nueva entrada mide169×117px frente a171×115px del cuerpo normal; las91muestras del escritorio mantienen el apoyo y el área dentro del0,5%. La cuchara se acerca, vacía y se retira antes de la deglución; la gelatina conserva área durante el pequeño pulso. El panel conserva la expresión final de alivio al liberar la sesión. Evidencia y alcance en `docs/companion/evidence/jelly-medicine-2026-09-12/`.
- Descanso corregido e integrado en 2.5.0: seis expresiones sobre un cuerpo/alfa común, deformación continua con área constante, apoyo y recuperación compartidos. También está conectada la ruta del hogar. Evidencia `docs/companion/evidence/jelly-rest-art-2026-09-12/`; no volver a contabilizar esta sustitución como pendiente.
- Juego ya usa un resorte con base fija, contacto compartido, dos compresiones/liberaciones y vuelo/regreso con continuidad de velocidad. El renderer conserva la primera pose PLAY y deforma el cuerpo manteniendo área aparente; pendientes expresiones e intermedios dibujados para acompañar esa coreografía. Evidencia `docs/companion/evidence/jelly-spring-2026-09-12/`.
- El retorno de caricias/juego y los saltos de dibujo entre poses necesitan intermedios revisados a velocidad real. La calibración de contacto no sustituye esos intermedios.

No usar `pet_jelly.png` (retrato512) para calcular la escala del escritorio: su fuente real es `jelly_0.png` (768).

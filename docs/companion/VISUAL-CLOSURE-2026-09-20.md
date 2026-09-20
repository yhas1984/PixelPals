# Cierre visual y continuidad — 20 de septiembre de 2026

## Estado de la entrega

Esta revisión continúa la PR-17 ya integrada y se trabaja sobre la rama
`codex/pixelpals-2.5.1-continuity`. El foco es conservar la continuidad visual
entre hogar, cuidados y escritorio sin convertir las comprobaciones técnicas en
una aprobación artística de todo el catálogo.

Ginger incorpora el giro de dos poses y conserva el descanso nativo en los
frames 16–19. El hogar y el escritorio comparten la cámara y el reloj del giro;
el apoyo se mantiene mientras cambia la orientación. La ruta de descanso y la
ruta de giro tienen fallback para los bancos anteriores.

Menta conserva sus posiciones con la paleta menta corregida. Ángel usa una
escala estable de `desktopCare` para igualar la cabeza y el halo con el banco de
cuidados, sin ajustar el recorte por frame. Estas correcciones resuelven los
pendientes gráficos concretos de Ginger, Menta y Ángel registrados en la
revisión anterior.

## Revisión del catálogo

Se revisaron las hojas de contacto de las 15 mascotas para las seis acciones,
en modo normal y reducido, mediante la revisión conjunta de root y los agentes.
Las muestras no muestran nuevos defectos concretos después de estos ajustes.
Cada hoja contiene seis fases por acción. Se revisaron además muestras
temporales de transiciones críticas; el exportador permite obtener imágenes
cada 100 ms. Los límites de recorte, alfa y cámara complementan esa inspección.

| Mascotas | Decisión sobre los cuidados renderizados |
| --- | --- |
| Corgi | Comida, pelota, caricias, baño, sueño y medicina legibles; PLAY incluye carrera nativa, recogida y liberación de la pelota. |
| Ginger | Contactos y anatomía conservados; ovillo nativo, ojos cerrados en descanso reducido y cesta estable al despertar. |
| Menta, Ángel | Paleta de Menta y calibración de Ángel corregidas; objetos y cuerpo coherentes en las seis acciones. |
| Bloop, Nube Michi | Se conserva su naturaleza flotante, sin reintroducir la línea o sombra de Bloop; objetos propios. |
| Jelly, Patito, Yuki, Diablillo | Se conservan gelatina, alas, nieve/ducha y globos; sin nuevos defectos observados en las fases revisadas. |
| Lumi, Moki, Piru, Taro, Tela | Anatomía y objetos de especie legibles; Tela pequeña con insecto/telaraña y Taro con caparazón rígido. |

La exportación inicial de Corgi PLAY omitía su carrera nativa. El test ahora
reproduce `CorgiBehavior`, `CorgiFetchMotion`, la pelota y el apoyo capturado al
iniciar el cuidado. Era un defecto de la herramienta de revisión, no un fallo
de esa ruta de producción. Las capturas corregidas se revisaron de nuevo.

## Aplicación y regresiones

Hogar, compañeros, aventuras y tienda se capturaron desde `MainActivity` en
español e inglés, con texto al 100 % y 150 %. La revisión espera al contenido
real y a la finalización de las animaciones de entrada. Las listas permiten
desplazamiento y la navegación permanece accesible con texto ampliado.

La ejecución Android verificó adopción, permisos, navegación, compra local de
cosméticos y decoración, derechos, monedero, migraciones, expediciones,
recompensas, diario, álbum, postales y cuidados. Se corrigió además un fallo
real de idioma en API 33+: el repositorio consulta `LocaleManager` para
conservar el idioma aunque ya no exista una Activity de AppCompat abierta.

## Validación de la candidata

- 336 pruebas JVM correctas y 59 pruebas Python correctas.
- 361 casos Android únicos correctos en API 35, sin omisiones pendientes.
  La pasada general completó 357 antes de interrumpirse: 354 correctos y tres
  fallos. La repetición focalizada de 38 casos pasó e incluyó los tres fallos,
  los cuatro casos restantes y las rutas afectadas. El informe consolidado
  identifica el registro de origen de cada resultado; no se presenta como una
  única ejecución ininterrumpida de 361 casos.
- La medicina conservaba sus aserciones de pose, cancelación y recompensa única;
  su espera se amplió para el renderer del emulador. El descanso reducido de
  Ginger exige ahora la pose dormida 19, en vez de la antigua entrada 16.
- Android 8/API 26: 15 casos focalizados correctos sobre el APK definitivo,
  incluyendo idioma, giros, despegue, cámara de Ginger, migraciones, gasto de
  monedas y compra/equipamiento de cosméticos.
- No molestar activado y desactivado: correcto en API 35. La repetición del
  recorrido con texto al 150 % y las secuencias de Corgi/Ginger también pasan.
- Lint debug/release sin errores: 249/223 advertencias respectivamente.
- Firma, hashes, reproducción y AAB: [INTERNAL-2.5.1.md](INTERNAL-2.5.1.md).

Evidencia local: `evidence/final-2.5.1/`, en particular
`android-api35-consolidated.json`, `catalogue-final-review/`,
`ginger-care-continuity.png`, `desktop-care-handoffs/` y `app-journey-review/`.

## Límites de aceptación

Esta revisión no declara completa la aceptación temporal en móvil, ni valida
compras reales, Play Billing o una promoción de release. Las capturas y los
renderers locales sirven para inspección y regresión; no sustituyen una prueba
física del dispositivo ni una aceptación final del producto.

No quedan defectos concretos abiertos de esta revisión local. Quedan pendientes
la aceptación temporal completa en el móvil físico, consumo/bloqueo/orientación
y compras/restauración reales desde Play. La revisión gráfica realizada se
conserva como evidencia; no se descarta ni se presenta como otra auditoría
completa pendiente desde cero.

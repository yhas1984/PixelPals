# PixelPals: plan de entrega integral

Actualizado: 9 de septiembre de 2026. Objetivo vigente: perfeccionar e implementar la transformación integral de PixelPals en un compañero vivo para las 15 mascotas. Este documento ordena el trabajo restante; no sustituye el alcance original por lo que ya compila.

**Seguimiento actual:** [candidata 2.5.1](INTERNAL-2.5.1.md) y
[revisión del 20 de septiembre](VISUAL-CLOSURE-2026-09-20.md). Los requisitos
siguientes se conservan; los avances fechados al final son un registro histórico.

## Condiciones de entrega

- Kotlin, Views/Fragments, Room y motor existente; experiencia local sin cuentas ni backend.
- Conservar progreso, monedas, compras, derechos, tesoros, vínculo y cambios locales de versión.
- Una mascota activa por escena y escritorio; personalidad, nombre y hogar persistentes por mascota.
- Mantener anuncios de apertura y banner, frecuencia y consentimiento. Sin nuevas suscripciones, aceleradores ni productos de dinero real.
- La ausencia no quita vínculo ni recompensas: recuperación y enfermedades reversibles.
- La publicación en producción necesita aprobación explícita. Una candidata técnica no equivale a publicación ni aceptación visual.

## Orden de ejecución

1. Cerrar la validación interrumpida de alimentos por especie y reducción de Tela.
2. Corregir escala interna y transiciones de Tela; revisar Corgi conservando su identidad artística original.
3. Extender el mismo criterio de anatomía, identidad y continuidad a las otras 13 mascotas.
4. Auditar y cerrar todos los recorridos de hogar, adopción, decoración, aventuras, recuerdos y tienda.
5. Ejecutar la aceptación completa sobre una candidata identificada y preparar entrega.

Los hitos son internos. No se declara terminada la transformación hasta cerrar todos los requisitos siguientes.

## A. Identidad y recursos de cada mascota

Una ficha por mascota debe definir tamaño relativo, temperamento, ritmo, comida, juguete, cama, hábitat, iniciativa, preferencias aprendidas y reacciones. Los perfiles actuales son la base a revisar, no prueba de calidad final.

| Mascota | Comida actual del perfil | Juguete propio | Descanso propio |
| --- | --- | --- | --- |
| Corgi | Comida servida | Pelota | Cojín |
| Bloop | Niebla | Burbuja | Niebla lunar |
| Nube Michi | Gotas de rocío | Arcoíris | Nube |
| Jelly | Fruta | Resorte | Charco |
| Ginger | Pescado | Pluma | Cesta |
| Angel | Estrella | Halo | Cuna de nube |
| Patito | Semillas | Hoja | Nido |
| Diablillo | Chile | Globos | Alas recogidas |
| Moki | Mosca | Hoja móvil | Rama |
| Yuki | Copo de nieve | Bola de nieve | Nieve |
| Piru | Pez pequeño | Disco | Hielo |
| Taro | Lechuga | Molinillo | Musgo |
| Menta | Huevo | Aro | Hoja cálida |
| Tela | Mosca | Seda | Telaraña |
| Lumi | Bayas | Orbe mágico | Luz de estrellas |

Aceptación por mascota:

- Alimento y objetos reconocibles y coherentes en hogar, cuidados, escritorio y previsualización comercial. No sustituir comida por croquetas genéricas.
- Variantes adquiridas distinguibles; compatibilidad de juguetes y camas explicada y aplicada al equipar. No mostrar una interacción imposible por anatomía.
- Hogar acorde con la especie, manteniendo los tres ambientes y las 24 decoraciones del alcance original. Yuki debe tener nieve; Ginger oportunidades de trepar.
- Tela claramente menor que Corgi. La escala de especie se separa de la calibración de cámara del atlas; no normalizar todas las mascotas al mismo tamaño.
- Recursos transparentes reales, sin fondos falsos, bordes contaminados ni recortes. Candidatos rechazados no se empaquetan.

## B. Movimiento, física e interacción

Prioridad de revisión: escritorio, seguido de hogar y cuidados. Medir el cuerpo/cabeza con referencias anatómicas; el ancho de patas desplegadas no es una medida estable del tamaño corporal.

- Registrar frames usados realmente por cada ruta y clips ausentes. Añadir los intermedios necesarios de giro, anticipación, contacto, recuperación, acostarse y despertar.
- Comparar secuencias completas a velocidad normal y reducida, además de hojas estáticas. Revisar bucles y transiciones entre clips, no únicamente frames aislados.
- Mantener volumen corporal, apoyo, orientación y posición; evitar patas plantadas que resbalan, cambios bruscos de cámara y estiramientos artificiales en anatomías rígidas.
- Completar alimentación, juego, limpieza, descanso, cariño y medicina en las 15 mascotas, con contacto real con sus objetos.
- Reacción física acorde con especie y personalidad; no basta variar la velocidad de un comportamiento genérico.
- Sueño y sueños coherentes con horario y No molestar; despertar sin saltos. Yuki conserva sensibilidad al calentamiento del móvil y recuperación, juego con nieve y ducha.
- Validar entrada/salida de cuidados, arrastre, límites, caída, interrupción, bloqueo y reanudación. Objetos y mascota comparten coordenadas y profundidad; la cama no tapa indebidamente al animal.

Evidencia requerida: tabla por mascota/clip/superficie, capturas o secuencias reproducibles, medidas anatómicas cuando se calibre escala y pruebas de las regresiones detectadas. Ninguna mascota obtiene aceptación global con una prueba de otra especie.

## C. Producto y persistencia

| Área | Aceptación obligatoria |
| --- | --- |
| Hogar y navegación | Diorama interactivo con luz por hora, necesidades y objetos utilizables; acceso claro a Hogar, Mascotas, Aventuras, Tienda y Ajustes. |
| Adopción | Nueva instalación: conocer, nombrar, interactuar y descubrir hogar. Actualización: conservar mascota/progreso e introducción breve. |
| Permisos | Pedir superposición al llevar al escritorio; notificaciones/acceso de uso con explicación contextual. Probar concedidos, denegados y revocados; hogar usable sin superposición. |
| Accesibilidad | Español/inglés, texto ampliado, contraste, etiquetas, sonido, vibración y movimiento reducido. Botones alternativos a gestos esenciales. |
| Personalidad | Preferencias aprendidas de interacciones reales, vínculo que modifica saludos/confianza/iniciativa y estado común hogar/escritorio. |
| Decoración | Tres ambientes y 24 elementos; arrastre con dedo, previsualización, ajuste válido, guardar/restaurar por mascota y alternativas accesibles. Camas/juguetes autónomos y tesoros expuestos. |
| Escritorio | Una mascota; objeto de cuidado compatible sólo cuando corresponda, sin pelota permanente no solicitada ni bloqueo del móvil. |
| Expediciones | Pradera, bosque y cielo nocturno; viaje temporizado pausa cuidados/escritorio, regreso al reabrir, cancelación sin recompensa e idempotencia ante reinicios/cambios de hora. |
| Diario y álbum | Adopción, juegos, descubrimientos y vínculo ilustrados; recompensas vinculadas a álbum/decoración. |
| Postales | Captura estática de escena y compartir Android, sin exportación de vídeo. |
| Tienda | Previsualización antes de comprar/equipar, monedero existente, iniciales gratuitos y desbloqueos por juego/monedas; conservar compras y restauración. |
| Datos | Migraciones desde esquemas soportados sin borrado, Room desde v8, modelos/repositorios/StateFlow; transacción única para monedas y recompensas, sin duplicados por recreación. |

Separar progresivamente responsabilidades de PetView, PetService y repositorio general sin reescribir el framework. La comprobación del código debe acompañarse de recorridos reales con estados de carga/error.

### Tienda, compras, tesoros y cosméticos

Estos sistemas son parte obligatoria de la entrega, no trabajo opcional posterior al movimiento.

Restauración manual y validación integrada: botón visible, progreso, resultado y reintento en tienda; reconciliación conservada en ViewModel al recrear la vista. Suite JVM completa: 252; Android completa: 212, ambas sin fallos tras corregir una expectativa antigua de duración de trepa. Precios corregidos para no mostrar glifos ausentes en API 26, con verificación localizada posterior. Alcance exacto, hashes y pendientes en `STORE-RESTORE-VALIDATION-2026-09-09.md`; facturación real de Play y aceptación visual integral siguen abiertas.

Evidencia parcial 2026-09-09: se corrigió el tamaño, radio y desplazamiento de los cosméticos de escritorio para respetar la proporción de Tela; hogar ya dimensiona el actor por especie. Build/lint correctos (53s), ocho pruebas Android de proporciones, giro, compra y desplazamiento pasaron (7.066s); informe en `evidence/cosmetic-species/`. Esto no sustituye la revisión visual del catálogo completo ni las compras y restauración desde Play.

Revisión de movimiento cosmético: se eliminó la doble aplicación de escala vertical al balanceo de objetos flotantes. Una prueba del renderer mide el desplazamiento visible a dos escalas y evita amplitudes cuadráticas. Pasaron build/lint (50s) y cuatro pruebas Android (0.305s), incluyendo giro, proporción por especie y recompensa de expedición tras recrear el repositorio y reclamar de nuevo. Esta última prueba usa la misma base abierta; no equivale a cerrar el proceso ni a reiniciar el teléfono. Evidencia en `evidence/cosmetic-motion/`.

- **Tienda completa:** catálogo de mascotas, cosméticos y decoración con identidad visual común, precio y propiedad claros, previsualización fiel al resultado y estado equipado visible. Revisar selección, cambio de pestaña, compra, equipamiento y regreso al hogar/escritorio.
- **Compras con monedas:** saldo coherente en toda la app, gasto y concesión transaccionales, saldo insuficiente explicado, doble toque sin doble gasto y reinicio sin perder el producto adquirido. Respetar artículos gratuitos y desbloqueos por juego.
- **Compras de dinero real existentes:** precio localizado suministrado por Play, cancelación, compra pendiente, error, reconocimiento, propiedad y restauración. Una compra de cosmético no cambia la mascota seleccionada. Verificar los derechos existentes tras actualización. No añadir nuevos productos de dinero real ni suscripciones.
- **Tesoros:** descubrimiento, concesión única, persistencia, colección/álbum y exhibición en el hogar; revisar duplicados, contador, estados vacíos y recompensas de expediciones. Interrumpir o recrear una pantalla no duplica la recompensa.
- **Cosméticos:** previsualizar, adquirir, equipar y quitar; persistir la selección prevista por mascota. Aplicación coherente en hogar, escritorio, cuidados y postales cuando corresponda. No alterar anatomía, escala, hitbox o alimento ni filtrar tintes a otra mascota/objeto. Explicar incompatibilidades antes de comprar/equipar.
- **Economía y presentación:** fuentes y gastos de monedas comprensibles; recompensas y valor de los artículos visibles. La monetización se apoya en personalidad, calidad artística y personalización, sin castigar ausencias ni perder compras/vínculo.
- **Validación comercial:** recorrer el flujo completo desde previsualizar hasta ver el artículo aplicado, reiniciar y restaurar. Pruebas locales para economía y persistencia, más compras/restauración reales desde una pista de Play para el sistema de facturación.

### Calidad de la app completa

Tienda con texto ampliado, 2026-09-09: la revisión real en API 26 al 200 % detectó pestañas partidas, precio truncado y botones con altura fija. La cabecera ahora usa AppBarLayout desplazable con pestañas horizontales; banner/estado permanecen fuera del área desplazable. Precio separado, nombre sin truncado y botón de compra con altura adaptable. Se comprobó mediante gestos reales la retirada de cabecera y acceso al catálogo en vertical/horizontal; en horizontal puede requerir más de un desplazamiento y una tarjeta puede superar el área visible. Se restauraron los ajustes originales del emulador. Build/lint finales correctos (32s), cuatro pruebas de tienda/restauración/compra/localización pasaron (5.512s). `evidence/store-accessibility/` conserva capturas y logs. No equivale a validación completa de TalkBack ni del catálogo cosmético al 200 %.

- Revisar todas las pantallas, diálogos, menús, anuncios y recorridos de regreso con la misma paleta, tipografía, espaciado, iconografía y tono en español e inglés.
- Comprobar selección de mascota, ajustes, adopción, permisos, diario, álbum, aventuras, tienda y editor de hogar sin pantallas vacías accidentales ni controles ocultos por barras del sistema, teclado o texto ampliado.
- Probar navegación Atrás, recreación, orientación, bloqueo/reanudación, cierre de proceso y reapertura; conservar estado y evitar tareas/ventanas duplicadas.
- Revisar estados vacíos, carga, error y recuperación; ninguna ruta esencial depende de un gesto sin alternativa accesible.
- Medir rendimiento también al abrir la tienda, cambiar mascota, cargar assets y regresar del escritorio. Conservar los controles actuales de anuncios y consentimiento.
- La aceptación final combina calidad visual, interacción, datos, estabilidad y funcionamiento comercial; no se limita a las mascotas ni a compilar.

## D. Validación y release

Persistencia y hábitats, 2026-09-09: ExpeditionDiskPersistenceTest usa una base Room temporal en disco, cierra/reabre antes de completar la expedición y vuelve a cerrar/reabrir tras la recompensa. Conserva tesoro, saldo, decoración y diario, y rechaza otra reclamación del mismo requestId. No equivale a reinicio de proceso/SO. Bloop dispone de un claro brumoso acorde con su perfil de niebla, separado del estanque de Patito. Se exportaron los quince fondos con iluminación diurna/nocturna; se revisó la hoja diurna final y ambas hojas de la primera iteración, ajustando la bruma que inicialmente parecía burbujas. Dos pruebas pasaron (0.307s), build/lint correctos (55s); `evidence/habitat-disk/` conserva los renders finales y logs. Estas hojas no validan las animaciones ni las escenas con mobiliario.

Avance Corgi 2026-09-09: al terminar la marcha, una pausa de apoyo de 0.18s con el frame original 0 precede a olfatear, inclinarse para jugar o descansar. Conserva la acción/duración y cancela la acción pendiente ante interacción, lanzamiento, reset o salida de cuidados. Cuatro pruebas CorgiScaleRenderingTest pasaron (0.331s), build/lint correctos; evidencia en `evidence/corgi-action-plant/`. La pausa no sustituye los frames anatómicos de giro/asentamiento pendientes. El candidato generado se rechazó por fondo cuadriculado RGB sin alpha; prompt y revisión en `../../tools/corgi/raw/sit-transition-2026-09-09/REVIEW.md`. La revisión del escritorio físico de esta sesión quedó pendiente por bloqueo del móvil, no se afirma aceptación visual.

- Identificar candidata por hash del APK y estado de fuentes; guardar comandos, resultados y dispositivo/API.
- JVM, validadores de assets, lint e instrumentación del árbol final. Los informes históricos siguen siendo históricos después de cambios posteriores.
- Comparar fluidez, memoria y batería con la base; prueba prolongada en móvil real con escritorio/servicio, bloqueo, reanudación y orientación.
- Revisar visualmente las 15 mascotas y todos sus cuidados antes de habilitar recursos experimentales en release.
- Comprobar firma y artefacto release; validar compras reales y restauración desde pista de pruebas de Play.
- No prometer protección absoluta contra manipulación del reloj en una aplicación local.

Quedan fuera: convivencia simultánea, multijugador, cuentas, sincronización, conversación generativa y vídeo.

## Estado de ejecución al actualizar este documento

- Hay implementación y evidencia parcial extensa en este directorio. Ningún hito completo se infiere de su mera existencia.
- Corrección de revocación de superposición validada e instalada: véase `OVERLAY-PERMISSION-2026-09-09.md`.
- Alimentos por especie y Tela al 60 %: compilación y lint completados; 15 pruebas Android del hogar/cuidados pasaron. Hoja comparativa de alimentos de las 15 especies revisada visualmente, con grillo sobre seda para Tela y comida compartida también para Corgi. Evidencia en `evidence/species-food-size/`. Esto valida este lote de renderizado, no la continuidad completa de movimientos.
- Tela: primera calibración anatómica de frames 20–24 aplicada y revisada en una hoja del renderer real; compilación/lint y dos pruebas pasaron. **El resto del atlas y las secuencias continuas siguen pendientes.** Véase `TELA-CAMERA-2026-09-09.md`.
- Ciclo de caminar de Tela: orientación fuente normalizada y cámara de 8–11 calibrada alrededor del apoyo; revisión de hoja, nueve pruebas y lint/compilación correctos. Véase `TELA-WALK-2026-09-09.md`. Sigue pendiente la aceptación de secuencias continuas.
- Marcha de Tela vinculada a distancia y velocidad limitada por tamaño: pruebas en ambos sentidos a 30/60/120 FPS y sin desplazamiento pasaron. La aceptación visual continua sigue pendiente.
- Paredes/techo de Tela siguen distancia y limpian transformaciones al entrar; recuperación de equipamiento tras compra corregida con propiedad/saldo conservados. Ocho pruebas JVM y cinco Android pasaron; véase `SURFACE-STORE-2026-09-09.md`. No equivale a aceptación visual de trepa ni a validación Play.
- Base geométrica de calibración corregida: área táctil y apoyo de cuidados siguen la escala por frame; cuatro pruebas Android y lint/compilación pasaron. Véase `FRAME-CAMERA-2026-09-09.md`. No se han activado factores nuevos de Tela con este cambio.
- Pruebas completas anteriores y soak del emulador no validan los últimos cambios ni sustituyen la revisión física de movimientos/batería.
- Firma, Play y aceptación visual final siguen pendientes de evidencia actual.

## Forma de trabajo

Usar Luna para tareas delimitadas: una ruta, una mascota, un conjunto de assets o una revisión concreta, con archivos y criterio de salida explícitos. Root integra y verifica. Gradle y ADB se ejecutan en serie; no duplicar builds. Si Luna no está disponible, continuar el trabajo autorizado directamente y no inventar resultados de delegación.

Para cerrar cada lote registrar: requisito, cambio, evidencia, resultado y limitación pendiente. Si una comprobación revela una diferencia visible, corregirla y repetir sólo las validaciones afectadas antes de ampliar el alcance de pruebas.

## Lote de cosméticos y monedas — 2026-09-09

- Se habilita Quitar/Remove para el cosmético equipado; el mismo flujo del ViewModel guarda el valor nulo, solicita refresco de la mascota y actualiza el catálogo. La propiedad y el saldo se conservan. La prueba JVM cubre retirar y volver a equipar sin una segunda compra.
- Luna adaptó las tarjetas de cosméticos y monedas: texto envolvente, iconos sin caja rígida y botones con altura mínima táctil y crecimiento por contenido.
- Compilación y suite JVM correctas; cuatro pruebas Android de tienda/localización correctas (8,073 s). La primera invocación usó un paquete incorrecto para LocalizationResourcesTest; se corrigió y repitió la selección completa.
- Revisión visual en emulador API 26, texto al 200 %: iconos, nombres, precios y botones de las tarjetas visibles legibles. Capturas y logs en `evidence/cosmetics-accessibility/`. Se restauró la escala de texto del emulador.
- Pendientes: revisión visual del catálogo completo, TalkBack y compras/restauración reales desde Play. Este lote no cierra el plan general.

## Álbum y exhibición — 2026-09-09

- El álbum permite abrir historias de tesoros descubiertos aunque no se puedan regalar y consultar pistas de los pendientes. El regalo conserva su confirmación. Nombres sin truncado, emoji adaptable y una columna cuando el ancho efectivo con texto ampliado lo requiere.
- Compilación correcta y tres pruebas Android del álbum (3,77 s), incluida lectura de la historia tras agotar las copias sin modificar el inventario. Evidencia en `evidence/treasure-details/`. Falta revisión visual del álbum con fuente ampliada y orientación horizontal.
- Auditoría de Luna verificada en el código: el hogar muestra automáticamente el primer tesoro descubierto. Falta selector de tesoro expuesto persistido por mascota. El inventario compartido actual se conserva; no es necesario convertirlo en inventarios separados para cumplir la personalización de la exhibición.

## Elección de tesoro expuesto — 2026-09-09

- Hogar > Decorar > Exponer un tesoro permite automático, vacío o un recuerdo descubierto. Tocar el expositor fuera de edición abre el mismo selector. La elección se guarda por mascota mediante selectedTreasureId en Room 11; no consume objetos ni cambia el inventario global. Los recuerdos descubiertos siguen disponibles después de regalar sus copias.
- Migración 10→11 sin borrado; esquema 11 exportado. Matriz de migración ampliada a versiones 2–10. Veinte pruebas Android pasaron (4,677 s), incluyendo repositorio, migraciones, selección mediante diálogo, recreación y vacío. Suite JVM y compilación correctas (48 s).
- Revisión visual mediante el menú real en API 26 detectó un emoji de hueso sin soporte; se omite el símbolo si la fuente no lo representa. Compilación final 4 s y prueba del selector 3,028 s correctas. Captura anterior a esa corrección y logs en `evidence/treasure-selection/`.
- Pendientes de este recorrido: revisión final con texto ampliado/horizontal y catálogo completo de expositores. El alcance general de movimientos, assets, Play y validación física sigue abierto.

## Frenada de Corgi en el escritorio — 2026-09-09

- La carrera breve tras un gesto calcula su destino dentro del espacio disponible y utiliza progreso con velocidad nula en ambos extremos. Evita cortar el movimiento al chocar con un borde; termina con apoyo antes de olfatear. Si no hay espacio, permanece apoyado en vez de ciclar las patas sin avanzar. Conserva atlas y calibración actuales.
- Compilación correcta (3 s). Cinco pruebas Android de Corgi correctas (0,355 s), incluyendo ambos bordes a 30/60/120 FPS, ausencia de deslizamiento inverso, llegada con desplazamiento final máximo de un píxel y pose apoyada. Evidencia en `evidence/corgi-braking/`.
- No equivale a aceptación visual física: siguen pendientes frames anatómicos intermedios, revisión de todos los movimientos y especies y pruebas prolongadas en dispositivo.

## Ginger: contacto y primer fotograma de aterrizaje — 2026-09-09

- Corregido updateAirborne: al detectar el suelo dibuja inmediatamente la pose de impacto y elimina la inclinación aérea. Antes conservaba el frame de vuelo y su rotación durante un dibujo sobre el suelo.
- Cinco pruebas Android pasaron (0,87 s), incluida llegada a 30/60/120 FPS y renderer real de 15 poses en ambos sentidos. Los 30 ejemplos tienen píxel opaco inferior y=199 frente a línea de cuidados y=200. Lámina revisada visualmente: apoyos coherentes entre poses. No se cambió el desplazamiento del atlas ni la escala.
- La auditoría previa de Luna sobre un supuesto desfase de medio sprite no justificaba una corrección: confundía el límite lógico de la ventana con la línea de apoyo del renderer. Tampoco se adoptó su propuesta de temporizar la marcha independientemente de la distancia, que contrariaría el contacto con el suelo. Esta verificación demuestra coherencia entre poses/cuidados; no sustituye revisión física ni aceptación de las secuencias continuas.
- Evidencia en `evidence/ginger-ground/`; test de render preparado por Luna, línea gráfica de apoyo corregida por root antes de exportar. Compilación correcta. Siguen pendientes las transiciones anatómicas y el resto del catálogo.

## Ginger: acecho con apoyo y frenada — 2026-09-09

- El acecho usa progresión suave con velocidad nula al inicio/final, duración calculada por distancia y velocidad máxima prevista de 0,45 tamaños de sprite/s. Las patas siguen distancia real en vez de un reloj independiente. Al llegar dibuja inmediatamente la postura de preparación del salto. Escala y atlas conservados.
- Luna preparó las pruebas de recorrido; root incluyó el último desplazamiento al entrar en coil y corrigió la preparación del puente, que informaba x=300 aunque sus parámetros se habían fijado en x=500. La primera ejecución falló por esa preparación, no se relajó tolerancia.
- Compilación correcta; seis pruebas Android de acecho/aterrizaje correctas (0,298 s): ambos sentidos a 30/60/120 FPS, inicio/final <=1 píxel, recorrido monótono, frames de acecho y estabilidad cuando no hay distancia. Evidencia en `evidence/ginger-stalk/`.
- Validado en emulador; instalación física pendiente por desconexión ADB. Sigue pendiente revisión visual continua y producción de frames intermedios necesarios.

## Verificación completa del árbol actual — 2026-09-09

- Compilación debug/test, 252 pruebas JVM (sin fallos/errores/omitidas) y lint completados en 36 s. Lint conserva 234 advertencias y ningún error. Validador de assets correcto (280 frames de producción bajo su cobertura) y 17 pruebas de herramientas de cuidados correctas. No sustituye aceptación visual.
- Primera suite Android: 220 pruebas, dos fallos (106,049 s). Uno era la comparación de pager.bottom con banner.top, inválida tras introducir la cabecera plegable: se corrigió el test para comprobar los límites visibles recortados en coordenadas globales. El otro fue un ScrollTo de Espresso al botón del álbum desde el panel.
- Repetición de navegación/álbum: diez pruebas correctas (15,414 s). Segunda suite completa: 220 pruebas sin fallos (105,765 s). El fallo ScrollTo no se reprodujo; su causa sigue sin confirmar y se registra como intermitente, no como bug de producción resuelto.
- Evidencia y huellas de fuentes/APK en `evidence/current-validation/`. Todo ejecutado en emulador API 26; móvil desconectado. Versión local conservada 2.4.0 / 21 y rama feature/pixelpals-living-companion; no se publicó ni se promovieron assets.
- Auditoría de Luna: siguen abiertos frames/transiciones necesarios, aceptación visual continua de las quince mascotas y cuidados, promoción revisada a release, firma actual, facturación/restauración reales desde Play y rendimiento físico prolongado. Una suite técnica verde no cierra esos requisitos.

## APK optimizado de revisión — 2026-09-09

- Intento de assembleRelease/lintRelease con companion.releaseCandidate=true llegó a packageRelease y falló por SigningConfig release sin storeFile (2 min 18 s). Compilación, R8 y lint completados; firma de producción no disponible. Confirma el bloqueo real de empaquetado sin configuración de firma.
- Se generó una clave temporal local de revisión, ajena a la firma publicada, y se produjo el APK optimizado con propiedades de firma sólo para esa invocación (2 s). Artefacto: `app/build/outputs/review/pixelpals-companion-review.apk`. No es un artefacto aprobado para Play ni puede actualizar la instalación publicada con su firma original.
- apksigner verificó la firma CN=PixelPals Local Review; manifest com.pixelpals.app, versión2.4.0/code21, min26/target36. Los quince paquetes de cuidados (30 archivos) coinciden byte a byte con carePreview y todas sus rutas de atlas existen dentro del APK.
- Instalación en emulador API26 correcta; arranque Status ok,790ms, proceso activo y pantalla inicial revisada visualmente. Esto no valida todos los recorridos del build optimizado ni compras reales.
- Después se ejecutó mergeReleaseAssets/generateReleaseBuildConfig sin flag: CARE_SCENES_ENABLED=false. No se modificó la configuración predeterminada ni se publicó nada. Evidencia en `evidence/release-review-current/`.
- Sigue pendiente clave/configuración de firma de producción, aceptación artística y física, y validación desde pista Play. Luna revisó el bloqueo de firma; no fue necesario cambiar Gradle.

## Producción del frame intermedio de Corgi — segundo candidato 2026-09-09

- Se intentó producir un frame de media sentada con image_gen integrado, referenciando corgi_0 y corgi_6. Rechazado tras inspección visual y técnica: RGB1254x1254 sin alfa, tablero de transparencia pintado y cambios de proporciones. Ningún asset de la app fue sustituido ni se añadieron referencias al candidato.
- Candidato, prompt exacto, contrato revisado por Luna y diagnóstico en `tools/corgi/raw/sit-transition-2026-09-09-v2/`.
- Se solicitó elección al usuario para probar la vía CLI con gpt-image-1.5, que necesita autorización explícita según la guía imagegen y una API key local. No se ejecutó la alternativa ni se solicitó pegar credenciales. No repetir esta misma generación integrada sin nueva evidencia de que pueda producir alfa real.
- El frame sigue pendiente: un candidato generado y rechazado no cuenta como entrega artística terminada. El resto del alcance continúa abierto.

## Adopción en APK optimizado y continuidad visual de cuidados — 2026-09-09

- En el APK optimizado de revisión anterior se recorrió introducción → nombre Milo → cariño → recompensa energía+2/vínculo+3 → oferta opcional de escritorio. Tras force-stop y reapertura se conservaron nombre, valores y guía. No se concedió superposición ni se hicieron compras.
- Luna corrigió cancelación de introducción: Atrás/exterior marca la introducción vista y, para nuevas adopciones, conserva la guía de nombre sin abrir otro diálogo. El dismiss programático por recreación no confirma la introducción. Nuevo test cubre cancelación y recreación.
- La revisión visual detectó colores violetas heredados en CareScenePanel; se sustituyeron por surface_warm/surface_tinted/surface_subtle y textos compartidos. Captura debug posterior revisada visualmente. Iconos y assets no se sustituyeron.
- Compilación debug y nueve pruebas Android de introducción/cuidados correctas (27,579 s). Evidencia en `evidence/release-adoption-care/`. Las dos correcciones nuevas están verificadas en debug; el APK optimizado anterior no las contiene y deberá regenerarse al cerrar el siguiente lote release.
- Frames intermedios siguen pendientes de la decisión sobre la vía de generación; no se ha usado CLI ni se han consumido créditos API. Los requisitos físicos y Play siguen abiertos.

## Iconos de cuidados y objetos elegidos — 2026-09-09

- La bandeja refleja la cama y el juguete seleccionados para cada mascota usando el mismo pintor que la escena. Al cambiar de mascota limpia las selecciones anteriores y actualiza los iconos al cargar sus objetos.
- Revisión de la lámina de 15 mascotas detectó el molinillo de Taro fuera del botón. Se ajustó el encuadre de objetos de hogar (anclados al suelo) y herramientas; una prueba detectó además la burbuja superior de la esponja. Se corrigió su margen sin recortar la ilustración.
- Compilación correcta (4 s); seis pruebas Android correctas (13,111 s), incluyendo geometría de variantes elegidas, distinción entre alimentos/acciones y límites de los 90 iconos predeterminados. Lámina posterior revisada visualmente. Evidencia en `evidence/care-tool-consistency/`.
- Móvil reconectado mediante servicio ADB anunciado en 192.168.1.160:41481. Instalación debug con -r correcta, sin borrado de datos; arranque en frío Status ok, 1208 ms. Esto no sustituye revisión física continua de cuidados/movimientos ni pruebas prolongadas. El APK optimizado de revisión anterior aún no contiene estas correcciones.
- Tienda, compras, cosméticos, tesoros y recorrido general conservan todos sus requisitos de aceptación; siguen pendientes frames necesarios, aceptación del catálogo completo y pruebas reales de Play.

## Telaraña habitable de Tela — 2026-09-09

- Petición añadida al alcance: telaraña que abarque el hogar y sirva a Tela para desplazarse y comer moscas. Implementada sobre los tres ambientes existentes; mantiene decoraciones e inventario. La red y las rutas comparten 25 nodos y las mismas aristas rectas. Giro detenido y avance/frenada suaves, pasos ligados a distancia; Tela conserva escala de especie 0,60.
- Patrulla sin alimentación automática. Tocar una mosca o pulsar «Dar una mosca a Tela» reserva FEED mediante CareSceneCoordinator; la transacción se completa tras llegar y envolverla. Cancelar, editar, abrir cuidados, viajar o salir antes del marcador no concede comida. Las moscas reaparecen tras 35 s de tiempo visible; no son inventario ni recompensa monetaria. El panel habitual también usa mosca en el perfil de Tela, conservando alimentación WEB.
- Sueño por horario/No molestar, energía baja/enfermedad y despertar pausan el recorrido; movimiento reducido detiene patrulla autónoma. La acción explícita sigue disponible mediante botón. Cosméticos acompañan la orientación sobre la red. No se generaron ni promovieron assets nuevos: conserva las poses del atlas actual; la envoltura y los hilos son ilustraciones Canvas.
- Luna preparó un primer modelo/pintor y tests. La revisión de root detectó incompatibilidad entre círculos dibujados y rutas rectas, cancelación a mitad de arista y gestión incorrecta del destino durante patrulla; se reemplazó esa parte por geometría compartida y rutas verificadas, con tests a 30/60/120 FPS. No se aceptó el primer borrador como evidencia.
- 257 pruebas JVM correctas; compilación final 12 s. Seis pruebas Android correctas (53,406 s): secuencia real del HomeSceneView, marcador único, botón accesible, cancelación al pausar, pausa de escena e iconos. Lint correcto en el lote previo al último ajuste de contacto; la compilación posterior incluye dicho ajuste.
- Recorrido real en emulador API26: botón → caminar → envolver mosca → resultado → patrulla. Food72→100 y Bond0→8. Grabación de 20 s y muestras visuales revisadas, además de lámina de secuencia. Evidencia en `evidence/tela-home-web/`. Las muestras y pruebas no equivalen a aceptación de todos los clips ni de las quince mascotas.
- Siguen pendientes los nuevos frames/anatomía de otras transiciones, aceptación física prolongada, comprobación completa de release y compras/restauración reales de Play. Este avance no cierra la transformación integral.
- Instalación debug final en el móvil conectado por ADB correcta con -r, conservando datos; no se abrió ninguna pantalla del teléfono para evitar interrumpir su uso. La revisión visual de esta funcionalidad se realizó en emulador.

## Tela: interacción y arrastre en escritorio — 2026-09-09

- Confirmado en el código: onInteract conservaba rotación y offsets del balanceo previo después de retirar la seda; updateDrag forzaba orientación positiva y conservaba el offset vertical anterior. Ahora la interacción limpia esas transformaciones y normaliza escalas conservando el signo horizontal; el arrastre conserva la orientación y limpia el desplazamiento vertical heredado.
- Luna preparó dos pruebas de regresión para ambos signos de orientación. Compilación correcta (6 s); cuatro pruebas Android correctas (0,925 s), incluyendo marcha a distintas frecuencias/direcciones y poses estacionarias en paredes/techo. Evidencia en `evidence/tela-interaction-pose/`.
- Validación técnica en emulador. No sustituye la revisión física de continuidad ni el trabajo pendiente de contactos anatómicos y frames del catálogo completo.

## Validación integrada tras la telaraña — 2026-09-09

- Compilación, 257 pruebas JVM y lint correctos (69 s). Lint: 232 advertencias, cero errores/fatales. Huellas del árbol y APK de esa ejecución guardadas en `evidence/integrated-web-validation/initial-fingerprints.json`.
- Suite Android inicial: 228 pruebas, tres fallos (377,462 s). Dos expectativas correspondían al comportamiento anterior: alimento único entre todas las especies, y Tela siguiendo la ruta genérica de muebles del suelo. Se permite específicamente la mosca compartida con Moki; la ruta de Tela se comprueba mediante su prueba dedicada, ampliada con descanso por energía baja, inmovilidad sobre el hilo y despertar antes de volver a moverse. La revisión genérica conserva las otras 14 mascotas; el resto de pruebas de visibilidad mantienen las 15.
- El tercer fallo comparaba vínculo antes/después de cancelar pero la escena alcanzó el marcador de comida durante la espera de ActivityScenario. La prueba congela el tiempo de presentación antes de solicitar el cambio de estado, exige sesión READY y verifica ausencia de recompensa. Posteriormente se aisló de la animación continua con movimiento reducido, restaurando la preferencia en finally. La animación normal conserva cobertura de render independiente.
- Una repetición agotó 45 s en MonitoringInstrumentation.startActivitySync esperando cola inactiva, con el hilo principal en ThreadedRenderer.nSyncAndDrawFrame. No demuestra que el arranque normal de la app tarde 45 s. Se añadió una caché Bitmap perezosa de los hilos estáticos de la red (1000×760, aproximadamente 3 MB), manteniendo moscas/mascota dinámicas. Evita recrear los trazados estáticos por fotograma; no se atribuye aún una mejora medida de fluidez/batería.
- Tras la caché: compilación correcta (9 s), cuatro comprobaciones Android correctas (49,19 s). Tras aislar la prueba de ciclo de vida: compilación test correcta (2 s), prueba correcta (46,318 s). La demora del framework sigue registrada; no se relajó el timeout. La suite completa no se volvió a ejecutar después de esos cambios, por lo que no se declara una nueva suite completa verde.
- Auditoría de Luna: no se adoptaron sus afirmaciones sobre carga obsoleta o pending reasignado tras cancelación al contradecir el flujo real. Su hipótesis de orden de cancelación requiere una reproducción con mutex contendido; applicationScope utiliza Main.immediate. No se presenta como bug confirmado ni solucionado.
- Evidencia en `evidence/integrated-web-validation/`. Mantiene abiertos aceptación artística/física, frames pendientes, validación release y Play del plan integral.

## Temperamento aprendido en la telaraña — 2026-09-09

- Verificado: PetView ya recibe CompanionTraits y aplica tempo a comportamiento e iniciativa a señales espontáneas. La nueva ruta de Tela aún usaba velocidad y pausas constantes. HomeSceneView ahora le pasa tempo de perfil/aprendizaje e iniciativa compartida.
- El modelo aplica el ritmo al iniciar cada arista y la iniciativa a las pausas de patrulla. Cambios de temperamento a mitad del recorrido no cambian posición de golpe. Giro, comida y reaparición de moscas conservan tiempo visible real; no adelantan recompensas.
- Tres nuevas pruebas JVM comprueban llegada más rápida, duración de comida constante, pausas diferenciadas sin alimentación automática y ausencia de salto al cambiar tempo. Suite JVM: 260 correctas. Compilación correcta (3 s, tras corregir una errata de declaración en un test). Prueba Android de recorrido/render/descanso/despertar correcta (0,471 s). Evidencia en `evidence/tela-web-personality/`.
- Se intentó delegar las pruebas a Luna, pero la herramienta rechazó la nueva tarea por límite de agentes; root completó la implementación y validación. El alcance general y las puertas de aceptación pendientes se mantienen.

## Cosméticos: previsualizar sin saldo — 2026-09-09

- Las tarjetas de cosméticos incluyen «Ver en mi mascota», separado de compra/equipamiento y disponible aunque la acción comercial esté deshabilitada. Antes, un cosmético no comprado con saldo insuficiente abría el aviso de monedas antes de mostrar la escena.
- El diálogo presenta el efecto sobre la mascota seleccionada y se descarta al destruir la vista. No llama a compra/equipamiento. Etiquetas en español/inglés con nombre del cosmético; las escenas de vista previa conservan su descripción accesible durante los fotogramas, sin anunciar controles de hogar inexistentes.
- Recorrido real en emulador: tienda → cosméticos → Golden Glow con saldo0 → vista previa sobre Tela → cerrar. Saldo0 y acción Buy40 conservados. Capturas en `evidence/cosmetic-preview-access/`; la captura del diálogo precede a la corrección final de su etiqueta accesible.
- Compilación final correcta (2 s) y prueba Android del botón correcta: permanece accesible con compra deshabilitada y no ejecuta su callback comercial. No se realizaron compras ni se modificaron productos/precios. La aceptación de facturación real en Play sigue pendiente.

### 2026-09-09 — Store decoration touch placement
- Owned decoration previews open the home with the selected object ready for touch placement, matching the home editing flow.
- Validated the actual store-to-home route on API26 emulator; 4 gesture regression tests pass. Evidence: `evidence/store-touch-placement/`.
- Decoration preview dialog height needs refinement; full delivery gates remain open.

### 2026-09-09 — Compact decoration previews
- Resolved excessive dialog height while preserving explicit card dimensions.
- Verified measurement regression and real API26 dialog at 200% font scale; purchase/cancel actions remain readable. Evidence: `evidence/decoration-preview-size/`.

### 2026-09-09 — Integrated store/web validation
- Current debug build: 260 JVM tests pass; full Android repeat passes all 230 tests without omissions after explicitly isolating motion preferences in animation test fixtures and granting overlay permission on the emulator.
- Asset validator passes all 15 pets / 280 production frames. Reviewed the current all-pet home sheet; this is not continuous motion acceptance.
- Initial failures, corrective fixture rationale, exact hashes and final logs: `evidence/integrated-store-web-validation/`. Full visual/art, physical performance and Play/release gates remain open.

### 2026-09-09 — Tela ceiling contact
- Removed the vertical teleport when ceiling traversal begins within the 30px edge tolerance. Contact settles over 0.25s and then holds the ceiling.
- Debug/test builds and 14 Android perimeter cases pass. Evidence and explicit visual limitations: `evidence/tela-ceiling-contact/`.

### 2026-09-09 — Tela wall/ceiling scale
- Calibrated frames 12–19 against approximate eye-spacing landmarks, preserving the original art and 0.60 species size. Confirmed source frames equal packaged atlas cells.
- Reviewed the actual renderer sheet and passed desktop visibility/clipping and camera geometry checks. Evidence: `evidence/tela-surface-camera/`.
- Continuous surface transitions and missing intermediate artwork remain pending; this is camera calibration, not completed motion acceptance.

### 2026-09-09 — Tela sustained sleep
- Sustained desktop/home sleep uses shared, matching closed-eye breathing poses; removed the repeated seated-to-curled posture jump from sleep playback, preserving source artwork.
- Four Android continuity/rest-route/web-render tests pass. Evidence: `evidence/tela-sleep-continuity/`.
- A dedicated curl/uncurl animation and other missing intermediate frames remain outstanding.

### 2026-09-09 — Tela intermediate artwork attempt
- Generated one new curl-transition candidate through the built-in image tool using original frames 39 and 37 as references. This was a distinct Tela pose request, not a repeat of the earlier Corgi prompts.
- Rejected after file and visual inspection: RGB 1254×1254 with baked checkerboard, no alpha; changed markings/fur/leg proportions. No runtime asset replaced or frame claimed complete.
- Exact prompt, candidate and validation: `../../tools/tela/raw/curl-transition-2026-09-09/`. Do not repeat the same built-in request. The earlier question authorizing a transparent-output CLI/API fallback remains unanswered; do not use that paid API path without authorization.
- New intermediate art remains an open delivery requirement. Continue independent implementation/review work while that production path is unresolved.

### 2026-09-09 — Expedition reboot/cancellation persistence
- Added on-disk Room coverage combining reopen, simulated boot, backward wall clock, continued uptime, cancellation and stale request IDs against a newer trip.
- Both disk persistence tests pass. No production changes were required. Evidence: `evidence/expedition-reboot-cancel/`.
- This uses injected clocks and real database reopen; actual Android process death and physical reboot are not claimed validated by this test.

### 2026-09-09 — Actual process-stop expedition cancellation
- Real API26 emulator UI trip survived am force-stop and reopened in a new PID, retaining progress; no clock/database manipulation.
- No desktop service during travel; cancellation resumed foreground PetService. Stable disk comparison confirms no treasure, inventory or journal changes and cleared expedition.
- Evidence: `evidence/expedition-process-cancel/`. Timed reward completion after process death and physical reboot remain distinct pending checks.

### 2026-09-09 — Real Android reboot and timed expedition reward
- Completed a real five-minute meadow trip across an actual API26 emulator reboot (boot_count 2→3), without editing clocks or database values. App remained stopped for most of the duration.
- Collected once, restarted the app again, and verified exactly one treasure, wildflowers and journal event; reward tables unchanged after reopening. Persisted needs/condition unchanged by travel.
- Evidence: `evidence/expedition-real-reboot-reward/`. This closes emulator reboot/completion recovery evidence; physical phone reboot remains distinct. Ready-trip copy should say returned rather than exploring/0 minutes.

### 2026-09-09 — Clear expedition ready state
- Completed trip now says the adventure is finished and explains collection/resuming care, replacing exploring/0-minute copy in English and Spanish.
- Two actual Android UI tests pass (active→ready and unknown-trip cancellation), build passes. Evidence: `evidence/expedition-ready-copy/`.

### 2026-09-09 — Species-aware care journal art
- Care memories now illustrate their recorded action with the same species-specific tools as care scenes, instead of an identical heart for all actions. Foreground proportions are preserved in wide cards.
- Build and three Android tests pass; actual rendered sheet reviewed. Evidence: `evidence/journal-care-species/`.

### 2026-09-09 — Ginger midair affection
- Fixed a tap during flight freezing Ginger and later snapping her to the floor. Airborne affection preserves trajectory/facing while retaining feedback; ground interaction remains available.
- Seven desktop/stalk Android tests pass, including touched/control trajectory comparison at 30/60/120Hz. Evidence: `evidence/ginger-midair-touch/`.

### 2026-09-09 — Shared flight and affection
- Fixed PetView taps interrupting an active shared fall. Existing physics body, state, scale and facing survive while feedback and care affordance still execute.
- Eight Android physics integration tests pass, including catalogue coverage for non-runtime input behaviors. Evidence: `evidence/shared-flight-affection/`.


### 2026-09-12 — Corgi: apoyo al sentarse y levantarse
- Añadido un pack opt-in que conserva los 14 originales y añade dos intermedios de caderas, postura sentada y parpadeo con cuerpo idéntico. Cámara fija y apoyos compartidos en hogar/escritorio, sin normalizar cada silueta.
- Sentarse/levantarse duran 520 ms; la locomoción espera al endpoint de pie. Sueño programado conserva la postura de entrada y completa despliegue y levantamiento antes de reanudar. Corregidas interrupciones y reactivación del horario durante el despertar.
- Verificación final: 281 JVM, 303 Android y 20 Python sin fallos ni omisiones; lint cero errores/244 advertencias. Render real de 240 frames a 30 FPS y traza de posiciones; evidencia en `evidence/corgi-sit-2026-09-12/`.
- Candidata debug `PixelPals-2.4.0-review-corgi-sit.apk`, 2.4.0/código21, anuncios de prueba, SHA-256 `c288e997aa1e8098eaa23f3855e7662612557db85520d559c92c3ae8f7864e0c`. No promoción a producción; giros, otros cambios de acción y el resto del cierre siguen abiertos.

### 2026-09-12 — Corgi: giro con apoyos
- Tres poses nuevas amplían el atlas opt-in a 23 celdas; las veinte anteriores son idénticas a la candidata de sentado. Cámara común y suelo conservado, con perspectiva frontal más estrecha.
- Bordes del escritorio y cambios de destino del hogar usan siete poses durante 560 ms, después de frenar. El rumbo se confirma al final; el arranque espera al siguiente avance. Interrupciones y movimiento reducido limpian la transición.
- 287 JVM, 309 Android y 20 Python correctos, sin omisiones; lint cero errores/244 advertencias. Secuencia de 90 imágenes del renderer Android y lámina de hogar, ambas direcciones; evidencia `evidence/corgi-turn-2026-09-12/`.
- Candidata debug `PixelPals-2.4.0-review-corgi-turn.apk`, instalada en el OnePlus con SHA-256 `5c30cbea7718de7f1258063e3dd8087ed67a8110b9d5e6bd89bc6b624b5e5aed`. Conserva versión/código, anuncios y separación `.debug`. No cierra los giros al lanzar, otros cambios de acción, las quince mascotas, la prueba prolongada física ni Play.

### 2026-09-12 — Corgi: giro al lanzar y caída sin teletransporte
- Los lanzamientos desde altura y verticales delegan en la física compartida. El sprint horizontal en suelo gira con los apoyos existentes si cambia de dirección, antes de anticipar y correr. Interrumpirlo conserva la orientación visible; movimiento reducido cancela el desplazamiento pendiente.
- El reposo de perfiles terrestres ajusta el apoyo exactamente al suelo, conservando los perfiles flotantes/acuáticos y el anclaje de trepadoras.
- 289 JVM y 315 Android correctos, sin omisiones; lint cero errores/244 advertencias. No se modifican los 28 archivos gráficos de Bloop/Corgi empaquetados. Evidencia: `evidence/corgi-fling-2026-09-12/`.
- Candidata debug `PixelPals-2.4.0-review-corgi-fling.apk`, instalada y verificada en OnePlus; SHA-256 `9b6fc1284f6f847dae3aed27ef7fd0887bbeb169197a157a92471b056d82a24f`. Mantiene Bloop seleccionado, versión/código y anuncios. Siguen pendientes la aceptación artística completa, otros cambios de acción, Ginger/Jelly/Tela, la revisión general, el ensayo prolongado físico y Play.

### 2026-09-12 — Ginger: sentado/levantado y halo de cuidados
- Tres dibujos optativos nuevos completan la subida/bajada entre sentado y marcha. Hogar y escritorio comparten cámara y una transición de 660 ms, sin desplazamiento durante los apoyos. Se conservan los 16 frames originales, se cancelan acciones pendientes al arrastrar/lanzar y el despertar termina de pie sin repetir la subida.
- 293 JVM y 320 casos Android únicos validados entre la suite general y la repetición de la expectativa de descanso actualizada; detalle preciso en `evidence/ginger-posture-2026-09-12/README.md`. Lint cero errores/245 advertencias.
- A petición del usuario se retiró después el halo blanco/gris bajo las patas de las 24 poses de cuidados. Siete Python y siete pruebas Android de renderizado correctas; la APK cambia exclusivamente ese PNG frente a la candidata de posturas.
- Candidata de revisión instalada en OnePlus: `PixelPals-2.4.0-review-ginger-care.apk`, SHA-256 `488ce6f850d4798da351fd281eb132003c69dd942b2e0a88f5ea0efe8ed43337`. Conserva `.debug`, versión/código, anuncios y selección. Evidencia en `evidence/ginger-shadow-2026-09-12/README.md`.
- Siguen pendientes la aceptación completa de Ginger (giro, ovillo/estiramiento y continuidad con cuidados), Jelly/Tela y demás secuencias del catálogo, revisión conjunta de la app, ensayo prolongado físico y Play.


### 2026-09-12 — Jelly: continuidad elástica y contorno
- Escritorio con trayectoria balística, colisiones, liberación sin recolocación y conservación del impulso al tocar en el aire. Compresión y recuperación continuas sobre el cuerpo brillante original; hogar comparte curvas, aterrizaje y contacto con el resorte. Evidencia `evidence/jelly-elastic-2026-09-12/`: 311 JVM, 325 Android completos, lint cero errores/245 advertencias.
- A petición del usuario, retirado después el contorno blanco de los ocho recursos de animación, el retrato y las 24 poses de cuidados. Se conservan RGB, reflejos internos y burbujas de sueño. Apoyo de la gelatina limpio recalibrado, cámara original del descanso conservada y pipeline reproducible; sin alterar otros pets ni anclajes del atlas.
- Revisión del contorno: 311 JVM, 19 Android focalizados y 14 Python únicos correctos; lint sin errores. APK `.debug` 2.4.0/código21 instalada y hash verificado en OnePlus, conservando Jelly seleccionado: `ea1c1a4bbdb393be30b8b1aff3055e52a41d1fb413f99e83c3a6736846a4078e`. Evidencia `evidence/jelly-outline-2026-09-12/`.
- Un candidato nuevo de expresión de salto se conserva fuera del paquete: requiere intermedios/alineación y aceptación visual. El catálogo completo, la continuidad entre todas las acciones, revisión general de la app, ensayo prolongado físico y validación Play siguen abiertos.


### 2026-09-12 — Jelly: apoyo continuo en cuidados y descanso
- La revisión posterior al contorno midió un salto vertical de 7–9px al entrar/salir de las seis acciones. El pipeline recalibra únicamente ground.y de las 24 poses; boca, cabeza, cuerpo, ground.x y PNG permanecen iguales. El hogar conserva la escala/cámara del descanso y compensa el margen transparente para apoyar la gelatina sobre su cama.
- Los 24 endpoints (entrada/salida, seis acciones, movimiento normal/reducido) quedan a un píxel del apoyo normal. Evidencia antes/después y láminas de renderer Android en `evidence/jelly-care-contact-2026-09-12/`.
- Validación de esta candidata: 311 JVM, 327 Android completos y 15 Python sin fallos ni omisiones; lint cero errores/245 advertencias. APK debug `PixelPals-2.4.0-review-jelly-contact.apk`, SHA-256 `1c9d6678b3a200943ebcf476bdc0e25f456147d4c84950026b44a5f94edec956`.
- Siguen pendientes cambios de silueta de medicina/descanso, intermedios de cuidados y coreografía causal del resorte. Medidas y decisiones en `tools/jelly/review/CARE-CONTINUITY.md`; no se compensa todo el banco con una escala global porque la entrada de comida ya coincide con el original. El alcance general y las pruebas Play siguen abiertos.

### 2026-09-12 — Jelly: resultado del descanso sin cambio de cámara
- El panel conserva la pose REST completada únicamente de Jelly mientras libera la sesión. Nueva acción, cancelación y pausa limpian o sustituyen la presentación; los gestos de una escena manual finalizada se liberan.
- 311 JVM y ocho Android focalizados correctos; lint cero errores/245 advertencias. Las 327 pruebas completas anteriores corresponden a la candidata de contacto, no a esta modificación posterior. Anuncio de prueba cerrado en el test, sin deshabilitar anuncios del producto.
- Instalada y verificada en OnePlus la candidata `PixelPals-2.4.0-review-jelly-rest-result.apk`, SHA-256 `3c5cce5df956b30cbf3ce6e69dd1bf3dfb9534609ec85e4bef3037ba98510c59`. Descanso real revisado: energía92→100, salud+3 y vínculo54 conservado; la pose final permanece en su apoyo. Evidencia en `evidence/jelly-rest-result-2026-09-12/`.
- Se conservan los pendientes artísticos de Jelly y el cierre general del plan; no hay promoción a producción.

### 2026-09-12 — Jelly: contacto físico con su resorte
- Resorte rosa/dorado compartido con hogar: base fija, compresión bajo el cuerpo, impulso, vuelo parabólico, aterrizaje y regreso al suelo. Los cambios entre impulso/vuelo conservan velocidad; Jelly mantiene área aparente y una sola pose/cámara limpia durante el juego. Movimiento reducido mantiene ambos quietos.
- 317 JVM correctas; 16 Android focalizadas, incluyendo cuidados de las 15 mascotas y ciclo de vida del panel, pasan. 624 muestras reales verifican contactos, base fija, límites y tres variaciones en hogar/escritorio. Exportadas 151 imágenes a30fps por superficie y GIF. Lint cero errores/246 advertencias, una nueva recomendación de Canvas.withScale. No hay nueva ejecución completa de toda la suite Android ni cambios de PNG en esta candidata.
- APK `PixelPals-2.4.0-review-jelly-spring.apk`, SHA-256 `11ac50d1dfc6670312bbb1349bd5e87efac9001d0fc58f8b17014c6d5d102191`, instalada y hash contrastado en OnePlus conservando Jelly seleccionado y habilitado. Evidencia `evidence/jelly-spring-2026-09-12/`.
- Continúan abiertos los dibujos de medicina/descanso, expresiones/intermedios y aceptación artística integral, además de los demás bloques del cierre y Play. Esta corrección no acredita el plan completo.

### 2026-09-12 — Resultado del juego y plurales del hogar
- El panel reconoce cada PLAY completado y explica el gasto de energía/apetito conservando todos los cambios reales, incluso cuando no concede vínculo ni monedas. Despertar y recuperación mantienen prioridad. El contador del hogar usa singular/plural Android para español/inglés.
- 317 JVM y16 Android focalizadas pasan sin fallos/omisiones; lint cero errores/246 advertencias. Revisión física de singular1 y plural2, resultado con costes sin recompensa; recorrido real en inglés al200% en emulador, cierre accesible mediante scroll y ajuste de fuente restaurado.
- Candidata `PixelPals-2.4.0-review-care-feedback.apk`, SHA-256 `5735402f08c12744429d5be8cfbb48b718c7f3c0d315080358d7718f6249619f`, instalada y verificada en OnePlus conservando Jelly activo. Evidencia `evidence/care-result-feedback-2026-09-12/`. Se mantienen arte, motor de cuidados, datos y monetización; los demás bloques del cierre continúan abiertos.

- **12 septiembre · Jelly, medicina:** cuatro dibujos nuevos con cámara común, cuchara coordinada y deglución que conserva área; el panel retiene la expresión final tras liberar la sesión. La candidata pasa 322 JVM, 18 Android focalizados y 17 Python; lint sin errores (246 advertencias). Instalada en OnePlus con SHA verificado; arte revisado en ambas superficies mediante renderer y en capturas físicas de la vista de prueba. El descanso de Jelly y los demás bloques de cierre siguen abiertos. Evidencia: `evidence/jelly-medicine-2026-09-12/README.md`.


### Descanso de Jelly y ajuste de cámara — 2026-09-12

Se integraron seis expresiones de sueño sobre un cuerpo/alfa común, deformación continua con área constante y recuperación de1.2s en hogar/escritorio (1.5s en cuidados). Se corrigió la cámara pequeña del sueño programado. Candidata instalada: `PixelPals-2.4.0-review-jelly-rest-art.apk`, SHA`c0cab5670d454a273060f2a08f2fb4256a2e023d8c191da5090ca1e3e2aedede`. JVM329, Android24 y Python19correctas; lint0errores/247advertencias. Evidencia y alcance en `evidence/jelly-rest-art-2026-09-12/README.md`. Esto cierra la sustitución del descanso irregular de Jelly en esas rutas; otros cuidados/interrupciones y la aceptación integral del catálogo y la app siguen abiertos. El usuario ha señalado el tiempo y consumo excesivos: agrupar pendientes en entregas verificables y evitar auditorías repetidas de cambios pequeños.

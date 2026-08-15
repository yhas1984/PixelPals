# PixelPals runtime contract V2

Este documento es la fuente de intención para locomoción, física, personalidad y
entrada de usuario. Un behavior puede añadir acciones propias, pero no puede
duplicar la interpretación de gestos, límites de pantalla o ciclo de vida de
efectos.

## Contrato común

- `PetView` convierte eventos Android en `tap`, `drag`, `release`, `fling` y
  `cancel`. El behavior recibe la intención; no crea temporizadores de touch.
- `PetBounds` es la única caja lógica. El pivote y `drawScale` del atlas definen
  el punto de contacto; los behaviors no añaden márgenes propios.
- Un `tap` entra en `INTERACTING` y termina en la personalidad del pet. Un
  `drag` limpia efectos temporales y sigue el dedo. Un `release` sin velocidad
  cae con la física de su especie. Un `fling` conserva la velocidad limitada por
  el perfil. `cancel`, hide y destroy limpian todos los efectos.
- La animación se reproduce únicamente desde `frames`, `loop` y
  `frameDurationMs` del JSON V2. La lógica decide el clip, nunca la duración de
  cada frame.
- Las transiciones deben ser deterministas con `PetRandom` inyectado y deben
  tener una salida válida: ningún estado puede bloquear el overlay ni cruzar la
  caja de la pantalla.

## Identidad y locomoción

| Pet | Identidad | Superficie primaria | Física | Respuesta al usuario | Personalidad dominante |
| --- | --- | --- | --- | --- | --- |
| Tela | Araña mint-lavanda de ocho patas, ojos grandes y marcas violetas | Perímetro: suelo, paredes y techo | `EDGE`, adhesión al borde | Tap: touch/happy; drag: se agarra al dedo; fling: reacopla al perímetro; seda y telaraña se limpian | Curiosa, juguetona, ligeramente imprevisible |
| Taro | Tortuga bípeda compacta y amable | Suelo | `GROUND` | Tap: social; drag/fling: salto corto y aterrizaje estable | Leal y pausado |
| Yuki | Mascota nevada ligera | Suelo y saltos | `GROUND` | Tap: curiosidad; drag/fling: rebote suave | Curiosa y soñadora |
| Menta | Serpiente mint flexible | Suelo, con deslizamiento continuo | `GROUND` | Tap: se enrolla; drag: sigue el dedo sin teletransporte | Elegante y tranquila |
| Piru | Pingüino pequeño | Suelo y agua simulada | `AQUATIC` | Tap: aleteo; drag/fling: deslizamiento amortiguado | Bouncy y social |
| Angel | Criatura alada | Espacio libre | `FLYING` | Tap: aleteo; drag/fling: deriva aérea limitada | Angelical |
| Ginger | Gato naranja | Suelo | `GROUND` | Tap: aseo/ronroneo; drag/fling: aterrizaje con patas | Dulce y curiosa |
| Moki | Roedor trepador | Suelo, paredes y techo | `EDGE` | Tap: inspección; drag/fling: se agarra y vuelve a una superficie | Curiosa |
| Bloop | Fantasma flotante | Espacio libre | `FLYING` | Tap: cambia forma; drag/fling: deriva elástica | Caótica y juguetona |
| Nube-Michi | Gato nube | Espacio libre | `FLYING` | Tap: ronroneo; drag/fling: flotación con amortiguación | Soñadora |
| Jelly | Gelatina blanda | Suelo | `GROUND` | Tap: squash/stretch; drag/fling: rebote sin atravesar suelo | Bouncy |
| Corgi | Perro pequeño | Suelo | `GROUND` | Tap: saludo; drag/fling: corre y frena | Leal |
| Patito | Pato | Suelo/agua simulada | `AQUATIC` | Tap: chapoteo; drag/fling: deslizamiento | Bouncy |
| Diablillo | Criatura traviesa | Suelo | `GROUND` | Tap: reacción traviesa; drag/fling: salto controlado | Caótica |

## Gates por behavior

Antes de promover un behavior V2 deben existir pruebas puras para la máquina de
estados y pruebas instrumentadas para bounds, tap, drag, fling, hide/show y
configuración. La aprobación visual se registra junto al hash del atlas y JSON;
si cambia cualquiera de los dos, la aprobación deja de ser válida.

Tela es el primer behavior `EDGE` de referencia. Taro será el primer behavior
`GROUND`; ambos deben demostrar el contrato antes de migrar Yuki y el resto.

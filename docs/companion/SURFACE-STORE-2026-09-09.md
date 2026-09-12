# Transiciones de Tela y recuperación de cosméticos

## Tela

WALK, CLIMB y CEILING inicializan sus transformaciones al entrar: no conservan el balanceo anterior ni una orientación de la superficie anterior durante el primer frame. La velocidad máxima de trepa/techo se limita por tamaño renderizado, respetando las duraciones mínimas de personalidad. Los ciclos de patas se buscan por distancia, también en paredes y techo; se eliminó el movimiento vertical por reloj que separaba las patas del apoyo sin desplazamiento.

La regresión añade paredes/techo estacionarios, con residuos iniciales de balanceo e inclinación: comprueba entrada limpia y ausencia de pasos/movimiento vertical sin desplazamiento. La revisión visual continua de trepa y techo sigue pendiente; no se han calibrado sus frames anatómicos en este lote.

## Tienda

Si comprar un cosmético tiene éxito pero equiparlo falla, la coroutine ya no abandona la operación dejando la tienda ocupada. Conserva el producto comprado, libera la operación y permite equipar de nuevo. Un aviso localizado en español/inglés explica que el cosmético sigue siendo del usuario y el reintento de Equipar no vuelve a cobrar. No se muestra el callback de éxito de equipamiento ante el fallo.

El test de ViewModel usa propiedad/saldo mutables: compra de 10 monedas desde saldo 100, equipamiento fallido, propiedad conservada/saldo 90, reintento exitoso con una sola llamada de compra y saldo 90. La prueba Android existente confirma que comprar cosméticos conserva la mascota seleccionada.

## Validación

- Debug/test assembly y lint: correctos, 1m 3s.
- StoreViewModelTest: 8 pruebas JVM, cero fallos/errores.
- TelaWalkingDistanceTest y CosmeticsPurchaseKeepsSelectedPetTest: 5 pruebas Android, 5.796 segundos.
- `git diff --check` limpio. Logs/XML en `evidence/surface-store/`.

Esto no valida compras/restauración reales de Play ni cierra la aceptación comercial o visual del plan.

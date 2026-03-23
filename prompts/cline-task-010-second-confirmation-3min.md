Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos:
- README.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/telegram-flow.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Implementar el envío de una segunda captura de confirmación 3 minutos después de la alerta inicial.

Qué debes hacer:
1. Programar una segunda acción diferida tras la alerta inicial.
2. A los 3 minutos, obtener una nueva captura completa.
3. Enviar esa segunda captura por Telegram.
4. Mantener el flujo desacoplado de la UI.
5. Añadir logs o trazas claras para seguir el comportamiento.
6. Mantener el sistema lo más simple y estable posible para el MVP.

Qué NO debes hacer todavía:
- no introducir un scheduler complejo innecesario
- no reestructurar de más el sistema si no hace falta
- no mezclar nuevas funciones fuera del objetivo de esta iteración

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- tras una alerta inicial, el sistema envía una nueva captura 3 minutos después,
- la segunda captura es nueva, no reutilizada,
- el comportamiento queda trazable y entendible.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- describe cómo se prueba manualmente,
- indica riesgos o limitaciones.
Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos:
- README.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/telegram-flow.md
- docs/roi-strategy.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Conectar la detección del ROI con el flujo inicial de alerta por Telegram.

Qué debes hacer:
1. Cuando la lógica de detección indique evento relevante, disparar el flujo de alerta.
2. Enviar primero el mensaje:
   `Minda Requiere Atención`
3. Obtener y enviar una captura completa actual.
4. Añadir una protección básica para evitar múltiples alertas descontroladas por el mismo evento.
5. Mantener la lógica separada entre detección, captura y Telegram.

Qué NO debes hacer todavía:
- no implementar aún mejoras complejas de anti-rebote si no hacen falta
- no introducir todavía sistemas avanzados de cola de eventos
- no mezclar demasiadas reglas industriales complejas

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- un evento detectado dispara mensaje + captura,
- el sistema no entra fácilmente en spam de alertas,
- el proyecto sigue modular y entendible.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- describe cómo se prueba manualmente,
- recomienda el siguiente paso.
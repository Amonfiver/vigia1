Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos:
- README.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/telegram-flow.md
- docs/setup-dev.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Implementar la configuración base de Telegram y una acción de prueba.

Qué debes hacer:
1. Añadir campos para bot token y chat_id.
2. Guardarlos localmente.
3. Crear una capa simple de servicio para Telegram usando OkHttp.
4. Implementar un botón o acción de "Enviar prueba".
5. Enviar un mensaje simple de prueba al destino configurado.
6. Reflejar en la UI si el envío fue exitoso o falló.
7. Mantener la arquitectura separando UI, datos y red.

Qué NO debes hacer todavía:
- no implementar envío de imágenes reales del flujo de alerta
- no implementar disparo automático por detección
- no implementar segunda captura a los 3 minutos
- no mezclar todavía Telegram con el detector

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- el usuario puede introducir bot token y chat_id,
- esos datos quedan guardados,
- puede enviar un mensaje de prueba,
- la app informa si la prueba funcionó o no.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- describe cómo se prueba manualmente,
- recomienda el siguiente paso.
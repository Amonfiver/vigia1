Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos para coger contexto:
- README.md
- docs/vision.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/roi-strategy.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Implementar solo la preview de cámara en la pantalla principal usando CameraX.

Qué debes hacer:
1. Mostrar la preview de cámara en la UI principal.
2. Solicitar permisos de cámara correctamente.
3. Mostrar un estado claro si el permiso no ha sido concedido.
4. Mantener visibles los botones de iniciar vigilancia y detener vigilancia, aunque aún no activen la lógica real.
5. Mantener la pantalla simple, estable y compilable.
6. Separar la lógica de cámara de la UI todo lo posible.

Qué NO debes hacer todavía:
- no implementar análisis de ROI
- no implementar definición manual de ROI
- no implementar Telegram real
- no implementar capturas reales
- no implementar detección de eventos

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- la app compila,
- se ve la cámara en pantalla,
- el permiso se gestiona correctamente,
- la arquitectura queda preparada para conectar más adelante el análisis de frames.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- indica limitaciones temporales,
- recomienda el siguiente paso.
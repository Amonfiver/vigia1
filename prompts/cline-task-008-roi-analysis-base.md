Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos:
- README.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/roi-strategy.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Implementar la base de análisis visual sobre el ROI guardado.

Qué debes hacer:
1. Crear una estructura clara para analizar el ROI guardado.
2. Hacer que el análisis solo se ejecute cuando la monitorización esté activa.
3. Crear una clase o interfaz separada para la lógica de detección.
4. Implementar una lógica provisional simple y claramente marcada como temporal.
5. Dejar el sistema preparado para sustituir esa lógica por una mejor más adelante.

Qué NO debes hacer todavía:
- no cerrar aún una inteligencia avanzada
- no implementar múltiples ROIs complejos
- no introducir entrenamiento por plantillas todavía
- no disparar aún todo el flujo completo si complica demasiado esta iteración

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- existe un flujo base de análisis del ROI,
- solo analiza cuando la vigilancia está activa,
- la lógica de detección es modular,
- queda lista para conectar con el disparo de alerta.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- indica las limitaciones de la lógica provisional,
- recomienda el siguiente paso.
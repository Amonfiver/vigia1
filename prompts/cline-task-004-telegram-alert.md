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
Implementar la base de definición manual de ROI.

Contexto importante:
En VIGIA1 el ROI NO se detecta automáticamente.
El ROI debe ser definido manualmente por el usuario/desarrollador sobre la imagen de cámara.

Qué debes hacer:
1. Crear un modo de edición o selección de ROI.
2. Permitir marcar manualmente un ROI rectangular sobre la preview de cámara o sobre una vista adecuada del frame.
3. Representar visualmente el rectángulo seleccionado.
4. Crear un modelo de datos claro para el ROI.
5. Dejar la solución simple pero funcional y ampliable.

Qué NO debes hacer todavía:
- no implementar todavía análisis real del ROI
- no implementar comparación de frames
- no implementar Telegram
- no implementar capturas enviadas
- no implementar múltiples ROIs avanzados

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- el usuario puede entrar en modo de definición de ROI,
- puede dibujar o marcar un ROI rectangular,
- el ROI queda visible y estructurado a nivel de modelo,
- la base queda preparada para guardarlo en la siguiente iteración.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- indica limitaciones actuales,
- recomienda el siguiente paso.
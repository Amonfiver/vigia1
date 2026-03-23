Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos:
- README.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/roi-strategy.md
- docs/setup-dev.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Guardar localmente el ROI definido y permitir redefinirlo desde una opción de ajustes o edición.

Qué debes hacer:
1. Implementar persistencia local del ROI.
2. Recuperar el ROI guardado al volver a abrir la app o la pantalla correspondiente.
3. Mostrar en UI si ya existe un ROI guardado.
4. Añadir una opción clara para redefinir el ROI cuando el usuario quiera.
5. Mantener la solución simple y apropiada para el MVP.

Qué NO debes hacer todavía:
- no implementar aún detección real
- no implementar Telegram real
- no implementar temporizador de confirmación
- no implementar varios perfiles complejos

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- el ROI puede guardarse localmente,
- el ROI puede recuperarse,
- el usuario puede redefinirlo desde UI o ajustes,
- la base queda lista para que el análisis use ese ROI guardado.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- describe cómo se prueba manualmente,
- recomienda el siguiente paso.
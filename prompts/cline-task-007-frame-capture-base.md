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
Implementar la base para obtener una captura completa del frame actual de cámara y dejarla lista para futuros envíos por Telegram.

Qué debes hacer:
1. Crear una pieza clara y reutilizable para capturar el frame completo actual.
2. Preparar el resultado para que pueda usarse más adelante al enviar imágenes por Telegram.
3. Mantener la separación entre cámara, captura y red.
4. Si es necesario, incluir una acción de prueba local para validar que la captura se obtiene correctamente.

Qué NO debes hacer todavía:
- no implementar el flujo automático de detección
- no enlazar todavía la captura con alertas automáticas
- no implementar todavía la segunda captura diferida
- no añadir complejidad innecesaria

Regla obligatoria:
Cada archivo de código creado o modificado debe incluir al inicio una cabecera breve de documentación explicando:
- propósito del archivo,
- responsabilidad principal,
- alcance dentro del sistema,
- decisiones técnicas relevantes,
- limitaciones temporales del MVP,
- cambios recientes si aplica.

Resultado esperado:
- existe una base clara para capturar el frame completo actual,
- la solución queda reutilizable para el flujo de alerta,
- el proyecto sigue compilable y ordenado.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- describe cómo se valida manualmente,
- recomienda el siguiente paso.
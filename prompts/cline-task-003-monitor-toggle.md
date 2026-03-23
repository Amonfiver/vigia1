Continuamos en modo SDD sobre VIGIA1.

Antes de hacer cambios:
1. Lee estos archivos:
- README.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/sdd/001-spec-mvp.md
- docs/sdd/002-tasks-initial.md
- docs/sdd/003-acceptance-criteria.md
- prompts/cline-system-prompt.md

Objetivo de esta iteración:
Implementar solo el estado interno de monitorización activa o detenida.

Qué debes hacer:
1. Crear un estado de monitorización centralizado.
2. Al pulsar "Iniciar vigilancia", cambiar el estado a activo.
3. Al pulsar "Detener vigilancia", cambiar el estado a detenido.
4. Reflejar claramente el estado actual en la UI.
5. Dejar preparado el sistema para que en el futuro el análisis de frames solo procese cuando la monitorización esté activa.

Qué NO debes hacer todavía:
- no implementar definición de ROI
- no implementar análisis visual real
- no implementar Telegram
- no implementar capturas
- no implementar temporizador de 3 minutos

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
- el usuario puede iniciar y detener vigilancia,
- la UI refleja el estado,
- la base queda lista para conectar más tarde con el análisis real.

Al terminar:
- explica qué hiciste,
- lista archivos tocados,
- indica cómo queda preparado para la siguiente iteración.
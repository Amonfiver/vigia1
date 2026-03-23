# Estrategia ROI - VIGIA1

## Qué es un ROI en este proyecto

ROI significa región de interés.

En VIGIA1, un ROI es la zona concreta de la imagen de cámara que se quiere vigilar. En lugar de analizar toda la escena sin control, el sistema observa solo la parte importante definida por el usuario/desarrollador.

## Cómo se define el ROI

En esta primera versión, el ROI no se detecta automáticamente.

El ROI será:

- definido manualmente por el usuario/desarrollador,
- marcado sobre la imagen de cámara,
- guardado localmente,
- y reutilizado hasta que el usuario lo cambie.

## Quién puede definirlo

En el uso real del proyecto, el ROI podrá definirlo el usuario o desarrollador durante la primera configuración, o posteriormente desde una opción de ajustes o redefinición.

## Forma del ROI

En el MVP, el ROI será:

- rectangular
- simple
- guardado mediante coordenadas relativas o absolutas según la implementación elegida

## Número de ROIs

Para el MVP se recomienda empezar con:

- un solo ROI principal

La arquitectura debe quedar preparada para soportar varios ROIs en el futuro, pero no es necesario cerrar esa complejidad todavía.

## Por qué no se usa detección automática de ROI

Se descarta la detección automática temprana del ROI porque en iteraciones anteriores resultó menos fiable y generó errores innecesarios.

La selección manual da:

- más control,
- más precisión práctica,
- menos ambigüedad,
- mejor repetibilidad.

## Qué se compara dentro del ROI

En esta primera fase, el sistema comparará el contenido visual del ROI usando una lógica inicial simple.

La implementación exacta puede comenzar con alguna de estas estrategias provisionales:

- diferencia respecto a un estado base,
- cambio de intensidad o color,
- cambio agregado de píxeles,
- comparación simple entre frames.

La elección concreta de la primera regla técnica se podrá cerrar durante la implementación, pero siempre separada del resto de módulos.

## Estado base

El sistema podrá trabajar con la idea de un estado base o estado esperado del ROI.

Ese estado base puede ser:

- el primer estado observado tras activar monitorización,
- o una referencia inicial definida por la propia lógica temporal del detector.

## Qué se considera “cambio específico” en esta fase

En esta fase inicial, “cambio específico” significa:

un cambio visual relevante dentro del ROI que supere la regla temporal definida para disparar alerta.

Todavía no implica inteligencia avanzada ni clasificación completa del mundo real. Es una primera lógica funcional pensada para demostrar el flujo completo:

- vigilar,
- detectar,
- alertar,
- confirmar.

## Requisitos funcionales relacionados con ROI

- el usuario puede entrar en modo definición de ROI
- puede marcarlo manualmente
- puede guardarlo
- la app recupera ese ROI en siguientes sesiones
- el usuario puede redefinirlo desde ajustes cuando quiera

## Riesgos a tener en cuenta

- ROI mal definido
- cambios de iluminación
- movimiento de cámara
- variaciones que produzcan falsos positivos

Por eso la lógica de detección debe diseñarse como intercambiable y mejorable.
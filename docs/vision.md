# Visión del proyecto VIGIA1

## Qué es VIGIA1

VIGIA1 es el nuevo proyecto realista de VIGIA, creado a partir de la experiencia acumulada en intentos anteriores como MindaVigilante y VIGIA2.

El objetivo no es construir una demo bonita ni un sistema teórico, sino una herramienta útil, estable y ampliable que pueda servir de base para vigilancia visual real en procesos industriales.

## Problema real que resuelve

En determinados entornos industriales o de producción, el operario no puede estar mirando continuamente una zona concreta para detectar incidencias visuales. Eso genera dependencia de supervisión manual constante, riesgo de retrasos y pérdida de tiempo de reacción.

VIGIA busca reducir esa dependencia usando un smartphone como vigilante visual, capaz de observar una escena, detectar un cambio relevante y avisar por Telegram con evidencia visual.

## Contexto industrial

Este proyecto está pensado para escenarios donde existe una vía, transfer, zona de paso, punto de acumulación o área crítica que necesita vigilancia visual periódica o continua.

El smartphone actúa como cámara fija de observación. La app no pretende sustituir un sistema industrial completo en esta fase, sino ofrecer una capa práctica de monitorización y aviso usando hardware accesible.

## Por qué nace de MindaVigilante y VIGIA2

VIGIA1 nace porque los intentos anteriores dejaron aprendizajes muy valiosos:

- no conviene empezar por automatismos demasiado ambiciosos,
- la detección automática de zonas puede fallar,
- es mejor controlar manualmente ciertos parámetros clave,
- la arquitectura debe prepararse para crecer desde una base simple,
- el sistema debe centrarse primero en resolver una necesidad concreta y real.

Por eso, VIGIA1 se plantea como el sistema verdadero: más aterrizado, más controlado y más alineado con el uso real.

## Decisión clave de diseño

Una de las decisiones más importantes de VIGIA1 es que el ROI no se detectará automáticamente en el MVP.

El usuario o desarrollador definirá manualmente la zona de interés sobre la imagen de cámara y la dejará guardada para futuras sesiones, con opción de redefinirla desde ajustes cuando sea necesario.

Esto mejora la fiabilidad y evita errores tempranos por intentar adivinar zonas automáticamente.

## Meta realista del MVP

La meta del MVP es lograr una app Android en Kotlin que:

- muestre la cámara,
- permita iniciar y detener la vigilancia,
- permita definir y guardar manualmente el ROI,
- monitorice ese ROI,
- detecte un cambio visual específico con lógica inicial simple,
- envíe a Telegram un mensaje y una captura,
- y mande una segunda captura a los 3 minutos.

## Qué no intentamos resolver aún

En esta primera fase no se busca cerrar todavía:

- inteligencia visual avanzada,
- entrenamiento complejo,
- múltiples modos industriales completos,
- sistema multi-escena sofisticado,
- backend o panel web.

Primero se construye el núcleo útil.
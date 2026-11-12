# PROGRESS

Estado del proyecto respecto a `ROADMAP.md`. Se actualiza al terminar cada hito.

## Fase actual: 2 — Cañón y proyectil en el juego

## Hitos completados

### Fase 0 — Preparación

- **0.1 Proyecto Gradle.** Fabric Loom 1.11.8, Gradle 8.14.3 (wrapper), Minecraft 1.21.1,
  Yarn 1.21.1+build.3, Fabric Loader 0.20.1, Fabric API 0.116.18+1.21.1, Java 21.
  JUnit mediante `fabric-loader-junit`, como indica la documentación de Fabric.

### Fase 1 — Núcleo de física

- **1.1 – 1.4 completados.** Paquete `fisica` (11 clases, solo Java estándar) y 47 tests
  JUnit en verde (`./gradlew test`), incluido el test de arquitectura.

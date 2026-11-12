# PROGRESS

Estado del proyecto respecto a `ROADMAP.md`. Se actualiza al terminar cada hito.

## Fase actual: todas las fases del roadmap (0 – 4) están completadas

Lo siguiente serían las «Ideas para más adelante» del roadmap.

## Cómo se verifica

| Comprobación | Comando | Resultado |
| --- | --- | --- |
| Compilación + tests JUnit de física | `./gradlew build` | 51 tests en verde |
| Pruebas dentro del juego (servidor) | `./gradlew runGametest` | 14 pruebas en verde |

**Sin verificar:** la parte visual en un cliente real (aspecto del bloque, proyectil en vuelo,
partículas, textos del chat). El código de cliente compila contra Minecraft 1.21.1, pero el
entorno donde se desarrolló no puede abrir el juego. Hay que probarlo con `./gradlew runClient`.

## Hitos completados

### Fase 0 — Preparación

- **0.1 Proyecto Gradle.** Fabric Loom 1.11.8, Gradle 8.14.3 (wrapper), Minecraft 1.21.1,
  Yarn 1.21.1+build.3, Fabric Loader 0.20.1, Fabric API 0.116.18+1.21.1, Java 21.
  JUnit mediante `fabric-loader-junit`, como indica la documentación de Fabric.

### Fase 1 — Núcleo de física

- **1.1 Cinemática analítica** (`Lanzamiento`).
- **1.2 Problema inverso** (`Punteria`).
- **1.3 Dinámica numérica** (`ModeloFuerzas`, `Integrador`, `Simulador`,
  `LanzamientoConArrastreLineal`, `Energia`).
- **1.4 Tests JUnit** y test de arquitectura (el paquete `fisica` no importa Minecraft).

### Fase 2 — Cañón y proyectil

- **2.1 Bloque cañón** con entidad de bloque, objeto, modelo, texturas, receta, botín y
  traducciones (es/en).
- **2.2 Proyectil** integrado con RK4 por el paquete de física; impacto por trazado de rayos
  contra los bloques; estela de partículas. No se guarda con el mundo.
- **2.3 Disparo** con clic derecho y con el flanco de subida de una señal de redstone.

### Fase 3 — Laboratorio

- **3.1 Comando `/canon`**: velocidad, ángulo, azimut, gravedad (valor o astro), rozamiento,
  apuntar, info.
- **3.2 Informe de cada disparo** (`InformeDisparo`): medido frente a teórico con error.
- **3.3 Vista previa** (`/canon prever`) y **puntería automática** (`/canon objetivo`).

### Fase 4 — Verificación y entrega

- **4.1 GameTest**: 14 pruebas en un servidor real. El impacto coincide con la teoría con
  menos de 2 cm y 5 ms de error en tiros a 0°, 45° y 75°, y con gravedad lunar.
- **4.2 README** con uso, física, estructura y prácticas propuestas.

## Decisiones y notas

- El `ROADMAP.md` original no estaba en el repositorio; el plan se redactó desde cero.
- Los mensajes del chat y los comandos están en español de forma fija (no dependen del idioma
  del cliente); los nombres del bloque y de los objetos sí están traducidos.
- El proyectil no daña entidades ni rompe bloques: es un instrumento de medida.
- Fallos encontrados por las pruebas dentro del juego y corregidos: el azimut inicial de un
  cañón orientado al este salía 270° en lugar de −90°.
- Caso límite conocido: si el proyectil llega a la cara de un bloque justo al acabar un tick,
  Minecraft sitúa el choque hasta 0,5 mm dentro del bloque.

# ROADMAP — Tiro parabólico en Minecraft

Proyecto de Física I: un mod de Minecraft Java que convierte el juego en un laboratorio de
tiro parabólico. Un cañón dispara un proyectil que sigue las ecuaciones de la cinemática,
y el mod compara lo que se mide en el mundo con lo que predice la teoría.

## Reglas del proyecto

- **Versión fija:** Minecraft 1.21.1, Fabric, JDK 21. No se cambia de versión.
- **La física va aparte:** paquete `io.github.diegonav05.tiroparabolico.fisica`, sin ninguna
  clase de Minecraft ni de Fabric, y con tests JUnit.
- **APIs reales:** solo se usan APIs comprobadas en la documentación oficial de Fabric para
  1.21.1 y en el propio código mapeado (Yarn `1.21.1+build.3`).
- **Una fase cada vez.** Al terminar cada hito: compilar, ejecutar los tests, hacer commit y
  actualizar `PROGRESS.md`.

## Convenios físicos

| Magnitud | Convenio |
| --- | --- |
| Longitud | 1 bloque = 1 m |
| Tiempo | 1 tick = 1/20 s = 0,05 s |
| Gravedad | 9,80665 m/s² por defecto (configurable) |
| Ejes | `y` vertical; `x`, `z` horizontales (los de Minecraft) |
| Integración | Runge-Kutta 4 con paso de un tick (exacto para el tiro ideal) |

## Fase 0 — Preparación

- [x] Hito 0.1: proyecto Gradle con Fabric Loom, Minecraft 1.21.1, Yarn, Fabric API y JUnit.

## Fase 1 — Núcleo de física (sin Minecraft)

- [x] Hito 1.1: cinemática analítica (`Lanzamiento`): posición, velocidad, tiempo de vuelo,
  alcance, altura máxima y ecuación de la trayectoria, con suelo a cualquier altura.
- [x] Hito 1.2: problema inverso (`Punteria`): ángulos para alcanzar un objetivo, rapidez
  mínima y ángulo de alcance máximo desde una altura.
- [x] Hito 1.3: dinámica numérica (`ModeloFuerzas`, `Integrador`, `Simulador`): Euler,
  Euler semiimplícito y RK4, con rozamiento lineal y cuadrático; solución analítica del
  rozamiento lineal; energía mecánica.
- [x] Hito 1.4: tests JUnit de todo lo anterior y test de arquitectura que impide que el
  paquete de física importe clases del juego.

## Fase 2 — Cañón y proyectil en el juego

- [ ] Hito 2.1: bloque «cañón» con su entidad de bloque (guarda rapidez, elevación, azimut,
  gravedad y rozamiento), objeto, modelo, texturas, receta y traducciones.
- [ ] Hito 2.2: entidad «proyectil» cuyo movimiento lo calcula el paquete de física tick a
  tick (no la gravedad de Minecraft), con detección de impacto contra bloques y estela.
- [ ] Hito 2.3: disparo con clic derecho y con señal de redstone.

## Fase 3 — Laboratorio

- [ ] Hito 3.1: comando `/canon` para configurar el cañón (rapidez, ángulo, azimut,
  gravedad, rozamiento) y consultar las predicciones teóricas.
- [ ] Hito 3.2: informe de cada disparo: alcance, tiempo de vuelo y altura máxima medidos
  frente a los teóricos, con su error.
- [ ] Hito 3.3: vista previa de la trayectoria con partículas y puntería automática a un
  objetivo (tiro tenso o por elevación).

## Fase 4 — Verificación y entrega

- [ ] Hito 4.1: pruebas dentro del juego (Fabric GameTest, servidor sin interfaz) que
  disparan el cañón y comprueban que el impacto coincide con la teoría.
- [ ] Hito 4.2: README con instalación, uso, la física explicada y prácticas propuestas.

## Ideas para más adelante (fuera del alcance actual)

- Pantalla gráfica para configurar el cañón sin comandos.
- Modelo del cañón que gire e incline el tubo según el ángulo.
- Exportar los datos de cada disparo a CSV para hacer gráficas.
- Daño a entidades e impacto elástico (rebotes con coeficiente de restitución).

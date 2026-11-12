# Mod de físicas parabólicas en Minecraft Java

Proyecto de Física I: un mod para **Minecraft Java 1.21.1 (Fabric, Java 21)** que convierte el
juego en un laboratorio de tiro parabólico. Un cañón dispara un proyectil que obedece las
ecuaciones de la cinemática —no la física interna de Minecraft— y, al caer, el mod compara lo
medido en el mundo con lo que predice la teoría.

- Plan del proyecto: [`ROADMAP.md`](ROADMAP.md) · Estado: [`PROGRESS.md`](PROGRESS.md)

## Qué hace

- **Cañón de laboratorio**: un bloque que guarda las condiciones iniciales del tiro (rapidez,
  ángulo de elevación, azimut, gravedad y rozamiento).
- **Proyectil con física real**: en cada tick (0,05 s) el servidor integra la ecuación del
  movimiento con Runge-Kutta 4 y mueve el proyectil. Deja una estela que dibuja la parábola.
- **Informe de cada disparo** en el chat: tiempo de vuelo, alcance y altura máxima medidos,
  junto al valor teórico y el error relativo.
- **Vista previa** de la trayectoria con partículas antes de disparar.
- **Puntería automática**: dado un objetivo, calcula el ángulo (tiro tenso o por elevación).
- **Otros planetas y rozamiento**: gravedad de la Luna, Marte o Júpiter, y rozamiento con el
  aire proporcional a la velocidad para ver cómo se deforma la parábola.

## Convenios

| Magnitud | En el juego |
| --- | --- |
| 1 metro | 1 bloque |
| 1 segundo | 20 ticks |
| Gravedad por defecto | 9,80665 m/s² |
| Boca del cañón | centro del bloque, 1,5 m sobre su base |
| Azimut | el «yaw» de la pantalla F3: 0° sur, 90° oeste, ±180° norte, −90° este |

## Cómo se usa

1. Consigue el **Cañón de laboratorio** en el inventario creativo (pestañas de redstone o de
   bloques funcionales) o fabrícalo:

   ```text
   H H H      H = lingote de hierro
   H P D      P = pólvora
   H H H      D = dispensador
   ```

2. Colócalo: apunta hacia donde estés mirando.
3. **Clic derecho** → dispara. **Agachado + clic derecho con la mano vacía** → muestra la
   configuración y las predicciones. Una **señal de redstone** también lo dispara.
4. Ajusta el cañón con `/canon` (actúa sobre el cañón al que miras o, si no, el más cercano
   en 8 bloques):

| Comando | Efecto |
| --- | --- |
| `/canon` o `/canon info` | Configuración y predicción teórica |
| `/canon velocidad <m/s>` | Rapidez inicial (0,5 – 100) |
| `/canon angulo <grados>` | Ángulo de elevación (−90 – 90) |
| `/canon azimut <grados>` | Dirección horizontal (yaw de F3) |
| `/canon apuntar` | Copia la dirección de tu mirada |
| `/canon gravedad <m/s²>` | Gravedad (0,1 – 100) |
| `/canon gravedad tierra\|luna\|marte\|jupiter` | Gravedad de un astro |
| `/canon rozamiento <b>` | Rozamiento lineal `a = −g − b·v` (1/s); 0 = tiro ideal |
| `/canon objetivo <x> <y> <z> [tenso\|alto]` | Calcula el ángulo para acertar en ese punto |
| `/canon prever` | Dibuja la trayectoria prevista y anuncia el impacto |
| `/canon disparar` | Dispara |

Ejemplo de informe (v0 = 20 m/s, θ = 45°, suelo llano):

```text
━━ Impacto en (42.74, 64.00, 0.50) ━━
Tiempo de vuelo: 2.986 s  (teoría 2.986 s, error 0.00 %)
Alcance horizontal: 42.24 m  (teoría 42.24 m, error 0.00 %)
Altura máxima sobre la boca: 10.20 m  (teoría 10.20 m, error 0.00 %)
Rapidez de impacto: 20.72 m/s
```

## La física

Tiro ideal (solo actúa el peso), con `θ` el ángulo de elevación:

```text
x(t) = v0·cos θ · t                 vx = v0·cos θ          (constante)
y(t) = y0 + v0·sen θ · t − ½·g·t²   vy = v0·sen θ − g·t

Tiempo de vuelo (mismo nivel):  T = 2·v0·sen θ / g
Alcance (mismo nivel):          R = v0²·sen(2θ) / g
Altura máxima:                  H = (v0·sen θ)² / (2g)
Trayectoria:                    y = y0 + x·tan θ − g·x² / (2·v0²·cos² θ)
Ángulos para un objetivo (d, Δy):
                                tan θ = (v0² ± √(v0⁴ − g·(g·d² + 2·Δy·v0²))) / (g·d)
```

Como la boca está 1,5 m por encima del suelo del cañón, el alcance real es algo mayor que
`v0²·sen(2θ)/g` y el ángulo óptimo algo menor que 45°: el mod usa las fórmulas generales con
el suelo a cualquier altura, y el informe evalúa la teoría a la altura real del impacto.

Con rozamiento lineal (`a = −g·ĵ − b·v`) existe solución analítica y velocidad límite `g/b`;
el paquete de física también admite rozamiento cuadrático, que se resuelve numéricamente.

**Precisión.** RK4 es exacto cuando la aceleración es constante, así que en el tiro ideal el
proyectil está en cada tick exactamente sobre la parábola. El único error es el del último
tramo, que se trata como una recta: como mucho la flecha `g·dt²/8 ≈ 3 mm`.

## Estructura del código

```text
src/main/java/io/github/diegonav05/tiroparabolico/
  fisica/     Física pura: SIN clases de Minecraft. Cinemática, puntería, integradores, energía.
  bloque/     Bloque cañón y su entidad de bloque (puente Minecraft ↔ física).
  entidad/    Proyectil: avanza con el integrador del paquete de física.
  juego/      Colisiones con el terreno y textos del chat.
  comando/    Comando /canon.
  registro/   Registro de bloques, objetos y entidades.
src/client/   Renderizado del proyectil.
src/test/     Tests JUnit del paquete de física.
src/gametest/ Pruebas dentro del juego (Fabric GameTest).
```

## Compilar y probar

Requiere JDK 21. No hace falta instalar Gradle.

```bash
./gradlew build
```

Compila, ejecuta los tests JUnit de física y deja el mod en `build/libs/tiro-parabolico-1.0.0.jar`.

```bash
./gradlew runGametest
```

Arranca un servidor sin interfaz, coloca cañones reales, dispara y comprueba que el impacto
coincide con la teoría (2 cm, 5 ms), además de los comandos, la redstone y el guardado.

```bash
./gradlew runClient
```

Abre Minecraft con el mod para probarlo a mano.

## Instalar el mod

1. Instala [Fabric Loader](https://fabricmc.net/use/installer/) para Minecraft 1.21.1.
2. Copia en la carpeta `mods` el jar de **Fabric API** para 1.21.1 y
   `build/libs/tiro-parabolico-1.0.0.jar`.

## Prácticas propuestas

1. **Alcance frente al ángulo.** Con `v0` fija, dispara de 15° a 75° de 10° en 10°. Representa
   el alcance y comprueba que los ángulos complementarios dan (casi) el mismo. ¿Por qué no es
   exacto? (Pista: la boca está a 1,5 m del suelo.)
2. **Independencia de movimientos.** Tiro horizontal (`/canon angulo 0`) desde una torre con
   distintas `v0`: el tiempo de vuelo no cambia.
3. **Medir g.** En una «gravedad desconocida» fijada por un compañero, deduce `g` a partir del
   tiempo de vuelo y el alcance medidos.
4. **Dar en el blanco.** Calcula a mano el ángulo para un objetivo y compáralo con
   `/canon objetivo`. Prueba las dos soluciones.
5. **El aire existe.** Repite la práctica 1 con `/canon rozamiento 0.2`: el ángulo óptimo baja
   y la trayectoria deja de ser simétrica.

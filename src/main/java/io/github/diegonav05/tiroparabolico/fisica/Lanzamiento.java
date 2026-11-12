package io.github.diegonav05.tiroparabolico.fisica;

import java.util.OptionalDouble;

/**
 * Tiro parabólico ideal (sin rozamiento): solución analítica.
 *
 * <pre>
 *   r(t) = r0 + v0·t − ½·g·t²·ĵ
 *   v(t) = v0 − g·t·ĵ
 * </pre>
 *
 * @param posicionInicial  posición de salida r0 (m)
 * @param velocidadInicial velocidad de salida v0 (m/s)
 * @param gravedad         módulo de la aceleración de la gravedad g (m/s²), positivo
 */
public record Lanzamiento(Vector3 posicionInicial, Vector3 velocidadInicial, double gravedad) {
	public Lanzamiento {
		if (posicionInicial == null || velocidadInicial == null) {
			throw new IllegalArgumentException("La posición y la velocidad iniciales son obligatorias");
		}
		if (!posicionInicial.esFinito() || !velocidadInicial.esFinito()) {
			throw new IllegalArgumentException("La posición y la velocidad iniciales deben ser finitas");
		}
		if (!(gravedad > 0) || !Double.isFinite(gravedad)) {
			throw new IllegalArgumentException("La gravedad debe ser positiva y finita: " + gravedad);
		}
	}

	/**
	 * Crea un lanzamiento a partir del módulo de la velocidad y de dos ángulos.
	 *
	 * @param rapidez          módulo de la velocidad inicial (m/s), no negativo
	 * @param elevacionGrados  ángulo sobre la horizontal, de −90° a 90°
	 * @param azimutGrados     dirección horizontal: 0° apunta a +x y 90° a +z
	 */
	public static Lanzamiento desdeAngulos(Vector3 posicionInicial, double rapidez, double elevacionGrados,
			double azimutGrados, double gravedad) {
		if (!(rapidez >= 0) || !Double.isFinite(rapidez)) {
			throw new IllegalArgumentException("La rapidez debe ser no negativa y finita: " + rapidez);
		}
		if (!(elevacionGrados >= -90 && elevacionGrados <= 90)) {
			throw new IllegalArgumentException("La elevación debe estar entre -90° y 90°: " + elevacionGrados);
		}
		double theta = Math.toRadians(elevacionGrados);
		double phi = Math.toRadians(azimutGrados);
		// cos(90°) no es exactamente 0 en coma flotante: el tiro vertical se trata aparte.
		double horizontal = Math.abs(elevacionGrados) == 90 ? 0 : rapidez * Math.cos(theta);
		Vector3 v0 = new Vector3(horizontal * Math.cos(phi), rapidez * Math.sin(theta), horizontal * Math.sin(phi));
		return new Lanzamiento(posicionInicial, v0, gravedad);
	}

	/** Módulo de la velocidad inicial (m/s). */
	public double rapidezInicial() {
		return velocidadInicial.norma();
	}

	/** Componente horizontal de la velocidad (m/s); es constante durante todo el vuelo. */
	public double rapidezHorizontal() {
		return velocidadInicial.normaHorizontal();
	}

	/** Ángulo de salida sobre la horizontal, en grados. */
	public double elevacionGrados() {
		return Math.toDegrees(Math.atan2(velocidadInicial.y(), rapidezHorizontal()));
	}

	/** Posición en el instante {@code t}. */
	public Vector3 posicion(double t) {
		return new Vector3(
				posicionInicial.x() + velocidadInicial.x() * t,
				posicionInicial.y() + velocidadInicial.y() * t - 0.5 * gravedad * t * t,
				posicionInicial.z() + velocidadInicial.z() * t);
	}

	/** Velocidad en el instante {@code t}. */
	public Vector3 velocidad(double t) {
		return new Vector3(velocidadInicial.x(), velocidadInicial.y() - gravedad * t, velocidadInicial.z());
	}

	/** Estado completo (tiempo, posición y velocidad) en el instante {@code t}. */
	public EstadoCinematico estado(double t) {
		return new EstadoCinematico(t, posicion(t), velocidad(t));
	}

	/** Instante en que se alcanza la altura máxima: {@code vy/g} (0 si se dispara hacia abajo). */
	public double tiempoAlturaMaxima() {
		return Math.max(0, velocidadInicial.y() / gravedad);
	}

	/** Altura máxima absoluta: {@code y0 + vy²/(2g)}. */
	public double alturaMaxima() {
		double vy = velocidadInicial.y();
		if (vy <= 0) {
			return posicionInicial.y();
		}
		return posicionInicial.y() + vy * vy / (2 * gravedad);
	}

	/**
	 * Instantes (t ≥ 0) en los que el proyectil pasa por la altura {@code y}, en orden creciente.
	 * Puede haber cero, uno o dos: subiendo y bajando.
	 */
	public double[] tiemposEnAltura(double y) {
		double vy = velocidadInicial.y();
		double discriminante = vy * vy + 2 * gravedad * (posicionInicial.y() - y);
		if (discriminante < 0) {
			return new double[0];
		}
		double raiz = Math.sqrt(discriminante);
		double t1 = (vy - raiz) / gravedad;
		double t2 = (vy + raiz) / gravedad;
		if (t2 < 0) {
			return new double[0];
		}
		if (t1 < 0 || raiz == 0) {
			return new double[] {t2};
		}
		return new double[] {t1, t2};
	}

	/**
	 * Tiempo de vuelo hasta caer a la altura {@code ySuelo} (rama descendente).
	 * Vacío si el proyectil nunca llega a esa altura.
	 */
	public OptionalDouble tiempoDeVuelo(double ySuelo) {
		double[] tiempos = tiemposEnAltura(ySuelo);
		if (tiempos.length == 0) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(tiempos[tiempos.length - 1]);
	}

	/** Tiempo de vuelo hasta volver a la altura de salida: {@code 2·vy/g}. */
	public double tiempoDeVuelo() {
		return tiempoDeVuelo(posicionInicial.y()).orElse(0);
	}

	/** Alcance horizontal al caer a la altura {@code ySuelo}. Vacío si nunca llega. */
	public OptionalDouble alcance(double ySuelo) {
		OptionalDouble t = tiempoDeVuelo(ySuelo);
		return t.isPresent() ? OptionalDouble.of(rapidezHorizontal() * t.getAsDouble()) : OptionalDouble.empty();
	}

	/** Alcance horizontal al volver a la altura de salida: {@code v0²·sen(2θ)/g}. */
	public double alcance() {
		return rapidezHorizontal() * tiempoDeVuelo();
	}

	/**
	 * Ecuación de la trayectoria: altura cuando se ha recorrido la distancia horizontal {@code d}.
	 *
	 * <pre>y(d) = y0 + d·tan θ − g·d² / (2·v0²·cos² θ)</pre>
	 */
	public double alturaEnDistancia(double d) {
		double vh = rapidezHorizontal();
		if (vh == 0) {
			throw new IllegalStateException("Tiro vertical: la trayectoria no es función de la distancia");
		}
		double t = d / vh;
		return posicionInicial.y() + velocidadInicial.y() * t - 0.5 * gravedad * t * t;
	}
}

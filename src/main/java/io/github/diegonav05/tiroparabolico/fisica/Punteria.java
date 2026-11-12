package io.github.diegonav05.tiroparabolico.fisica;

/** Problema inverso del tiro parabólico ideal: con qué ángulo o velocidad se alcanza un objetivo. */
public final class Punteria {
	private Punteria() {
	}

	/**
	 * Ángulos de elevación (grados) con los que, disparando con rapidez {@code v}, se alcanza un
	 * objetivo situado a distancia horizontal {@code d} y a una altura {@code dy} sobre la boca.
	 *
	 * <pre>tan θ = (v² ± √(v⁴ − g·(g·d² + 2·dy·v²))) / (g·d)</pre>
	 *
	 * @return {@code {tiro tenso, tiro por elevación}} en orden creciente, o un array vacío si el
	 *         objetivo está fuera de alcance
	 */
	public static double[] angulosParaObjetivo(double v, double d, double dy, double g) {
		if (!(d > 0)) {
			throw new IllegalArgumentException("La distancia horizontal debe ser positiva: " + d);
		}
		if (!(v > 0) || !(g > 0)) {
			throw new IllegalArgumentException("La rapidez y la gravedad deben ser positivas");
		}
		double v2 = v * v;
		double discriminante = v2 * v2 - g * (g * d * d + 2 * dy * v2);
		if (discriminante < 0) {
			return new double[0];
		}
		double raiz = Math.sqrt(discriminante);
		double bajo = Math.toDegrees(Math.atan2(v2 - raiz, g * d));
		double alto = Math.toDegrees(Math.atan2(v2 + raiz, g * d));
		return new double[] {bajo, alto};
	}

	/**
	 * Rapidez mínima para alcanzar un objetivo a distancia horizontal {@code d} y altura {@code dy}:
	 * {@code v² = g·(dy + √(dy² + d²))}.
	 */
	public static double rapidezMinimaParaObjetivo(double d, double dy, double g) {
		return Math.sqrt(g * (dy + Math.hypot(d, dy)));
	}

	/**
	 * Ángulo (grados) que da el máximo alcance al disparar desde una altura {@code h} sobre el suelo.
	 * Con {@code h = 0} son los 45° clásicos; con {@code h > 0} es algo menor.
	 *
	 * <pre>tan θ = v / √(v² + 2·g·h)</pre>
	 */
	public static double anguloDeAlcanceMaximo(double v, double h, double g) {
		return Math.toDegrees(Math.atan2(v, Math.sqrt(v * v + 2 * g * h)));
	}

	/** Alcance máximo al disparar desde una altura {@code h}: {@code (v/g)·√(v² + 2·g·h)}. */
	public static double alcanceMaximo(double v, double h, double g) {
		return v / g * Math.sqrt(v * v + 2 * g * h);
	}
}

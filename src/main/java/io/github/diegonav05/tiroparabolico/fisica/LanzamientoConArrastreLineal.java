package io.github.diegonav05.tiroparabolico.fisica;

/**
 * Tiro con rozamiento proporcional a la velocidad ({@code a = −g·ĵ − b·v}): solución analítica.
 *
 * <pre>
 *   v(t) = v∞ + (v0 − v∞)·e^(−b·t)            con v∞ = −(g/b)·ĵ (velocidad límite)
 *   r(t) = r0 + v∞·t + (v0 − v∞)·(1 − e^(−b·t))/b
 * </pre>
 *
 * @param posicionInicial  r0 (m)
 * @param velocidadInicial v0 (m/s)
 * @param gravedad         g (m/s²), positiva
 * @param arrastre         b = k/m (1/s), positivo
 */
public record LanzamientoConArrastreLineal(Vector3 posicionInicial, Vector3 velocidadInicial, double gravedad,
		double arrastre) {
	public LanzamientoConArrastreLineal {
		if (!(gravedad > 0) || !Double.isFinite(gravedad)) {
			throw new IllegalArgumentException("La gravedad debe ser positiva y finita: " + gravedad);
		}
		if (!(arrastre > 0) || !Double.isFinite(arrastre)) {
			throw new IllegalArgumentException("El coeficiente de arrastre debe ser positivo y finito: " + arrastre);
		}
	}

	/** Velocidad límite de caída: {@code g/b} hacia abajo. */
	public Vector3 velocidadLimite() {
		return new Vector3(0, -gravedad / arrastre, 0);
	}

	public Vector3 velocidad(double t) {
		Vector3 vl = velocidadLimite();
		return vl.suma(velocidadInicial.resta(vl).escala(Math.exp(-arrastre * t)));
	}

	public Vector3 posicion(double t) {
		Vector3 vl = velocidadLimite();
		// −expm1(−b·t) = 1 − e^(−b·t), calculado sin pérdida de precisión cuando b·t es pequeño.
		double factor = -Math.expm1(-arrastre * t) / arrastre;
		return posicionInicial.suma(vl.escala(t)).suma(velocidadInicial.resta(vl).escala(factor));
	}
}

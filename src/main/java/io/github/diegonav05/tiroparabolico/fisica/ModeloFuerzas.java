package io.github.diegonav05.tiroparabolico.fisica;

/**
 * Fuerzas por unidad de masa que actúan sobre el proyectil: peso y rozamiento con el aire.
 *
 * <pre>a(v) = −g·ĵ − b·v − c·|v|·v</pre>
 *
 * @param gravedad           g (m/s²), positiva
 * @param arrastreLineal     b = k/m (1/s): rozamiento proporcional a la velocidad (régimen de Stokes)
 * @param arrastreCuadratico c = ½·ρ·Cd·A/m (1/m): rozamiento proporcional al cuadrado de la velocidad
 */
public record ModeloFuerzas(double gravedad, double arrastreLineal, double arrastreCuadratico) {
	public ModeloFuerzas {
		if (!(gravedad > 0) || !Double.isFinite(gravedad)) {
			throw new IllegalArgumentException("La gravedad debe ser positiva y finita: " + gravedad);
		}
		if (!(arrastreLineal >= 0) || !Double.isFinite(arrastreLineal)
				|| !(arrastreCuadratico >= 0) || !Double.isFinite(arrastreCuadratico)) {
			throw new IllegalArgumentException("Los coeficientes de arrastre deben ser no negativos y finitos");
		}
	}

	/** Tiro ideal: solo actúa la gravedad. */
	public static ModeloFuerzas sinArrastre(double gravedad) {
		return new ModeloFuerzas(gravedad, 0, 0);
	}

	public boolean tieneArrastre() {
		return arrastreLineal > 0 || arrastreCuadratico > 0;
	}

	/** Aceleración (m/s²) de un proyectil que se mueve con velocidad {@code v}. */
	public Vector3 aceleracion(Vector3 v) {
		double factor = arrastreLineal + arrastreCuadratico * v.norma();
		return new Vector3(-factor * v.x(), -gravedad - factor * v.y(), -factor * v.z());
	}
}

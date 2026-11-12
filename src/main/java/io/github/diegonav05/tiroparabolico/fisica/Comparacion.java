package io.github.diegonav05.tiroparabolico.fisica;

/** Comparación entre un valor medido y su valor teórico. */
public record Comparacion(double medido, double teorico) {
	public double errorAbsoluto() {
		return Math.abs(medido - teorico);
	}

	/** Error relativo en tanto por ciento; 0 si ambos valores son nulos. */
	public double errorRelativoPorcentaje() {
		if (teorico == 0) {
			return medido == 0 ? 0 : Double.POSITIVE_INFINITY;
		}
		return 100 * errorAbsoluto() / Math.abs(teorico);
	}
}

package io.github.diegonav05.tiroparabolico.fisica;

/** Estado de una partícula: instante, posición y velocidad. */
public record EstadoCinematico(double tiempo, Vector3 posicion, Vector3 velocidad) {
	/** Interpolación lineal entre este estado ({@code f = 0}) y {@code otro} ({@code f = 1}). */
	public EstadoCinematico interpola(EstadoCinematico otro, double f) {
		return new EstadoCinematico(
				tiempo + (otro.tiempo - tiempo) * f,
				posicion.interpola(otro.posicion, f),
				velocidad.interpola(otro.velocidad, f));
	}
}

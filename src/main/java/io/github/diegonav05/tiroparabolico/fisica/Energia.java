package io.github.diegonav05.tiroparabolico.fisica;

/** Energías de una partícula en un campo gravitatorio uniforme (julios, con la masa en kg). */
public final class Energia {
	private Energia() {
	}

	/** Energía cinética: {@code ½·m·v²}. */
	public static double cinetica(double masa, Vector3 velocidad) {
		return 0.5 * masa * velocidad.punto(velocidad);
	}

	/** Energía potencial gravitatoria respecto a {@code y = 0}: {@code m·g·y}. */
	public static double potencial(double masa, double gravedad, double altura) {
		return masa * gravedad * altura;
	}

	/** Energía mecánica: cinética más potencial. Se conserva si no hay rozamiento. */
	public static double mecanica(double masa, double gravedad, EstadoCinematico estado) {
		return cinetica(masa, estado.velocidad()) + potencial(masa, gravedad, estado.posicion().y());
	}
}

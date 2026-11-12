package io.github.diegonav05.tiroparabolico.fisica;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Simulación numérica paso a paso de un proyectil hasta que toca un suelo horizontal. */
public final class Simulador {
	private Simulador() {
	}

	/**
	 * Integra el movimiento hasta que el proyectil baja a la altura {@code ySuelo}
	 * o se alcanza {@code tiempoMaximo}.
	 *
	 * @param inicial      estado de salida
	 * @param modelo       fuerzas que actúan
	 * @param integrador   método numérico
	 * @param dt           paso de tiempo (s), positivo
	 * @param ySuelo       altura del suelo (m)
	 * @param tiempoMaximo duración máxima simulada (s)
	 */
	public static Trayectoria simular(EstadoCinematico inicial, ModeloFuerzas modelo, Integrador integrador,
			double dt, double ySuelo, double tiempoMaximo) {
		if (!(dt > 0) || !Double.isFinite(dt)) {
			throw new IllegalArgumentException("El paso de tiempo debe ser positivo: " + dt);
		}
		List<EstadoCinematico> muestras = new ArrayList<>();
		muestras.add(inicial);
		EstadoCinematico actual = inicial;
		long pasosMaximos = (long) Math.ceil(tiempoMaximo / dt);
		for (long i = 0; i < pasosMaximos; i++) {
			EstadoCinematico siguiente = integrador.paso(actual, modelo, dt);
			Optional<EstadoCinematico> impacto = cruceConSuelo(actual, siguiente, ySuelo);
			if (impacto.isPresent()) {
				muestras.add(impacto.get());
				return new Trayectoria(muestras, impacto);
			}
			muestras.add(siguiente);
			actual = siguiente;
		}
		return new Trayectoria(muestras, Optional.empty());
	}

	/** Simula un {@link Lanzamiento} desde su instante inicial. */
	public static Trayectoria simular(Lanzamiento lanzamiento, ModeloFuerzas modelo, Integrador integrador,
			double dt, double ySuelo, double tiempoMaximo) {
		return simular(lanzamiento.estado(0), modelo, integrador, dt, ySuelo, tiempoMaximo);
	}

	/**
	 * Si entre los estados {@code a} y {@code b} el proyectil atraviesa el suelo hacia abajo,
	 * devuelve el estado interpolado linealmente en el punto de cruce.
	 */
	public static Optional<EstadoCinematico> cruceConSuelo(EstadoCinematico a, EstadoCinematico b, double ySuelo) {
		double ya = a.posicion().y();
		double yb = b.posicion().y();
		if (yb > ySuelo || yb >= ya || ya < ySuelo) {
			return Optional.empty();
		}
		double f = (ya - ySuelo) / (ya - yb);
		return Optional.of(a.interpola(b, f));
	}
}

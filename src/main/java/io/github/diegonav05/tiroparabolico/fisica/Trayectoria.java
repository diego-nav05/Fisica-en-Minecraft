package io.github.diegonav05.tiroparabolico.fisica;

import java.util.List;
import java.util.Optional;

/**
 * Resultado de una simulación numérica.
 *
 * @param muestras estados sucesivos, separados un paso de tiempo; si hubo impacto, el último es el impacto
 * @param impacto  estado en el momento de tocar el suelo, o vacío si se agotó el tiempo máximo
 */
public record Trayectoria(List<EstadoCinematico> muestras, Optional<EstadoCinematico> impacto) {
	public Trayectoria {
		if (muestras.isEmpty()) {
			throw new IllegalArgumentException("Una trayectoria necesita al menos el estado inicial");
		}
		muestras = List.copyOf(muestras);
	}

	public EstadoCinematico inicio() {
		return muestras.get(0);
	}

	public EstadoCinematico fin() {
		return muestras.get(muestras.size() - 1);
	}

	/** Mayor altura registrada entre las muestras (m). */
	public double alturaMaxima() {
		double max = Double.NEGATIVE_INFINITY;
		for (EstadoCinematico e : muestras) {
			max = Math.max(max, e.posicion().y());
		}
		return max;
	}

	/** Distancia horizontal entre el punto de salida y el final de la trayectoria (m). */
	public double alcance() {
		return inicio().posicion().distanciaHorizontal(fin().posicion());
	}

	/** Tiempo transcurrido entre la salida y el final de la trayectoria (s). */
	public double tiempoDeVuelo() {
		return fin().tiempo() - inicio().tiempo();
	}
}

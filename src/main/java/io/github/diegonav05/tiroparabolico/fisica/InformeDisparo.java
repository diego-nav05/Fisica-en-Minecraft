package io.github.diegonav05.tiroparabolico.fisica;

/**
 * Informe de un disparo: lo que se ha medido frente a lo que predice el tiro parabólico ideal.
 * Las alturas son relativas al punto de salida.
 *
 * @param puntoDeImpacto      posición donde terminó el vuelo
 * @param rapidezDeImpacto    módulo de la velocidad al impactar (m/s)
 * @param tiempo              tiempo de vuelo medido y teórico (s)
 * @param alcance             distancia horizontal recorrida, medida y teórica (m)
 * @param alturaMaxima        altura máxima sobre la salida, medida y teórica (m)
 * @param teoriaDisponible    falso si la parábola ideal nunca pasa por la altura del impacto
 *                            (solo puede ocurrir cuando hay rozamiento)
 */
public record InformeDisparo(Vector3 puntoDeImpacto, double rapidezDeImpacto, Comparacion tiempo,
		Comparacion alcance, Comparacion alturaMaxima, boolean teoriaDisponible) {

	/**
	 * Compara un impacto medido con la parábola ideal del mismo lanzamiento.
	 *
	 * <p>La teoría se evalúa a la altura real del impacto. Como la parábola pasa dos veces por
	 * cada altura (subiendo y bajando), se toma el instante más cercano al medido: así también
	 * se interpreta bien un proyectil que choca con una pared mientras sube.
	 *
	 * @param ideal                lanzamiento ideal (sin rozamiento) con las condiciones de salida
	 * @param impacto              estado medido en el momento del impacto; su tiempo cuenta desde la salida
	 * @param alturaMaximaAbsoluta mayor altura (coordenada y) registrada durante el vuelo
	 */
	public static InformeDisparo comparar(Lanzamiento ideal, EstadoCinematico impacto, double alturaMaximaAbsoluta) {
		Vector3 salida = ideal.posicionInicial();
		double tiempoMedido = impacto.tiempo();
		double alcanceMedido = salida.distanciaHorizontal(impacto.posicion());
		double alturaMedida = alturaMaximaAbsoluta - salida.y();

		double[] tiempos = ideal.tiemposEnAltura(impacto.posicion().y());
		if (tiempos.length == 0) {
			return new InformeDisparo(impacto.posicion(), impacto.velocidad().norma(),
					new Comparacion(tiempoMedido, Double.NaN),
					new Comparacion(alcanceMedido, Double.NaN),
					new Comparacion(alturaMedida, ideal.alturaMaxima() - salida.y()),
					false);
		}
		double tiempoTeorico = tiempos[0];
		for (double t : tiempos) {
			if (Math.abs(t - tiempoMedido) < Math.abs(tiempoTeorico - tiempoMedido)) {
				tiempoTeorico = t;
			}
		}
		double alcanceTeorico = ideal.rapidezHorizontal() * tiempoTeorico;
		// Si el impacto llega antes del vértice, la mayor altura alcanzada es la del propio impacto.
		double alturaTeorica = tiempoTeorico >= ideal.tiempoAlturaMaxima()
				? ideal.alturaMaxima() - salida.y()
				: Math.max(0, ideal.posicion(tiempoTeorico).y() - salida.y());
		return new InformeDisparo(impacto.posicion(), impacto.velocidad().norma(),
				new Comparacion(tiempoMedido, tiempoTeorico),
				new Comparacion(alcanceMedido, alcanceTeorico),
				new Comparacion(alturaMedida, alturaTeorica),
				true);
	}
}

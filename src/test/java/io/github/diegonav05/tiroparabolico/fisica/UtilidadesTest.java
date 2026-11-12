package io.github.diegonav05.tiroparabolico.fisica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UtilidadesTest {
	@Test
	void operacionesConVectores() {
		Vector3 a = new Vector3(3, 4, 12);
		assertEquals(13, a.norma(), 1e-12);
		assertEquals(Math.hypot(3, 12), a.normaHorizontal(), 1e-12);
		assertEquals(new Vector3(4, 6, 15), a.suma(new Vector3(1, 2, 3)));
		assertEquals(new Vector3(2, 2, 9), a.resta(new Vector3(1, 2, 3)));
		assertEquals(new Vector3(6, 8, 24), a.escala(2));
		assertEquals(3 + 8 + 36, a.punto(new Vector3(1, 2, 3)), 1e-12);
		assertEquals(5, new Vector3(0, 100, 0).distanciaHorizontal(new Vector3(3, -7, 4)), 1e-12);
		assertEquals(new Vector3(1.5, 2, 6), Vector3.CERO.interpola(a, 0.5));
		assertTrue(a.esFinito());
		assertFalse(new Vector3(0, Double.POSITIVE_INFINITY, 0).esFinito());
	}

	@Test
	void energias() {
		assertEquals(0.5 * 2 * 25, Energia.cinetica(2, new Vector3(3, 0, 4)), 1e-12);
		assertEquals(2 * 9.8 * 10, Energia.potencial(2, 9.8, 10), 1e-12);
		EstadoCinematico e = new EstadoCinematico(0, new Vector3(0, 10, 0), new Vector3(3, 0, 4));
		assertEquals(25 + 196, Energia.mecanica(2, 9.8, e), 1e-12);
	}

	@Test
	void comparacionEntreMedidaYTeoria() {
		Comparacion c = new Comparacion(10.2, 10.0);
		assertEquals(0.2, c.errorAbsoluto(), 1e-12);
		assertEquals(2.0, c.errorRelativoPorcentaje(), 1e-9);
		assertEquals(0, new Comparacion(0, 0).errorRelativoPorcentaje(), 0);
		assertTrue(Double.isInfinite(new Comparacion(1, 0).errorRelativoPorcentaje()));
	}

	@Test
	void elModeloSinArrastreSoloTieneGravedad() {
		ModeloFuerzas ideal = ModeloFuerzas.sinArrastre(9.8);
		assertFalse(ideal.tieneArrastre());
		assertEquals(0, ideal.aceleracion(new Vector3(50, 20, -3)).resta(new Vector3(0, -9.8, 0)).norma(), 0);
		// Con arrastre, la fuerza de rozamiento se opone a la velocidad.
		ModeloFuerzas aire = new ModeloFuerzas(9.8, 0.5, 0.1);
		assertTrue(aire.tieneArrastre());
		Vector3 a = aire.aceleracion(new Vector3(3, 0, 4));
		assertEquals(-(0.5 + 0.1 * 5) * 3, a.x(), 1e-12);
		assertEquals(-9.8, a.y(), 1e-12);
		assertEquals(-(0.5 + 0.1 * 5) * 4, a.z(), 1e-12);
	}
}

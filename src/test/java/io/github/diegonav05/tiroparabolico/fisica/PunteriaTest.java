package io.github.diegonav05.tiroparabolico.fisica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PunteriaTest {
	private static final double G = Constantes.G_TIERRA;

	@ParameterizedTest
	@CsvSource({"30, 40, 0", "30, 40, 10", "30, 40, -15", "50, 200, 20", "12, 3, 5"})
	void losDosAngulosAciertanEnElObjetivo(double v, double d, double dy) {
		double[] angulos = Punteria.angulosParaObjetivo(v, d, dy, G);
		assertEquals(2, angulos.length);
		assertTrue(angulos[0] <= angulos[1]);
		for (double angulo : angulos) {
			Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, v, angulo, 0, G);
			assertEquals(dy, l.alturaEnDistancia(d), 1e-7, "ángulo " + angulo);
		}
	}

	@Test
	void alMismoNivelLosAngulosSonComplementarios() {
		double[] angulos = Punteria.angulosParaObjetivo(25, 30, 0, G);
		assertEquals(90, angulos[0] + angulos[1], 1e-9);
	}

	@Test
	void fueraDeAlcanceNoHaySolucion() {
		double alcanceMaximo = 20 * 20 / G;
		assertEquals(0, Punteria.angulosParaObjetivo(20, alcanceMaximo + 0.5, 0, G).length);
		assertEquals(2, Punteria.angulosParaObjetivo(20, alcanceMaximo - 0.5, 0, G).length);
	}

	@Test
	void conLaRapidezMinimaLasDosSolucionesCoinciden() {
		double v = Punteria.rapidezMinimaParaObjetivo(60, 12, G);
		assertEquals(0, Punteria.angulosParaObjetivo(v * 0.999, 60, 12, G).length);
		double[] angulos = Punteria.angulosParaObjetivo(v * 1.0000001, 60, 12, G);
		assertEquals(2, angulos.length);
		assertEquals(angulos[0], angulos[1], 0.1);
		// Al mismo nivel: v² = g·d.
		assertEquals(Math.sqrt(G * 60), Punteria.rapidezMinimaParaObjetivo(60, 0, G), 1e-9);
	}

	@Test
	void anguloOptimoDesdeElSuelo() {
		assertEquals(45, Punteria.anguloDeAlcanceMaximo(20, 0, G), 1e-9);
		assertEquals(20 * 20 / G, Punteria.alcanceMaximo(20, 0, G), 1e-9);
	}

	@Test
	void anguloOptimoDesdeUnaAltura() {
		double v = 20;
		double h = 15;
		double optimo = Punteria.anguloDeAlcanceMaximo(v, h, G);
		assertTrue(optimo < 45);
		double alcanceOptimo = alcanceDesde(v, optimo, h);
		assertEquals(Punteria.alcanceMaximo(v, h, G), alcanceOptimo, 1e-9);
		for (double a = 0; a <= 90; a += 0.5) {
			assertTrue(alcanceDesde(v, a, h) <= alcanceOptimo + 1e-9, "ángulo " + a);
		}
	}

	@Test
	void rechazaDistanciasNoPositivas() {
		assertThrows(IllegalArgumentException.class, () -> Punteria.angulosParaObjetivo(10, 0, 0, G));
		assertThrows(IllegalArgumentException.class, () -> Punteria.angulosParaObjetivo(0, 5, 0, G));
	}

	private static double alcanceDesde(double v, double angulo, double h) {
		return Lanzamiento.desdeAngulos(new Vector3(0, h, 0), v, angulo, 0, G).alcance(0).orElseThrow();
	}
}

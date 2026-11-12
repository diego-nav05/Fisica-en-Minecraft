package io.github.diegonav05.tiroparabolico.fisica;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanzamientoTest {
	private static final double G = Constantes.G_TIERRA;
	private static final double EPS = 1e-9;

	private static Lanzamiento tiro(double v, double elevacion) {
		return Lanzamiento.desdeAngulos(Vector3.CERO, v, elevacion, 0, G);
	}

	@Test
	void descomponeLaVelocidadInicial() {
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 20, 30, 90, G);
		assertEquals(0, l.velocidadInicial().x(), EPS);
		assertEquals(10, l.velocidadInicial().y(), EPS);
		assertEquals(20 * Math.cos(Math.toRadians(30)), l.velocidadInicial().z(), EPS);
		assertEquals(20, l.rapidezInicial(), EPS);
		assertEquals(30, l.elevacionGrados(), EPS);
	}

	@Test
	void ejemploDeLibro() {
		// v0 = 20 m/s, θ = 30°, g = 10 m/s²: t = 2 s, R = 20·√3 m, H = 5 m.
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 20, 30, 0, 10);
		assertEquals(2.0, l.tiempoDeVuelo(), EPS);
		assertEquals(20 * Math.sqrt(3), l.alcance(), EPS);
		assertEquals(5.0, l.alturaMaxima(), EPS);
		assertEquals(1.0, l.tiempoAlturaMaxima(), EPS);
	}

	@ParameterizedTest
	@CsvSource({"10, 15", "25, 45", "40, 60", "7.5, 82", "100, 1"})
	void formulasClasicasAlMismoNivel(double v, double elevacion) {
		Lanzamiento l = tiro(v, elevacion);
		double theta = Math.toRadians(elevacion);
		assertEquals(2 * v * Math.sin(theta) / G, l.tiempoDeVuelo(), 1e-9);
		assertEquals(v * v * Math.sin(2 * theta) / G, l.alcance(), 1e-8);
		assertEquals(Math.pow(v * Math.sin(theta), 2) / (2 * G), l.alturaMaxima(), 1e-9);
	}

	@Test
	void angulosComplementariosDanElMismoAlcance() {
		assertEquals(tiro(30, 25).alcance(), tiro(30, 65).alcance(), 1e-9);
		assertEquals(tiro(30, 10).alcance(), tiro(30, 80).alcance(), 1e-9);
	}

	@Test
	void a45GradosElAlcanceEsMaximo() {
		double maximo = tiro(30, 45).alcance();
		for (double a = 1; a < 90; a += 1) {
			assertTrue(tiro(30, a).alcance() <= maximo + 1e-9, "ángulo " + a);
		}
		assertEquals(30 * 30 / G, maximo, 1e-9);
	}

	@Test
	void enElVerticeLaVelocidadVerticalEsNula() {
		Lanzamiento l = tiro(25, 50);
		double t = l.tiempoAlturaMaxima();
		assertEquals(0, l.velocidad(t).y(), EPS);
		assertEquals(l.alturaMaxima(), l.posicion(t).y(), EPS);
		assertEquals(l.rapidezHorizontal(), l.velocidad(t).norma(), EPS);
	}

	@Test
	void laVelocidadHorizontalEsConstante() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(1, 2, 3), 18, 37, 120, G);
		for (double t = 0; t < 4; t += 0.25) {
			assertEquals(l.velocidadInicial().x(), l.velocidad(t).x(), EPS);
			assertEquals(l.velocidadInicial().z(), l.velocidad(t).z(), EPS);
		}
	}

	@Test
	void tiroHorizontalDesdeUnaAltura() {
		// Desde h = 20 m con v = 15 m/s horizontal: t = √(2h/g), R = v·t.
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 20, 0), 15, 0, 0, G);
		double t = Math.sqrt(2 * 20 / G);
		assertEquals(t, l.tiempoDeVuelo(0).orElseThrow(), EPS);
		assertEquals(15 * t, l.alcance(0).orElseThrow(), EPS);
		assertEquals(20, l.alturaMaxima(), EPS);
		assertEquals(0, l.tiempoAlturaMaxima(), EPS);
	}

	@Test
	void caidaAUnSueloMasBajoQueLaSalida() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 1.5, 0), 12, 40, 0, G);
		double t = l.tiempoDeVuelo(0).orElseThrow();
		assertTrue(t > l.tiempoDeVuelo());
		assertEquals(0, l.posicion(t).y(), EPS);
		assertEquals(l.posicion(t).x(), l.alcance(0).orElseThrow(), EPS);
	}

	@Test
	void unaAlturaInalcanzableNoTieneTiempoDeVuelo() {
		Lanzamiento l = tiro(10, 45);
		assertTrue(l.tiempoDeVuelo(l.alturaMaxima() + 0.01).isEmpty());
		assertTrue(l.alcance(l.alturaMaxima() + 0.01).isEmpty());
		assertEquals(0, l.tiemposEnAltura(1000).length);
	}

	@Test
	void pasaDosVecesPorUnaAlturaIntermedia() {
		Lanzamiento l = tiro(20, 60);
		double[] tiempos = l.tiemposEnAltura(5);
		assertEquals(2, tiempos.length);
		assertTrue(tiempos[0] < l.tiempoAlturaMaxima() && l.tiempoAlturaMaxima() < tiempos[1]);
		assertEquals(5, l.posicion(tiempos[0]).y(), EPS);
		assertEquals(5, l.posicion(tiempos[1]).y(), EPS);
		// Simetría respecto al vértice.
		assertEquals(l.tiempoAlturaMaxima() - tiempos[0], tiempos[1] - l.tiempoAlturaMaxima(), EPS);
		assertArrayEquals(new double[] {l.tiempoAlturaMaxima()}, l.tiemposEnAltura(l.alturaMaxima()), 1e-6);
	}

	@Test
	void laRapidezDeImpactoAlMismoNivelEsLaDeSalida() {
		Lanzamiento l = tiro(22, 33);
		Vector3 v = l.velocidad(l.tiempoDeVuelo());
		assertEquals(22, v.norma(), EPS);
		assertEquals(-l.velocidadInicial().y(), v.y(), EPS);
	}

	@Test
	void ecuacionDeLaTrayectoria() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 2, 0), 20, 35, 0, G);
		double theta = Math.toRadians(35);
		for (double d = 0; d < 40; d += 5) {
			double esperado = 2 + d * Math.tan(theta) - G * d * d / (2 * 400 * Math.pow(Math.cos(theta), 2));
			assertEquals(esperado, l.alturaEnDistancia(d), 1e-9);
		}
		assertEquals(0, l.alturaEnDistancia(l.alcance(0).orElseThrow()), 1e-9);
		assertThrows(IllegalStateException.class, () -> tiro(10, 90).alturaEnDistancia(1));
	}

	@Test
	void tiroVerticalNoAvanza() {
		Lanzamiento l = tiro(10, 90);
		assertEquals(0, l.alcance(), 1e-9);
		assertEquals(100 / (2 * G), l.alturaMaxima(), EPS);
	}

	@Test
	void enLaLunaLlegaMasLejos() {
		Lanzamiento tierra = Lanzamiento.desdeAngulos(Vector3.CERO, 20, 45, 0, Constantes.G_TIERRA);
		Lanzamiento luna = Lanzamiento.desdeAngulos(Vector3.CERO, 20, 45, 0, Constantes.G_LUNA);
		assertEquals(Constantes.G_TIERRA / Constantes.G_LUNA, luna.alcance() / tierra.alcance(), 1e-9);
	}

	@Test
	void rechazaParametrosSinSentido() {
		assertThrows(IllegalArgumentException.class, () -> new Lanzamiento(Vector3.CERO, Vector3.CERO, 0));
		assertThrows(IllegalArgumentException.class, () -> new Lanzamiento(Vector3.CERO, Vector3.CERO, -9.8));
		assertThrows(IllegalArgumentException.class, () -> new Lanzamiento(Vector3.CERO, Vector3.CERO, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> Lanzamiento.desdeAngulos(Vector3.CERO, -1, 45, 0, G));
		assertThrows(IllegalArgumentException.class, () -> Lanzamiento.desdeAngulos(Vector3.CERO, 10, 91, 0, G));
		assertThrows(IllegalArgumentException.class,
				() -> new Lanzamiento(new Vector3(Double.NaN, 0, 0), Vector3.CERO, G));
	}
}

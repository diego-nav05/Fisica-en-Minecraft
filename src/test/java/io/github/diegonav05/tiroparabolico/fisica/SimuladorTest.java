package io.github.diegonav05.tiroparabolico.fisica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimuladorTest {
	private static final double G = Constantes.G_TIERRA;
	private static final double DT = Constantes.DT_TICK;

	@Test
	void rk4ReproduceLaParabolaExactaEnCadaTick() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(3, 70, -8), 35, 52, 200, G);
		ModeloFuerzas ideal = ModeloFuerzas.sinArrastre(G);
		EstadoCinematico e = l.estado(0);
		for (int tick = 1; tick <= 200; tick++) {
			e = Integrador.RK4.paso(e, ideal, DT);
			Vector3 esperado = l.posicion(tick * DT);
			assertEquals(esperado.x(), e.posicion().x(), 1e-9);
			assertEquals(esperado.y(), e.posicion().y(), 1e-9);
			assertEquals(esperado.z(), e.posicion().z(), 1e-9);
			assertEquals(l.velocidad(tick * DT).y(), e.velocidad().y(), 1e-9);
		}
	}

	@Test
	void laSimulacionCoincideConLaTeoriaALaPrecisionDeUnTick() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 1.5, 0), 20, 45, 0, G);
		Trayectoria t = Simulador.simular(l, ModeloFuerzas.sinArrastre(G), Integrador.RK4, DT, 0, 60);
		assertTrue(t.impacto().isPresent());
		// El último tramo se interpola con una recta: el error máximo es la flecha g·dt²/8 ≈ 3 mm.
		assertEquals(l.tiempoDeVuelo(0).orElseThrow(), t.tiempoDeVuelo(), 1e-3);
		assertEquals(l.alcance(0).orElseThrow(), t.alcance(), 5e-3);
		assertEquals(0, t.fin().posicion().y(), 1e-12);
		assertEquals(l.alturaMaxima(), t.alturaMaxima(), G * DT * DT / 8 + 1e-9);
	}

	@Test
	void eulerEsPeorQueRk4YMejoraAlReducirElPaso() {
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 20, 45, 0, G);
		ModeloFuerzas ideal = ModeloFuerzas.sinArrastre(G);
		double teorico = l.alcance();
		double errorEuler = Math.abs(Simulador.simular(l, ideal, Integrador.EULER, DT, 0, 60).alcance() - teorico);
		double errorEulerFino = Math.abs(
				Simulador.simular(l, ideal, Integrador.EULER, DT / 10, 0, 60).alcance() - teorico);
		double errorRk4 = Math.abs(Simulador.simular(l, ideal, Integrador.RK4, DT, 0, 60).alcance() - teorico);
		assertTrue(errorEuler > 10 * errorRk4, "Euler " + errorEuler + " frente a RK4 " + errorRk4);
		assertTrue(errorEulerFino < errorEuler / 5, "Euler es de orden 1: el error baja con el paso");
	}

	@Test
	void sinRozamientoSeConservaLaEnergiaMecanica() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 10, 0), 30, 60, 45, G);
		Trayectoria t = Simulador.simular(l, ModeloFuerzas.sinArrastre(G), Integrador.RK4, DT, 0, 60);
		double inicial = Energia.mecanica(2.0, G, t.inicio());
		for (EstadoCinematico e : t.muestras().subList(0, t.muestras().size() - 1)) {
			assertEquals(inicial, Energia.mecanica(2.0, G, e), 1e-8 * inicial);
		}
	}

	@Test
	void conRozamientoLaEnergiaMecanicaDisminuye() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 10, 0), 30, 60, 45, G);
		Trayectoria t = Simulador.simular(l, new ModeloFuerzas(G, 0.1, 0.01), Integrador.RK4, DT, 0, 60);
		double anterior = Energia.mecanica(1.0, G, t.inicio());
		for (EstadoCinematico e : t.muestras().subList(1, t.muestras().size() - 1)) {
			double actual = Energia.mecanica(1.0, G, e);
			assertTrue(actual < anterior, "la energía debe decrecer en t = " + e.tiempo());
			anterior = actual;
		}
	}

	@Test
	void rk4CoincideConLaSolucionAnaliticaDelArrastreLineal() {
		Vector3 r0 = new Vector3(0, 5, 0);
		Lanzamiento ideal = Lanzamiento.desdeAngulos(r0, 25, 40, 30, G);
		LanzamientoConArrastreLineal exacto = new LanzamientoConArrastreLineal(r0, ideal.velocidadInicial(), G, 0.3);
		ModeloFuerzas modelo = new ModeloFuerzas(G, 0.3, 0);
		EstadoCinematico e = ideal.estado(0);
		for (int tick = 1; tick <= 100; tick++) {
			e = Integrador.RK4.paso(e, modelo, DT);
			Vector3 p = exacto.posicion(tick * DT);
			Vector3 v = exacto.velocidad(tick * DT);
			assertEquals(0, p.resta(e.posicion()).norma(), 1e-7);
			assertEquals(0, v.resta(e.velocidad()).norma(), 1e-7);
		}
	}

	@Test
	void elRozamientoAcortaElAlcanceYBajaLaAltura() {
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 30, 45, 0, G);
		Trayectoria ideal = Simulador.simular(l, ModeloFuerzas.sinArrastre(G), Integrador.RK4, DT, 0, 60);
		Trayectoria lineal = Simulador.simular(l, new ModeloFuerzas(G, 0.2, 0), Integrador.RK4, DT, 0, 60);
		Trayectoria cuadratico = Simulador.simular(l, new ModeloFuerzas(G, 0, 0.01), Integrador.RK4, DT, 0, 60);
		assertTrue(lineal.alcance() < ideal.alcance());
		assertTrue(cuadratico.alcance() < ideal.alcance());
		assertTrue(lineal.alturaMaxima() < ideal.alturaMaxima());
		assertTrue(cuadratico.alturaMaxima() < ideal.alturaMaxima());
	}

	@Test
	void conArrastreLinealSeTiendeALaVelocidadLimite() {
		LanzamientoConArrastreLineal l = new LanzamientoConArrastreLineal(Vector3.CERO, new Vector3(10, 10, 0), G, 0.5);
		assertEquals(-G / 0.5, l.velocidadLimite().y(), 1e-12);
		assertEquals(0, l.velocidad(100).resta(l.velocidadLimite()).norma(), 1e-9);
		// El avance horizontal está acotado por v0x/b.
		assertEquals(10 / 0.5, l.posicion(100).x(), 1e-9);
	}

	@Test
	void sinImpactoSeDetieneEnElTiempoMaximo() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 1000, 0), 10, 0, 0, G);
		Trayectoria t = Simulador.simular(l, ModeloFuerzas.sinArrastre(G), Integrador.RK4, DT, 0, 1.0);
		assertTrue(t.impacto().isEmpty());
		assertEquals(1.0, t.tiempoDeVuelo(), 1e-9);
		assertEquals(21, t.muestras().size());
	}

	@Test
	void cruceConSueloInterpolaElPuntoDeImpacto() {
		EstadoCinematico a = new EstadoCinematico(1.0, new Vector3(0, 1, 0), new Vector3(4, -2, 0));
		EstadoCinematico b = new EstadoCinematico(2.0, new Vector3(4, -3, 0), new Vector3(4, -6, 0));
		EstadoCinematico impacto = Simulador.cruceConSuelo(a, b, 0).orElseThrow();
		assertEquals(1.25, impacto.tiempo(), 1e-12);
		assertEquals(1.0, impacto.posicion().x(), 1e-12);
		assertEquals(0.0, impacto.posicion().y(), 1e-12);
		assertEquals(-3.0, impacto.velocidad().y(), 1e-12);
		// Subiendo no hay impacto, aunque se cruce la altura del suelo.
		assertTrue(Simulador.cruceConSuelo(b, a, 0).isEmpty());
		// Todavía en el aire.
		assertTrue(Simulador.cruceConSuelo(a, a.interpola(b, 0.1), 0).isEmpty());
	}

	@Test
	void rechazaUnPasoDeTiempoNoPositivo() {
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 10, 45, 0, G);
		assertThrows(IllegalArgumentException.class,
				() -> Simulador.simular(l, ModeloFuerzas.sinArrastre(G), Integrador.RK4, 0, 0, 10));
		assertThrows(IllegalArgumentException.class, () -> new ModeloFuerzas(G, -1, 0));
		assertThrows(IllegalArgumentException.class, () -> new ModeloFuerzas(0, 0, 0));
	}
}

package io.github.diegonav05.tiroparabolico.fisica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InformeDisparoTest {
	private static final double G = Constantes.G_TIERRA;
	private static final double DT = Constantes.DT_TICK;

	@Test
	void unDisparoIdealCoincideConLaTeoria() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(10, 65.5, -4), 20, 45, 30, G);
		Trayectoria t = Simulador.simular(l, ModeloFuerzas.sinArrastre(G), Integrador.RK4, DT, 64, 60);
		InformeDisparo informe = InformeDisparo.comparar(l, t.impacto().orElseThrow(), t.alturaMaxima());
		assertTrue(informe.teoriaDisponible());
		assertEquals(l.tiempoDeVuelo(64).orElseThrow(), informe.tiempo().teorico(), 1e-9);
		assertEquals(l.alcance(64).orElseThrow(), informe.alcance().teorico(), 1e-9);
		assertEquals(l.alturaMaxima() - 65.5, informe.alturaMaxima().teorico(), 1e-9);
		assertTrue(informe.tiempo().errorRelativoPorcentaje() < 0.05);
		assertTrue(informe.alcance().errorRelativoPorcentaje() < 0.05);
		assertTrue(informe.alturaMaxima().errorRelativoPorcentaje() < 0.05);
		assertEquals(l.velocidad(informe.tiempo().teorico()).norma(), informe.rapidezDeImpacto(), 0.05);
	}

	@Test
	void unChoqueMientrasSubeSeComparaConLaRamaAscendente() {
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 20, 60, 0, G);
		// Pared a mitad de la subida.
		double tChoque = l.tiempoAlturaMaxima() / 2;
		InformeDisparo informe = InformeDisparo.comparar(l, l.estado(tChoque), l.posicion(tChoque).y());
		assertEquals(tChoque, informe.tiempo().teorico(), 1e-9);
		assertEquals(l.posicion(tChoque).x(), informe.alcance().teorico(), 1e-9);
		assertEquals(l.posicion(tChoque).y(), informe.alturaMaxima().teorico(), 1e-9);
		assertEquals(0, informe.alturaMaxima().errorAbsoluto(), 1e-9);
	}

	@Test
	void elRozamientoSeVeComoUnAlcanceMenorQueElTeorico() {
		Lanzamiento l = Lanzamiento.desdeAngulos(new Vector3(0, 1.5, 0), 30, 45, 0, G);
		Trayectoria t = Simulador.simular(l, new ModeloFuerzas(G, 0.2, 0), Integrador.RK4, DT, 0, 60);
		InformeDisparo informe = InformeDisparo.comparar(l, t.impacto().orElseThrow(), t.alturaMaxima());
		assertTrue(informe.alcance().medido() < informe.alcance().teorico());
		assertTrue(informe.alturaMaxima().medido() < informe.alturaMaxima().teorico());
		assertTrue(informe.alcance().errorRelativoPorcentaje() > 5);
	}

	@Test
	void siLaParabolaIdealNoLlegaALaAlturaDelImpactoNoHayTeoria() {
		Lanzamiento l = Lanzamiento.desdeAngulos(Vector3.CERO, 5, 45, 0, G);
		EstadoCinematico impacto = new EstadoCinematico(1, new Vector3(3, 50, 0), Vector3.CERO);
		InformeDisparo informe = InformeDisparo.comparar(l, impacto, 50);
		assertFalse(informe.teoriaDisponible());
		assertTrue(Double.isNaN(informe.alcance().teorico()));
		assertEquals(3, informe.alcance().medido(), 1e-12);
	}
}

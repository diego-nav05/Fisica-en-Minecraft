package io.github.diegonav05.tiroparabolico.fisica;

/** Métodos numéricos para avanzar un paso de tiempo la ecuación del movimiento. */
public enum Integrador {
	/** Euler explícito: orden 1. Sencillo pero acumula error (no conserva la energía). */
	EULER {
		@Override
		public EstadoCinematico paso(EstadoCinematico e, ModeloFuerzas modelo, double dt) {
			Vector3 a = modelo.aceleracion(e.velocidad());
			return new EstadoCinematico(e.tiempo() + dt,
					e.posicion().suma(e.velocidad().escala(dt)),
					e.velocidad().suma(a.escala(dt)));
		}
	},
	/** Euler semiimplícito: actualiza primero la velocidad y con ella la posición. Orden 1. */
	EULER_SEMIIMPLICITO {
		@Override
		public EstadoCinematico paso(EstadoCinematico e, ModeloFuerzas modelo, double dt) {
			Vector3 v = e.velocidad().suma(modelo.aceleracion(e.velocidad()).escala(dt));
			return new EstadoCinematico(e.tiempo() + dt, e.posicion().suma(v.escala(dt)), v);
		}
	},
	/**
	 * Runge-Kutta de orden 4. Con aceleración constante (tiro ideal) reproduce
	 * exactamente la parábola; es el que usa el proyectil dentro del juego.
	 */
	RK4 {
		@Override
		public EstadoCinematico paso(EstadoCinematico e, ModeloFuerzas modelo, double dt) {
			Vector3 v1 = e.velocidad();
			Vector3 a1 = modelo.aceleracion(v1);
			Vector3 v2 = v1.suma(a1.escala(dt / 2));
			Vector3 a2 = modelo.aceleracion(v2);
			Vector3 v3 = v1.suma(a2.escala(dt / 2));
			Vector3 a3 = modelo.aceleracion(v3);
			Vector3 v4 = v1.suma(a3.escala(dt));
			Vector3 a4 = modelo.aceleracion(v4);

			Vector3 dx = v1.suma(v2.escala(2)).suma(v3.escala(2)).suma(v4).escala(dt / 6);
			Vector3 dv = a1.suma(a2.escala(2)).suma(a3.escala(2)).suma(a4).escala(dt / 6);
			return new EstadoCinematico(e.tiempo() + dt, e.posicion().suma(dx), e.velocidad().suma(dv));
		}
	};

	/** Devuelve el estado tras avanzar {@code dt} segundos. */
	public abstract EstadoCinematico paso(EstadoCinematico estado, ModeloFuerzas modelo, double dt);
}

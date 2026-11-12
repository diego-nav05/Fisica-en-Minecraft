package io.github.diegonav05.tiroparabolico.fisica;

/** Constantes físicas y de simulación. */
public final class Constantes {
	/** Gravedad estándar en la superficie terrestre (m/s²). */
	public static final double G_TIERRA = 9.80665;
	/** Gravedad en la superficie de la Luna (m/s²). */
	public static final double G_LUNA = 1.62;
	/** Gravedad en la superficie de Marte (m/s²). */
	public static final double G_MARTE = 3.71;
	/** Gravedad en la cima de las nubes de Júpiter (m/s²). */
	public static final double G_JUPITER = 24.79;

	/** Duración de un tick del juego (s): Minecraft avanza a 20 ticks por segundo. */
	public static final double DT_TICK = 1.0 / 20.0;

	private Constantes() {
	}
}

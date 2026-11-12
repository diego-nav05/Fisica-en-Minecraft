package io.github.diegonav05.tiroparabolico.fisica;

/** Vector inmutable de tres componentes; {@code y} es la vertical. */
public record Vector3(double x, double y, double z) {
	public static final Vector3 CERO = new Vector3(0, 0, 0);

	public Vector3 suma(Vector3 o) {
		return new Vector3(x + o.x, y + o.y, z + o.z);
	}

	public Vector3 resta(Vector3 o) {
		return new Vector3(x - o.x, y - o.y, z - o.z);
	}

	public Vector3 escala(double k) {
		return new Vector3(x * k, y * k, z * k);
	}

	public double punto(Vector3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	/** Módulo del vector. */
	public double norma() {
		return Math.sqrt(x * x + y * y + z * z);
	}

	/** Módulo de la proyección sobre el plano horizontal (x, z). */
	public double normaHorizontal() {
		return Math.hypot(x, z);
	}

	/** Distancia horizontal (ignorando la altura) hasta otro punto. */
	public double distanciaHorizontal(Vector3 o) {
		return Math.hypot(x - o.x, z - o.z);
	}

	/** Interpolación lineal: {@code f = 0} devuelve este vector y {@code f = 1} devuelve {@code o}. */
	public Vector3 interpola(Vector3 o, double f) {
		return new Vector3(x + (o.x - x) * f, y + (o.y - y) * f, z + (o.z - z) * f);
	}

	public boolean esFinito() {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
	}
}

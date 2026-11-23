package io.github.diegonav05.tiroparabolico.entidad;

/**
 * Lo que hace la bala al llegar: por defecto nada (es un instrumento de medida).
 *
 * @param dano              si mata a las criaturas que golpea en vuelo y a las que están donde cae
 * @param potenciaExplosion potencia de la explosión al impactar (0 = sin explosión; la dinamita es 4)
 * @param romperBloques     si la explosión rompe bloques
 */
public record EfectosImpacto(boolean dano, float potenciaExplosion, boolean romperBloques) {
	public static final EfectosImpacto NINGUNO = new EfectosImpacto(false, 0, true);

	public boolean explota() {
		return potenciaExplosion > 0;
	}
}

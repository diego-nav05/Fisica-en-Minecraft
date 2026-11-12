package io.github.diegonav05.tiroparabolico.juego;

import java.util.Locale;
import java.util.OptionalDouble;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import io.github.diegonav05.tiroparabolico.bloque.CanonBlockEntity;
import io.github.diegonav05.tiroparabolico.fisica.Comparacion;
import io.github.diegonav05.tiroparabolico.fisica.InformeDisparo;
import io.github.diegonav05.tiroparabolico.fisica.Lanzamiento;
import io.github.diegonav05.tiroparabolico.fisica.ModeloFuerzas;
import io.github.diegonav05.tiroparabolico.fisica.Vector3;

/** Textos que el mod escribe en el chat: ficha del cañón e informe de cada disparo. */
public final class Mensajes {
	private Mensajes() {
	}

	public static String num(double valor, int decimales) {
		if (Double.isNaN(valor)) {
			return "—";
		}
		return String.format(Locale.ROOT, "%." + decimales + "f", valor);
	}

	public static String punto(Vector3 p) {
		return "(" + num(p.x(), 2) + ", " + num(p.y(), 2) + ", " + num(p.z(), 2) + ")";
	}

	/** Nombre del punto cardinal hacia el que apunta un yaw de Minecraft. */
	public static String cardinal(double yaw) {
		return switch (Direction.fromRotation(yaw)) {
			case NORTH -> "norte";
			case SOUTH -> "sur";
			case WEST -> "oeste";
			case EAST -> "este";
			default -> "?";
		};
	}

	private static MutableText titulo(String texto) {
		return Text.literal("━━ " + texto + " ━━").formatted(Formatting.GOLD, Formatting.BOLD);
	}

	private static MutableText dato(String etiqueta, String valor) {
		return Text.literal(etiqueta + " ").formatted(Formatting.GRAY)
				.append(Text.literal(valor).formatted(Formatting.WHITE));
	}

	/** Configuración del cañón y predicciones del tiro ideal. */
	public static Text ficha(CanonBlockEntity canon) {
		BlockPos pos = canon.getPos();
		Lanzamiento l = canon.lanzamiento();
		double ySuelo = pos.getY();
		OptionalDouble tiempo = l.tiempoDeVuelo(ySuelo);
		OptionalDouble alcance = l.alcance(ySuelo);

		MutableText t = titulo("Cañón en " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
		t.append("\n").append(dato("Rapidez inicial v0 =", num(canon.getRapidez(), 2) + " m/s"));
		t.append("\n").append(dato("Ángulo de elevación θ =", num(canon.getElevacion(), 2) + "°"));
		t.append("\n").append(dato("Azimut =", num(canon.getAzimut(), 1) + "° (" + cardinal(canon.getAzimut()) + ")"));
		t.append("\n").append(dato("Gravedad g =", num(canon.getGravedad(), 3) + " m/s²"));
		t.append("\n").append(dato("Rozamiento b =",
				canon.getArrastre() == 0 ? "0 (tiro ideal)" : num(canon.getArrastre(), 3) + " 1/s"));
		t.append("\n").append(dato("Componentes: v0·cos θ =", num(l.rapidezHorizontal(), 2) + " m/s")
				.append(dato(",  v0·sen θ =", num(l.velocidadInicial().y(), 2) + " m/s")));
		t.append("\n").append(Text.literal("Predicción ideal (boca a " + num(CanonBlockEntity.ALTURA_BOCA, 1)
				+ " m sobre el suelo del cañón):").formatted(Formatting.AQUA));
		t.append("\n").append(dato("  Tiempo de vuelo:", num(tiempo.orElse(Double.NaN), 3) + " s"));
		t.append("\n").append(dato("  Alcance:", num(alcance.orElse(Double.NaN), 2) + " m"));
		t.append("\n").append(dato("  Altura máxima sobre la boca:",
				num(l.alturaMaxima() - l.posicionInicial().y(), 2) + " m"));
		if (canon.getArrastre() > 0) {
			t.append("\n").append(Text.literal("Con rozamiento el proyectil llegará menos lejos que esta predicción.")
					.formatted(Formatting.YELLOW));
		}
		return t;
	}

	public static void enviarFicha(ServerPlayerEntity jugador, CanonBlockEntity canon) {
		jugador.sendMessage(ficha(canon));
	}

	private static Text linea(String magnitud, Comparacion c, String unidad, int decimales, boolean hayTeoria) {
		MutableText t = dato(magnitud, num(c.medido(), decimales) + " " + unidad);
		if (hayTeoria) {
			double error = c.errorRelativoPorcentaje();
			Formatting color = error < 1 ? Formatting.GREEN : error < 5 ? Formatting.YELLOW : Formatting.RED;
			t.append(Text.literal("  (teoría " + num(c.teorico(), decimales) + " " + unidad + ", ")
					.formatted(Formatting.GRAY));
			t.append(Text.literal("error " + (Double.isInfinite(error) ? "—" : num(error, 2) + " %")).formatted(color));
			t.append(Text.literal(")").formatted(Formatting.GRAY));
		}
		return t;
	}

	/** Informe de un disparo: medidas frente a teoría. */
	public static Text informe(InformeDisparo informe, ModeloFuerzas modelo) {
		MutableText t = titulo("Impacto en " + punto(informe.puntoDeImpacto()));
		boolean teoria = informe.teoriaDisponible();
		t.append("\n").append(linea("Tiempo de vuelo:", informe.tiempo(), "s", 3, teoria));
		t.append("\n").append(linea("Alcance horizontal:", informe.alcance(), "m", 2, teoria));
		t.append("\n").append(linea("Altura máxima sobre la boca:", informe.alturaMaxima(), "m", 2, true));
		t.append("\n").append(dato("Rapidez de impacto:", num(informe.rapidezDeImpacto(), 2) + " m/s"));
		if (modelo.tieneArrastre()) {
			t.append("\n").append(Text.literal("La teoría es el tiro ideal: la diferencia es el efecto del rozamiento.")
					.formatted(Formatting.YELLOW));
		}
		return t;
	}

	public static void enviarInforme(ServerPlayerEntity jugador, InformeDisparo informe, ModeloFuerzas modelo) {
		jugador.sendMessage(informe(informe, modelo));
	}

	public static void enviarVueloInterrumpido(ServerPlayerEntity jugador, String motivo, double segundos) {
		jugador.sendMessage(Text.literal("El proyectil " + motivo + " tras " + num(segundos, 2)
				+ " s de vuelo: no hay impacto que medir.").formatted(Formatting.YELLOW));
	}
}

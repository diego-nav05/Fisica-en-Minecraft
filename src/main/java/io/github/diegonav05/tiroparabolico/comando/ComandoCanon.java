package io.github.diegonav05.tiroparabolico.comando;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import io.github.diegonav05.tiroparabolico.bloque.CanonBlockEntity;
import io.github.diegonav05.tiroparabolico.fisica.Constantes;
import io.github.diegonav05.tiroparabolico.fisica.EstadoCinematico;
import io.github.diegonav05.tiroparabolico.fisica.Punteria;
import io.github.diegonav05.tiroparabolico.fisica.Vector3;
import io.github.diegonav05.tiroparabolico.juego.Balistica;
import io.github.diegonav05.tiroparabolico.juego.Mensajes;

/**
 * Comando {@code /canon}: configura y maneja el cañón al que mira el jugador
 * (o, si no mira a ninguno, el más cercano).
 */
public final class ComandoCanon {
	private static final double DISTANCIA_MIRADA = 32;
	private static final int RADIO_BUSQUEDA = 8;
	private static final SimpleCommandExceptionType SIN_CANON = new SimpleCommandExceptionType(Text.literal(
			"No hay ningún cañón: mira hacia uno o acércate a menos de " + RADIO_BUSQUEDA + " bloques."));

	private ComandoCanon() {
	}

	private interface Accion {
		Text aplicar(CanonBlockEntity canon, ServerPlayerEntity jugador) throws CommandSyntaxException;
	}

	public static void registrar(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("canon")
				.executes(ctx -> ejecutar(ctx, (canon, jugador) -> Mensajes.ficha(canon)))
				.then(literal("info")
						.executes(ctx -> ejecutar(ctx, (canon, jugador) -> Mensajes.ficha(canon))))
				.then(literal("disparar")
						.executes(ctx -> ejecutar(ctx, (canon, jugador) -> {
							canon.disparar(jugador.getServerWorld(), jugador);
							return Text.literal("¡Fuego!");
						})))
				.then(literal("velocidad")
						.then(argument("metros_por_segundo",
								DoubleArgumentType.doubleArg(CanonBlockEntity.RAPIDEZ_MIN, CanonBlockEntity.RAPIDEZ_MAX))
								.executes(ctx -> ejecutar(ctx, (canon, jugador) -> {
									canon.setRapidez(DoubleArgumentType.getDouble(ctx, "metros_por_segundo"));
									return Text.literal("Rapidez inicial: " + Mensajes.num(canon.getRapidez(), 2) + " m/s");
								}))))
				.then(literal("angulo")
						.then(argument("grados",
								DoubleArgumentType.doubleArg(CanonBlockEntity.ELEVACION_MIN, CanonBlockEntity.ELEVACION_MAX))
								.executes(ctx -> ejecutar(ctx, (canon, jugador) -> {
									canon.setElevacion(DoubleArgumentType.getDouble(ctx, "grados"));
									return Text.literal("Ángulo de elevación: " + Mensajes.num(canon.getElevacion(), 2) + "°");
								}))))
				.then(literal("azimut")
						.then(argument("grados", DoubleArgumentType.doubleArg(-360, 360))
								.executes(ctx -> ejecutar(ctx, (canon, jugador) -> {
									canon.setAzimut(DoubleArgumentType.getDouble(ctx, "grados"));
									return textoAzimut(canon);
								}))))
				.then(literal("gravedad")
						.then(argument("metros_por_segundo2",
								DoubleArgumentType.doubleArg(CanonBlockEntity.GRAVEDAD_MIN, CanonBlockEntity.GRAVEDAD_MAX))
								.executes(ctx -> ejecutar(ctx, (canon, jugador) -> fijarGravedad(canon,
										DoubleArgumentType.getDouble(ctx, "metros_por_segundo2"), ""))))
						.then(literal("tierra").executes(ctx -> ejecutar(ctx,
								(canon, jugador) -> fijarGravedad(canon, Constantes.G_TIERRA, " (Tierra)"))))
						.then(literal("luna").executes(ctx -> ejecutar(ctx,
								(canon, jugador) -> fijarGravedad(canon, Constantes.G_LUNA, " (Luna)"))))
						.then(literal("marte").executes(ctx -> ejecutar(ctx,
								(canon, jugador) -> fijarGravedad(canon, Constantes.G_MARTE, " (Marte)"))))
						.then(literal("jupiter").executes(ctx -> ejecutar(ctx,
								(canon, jugador) -> fijarGravedad(canon, Constantes.G_JUPITER, " (Júpiter)")))))
				.then(literal("rozamiento")
						.then(argument("coeficiente", DoubleArgumentType.doubleArg(0, CanonBlockEntity.ARRASTRE_MAX))
								.executes(ctx -> ejecutar(ctx, (canon, jugador) -> {
									canon.setArrastre(DoubleArgumentType.getDouble(ctx, "coeficiente"));
									return Text.literal(canon.getArrastre() == 0
											? "Rozamiento desactivado: tiro ideal."
											: "Rozamiento lineal b = " + Mensajes.num(canon.getArrastre(), 3)
													+ " 1/s (a = −g − b·v)");
								}))))
				.then(literal("apuntar")
						.executes(ctx -> ejecutar(ctx, (canon, jugador) -> {
							canon.apuntarComo(jugador.getYaw(), jugador.getPitch());
							return Text.literal("Cañón orientado como tu mirada: elevación "
									+ Mensajes.num(canon.getElevacion(), 1) + "°, azimut "
									+ Mensajes.num(canon.getAzimut(), 1) + "°");
						})))
				.then(literal("objetivo")
						.then(argument("posicion", Vec3ArgumentType.vec3())
								.executes(ctx -> ejecutar(ctx, (canon, jugador) -> apuntarAObjetivo(ctx, canon, false)))
								.then(literal("tenso").executes(
										ctx -> ejecutar(ctx, (canon, jugador) -> apuntarAObjetivo(ctx, canon, false))))
								.then(literal("alto").executes(
										ctx -> ejecutar(ctx, (canon, jugador) -> apuntarAObjetivo(ctx, canon, true))))))
				.then(literal("prever")
						.executes(ctx -> ejecutar(ctx, ComandoCanon::prever))));
	}

	private static int ejecutar(CommandContext<ServerCommandSource> ctx, Accion accion) throws CommandSyntaxException {
		ServerPlayerEntity jugador = ctx.getSource().getPlayerOrThrow();
		CanonBlockEntity canon = buscarCanon(jugador);
		Text respuesta = accion.aplicar(canon, jugador);
		ctx.getSource().sendFeedback(() -> respuesta, false);
		return 1;
	}

	/** El cañón al que mira el jugador o, en su defecto, el más cercano. */
	private static CanonBlockEntity buscarCanon(ServerPlayerEntity jugador) throws CommandSyntaxException {
		ServerWorld mundo = jugador.getServerWorld();
		HitResult mirada = jugador.raycast(DISTANCIA_MIRADA, 1.0f, false);
		if (mirada instanceof BlockHitResult bloque && mirada.getType() == HitResult.Type.BLOCK
				&& mundo.getBlockEntity(bloque.getBlockPos()) instanceof CanonBlockEntity canon) {
			return canon;
		}
		for (BlockPos pos : BlockPos.iterateOutwards(jugador.getBlockPos(), RADIO_BUSQUEDA, RADIO_BUSQUEDA, RADIO_BUSQUEDA)) {
			if (mundo.getBlockEntity(pos) instanceof CanonBlockEntity canon) {
				return canon;
			}
		}
		throw SIN_CANON.create();
	}

	private static Text textoAzimut(CanonBlockEntity canon) {
		return Text.literal("Azimut: " + Mensajes.num(canon.getAzimut(), 1) + "° ("
				+ Mensajes.cardinal(canon.getAzimut()) + ")");
	}

	private static Text fijarGravedad(CanonBlockEntity canon, double g, String nombre) {
		canon.setGravedad(g);
		return Text.literal("Gravedad: " + Mensajes.num(canon.getGravedad(), 3) + " m/s²" + nombre);
	}

	private static Text apuntarAObjetivo(CommandContext<ServerCommandSource> ctx, CanonBlockEntity canon,
			boolean porElevacion) throws CommandSyntaxException {
		Vec3d objetivo = Vec3ArgumentType.getVec3(ctx, "posicion");
		Vec3d boca = canon.boca();
		double d = Math.hypot(objetivo.x - boca.x, objetivo.z - boca.z);
		double dy = objetivo.y - boca.y;
		if (d < 1e-6) {
			throw new SimpleCommandExceptionType(Text.literal(
					"El objetivo está en la vertical del cañón: usa /canon angulo 90.")).create();
		}
		Optional<Double> angulo = canon.apuntarA(objetivo, porElevacion);
		if (angulo.isEmpty()) {
			double minima = Punteria.rapidezMinimaParaObjetivo(d, dy, canon.getGravedad());
			throw new SimpleCommandExceptionType(Text.literal("Objetivo fuera de alcance con v0 = "
					+ Mensajes.num(canon.getRapidez(), 2) + " m/s. Distancia " + Mensajes.num(d, 2) + " m, desnivel "
					+ Mensajes.num(dy, 2) + " m: hace falta al menos " + Mensajes.num(minima, 2) + " m/s.")).create();
		}
		String texto = "Objetivo a " + Mensajes.num(d, 2) + " m (desnivel " + Mensajes.num(dy, 2) + " m): "
				+ (porElevacion ? "tiro por elevación" : "tiro tenso") + " con θ = " + Mensajes.num(angulo.get(), 2)
				+ "°, azimut " + Mensajes.num(canon.getAzimut(), 1) + "°.";
		if (canon.getArrastre() > 0) {
			texto += " Ojo: el cálculo ignora el rozamiento, así que el disparo se quedará corto.";
		}
		return Text.literal(texto);
	}

	/** Dibuja con partículas la trayectoria que seguiría el proyectil y anuncia dónde caería. */
	private static Text prever(CanonBlockEntity canon, ServerPlayerEntity jugador) {
		ServerWorld mundo = jugador.getServerWorld();
		Balistica.Trazado trazado = Balistica.trazar(mundo, canon.lanzamiento().estado(0), canon.modelo(),
				Balistica.TICKS_MAXIMOS);
		List<EstadoCinematico> muestras = trazado.muestras();
		int dibujadas = 0;
		for (int i = 1; i < muestras.size() && dibujadas < 4000; i++) {
			Vector3 a = muestras.get(i - 1).posicion();
			Vector3 b = muestras.get(i).posicion();
			// Puntos intermedios para que la curva se vea continua también en tiros rápidos.
			int partes = Math.max(1, (int) Math.ceil(b.resta(a).norma() / 0.5));
			for (int k = 1; k <= partes; k++) {
				Vector3 p = a.interpola(b, (double) k / partes);
				mundo.spawnParticles(jugador, ParticleTypes.HAPPY_VILLAGER, true, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0);
				dibujadas++;
			}
		}
		if (trazado.impacto().isEmpty()) {
			return Text.literal("Trayectoria dibujada: el proyectil sale de la zona cargada sin impactar.");
		}
		EstadoCinematico impacto = trazado.impacto().get();
		Vec3d boca = canon.boca();
		double alcance = Math.hypot(impacto.posicion().x() - boca.x, impacto.posicion().z() - boca.z);
		return Text.literal("Trayectoria prevista: impacto en " + Mensajes.punto(impacto.posicion()) + " tras "
				+ Mensajes.num(impacto.tiempo(), 3) + " s, alcance " + Mensajes.num(alcance, 2) + " m.");
	}
}

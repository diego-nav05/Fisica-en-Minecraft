package io.github.diegonav05.tiroparabolico.comando;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.BlockPosArgumentType;
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
import io.github.diegonav05.tiroparabolico.juego.RegistroCanones;

/**
 * Comando {@code /canon}. Sin más, actúa sobre un cañón: el que mira el jugador (hasta
 * {@value #DISTANCIA_MIRADA} bloques), si no el que tiene seleccionado (a cualquier distancia) y,
 * si no, el más cercano (hasta {@value #RADIO_CERCANO} bloques). Con {@code /canon todos …}
 * actúa sobre todos los cañones cargados del mundo.
 */
public final class ComandoCanon {
	public static final double DISTANCIA_MIRADA = 160;
	public static final double RADIO_CERCANO = 128;
	private static final SimpleCommandExceptionType SIN_CANON = new SimpleCommandExceptionType(Text.literal(
			"No hay ningún cañón: mira hacia uno, selecciónalo con /canon lista o acércate a menos de "
					+ (int) RADIO_CERCANO + " bloques."));
	private static final SimpleCommandExceptionType SIN_CANONES = new SimpleCommandExceptionType(
			Text.literal("No hay cañones en las zonas cargadas de este mundo."));
	private static final SimpleCommandExceptionType NO_ES_CANON = new SimpleCommandExceptionType(
			Text.literal("En esa posición no hay ningún cañón."));

	private ComandoCanon() {
	}

	private interface Accion {
		Text aplicar(CommandContext<ServerCommandSource> ctx, CanonBlockEntity canon, ServerPlayerEntity jugador)
				throws CommandSyntaxException;
	}

	public static void registrar(CommandDispatcher<ServerCommandSource> dispatcher) {
		LiteralArgumentBuilder<ServerCommandSource> raiz = literal("canon")
				.executes(ctx -> uno(ctx, (c, canon, jugador) -> Mensajes.ficha(canon)))
				.then(literal("info").executes(ctx -> uno(ctx, (c, canon, jugador) -> Mensajes.ficha(canon))))
				.then(literal("lista").executes(ComandoCanon::lista))
				.then(literal("seleccionar")
						.executes(ctx -> uno(ctx, ComandoCanon::seleccionar))
						.then(argument("posicion", BlockPosArgumentType.blockPos())
								.executes(ComandoCanon::seleccionarEn)))
				.then(literal("prever").executes(ctx -> uno(ctx, ComandoCanon::prever)));
		anadirAcciones(raiz, false);

		LiteralArgumentBuilder<ServerCommandSource> todos = literal("todos").executes(ComandoCanon::lista);
		anadirAcciones(todos, true);
		raiz.then(todos);

		dispatcher.register(raiz);
	}

	/** Subcomandos que valen para un cañón o, bajo {@code todos}, para todos a la vez. */
	private static void anadirAcciones(LiteralArgumentBuilder<ServerCommandSource> nodo, boolean todos) {
		nodo.then(literal("disparar").executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
			// Con «todos» cada cañón manda un resumen corto, para no llenar el chat.
			canon.disparar(jugador.getServerWorld(), todos ? null : jugador);
			return Text.literal("¡Fuego!");
		})));
		nodo.then(literal("velocidad").then(argument("metros_por_segundo",
				DoubleArgumentType.doubleArg(CanonBlockEntity.RAPIDEZ_MIN, CanonBlockEntity.RAPIDEZ_MAX))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setRapidez(DoubleArgumentType.getDouble(c, "metros_por_segundo"));
					return Text.literal("Rapidez inicial: " + Mensajes.num(canon.getRapidez(), 2) + " m/s");
				}))));
		nodo.then(literal("angulo").then(argument("grados",
				DoubleArgumentType.doubleArg(CanonBlockEntity.ELEVACION_MIN, CanonBlockEntity.ELEVACION_MAX))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setElevacion(DoubleArgumentType.getDouble(c, "grados"));
					return Text.literal("Ángulo de elevación: " + Mensajes.num(canon.getElevacion(), 2) + "°");
				}))));
		nodo.then(literal("azimut").then(argument("grados", DoubleArgumentType.doubleArg(-360, 360))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setAzimut(DoubleArgumentType.getDouble(c, "grados"));
					return Text.literal("Azimut: " + Mensajes.num(canon.getAzimut(), 1) + "° ("
							+ Mensajes.cardinal(canon.getAzimut()) + ")");
				}))));
		nodo.then(literal("gravedad")
				.then(argument("metros_por_segundo2",
						DoubleArgumentType.doubleArg(CanonBlockEntity.GRAVEDAD_MIN, CanonBlockEntity.GRAVEDAD_MAX))
						.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> fijarGravedad(canon,
								DoubleArgumentType.getDouble(c, "metros_por_segundo2"), ""))))
				.then(literal("tierra").executes(ctx -> ejecutar(ctx, todos,
						(c, canon, jugador) -> fijarGravedad(canon, Constantes.G_TIERRA, " (Tierra)"))))
				.then(literal("luna").executes(ctx -> ejecutar(ctx, todos,
						(c, canon, jugador) -> fijarGravedad(canon, Constantes.G_LUNA, " (Luna)"))))
				.then(literal("marte").executes(ctx -> ejecutar(ctx, todos,
						(c, canon, jugador) -> fijarGravedad(canon, Constantes.G_MARTE, " (Marte)"))))
				.then(literal("jupiter").executes(ctx -> ejecutar(ctx, todos,
						(c, canon, jugador) -> fijarGravedad(canon, Constantes.G_JUPITER, " (Júpiter)")))));
		nodo.then(literal("rozamiento").then(argument("coeficiente",
				DoubleArgumentType.doubleArg(0, CanonBlockEntity.ARRASTRE_MAX))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setArrastre(DoubleArgumentType.getDouble(c, "coeficiente"));
					return Text.literal(canon.getArrastre() == 0
							? "Rozamiento desactivado: tiro ideal."
							: "Rozamiento lineal b = " + Mensajes.num(canon.getArrastre(), 3) + " 1/s (a = −g − b·v)");
				}))));
		nodo.then(literal("cadencia").then(argument("segundos",
				DoubleArgumentType.doubleArg(0, CanonBlockEntity.CADENCIA_MAX))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setCadencia(DoubleArgumentType.getDouble(c, "segundos"));
					return Text.literal(canon.getCadencia() == 0
							? "Redstone: un disparo por cada pulso."
							: "Redstone: mientras tenga señal, dispara cada " + Mensajes.num(canon.getCadencia(), 2) + " s.");
				}))));
		nodo.then(literal("restos").then(argument("segundos",
				DoubleArgumentType.doubleArg(0, CanonBlockEntity.RESTOS_MAX))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setRestos(DoubleArgumentType.getDouble(c, "segundos"));
					return Text.literal(canon.getRestos() == 0
							? "La bala desaparece al impactar."
							: "La bala quedará " + Mensajes.num(canon.getRestos(), 1) + " s en el suelo tras el impacto.");
				}))));
		nodo.then(literal("redstone")
				.then(literal("todos").executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setRedstoneTodos(true);
					return Text.literal("La señal de redstone de este cañón disparará todos los cañones cargados.");
				})))
				.then(literal("solo").executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
					canon.setRedstoneTodos(false);
					return Text.literal("La señal de redstone de este cañón solo lo dispara a él.");
				}))));
		// «daño» y «dano» (por si el teclado no tiene ñ).
		for (String palabra : new String[] {"daño", "dano"}) {
			nodo.then(literal(palabra)
					.then(literal("si").executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
						canon.setDano(true);
						return Text.literal("Daño activado: la bala mata lo que golpea y lo que hay donde cae.");
					})))
					.then(literal("no").executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
						canon.setDano(false);
						return Text.literal("Daño desactivado: la bala atraviesa a las criaturas sin herirlas.");
					}))));
		}
		nodo.then(literal("explosion").then(argument("potencia",
				DoubleArgumentType.doubleArg(0, CanonBlockEntity.POTENCIA_MAX))
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> fijarExplosion(c, canon, true)))
				.then(literal("romper").executes(
						ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> fijarExplosion(c, canon, true))))
				.then(literal("sin_romper").executes(
						ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> fijarExplosion(c, canon, false))))));
		nodo.then(literal("apuntar").executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> {
			canon.apuntarComo(jugador.getYaw(), jugador.getPitch());
			return Text.literal("Cañón orientado como tu mirada: elevación " + Mensajes.num(canon.getElevacion(), 1)
					+ "°, azimut " + Mensajes.num(canon.getAzimut(), 1) + "°");
		})));
		nodo.then(literal("objetivo").then(argument("posicion", Vec3ArgumentType.vec3())
				.executes(ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> apuntarAObjetivo(c, canon, false)))
				.then(literal("tenso").executes(
						ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> apuntarAObjetivo(c, canon, false))))
				.then(literal("alto").executes(
						ctx -> ejecutar(ctx, todos, (c, canon, jugador) -> apuntarAObjetivo(c, canon, true))))));
	}

	private static int ejecutar(CommandContext<ServerCommandSource> ctx, boolean todos, Accion accion)
			throws CommandSyntaxException {
		return todos ? enTodos(ctx, accion) : uno(ctx, accion);
	}

	/** Aplica la acción al cañón que corresponda (mirado, seleccionado o más cercano). */
	private static int uno(CommandContext<ServerCommandSource> ctx, Accion accion) throws CommandSyntaxException {
		ServerPlayerEntity jugador = ctx.getSource().getPlayerOrThrow();
		CanonBlockEntity canon = buscarCanon(jugador);
		canon.setOperador(jugador.getUuid());
		Text respuesta = accion.aplicar(ctx, canon, jugador);
		ctx.getSource().sendFeedback(() -> respuesta, false);
		return 1;
	}

	/** Aplica la acción a todos los cañones cargados del mundo del jugador. */
	private static int enTodos(CommandContext<ServerCommandSource> ctx, Accion accion) throws CommandSyntaxException {
		ServerPlayerEntity jugador = ctx.getSource().getPlayerOrThrow();
		List<CanonBlockEntity> canones = RegistroCanones.enMundo(jugador.getServerWorld());
		if (canones.isEmpty()) {
			throw SIN_CANONES.create();
		}
		int hechos = 0;
		Text ultimo = null;
		String primerFallo = null;
		for (CanonBlockEntity canon : canones) {
			canon.setOperador(jugador.getUuid());
			try {
				ultimo = accion.aplicar(ctx, canon, jugador);
				hechos++;
			} catch (CommandSyntaxException e) {
				if (primerFallo == null) {
					primerFallo = "(" + Mensajes.coordenadas(canon.getPos()) + ") " + e.getRawMessage().getString();
				}
			}
		}
		if (hechos == 0) {
			throw new SimpleCommandExceptionType(Text.literal("Ningún cañón pudo hacerlo. " + primerFallo)).create();
		}
		String resumen = "Hecho en " + hechos + " de " + canones.size() + (canones.size() == 1 ? " cañón" : " cañones")
				+ ". " + ultimo.getString();
		if (primerFallo != null) {
			resumen += " Fallaron " + (canones.size() - hechos) + ", por ejemplo " + primerFallo;
		}
		String texto = resumen;
		ctx.getSource().sendFeedback(() -> Text.literal(texto), false);
		return hechos;
	}

	/** El cañón al que mira el jugador; si no, el seleccionado; si no, el más cercano. */
	private static CanonBlockEntity buscarCanon(ServerPlayerEntity jugador) throws CommandSyntaxException {
		ServerWorld mundo = jugador.getServerWorld();
		HitResult mirada = jugador.raycast(DISTANCIA_MIRADA, 1.0f, false);
		if (mirada instanceof BlockHitResult bloque && mirada.getType() == HitResult.Type.BLOCK
				&& mundo.getBlockEntity(bloque.getBlockPos()) instanceof CanonBlockEntity canon) {
			return canon;
		}
		Optional<CanonBlockEntity> seleccionado = RegistroCanones.seleccionado(jugador.getUuid(), mundo);
		if (seleccionado.isPresent()) {
			return seleccionado.get();
		}
		return RegistroCanones.masCercano(mundo, jugador.getPos(), RADIO_CERCANO).orElseThrow(SIN_CANON::create);
	}

	private static int lista(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity jugador = ctx.getSource().getPlayerOrThrow();
		Text texto = Mensajes.lista(RegistroCanones.enMundo(jugador.getServerWorld()), jugador);
		ctx.getSource().sendFeedback(() -> texto, false);
		return 1;
	}

	private static Text seleccionar(CommandContext<ServerCommandSource> ctx, CanonBlockEntity canon,
			ServerPlayerEntity jugador) {
		RegistroCanones.seleccionar(jugador.getUuid(), canon);
		return textoSeleccion(canon);
	}

	private static int seleccionarEn(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity jugador = ctx.getSource().getPlayerOrThrow();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, jugador.getServerWorld(), "posicion");
		if (!(jugador.getServerWorld().getBlockEntity(pos) instanceof CanonBlockEntity canon)) {
			throw NO_ES_CANON.create();
		}
		RegistroCanones.seleccionar(jugador.getUuid(), canon);
		canon.setOperador(jugador.getUuid());
		Text texto = textoSeleccion(canon);
		ctx.getSource().sendFeedback(() -> texto, false);
		return 1;
	}

	private static Text textoSeleccion(CanonBlockEntity canon) {
		return Text.literal("Cañón en " + Mensajes.coordenadas(canon.getPos())
				+ " seleccionado: /canon lo manejará desde cualquier distancia (salvo que mires otro).");
	}

	private static Text fijarExplosion(CommandContext<ServerCommandSource> ctx, CanonBlockEntity canon, boolean romper) {
		canon.setExplosion(DoubleArgumentType.getDouble(ctx, "potencia"), romper);
		if (canon.getPotenciaExplosion() <= 0) {
			return Text.literal("Explosión desactivada.");
		}
		return Text.literal("Al impactar habrá una explosión de potencia " + Mensajes.num(canon.getPotenciaExplosion(), 1)
				+ " (la dinamita es 4)" + (romper ? " que rompe bloques." : " sin romper bloques."));
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

	/** Dibuja con partículas (visibles de lejos) la trayectoria prevista y anuncia dónde caería. */
	private static Text prever(CommandContext<ServerCommandSource> ctx, CanonBlockEntity canon,
			ServerPlayerEntity jugador) {
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

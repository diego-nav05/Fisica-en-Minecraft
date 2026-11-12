package io.github.diegonav05.tiroparabolico.prueba;

import java.util.function.Consumer;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import io.github.diegonav05.tiroparabolico.TiroParabolico;
import io.github.diegonav05.tiroparabolico.bloque.CanonBlock;
import io.github.diegonav05.tiroparabolico.bloque.CanonBlockEntity;
import io.github.diegonav05.tiroparabolico.entidad.ProyectilEntity;
import io.github.diegonav05.tiroparabolico.fisica.Constantes;
import io.github.diegonav05.tiroparabolico.fisica.InformeDisparo;
import io.github.diegonav05.tiroparabolico.fisica.Lanzamiento;
import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/**
 * Pruebas dentro del juego: se coloca un cañón de verdad en un mundo de verdad, se dispara y se
 * comprueba que el proyectil cae donde dicen las ecuaciones del tiro parabólico.
 *
 * <p>Escenario (estructura vacía de 8×8×8): suelo de piedra en y = 1 y cañón en (1, 2, 3)
 * apuntando al este (+x). La boca queda 1,5 m por encima del suelo.
 */
public class CanonGameTest implements FabricGameTest {
	private static final BlockPos CANON = new BlockPos(1, 2, 3);
	/** Precisión exigida: 2 cm y 5 ms (la flecha de la parábola en un tick es de unos 3 mm). */
	private static final double TOLERANCIA_M = 0.02;
	private static final double TOLERANCIA_S = 0.005;

	private static CanonBlockEntity montarEscenario(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 1, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(CANON, ModContenido.CANON.getDefaultState().with(CanonBlock.FACING, Direction.EAST));
		CanonBlockEntity canon = ctx.getBlockEntity(CANON);
		if (canon == null) {
			ctx.throwGameTestException("El cañón no tiene entidad de bloque");
		}
		return canon;
	}

	private static void comprobar(TestContext ctx, String magnitud, double esperado, double obtenido, double tolerancia) {
		ctx.assertTrue(Math.abs(esperado - obtenido) <= tolerancia,
				magnitud + ": esperado " + esperado + ", obtenido " + obtenido);
	}

	/**
	 * Espera al impacto y entonces ejecuta las comprobaciones una sola vez. Si alguna falla, la
	 * prueba falla en el acto con su mensaje; si el proyectil no impacta, falla por tiempo.
	 */
	private static void alImpactar(TestContext ctx, CanonBlockEntity canon, Consumer<InformeDisparo> comprobaciones) {
		ctx.runAtEveryTick(() -> canon.ultimoInforme().ifPresent(informe -> {
			comprobaciones.accept(informe);
			ctx.complete();
		}));
	}

	/** Dispara y, cuando llega el informe del impacto, lo compara con la teoría calculada aparte. */
	private static void dispararYComprobar(TestContext ctx, CanonBlockEntity canon) {
		Lanzamiento ideal = canon.lanzamiento();
		// Altura absoluta de la cara superior del suelo de piedra.
		double ySuelo = ctx.getAbsolutePos(new BlockPos(0, 2, 0)).getY();
		double tiempo = ideal.tiempoDeVuelo(ySuelo).orElseThrow();
		double alcance = ideal.alcance(ySuelo).orElseThrow();
		double altura = ideal.alturaMaxima() - ideal.posicionInicial().y();

		canon.disparar(ctx.getWorld(), null);
		alImpactar(ctx, canon, informe -> {
			comprobar(ctx, "tiempo de vuelo (s)", tiempo, informe.tiempo().medido(), TOLERANCIA_S);
			comprobar(ctx, "alcance (m)", alcance, informe.alcance().medido(), TOLERANCIA_M);
			comprobar(ctx, "altura máxima (m)", altura, informe.alturaMaxima().medido(), TOLERANCIA_M);
			comprobar(ctx, "altura del impacto (m)", ySuelo, informe.puntoDeImpacto().y(), 1e-6);
			// El propio informe debe dar un error despreciable frente a su teoría.
			ctx.assertTrue(informe.alcance().errorRelativoPorcentaje() < 0.5, "error relativo del alcance");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void elCanonSeOrientaSegunElBloque(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		// Este = yaw −90°.
		comprobar(ctx, "azimut", -90, canon.getAzimut(), 1e-9);
		Lanzamiento l = canon.lanzamiento();
		ctx.assertTrue(l.velocidadInicial().x() > 0 && Math.abs(l.velocidadInicial().z()) < 1e-9,
				"El cañón que mira al este debe disparar hacia +x: " + l.velocidadInicial());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void tiroA45GradosCoincideConLaTeoria(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(6);
		canon.setElevacion(45);
		dispararYComprobar(ctx, canon);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void tiroHorizontalCoincideConLaTeoria(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(8);
		canon.setElevacion(0);
		dispararYComprobar(ctx, canon);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void tiroPorElevacionCoincideConLaTeoria(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(8);
		canon.setElevacion(75);
		dispararYComprobar(ctx, canon);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void enLaLunaElTiroLlegaMasLejos(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setGravedad(Constantes.G_LUNA);
		canon.setRapidez(2.5);
		canon.setElevacion(45);
		dispararYComprobar(ctx, canon);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void elRozamientoAcortaElAlcance(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(6);
		canon.setElevacion(45);
		canon.setArrastre(1.0);
		canon.disparar(ctx.getWorld(), null);
		alImpactar(ctx, canon, informe -> {
			ctx.assertTrue(informe.alcance().medido() < informe.alcance().teorico() - 0.3,
					"Con rozamiento el alcance debe quedar claramente por debajo del ideal: " + informe.alcance());
			ctx.assertTrue(informe.alturaMaxima().medido() < informe.alturaMaxima().teorico(),
					"Con rozamiento la altura máxima debe ser menor que la ideal");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void laPunteriaAutomaticaAciertaEnElObjetivo(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(9);
		// Objetivo: un punto de la cara superior del suelo, en diagonal respecto al cañón.
		Vec3d objetivo = ctx.getAbsolute(new Vec3d(6.5, 2.0, 6.5));
		ctx.assertTrue(canon.apuntarA(objetivo, true).isPresent(), "El objetivo debería estar a tiro");
		canon.disparar(ctx.getWorld(), null);
		alImpactar(ctx, canon, informe -> {
			comprobar(ctx, "x del impacto", objetivo.x, informe.puntoDeImpacto().x(), 0.05);
			comprobar(ctx, "y del impacto", objetivo.y, informe.puntoDeImpacto().y(), 0.05);
			comprobar(ctx, "z del impacto", objetivo.z, informe.puntoDeImpacto().z(), 0.05);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void unaParedDetieneElProyectil(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(10);
		canon.setElevacion(0);
		// Pared de piedra de dos bloques de alto, a 3,5 m de la boca (el proyectil llega algo caído).
		ctx.setBlockState(5, 2, 3, Blocks.STONE);
		ctx.setBlockState(5, 3, 3, Blocks.STONE);
		double xPared = ctx.getAbsolutePos(new BlockPos(5, 3, 3)).getX();
		canon.disparar(ctx.getWorld(), null);
		alImpactar(ctx, canon, informe -> {
			// Con estos números el proyectil llega a la cara de la pared justo al acabar un tick, y el
			// trazado de rayos de Minecraft sitúa entonces el choque hasta 0,5 mm dentro del bloque.
			comprobar(ctx, "x del impacto contra la pared", xPared, informe.puntoDeImpacto().x(), 2e-3);
			ctx.assertTrue(informe.alcance().medido() < 3.6, "El proyectil no debe atravesar la pared");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void laRedstoneDisparaElCanon(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(5);
		ctx.assertTrue(canon.disparos() == 0, "El cañón no debería haber disparado todavía");
		ctx.setBlockState(CANON.north(), Blocks.REDSTONE_BLOCK);
		ctx.runAtTick(2, () -> {
			ctx.assertTrue(canon.disparos() == 1, "La señal de redstone debe disparar una vez: " + canon.disparos());
			ctx.assertTrue(!ctx.getWorld().getEntitiesByType(ModContenido.PROYECTIL, e -> true).isEmpty(),
					"Debe haber un proyectil en vuelo");
		});
		alImpactar(ctx, canon, informe -> {
			ctx.assertTrue(canon.disparos() == 1, "Una señal mantenida no debe repetir el disparo");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void elProyectilSigueLaParabolaTickATick(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(6);
		canon.setElevacion(60);
		Lanzamiento ideal = canon.lanzamiento();
		ProyectilEntity proyectil = canon.disparar(ctx.getWorld(), null);
		long inicio = ctx.getTick();
		for (int n = 1; n <= 15; n++) {
			ctx.runAtTick(inicio + n, () -> {
				if (proyectil.isRemoved()) {
					return;
				}
				double t = proyectil.estado().orElseThrow().tiempo();
				var teorica = ideal.posicion(t);
				double desvio = Math.sqrt(proyectil.squaredDistanceTo(teorica.x(), teorica.y(), teorica.z()));
				ctx.assertTrue(desvio < 1e-6, "En t = " + t + " s el proyectil se desvía " + desvio + " m de la parábola");
			});
		}
		ctx.runAtTick(inicio + 16, ctx::complete);
	}

	/** Jugador simulado situado junto al cañón del escenario. */
	private static ServerPlayerEntity jugadorJuntoAlCanon(TestContext ctx) {
		ServerPlayerEntity jugador = ctx.createMockCreativeServerPlayerInWorld();
		Vec3d pos = ctx.getAbsolute(new Vec3d(1.5, 2.0, 1.5));
		jugador.refreshPositionAndAngles(pos.x, pos.y, pos.z, -90f, -30f);
		return jugador;
	}

	private static void retirar(TestContext ctx, ServerPlayerEntity jugador) {
		ctx.getWorld().getServer().getPlayerManager().remove(jugador);
	}

	private static void comando(TestContext ctx, ServerPlayerEntity jugador, String comando) {
		try {
			ctx.getWorld().getServer().getCommandManager().getDispatcher().execute(comando, jugador.getCommandSource());
		} catch (CommandSyntaxException e) {
			ctx.throwGameTestException("El comando /" + comando + " ha fallado: " + e.getMessage());
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void elComandoConfiguraYDisparaElCanon(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		ServerPlayerEntity jugador = jugadorJuntoAlCanon(ctx);
		try {
			comando(ctx, jugador, "canon");
			comando(ctx, jugador, "canon info");
			comando(ctx, jugador, "canon velocidad 7.5");
			comprobar(ctx, "rapidez", 7.5, canon.getRapidez(), 1e-12);
			comando(ctx, jugador, "canon angulo 30");
			comprobar(ctx, "elevación", 30, canon.getElevacion(), 1e-12);
			comando(ctx, jugador, "canon gravedad luna");
			comprobar(ctx, "gravedad", Constantes.G_LUNA, canon.getGravedad(), 1e-12);
			comando(ctx, jugador, "canon gravedad 9.8");
			comprobar(ctx, "gravedad", 9.8, canon.getGravedad(), 1e-12);
			comando(ctx, jugador, "canon rozamiento 0.25");
			comprobar(ctx, "rozamiento", 0.25, canon.getArrastre(), 1e-12);
			comando(ctx, jugador, "canon rozamiento 0");
			comando(ctx, jugador, "canon azimut 0");
			ctx.assertTrue(canon.getCachedState().get(CanonBlock.FACING) == Direction.SOUTH,
					"Con azimut 0° el bloque debe mirar al sur");
			comando(ctx, jugador, "canon apuntar");
			comprobar(ctx, "azimut tras apuntar", -90, canon.getAzimut(), 1e-4);
			comprobar(ctx, "elevación tras apuntar", 30, canon.getElevacion(), 1e-4);
			ctx.assertTrue(canon.getCachedState().get(CanonBlock.FACING) == Direction.EAST,
					"Tras apuntar al este el bloque debe mirar al este");

			Vec3d objetivo = ctx.getAbsolute(new Vec3d(6.5, 2.0, 3.5));
			comando(ctx, jugador, "canon objetivo " + objetivo.x + " " + objetivo.y + " " + objetivo.z + " alto");
			ctx.assertTrue(canon.getElevacion() > 45, "El tiro por elevación usa el ángulo alto: " + canon.getElevacion());
			comando(ctx, jugador, "canon objetivo " + objetivo.x + " " + objetivo.y + " " + objetivo.z);
			ctx.assertTrue(canon.getElevacion() < 45, "El tiro tenso usa el ángulo bajo: " + canon.getElevacion());
			comando(ctx, jugador, "canon prever");
			comando(ctx, jugador, "canon disparar");
			ctx.assertTrue(canon.disparos() == 1, "El comando debe disparar el cañón");

			// Un objetivo imposible debe rechazarse sin tocar la configuración.
			double elevacionAntes = canon.getElevacion();
			boolean rechazado = false;
			try {
				ctx.getWorld().getServer().getCommandManager().getDispatcher()
						.execute("canon objetivo ~500 ~ ~", jugador.getCommandSource());
			} catch (CommandSyntaxException e) {
				rechazado = true;
			}
			ctx.assertTrue(rechazado, "Un objetivo fuera de alcance debe dar error");
			comprobar(ctx, "elevación sin cambios", elevacionAntes, canon.getElevacion(), 0);
		} finally {
			retirar(ctx, jugador);
		}
		alImpactar(ctx, canon, informe -> {
			Vec3d objetivo = ctx.getAbsolute(new Vec3d(6.5, 2.0, 3.5));
			comprobar(ctx, "x del impacto", objetivo.x, informe.puntoDeImpacto().x(), 0.05);
			comprobar(ctx, "z del impacto", objetivo.z, informe.puntoDeImpacto().z(), 0.05);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void elClicDerechoDisparaYAgachadoSoloInforma(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(5);
		ServerPlayerEntity jugador = jugadorJuntoAlCanon(ctx);
		try {
			jugador.setSneaking(true);
			ctx.useBlock(CANON, jugador);
			ctx.assertTrue(canon.disparos() == 0, "Agachado no se dispara: solo se muestra la ficha");
			jugador.setSneaking(false);
			ctx.useBlock(CANON, jugador);
			ctx.assertTrue(canon.disparos() == 1, "El clic derecho debe disparar el cañón");
		} finally {
			retirar(ctx, jugador);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void laConfiguracionSeGuardaConElMundo(TestContext ctx) {
		CanonBlockEntity canon = montarEscenario(ctx);
		canon.setRapidez(33);
		canon.setElevacion(12.5);
		canon.setAzimut(100);
		canon.setGravedad(3.71);
		canon.setArrastre(0.4);
		var registros = ctx.getWorld().getRegistryManager();
		var nbt = canon.createNbt(registros);
		CanonBlockEntity copia = new CanonBlockEntity(canon.getPos(), canon.getCachedState());
		copia.read(nbt, registros);
		comprobar(ctx, "rapidez", 33, copia.getRapidez(), 0);
		comprobar(ctx, "elevación", 12.5, copia.getElevacion(), 0);
		comprobar(ctx, "azimut", 100, copia.getAzimut(), 0);
		comprobar(ctx, "gravedad", 3.71, copia.getGravedad(), 0);
		comprobar(ctx, "rozamiento", 0.4, copia.getArrastre(), 0);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void laRecetaYElBotinEstanCargados(TestContext ctx) {
		var servidor = ctx.getWorld().getServer();
		ctx.assertTrue(servidor.getRecipeManager().get(TiroParabolico.id("canon")).isPresent(),
				"Falta la receta del cañón");
		montarEscenario(ctx);
		var botin = Block.getDroppedStacks(ctx.getWorld().getBlockState(ctx.getAbsolutePos(CANON)), ctx.getWorld(),
				ctx.getAbsolutePos(CANON), ctx.getBlockEntity(CANON));
		ctx.assertTrue(botin.size() == 1 && botin.get(0).isOf(ModContenido.CANON_OBJETO),
				"Al romper el cañón debe soltarse a sí mismo: " + botin);
		ctx.complete();
	}
}

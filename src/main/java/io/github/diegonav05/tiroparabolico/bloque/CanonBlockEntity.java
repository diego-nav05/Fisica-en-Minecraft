package io.github.diegonav05.tiroparabolico.bloque;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import io.github.diegonav05.tiroparabolico.entidad.EfectosImpacto;
import io.github.diegonav05.tiroparabolico.entidad.ProyectilEntity;
import io.github.diegonav05.tiroparabolico.fisica.Constantes;
import io.github.diegonav05.tiroparabolico.fisica.InformeDisparo;
import io.github.diegonav05.tiroparabolico.fisica.Lanzamiento;
import io.github.diegonav05.tiroparabolico.fisica.ModeloFuerzas;
import io.github.diegonav05.tiroparabolico.fisica.Punteria;
import io.github.diegonav05.tiroparabolico.fisica.Vector3;
import io.github.diegonav05.tiroparabolico.juego.Balistica;
import io.github.diegonav05.tiroparabolico.juego.RegistroCanones;
import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/**
 * Datos del cañón: las condiciones iniciales del tiro y su comportamiento con redstone. Aquí se
 * traduce entre el mundo de Minecraft (bloques, ticks, yaw) y el paquete de física (metros,
 * segundos, ángulos).
 */
public class CanonBlockEntity extends BlockEntity {
	public static final double RAPIDEZ_MIN = 0.5;
	public static final double RAPIDEZ_MAX = 100;
	public static final double ELEVACION_MIN = -90;
	public static final double ELEVACION_MAX = 90;
	public static final double GRAVEDAD_MIN = 0.1;
	public static final double GRAVEDAD_MAX = 100;
	public static final double ARRASTRE_MAX = 10;
	/** Cadencia mínima del disparo automático (s); 0 significa «un disparo por pulso». */
	public static final double CADENCIA_MIN = 0.5;
	public static final double CADENCIA_MAX = 60;
	public static final double CADENCIA_POR_DEFECTO = 2;
	public static final double RESTOS_MAX = 120;
	/** Potencia máxima de la explosión al impactar (la dinamita es 4). */
	public static final float POTENCIA_MAX = 10;
	public static final double RESTOS_POR_DEFECTO = 10;
	/** Altura de la boca del cañón sobre la base del bloque (m). */
	public static final double ALTURA_BOCA = 1.5;

	private double rapidez = 20;
	private double elevacion = 45;
	/** Dirección horizontal como el «yaw» de Minecraft (F3): 0° sur, 90° oeste, ±180° norte, −90° este. */
	private double azimut;
	private double gravedad = Constantes.G_TIERRA;
	/** Coeficiente de rozamiento lineal b = k/m (1/s); 0 = tiro ideal. */
	private double arrastre = 0;
	/** Segundos entre disparos mientras recibe redstone; 0 = un solo disparo por pulso. */
	private double cadencia = CADENCIA_POR_DEFECTO;
	/** Segundos que la bala queda en el suelo tras el impacto. */
	private double restos = RESTOS_POR_DEFECTO;
	/** Si es verdadero, su señal de redstone dispara también todos los demás cañones cargados. */
	private boolean redstoneTodos;
	/** Si la bala mata a las criaturas que golpea o que están donde cae. */
	private boolean dano;
	/** Potencia de la explosión al impactar; 0 = sin explosión. */
	private float potenciaExplosion;
	/** Si esa explosión rompe bloques. */
	private boolean romperBloques = true;

	/** Último jugador que lo manejó: recibe el informe de los disparos por redstone. No se guarda. */
	@Nullable
	private UUID operador;
	@Nullable
	private InformeDisparo ultimoInforme;
	private int disparos;
	private int ticksConSenal;

	public CanonBlockEntity(BlockPos pos, BlockState state) {
		super(ModContenido.CANON_ENTIDAD, pos, state);
		this.azimut = MathHelper.wrapDegrees(state.get(CanonBlock.FACING).asRotation());
	}

	// --- Registro de cañones cargados (para manejarlos desde lejos) ---

	@Override
	public void setWorld(World world) {
		super.setWorld(world);
		if (!world.isClient) {
			RegistroCanones.registrar(this);
		}
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		RegistroCanones.quitar(this);
	}

	@Override
	public void cancelRemoval() {
		super.cancelRemoval();
		if (world != null && !world.isClient) {
			RegistroCanones.registrar(this);
		}
	}

	// --- Configuración ---

	public double getRapidez() {
		return rapidez;
	}

	public void setRapidez(double valor) {
		this.rapidez = MathHelper.clamp(valor, RAPIDEZ_MIN, RAPIDEZ_MAX);
		markDirty();
	}

	public double getElevacion() {
		return elevacion;
	}

	public void setElevacion(double grados) {
		this.elevacion = MathHelper.clamp(grados, ELEVACION_MIN, ELEVACION_MAX);
		markDirty();
	}

	public double getAzimut() {
		return azimut;
	}

	public void setAzimut(double yawGrados) {
		this.azimut = MathHelper.wrapDegrees(yawGrados);
		markDirty();
		// El bloque gira para mirar hacia el punto cardinal más cercano.
		if (world != null && !world.isClient) {
			BlockState estado = getCachedState();
			Direction cara = Direction.fromRotation(azimut);
			if (estado.get(CanonBlock.FACING) != cara) {
				world.setBlockState(pos, estado.with(CanonBlock.FACING, cara));
			}
		}
	}

	public double getGravedad() {
		return gravedad;
	}

	public void setGravedad(double valor) {
		this.gravedad = MathHelper.clamp(valor, GRAVEDAD_MIN, GRAVEDAD_MAX);
		markDirty();
	}

	public double getArrastre() {
		return arrastre;
	}

	public void setArrastre(double valor) {
		this.arrastre = MathHelper.clamp(valor, 0, ARRASTRE_MAX);
		markDirty();
	}

	public double getCadencia() {
		return cadencia;
	}

	/** Segundos entre disparos con redstone; 0 (o menos) deja un disparo por pulso. */
	public void setCadencia(double segundos) {
		this.cadencia = limitarCadencia(segundos);
		markDirty();
	}

	private static double limitarCadencia(double segundos) {
		return segundos <= 0 ? 0 : MathHelper.clamp(segundos, CADENCIA_MIN, CADENCIA_MAX);
	}

	public double getRestos() {
		return restos;
	}

	public void setRestos(double segundos) {
		this.restos = MathHelper.clamp(segundos, 0, RESTOS_MAX);
		markDirty();
	}

	public boolean isRedstoneTodos() {
		return redstoneTodos;
	}

	public void setRedstoneTodos(boolean valor) {
		this.redstoneTodos = valor;
		markDirty();
	}

	public boolean isDano() {
		return dano;
	}

	public void setDano(boolean valor) {
		this.dano = valor;
		markDirty();
	}

	public float getPotenciaExplosion() {
		return potenciaExplosion;
	}

	public boolean isRomperBloques() {
		return romperBloques;
	}

	/** Explosión al impactar: potencia 0 la desactiva. */
	public void setExplosion(double potencia, boolean romper) {
		this.potenciaExplosion = (float) MathHelper.clamp(potencia, 0, POTENCIA_MAX);
		this.romperBloques = romper;
		markDirty();
	}

	public EfectosImpacto efectos() {
		return new EfectosImpacto(dano, potenciaExplosion, romperBloques);
	}

	public void setOperador(@Nullable UUID jugador) {
		this.operador = jugador;
	}

	// --- Puente con la física ---

	/** Punto de salida del proyectil: el centro del bloque, {@value #ALTURA_BOCA} m sobre su base. */
	public Vec3d boca() {
		return new Vec3d(pos.getX() + 0.5, pos.getY() + ALTURA_BOCA, pos.getZ() + 0.5);
	}

	/**
	 * Tiro ideal con la configuración actual. El yaw de Minecraft ψ apunta a (−sen ψ, cos ψ) en
	 * el plano (x, z); el azimut matemático φ del paquete de física apunta a (cos φ, sen φ),
	 * así que φ = ψ + 90°.
	 */
	public Lanzamiento lanzamiento() {
		Vec3d boca = boca();
		return Lanzamiento.desdeAngulos(new Vector3(boca.x, boca.y, boca.z), rapidez, elevacion, azimut + 90, gravedad);
	}

	public ModeloFuerzas modelo() {
		return new ModeloFuerzas(gravedad, arrastre, 0);
	}

	/** Orienta el cañón (azimut y elevación) como una mirada con el yaw y el pitch dados. */
	public void apuntarComo(float yaw, float pitch) {
		setAzimut(yaw);
		// En Minecraft el pitch es positivo hacia abajo.
		setElevacion(-pitch);
	}

	/**
	 * Calcula el azimut y la elevación para acertar en un punto con la rapidez actual
	 * (tiro ideal, sin rozamiento).
	 *
	 * @param porElevacion verdadero para el ángulo alto, falso para el tiro tenso
	 * @return el ángulo elegido, o vacío si el objetivo está fuera de alcance (no se cambia nada)
	 */
	public Optional<Double> apuntarA(Vec3d objetivo, boolean porElevacion) {
		Vec3d boca = boca();
		double dx = objetivo.x - boca.x;
		double dz = objetivo.z - boca.z;
		double d = Math.hypot(dx, dz);
		if (d < 1e-6) {
			return Optional.empty();
		}
		double[] angulos = Punteria.angulosParaObjetivo(rapidez, d, objetivo.y - boca.y, gravedad);
		if (angulos.length == 0) {
			return Optional.empty();
		}
		double angulo = porElevacion ? angulos[1] : angulos[0];
		setAzimut(Math.toDegrees(Math.atan2(-dx, dz)));
		setElevacion(angulo);
		return Optional.of(angulo);
	}

	// --- Disparo ---

	/**
	 * Dispara un proyectil con la configuración actual.
	 *
	 * @param tirador jugador que dispara a mano y recibe el informe completo, o {@code null}
	 *                para un disparo automático (redstone), cuyo resumen va al operador
	 */
	public ProyectilEntity disparar(ServerWorld mundo, @Nullable ServerPlayerEntity tirador) {
		if (tirador != null) {
			operador = tirador.getUuid();
		}
		UUID receptor = tirador != null ? tirador.getUuid() : operador;
		ProyectilEntity proyectil = new ProyectilEntity(ModContenido.PROYECTIL, mundo);
		proyectil.configurar(lanzamiento(), modelo(), pos, receptor, tirador == null,
				(int) Math.round(restos / Constantes.DT_TICK), efectos());
		mundo.spawnEntity(proyectil);
		disparos++;

		Vec3d boca = boca();
		mundo.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.6f, 1.6f);
		Balistica.particulasLejanas(mundo, ParticleTypes.CLOUD, boca.x, boca.y, boca.z, 8, 0.15, 0.02);
		return proyectil;
	}

	/** Flanco de subida de la señal de redstone: dispara en el acto y reinicia la cadencia. */
	public void alActivarseRedstone(ServerWorld mundo) {
		ticksConSenal = 0;
		dispararPorRedstone(mundo);
	}

	/**
	 * Dispara por redstone. Si el cañón está en modo «redstone todos», dispara también los demás
	 * cañones cargados del mundo que no estén recibiendo su propia señal (para no duplicar).
	 */
	public void dispararPorRedstone(ServerWorld mundo) {
		disparar(mundo, null);
		if (redstoneTodos) {
			for (CanonBlockEntity otro : RegistroCanones.enMundo(mundo)) {
				if (otro != this && !otro.getCachedState().get(CanonBlock.POWERED)) {
					otro.disparar(mundo, null);
				}
			}
		}
	}

	/** Se ejecuta cada tick en el servidor: disparo automático mientras haya señal. */
	public static void tickServidor(World world, BlockPos pos, BlockState state, CanonBlockEntity canon) {
		if (!(world instanceof ServerWorld mundo)) {
			return;
		}
		if (!state.get(CanonBlock.POWERED) || canon.cadencia <= 0) {
			canon.ticksConSenal = 0;
			return;
		}
		canon.ticksConSenal++;
		int ticksCadencia = Math.max(1, (int) Math.round(canon.cadencia / Constantes.DT_TICK));
		if (canon.ticksConSenal >= ticksCadencia) {
			canon.ticksConSenal = 0;
			canon.dispararPorRedstone(mundo);
		}
	}

	public void registrarInforme(InformeDisparo informe) {
		this.ultimoInforme = informe;
	}

	/** Informe del último disparo que ha terminado, si lo hay (no se guarda con el mundo). */
	public Optional<InformeDisparo> ultimoInforme() {
		return Optional.ofNullable(ultimoInforme);
	}

	public int disparos() {
		return disparos;
	}

	// --- Guardado ---

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putDouble("Rapidez", rapidez);
		nbt.putDouble("Elevacion", elevacion);
		nbt.putDouble("Azimut", azimut);
		nbt.putDouble("Gravedad", gravedad);
		nbt.putDouble("Arrastre", arrastre);
		nbt.putDouble("Cadencia", cadencia);
		nbt.putDouble("Restos", restos);
		nbt.putBoolean("RedstoneTodos", redstoneTodos);
		nbt.putBoolean("Dano", dano);
		nbt.putFloat("PotenciaExplosion", potenciaExplosion);
		nbt.putBoolean("RomperBloques", romperBloques);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		if (nbt.contains("Rapidez", NbtElement.NUMBER_TYPE)) {
			rapidez = MathHelper.clamp(nbt.getDouble("Rapidez"), RAPIDEZ_MIN, RAPIDEZ_MAX);
		}
		if (nbt.contains("Elevacion", NbtElement.NUMBER_TYPE)) {
			elevacion = MathHelper.clamp(nbt.getDouble("Elevacion"), ELEVACION_MIN, ELEVACION_MAX);
		}
		if (nbt.contains("Azimut", NbtElement.NUMBER_TYPE)) {
			azimut = MathHelper.wrapDegrees(nbt.getDouble("Azimut"));
		}
		if (nbt.contains("Gravedad", NbtElement.NUMBER_TYPE)) {
			gravedad = MathHelper.clamp(nbt.getDouble("Gravedad"), GRAVEDAD_MIN, GRAVEDAD_MAX);
		}
		if (nbt.contains("Arrastre", NbtElement.NUMBER_TYPE)) {
			arrastre = MathHelper.clamp(nbt.getDouble("Arrastre"), 0, ARRASTRE_MAX);
		}
		if (nbt.contains("Cadencia", NbtElement.NUMBER_TYPE)) {
			cadencia = limitarCadencia(nbt.getDouble("Cadencia"));
		}
		if (nbt.contains("Restos", NbtElement.NUMBER_TYPE)) {
			restos = MathHelper.clamp(nbt.getDouble("Restos"), 0, RESTOS_MAX);
		}
		redstoneTodos = nbt.getBoolean("RedstoneTodos");
		dano = nbt.getBoolean("Dano");
		if (nbt.contains("PotenciaExplosion", NbtElement.NUMBER_TYPE)) {
			potenciaExplosion = MathHelper.clamp(nbt.getFloat("PotenciaExplosion"), 0, POTENCIA_MAX);
		}
		if (nbt.contains("RomperBloques")) {
			romperBloques = nbt.getBoolean("RomperBloques");
		}
	}
}

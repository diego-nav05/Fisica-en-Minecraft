package io.github.diegonav05.tiroparabolico.bloque;

import java.util.Optional;

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
import org.jetbrains.annotations.Nullable;

import io.github.diegonav05.tiroparabolico.entidad.ProyectilEntity;
import io.github.diegonav05.tiroparabolico.fisica.Constantes;
import io.github.diegonav05.tiroparabolico.fisica.InformeDisparo;
import io.github.diegonav05.tiroparabolico.fisica.Lanzamiento;
import io.github.diegonav05.tiroparabolico.fisica.ModeloFuerzas;
import io.github.diegonav05.tiroparabolico.fisica.Punteria;
import io.github.diegonav05.tiroparabolico.fisica.Vector3;
import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/**
 * Datos del cañón: las condiciones iniciales del tiro. Aquí se traduce entre el mundo de
 * Minecraft (bloques, ticks, yaw) y el paquete de física (metros, segundos, ángulos).
 */
public class CanonBlockEntity extends BlockEntity {
	public static final double RAPIDEZ_MIN = 0.5;
	public static final double RAPIDEZ_MAX = 100;
	public static final double ELEVACION_MIN = -90;
	public static final double ELEVACION_MAX = 90;
	public static final double GRAVEDAD_MIN = 0.1;
	public static final double GRAVEDAD_MAX = 100;
	public static final double ARRASTRE_MAX = 10;
	/** Altura de la boca del cañón sobre la base del bloque (m). */
	public static final double ALTURA_BOCA = 1.5;

	private double rapidez = 20;
	private double elevacion = 45;
	/** Dirección horizontal como el «yaw» de Minecraft (F3): 0° sur, 90° oeste, ±180° norte, −90° este. */
	private double azimut;
	private double gravedad = Constantes.G_TIERRA;
	/** Coeficiente de rozamiento lineal b = k/m (1/s); 0 = tiro ideal. */
	private double arrastre = 0;

	@Nullable
	private InformeDisparo ultimoInforme;
	private int disparos;

	public CanonBlockEntity(BlockPos pos, BlockState state) {
		super(ModContenido.CANON_ENTIDAD, pos, state);
		this.azimut = MathHelper.wrapDegrees(state.get(CanonBlock.FACING).asRotation());
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
	 * @param tirador jugador que recibirá el informe, o {@code null} (redstone)
	 */
	public ProyectilEntity disparar(ServerWorld mundo, @Nullable ServerPlayerEntity tirador) {
		ProyectilEntity proyectil = new ProyectilEntity(ModContenido.PROYECTIL, mundo);
		proyectil.configurar(lanzamiento(), modelo(), pos, tirador == null ? null : tirador.getUuid());
		mundo.spawnEntity(proyectil);
		disparos++;

		Vec3d boca = boca();
		mundo.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.6f, 1.6f);
		mundo.spawnParticles(ParticleTypes.CLOUD, boca.x, boca.y, boca.z, 8, 0.15, 0.15, 0.15, 0.02);
		return proyectil;
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
	}
}

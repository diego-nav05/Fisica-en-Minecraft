package io.github.diegonav05.tiroparabolico.entidad;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import io.github.diegonav05.tiroparabolico.bloque.CanonBlockEntity;
import io.github.diegonav05.tiroparabolico.fisica.Constantes;
import io.github.diegonav05.tiroparabolico.fisica.EstadoCinematico;
import io.github.diegonav05.tiroparabolico.fisica.InformeDisparo;
import io.github.diegonav05.tiroparabolico.fisica.Integrador;
import io.github.diegonav05.tiroparabolico.fisica.Lanzamiento;
import io.github.diegonav05.tiroparabolico.fisica.ModeloFuerzas;
import io.github.diegonav05.tiroparabolico.fisica.Vector3;
import io.github.diegonav05.tiroparabolico.juego.Balistica;
import io.github.diegonav05.tiroparabolico.juego.Mensajes;
import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/**
 * Proyectil del cañón. Su movimiento no usa la gravedad ni el rozamiento de Minecraft: el
 * servidor integra en cada tick la ecuación del movimiento con el paquete de física (RK4,
 * dt = 0,05 s) y coloca la entidad en la posición resultante.
 */
public class ProyectilEntity extends Entity implements FlyingItemEntity {
	@Nullable
	private EstadoCinematico estado;
	@Nullable
	private Lanzamiento ideal;
	@Nullable
	private ModeloFuerzas modelo;
	@Nullable
	private BlockPos canon;
	@Nullable
	private UUID tirador;
	private double alturaMaxima;
	private int ticksDeVuelo;

	public ProyectilEntity(EntityType<? extends ProyectilEntity> tipo, World mundo) {
		super(tipo, mundo);
		this.noClip = true;
		setNoGravity(true);
	}

	/** Fija las condiciones iniciales. Debe llamarse antes de añadir la entidad al mundo. */
	public void configurar(Lanzamiento lanzamiento, ModeloFuerzas modelo, @Nullable BlockPos canon,
			@Nullable UUID tirador) {
		this.ideal = lanzamiento;
		this.modelo = modelo;
		this.estado = lanzamiento.estado(0);
		this.canon = canon;
		this.tirador = tirador;
		this.alturaMaxima = lanzamiento.posicionInicial().y();
		colocarEn(estado);
	}

	/** Estado físico actual (solo en el servidor). */
	public Optional<EstadoCinematico> estado() {
		return Optional.ofNullable(estado);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld mundo)) {
			return;
		}
		if (estado == null || modelo == null || ideal == null) {
			// Entidad sin configurar (no debería existir): se elimina.
			discard();
			return;
		}

		EstadoCinematico siguiente = Integrador.RK4.paso(estado, modelo, Constantes.DT_TICK);
		ticksDeVuelo++;

		if (!Balistica.enZonaSimulable(mundo, siguiente.posicion())) {
			terminarSinImpacto(mundo, "salió de la zona cargada del mundo");
			return;
		}
		Optional<EstadoCinematico> impacto = Balistica.impactoEnTramo(mundo, estado, siguiente, this);
		if (impacto.isPresent()) {
			terminarConImpacto(mundo, impacto.get());
			return;
		}

		estado = siguiente;
		alturaMaxima = Math.max(alturaMaxima, estado.posicion().y());
		colocarEn(estado);
		Vector3 p = estado.posicion();
		mundo.spawnParticles(ParticleTypes.END_ROD, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0);

		if (ticksDeVuelo >= Balistica.TICKS_MAXIMOS) {
			terminarSinImpacto(mundo, "superó el tiempo máximo de vuelo");
		}
	}

	private void colocarEn(EstadoCinematico e) {
		Vector3 p = e.posicion();
		Vector3 v = e.velocidad();
		setPosition(p.x(), p.y(), p.z());
		// Minecraft expresa la velocidad en bloques por tick.
		setVelocity(v.x() * Constantes.DT_TICK, v.y() * Constantes.DT_TICK, v.z() * Constantes.DT_TICK);
	}

	private void terminarConImpacto(ServerWorld mundo, EstadoCinematico impacto) {
		Vector3 p = impacto.posicion();
		alturaMaxima = Math.max(alturaMaxima, p.y());
		InformeDisparo informe = InformeDisparo.comparar(ideal, impacto, alturaMaxima);

		if (canon != null && mundo.isChunkLoaded(canon) && mundo.getBlockEntity(canon) instanceof CanonBlockEntity c) {
			c.registrarInforme(informe);
		}
		ServerPlayerEntity jugador = jugadorTirador(mundo);
		if (jugador != null) {
			Mensajes.enviarInforme(jugador, informe, modelo);
		}

		mundo.spawnParticles(ParticleTypes.EXPLOSION, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0);
		mundo.spawnParticles(ParticleTypes.FLAME, p.x(), p.y(), p.z(), 12, 0.1, 0.1, 0.1, 0.03);
		mundo.playSound(null, BlockPos.ofFloored(new Vec3d(p.x(), p.y(), p.z())),
				SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
		discard();
	}

	private void terminarSinImpacto(ServerWorld mundo, String motivo) {
		ServerPlayerEntity jugador = jugadorTirador(mundo);
		if (jugador != null) {
			Mensajes.enviarVueloInterrumpido(jugador, motivo, ticksDeVuelo * Constantes.DT_TICK);
		}
		discard();
	}

	@Nullable
	private ServerPlayerEntity jugadorTirador(ServerWorld mundo) {
		return tirador == null ? null : mundo.getServer().getPlayerManager().getPlayer(tirador);
	}

	@Override
	public ItemStack getStack() {
		return new ItemStack(ModContenido.BALA);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	// El proyectil no se guarda con el mundo (el tipo de entidad desactiva el guardado).
	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}

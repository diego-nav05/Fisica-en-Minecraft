package io.github.diegonav05.tiroparabolico.entidad;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
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
	/** Jugador que recibe el informe (el tirador, o el operador del cañón si disparó la redstone). */
	@Nullable
	private UUID receptor;
	/** Informe resumido en la barra de acción en vez del informe completo en el chat. */
	private boolean informeCompacto;
	private double alturaMaxima;
	private int ticksDeVuelo;
	/** Ticks que la bala queda en el suelo tras el impacto antes de desaparecer. */
	private int ticksRestos;
	private boolean aterrizado;
	private int ticksEnSuelo;
	private EfectosImpacto efectos = EfectosImpacto.NINGUNO;

	/** Daño que asegura la muerte de cualquier criatura normal, incluso con armadura. */
	private static final float DANO_LETAL = 10000f;
	/** Margen (m) que se añade a la caja de cada criatura al buscar golpes en vuelo. */
	private static final float MARGEN_GOLPE = 0.3f;

	public ProyectilEntity(EntityType<? extends ProyectilEntity> tipo, World mundo) {
		super(tipo, mundo);
		this.noClip = true;
		setNoGravity(true);
	}

	/**
	 * Fija las condiciones iniciales. Debe llamarse antes de añadir la entidad al mundo.
	 *
	 * @param receptor        jugador que recibirá el informe, o {@code null}
	 * @param informeCompacto verdadero para un resumen de una línea (disparos automáticos)
	 * @param ticksRestos     ticks que la bala queda visible en el suelo tras el impacto
	 * @param efectos         daño y explosión al impactar
	 */
	public void configurar(Lanzamiento lanzamiento, ModeloFuerzas modelo, @Nullable BlockPos canon,
			@Nullable UUID receptor, boolean informeCompacto, int ticksRestos, EfectosImpacto efectos) {
		this.efectos = efectos;
		this.ideal = lanzamiento;
		this.modelo = modelo;
		this.estado = lanzamiento.estado(0);
		this.canon = canon;
		this.receptor = receptor;
		this.informeCompacto = informeCompacto;
		this.ticksRestos = Math.max(0, ticksRestos);
		this.alturaMaxima = lanzamiento.posicionInicial().y();
		colocarEn(estado);
	}

	/** Estado físico actual (solo en el servidor). */
	public Optional<EstadoCinematico> estado() {
		return Optional.ofNullable(estado);
	}

	/** Verdadero cuando ya ha impactado y solo queda como resto en el suelo. */
	public boolean aterrizado() {
		return aterrizado;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld mundo)) {
			return;
		}
		if (aterrizado) {
			reposar(mundo);
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
		if (efectos.dano()) {
			// Con daño activado, la bala choca también con las criaturas que encuentra en el tramo.
			EntityHitResult golpe = buscarCriatura(mundo, estado, siguiente);
			if (golpe != null) {
				// El resultado da la posición de la criatura; el punto de contacto es donde el tramo
				// entra en su caja (ampliada con el mismo margen que se usó para buscarla).
				Vec3d desde = Balistica.aVec3d(estado.posicion());
				Vec3d hasta = Balistica.aVec3d(siguiente.posicion());
				double longitud = desde.distanceTo(hasta);
				Vec3d contacto = golpe.getEntity().getBoundingBox().expand(MARGEN_GOLPE).raycast(desde, hasta)
						.orElse(desde);
				double fGolpe = longitud > 0 ? desde.distanceTo(contacto) / longitud : 0;
				double fBloque = impacto
						.map(i -> (i.tiempo() - estado.tiempo()) / (siguiente.tiempo() - estado.tiempo()))
						.orElse(Double.POSITIVE_INFINITY);
				if (fGolpe <= fBloque) {
					terminarConImpacto(mundo, estado.interpola(siguiente, Math.min(1, fGolpe)), golpe.getEntity());
					return;
				}
			}
		}
		if (impacto.isPresent()) {
			terminarConImpacto(mundo, impacto.get(), null);
			return;
		}

		estado = siguiente;
		alturaMaxima = Math.max(alturaMaxima, estado.posicion().y());
		colocarEn(estado);
		Vector3 p = estado.posicion();
		Balistica.particulasLejanas(mundo, ParticleTypes.END_ROD, p.x(), p.y(), p.z(), 1, 0, 0);

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

	/** Primera criatura viva que la bala atraviesa en el tramo entre dos estados, o {@code null}. */
	@Nullable
	private EntityHitResult buscarCriatura(ServerWorld mundo, EstadoCinematico a, EstadoCinematico b) {
		Vec3d desde = Balistica.aVec3d(a.posicion());
		Vec3d hasta = Balistica.aVec3d(b.posicion());
		Box zona = new Box(desde, hasta).expand(1.0);
		return ProjectileUtil.getEntityCollision(mundo, this, desde, hasta, zona,
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), MARGEN_GOLPE);
	}

	/**
	 * Mata a la criatura golpeada en vuelo y a todas las que estén donde cae la bala (dentro de un
	 * bloque alrededor del punto de impacto).
	 *
	 * @return cuántas criaturas ha matado
	 */
	private int aplicarDano(ServerWorld mundo, Vector3 p, @Nullable Entity golpeada) {
		ServerPlayerEntity atacante = jugadorReceptor(mundo);
		DamageSource fuente = mundo.getDamageSources().thrown(this, atacante);
		Box zona = new Box(p.x() - 1, p.y() - 1, p.z() - 1, p.x() + 1, p.y() + 1.5, p.z() + 1);
		var victimas = new java.util.ArrayList<LivingEntity>(
				mundo.getEntitiesByClass(LivingEntity.class, zona, e -> e.isAlive() && !e.isSpectator()));
		if (golpeada instanceof LivingEntity viva && !victimas.contains(viva)) {
			victimas.add(viva);
		}
		int muertas = 0;
		for (LivingEntity victima : victimas) {
			victima.damage(fuente, DANO_LETAL);
			if (!victima.isAlive()) {
				muertas++;
			}
		}
		return muertas;
	}

	private void terminarConImpacto(ServerWorld mundo, EstadoCinematico impacto, @Nullable Entity golpeada) {
		Vector3 p = impacto.posicion();
		alturaMaxima = Math.max(alturaMaxima, p.y());
		InformeDisparo informe = InformeDisparo.comparar(ideal, impacto, alturaMaxima);

		if (canon != null && mundo.isChunkLoaded(canon) && mundo.getBlockEntity(canon) instanceof CanonBlockEntity c) {
			c.registrarInforme(informe);
		}
		ServerPlayerEntity jugador = jugadorReceptor(mundo);
		if (jugador != null) {
			if (informeCompacto) {
				Mensajes.enviarInformeCompacto(jugador, informe);
			} else {
				Mensajes.enviarInforme(jugador, informe, modelo);
			}
		}

		Balistica.particulasLejanas(mundo, ParticleTypes.EXPLOSION, p.x(), p.y(), p.z(), 1, 0, 0);
		Balistica.particulasLejanas(mundo, ParticleTypes.FLAME, p.x(), p.y(), p.z(), 12, 0.1, 0.03);
		mundo.playSound(null, BlockPos.ofFloored(new Vec3d(p.x(), p.y(), p.z())),
				SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);

		if (efectos.dano()) {
			int muertas = aplicarDano(mundo, p, golpeada);
			if (muertas > 0 && jugador != null && !informeCompacto) {
				Mensajes.enviarBajas(jugador, muertas);
			}
		}
		if (efectos.explota()) {
			mundo.createExplosion(this, p.x(), p.y(), p.z(), efectos.potenciaExplosion(),
					efectos.romperBloques() ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
		}

		if (ticksRestos <= 0) {
			discard();
			return;
		}
		// La bala se queda en el punto de impacto, brillando para que se vea desde lejos.
		aterrizado = true;
		estado = impacto;
		setPosition(p.x(), p.y(), p.z());
		setVelocity(Vec3d.ZERO);
		setGlowing(true);
	}

	/** La bala reposa en el suelo y marca el impacto hasta que se acaba su tiempo. */
	private void reposar(ServerWorld mundo) {
		ticksEnSuelo++;
		if (ticksEnSuelo % 10 == 0) {
			Balistica.particulasLejanas(mundo, ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.3, getZ(), 2, 0.1, 0);
		}
		if (ticksEnSuelo >= ticksRestos) {
			discard();
		}
	}

	private void terminarSinImpacto(ServerWorld mundo, String motivo) {
		ServerPlayerEntity jugador = jugadorReceptor(mundo);
		if (jugador != null) {
			Mensajes.enviarVueloInterrumpido(jugador, motivo, ticksDeVuelo * Constantes.DT_TICK);
		}
		discard();
	}

	@Nullable
	private ServerPlayerEntity jugadorReceptor(ServerWorld mundo) {
		return receptor == null ? null : mundo.getServer().getPlayerManager().getPlayer(receptor);
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

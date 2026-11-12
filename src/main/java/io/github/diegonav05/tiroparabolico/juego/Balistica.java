package io.github.diegonav05.tiroparabolico.juego;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import io.github.diegonav05.tiroparabolico.fisica.Constantes;
import io.github.diegonav05.tiroparabolico.fisica.EstadoCinematico;
import io.github.diegonav05.tiroparabolico.fisica.Integrador;
import io.github.diegonav05.tiroparabolico.fisica.ModeloFuerzas;
import io.github.diegonav05.tiroparabolico.fisica.Vector3;

/** Une la física con el mundo de bloques: colisiones del proyectil con el terreno. */
public final class Balistica {
	/** Duración máxima de un vuelo (ticks): 2 minutos. */
	public static final int TICKS_MAXIMOS = 20 * 120;

	private Balistica() {
	}

	public static Vec3d aVec3d(Vector3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}

	/**
	 * Busca el primer bloque sólido en el tramo recto entre dos estados consecutivos.
	 *
	 * @return el estado interpolado en el punto de choque, o vacío si el tramo está libre
	 */
	public static Optional<EstadoCinematico> impactoEnTramo(World mundo, EstadoCinematico a, EstadoCinematico b,
			@Nullable Entity entidad) {
		Vec3d desde = aVec3d(a.posicion());
		Vec3d hasta = aVec3d(b.posicion());
		double longitud = desde.distanceTo(hasta);
		if (longitud < 1e-9) {
			return Optional.empty();
		}
		RaycastContext contexto = entidad != null
				? new RaycastContext(desde, hasta, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entidad)
				: new RaycastContext(desde, hasta, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
						net.minecraft.block.ShapeContext.absent());
		BlockHitResult choque = mundo.raycast(contexto);
		if (choque.getType() != HitResult.Type.BLOCK) {
			return Optional.empty();
		}
		double fraccion = Math.min(1, desde.distanceTo(choque.getPos()) / longitud);
		return Optional.of(a.interpola(b, fraccion));
	}

	/** Verdadero si el punto está en una zona del mundo cargada y dentro de sus límites verticales. */
	public static boolean enZonaSimulable(World mundo, Vector3 p) {
		if (p.y() < mundo.getBottomY() - 16) {
			return false;
		}
		return mundo.isChunkLoaded(BlockPos.ofFloored(p.x(), p.y(), p.z()));
	}

	/** Resultado de trazar una trayectoria completa contra el terreno. */
	public record Trazado(List<EstadoCinematico> muestras, Optional<EstadoCinematico> impacto) {
	}

	/**
	 * Calcula por adelantado la trayectoria en el mundo, tick a tick, hasta chocar con un bloque,
	 * salir de la zona cargada o agotar {@code ticksMaximos}.
	 */
	public static Trazado trazar(World mundo, EstadoCinematico inicial, ModeloFuerzas modelo, int ticksMaximos) {
		List<EstadoCinematico> muestras = new ArrayList<>();
		muestras.add(inicial);
		EstadoCinematico actual = inicial;
		for (int i = 0; i < ticksMaximos; i++) {
			EstadoCinematico siguiente = Integrador.RK4.paso(actual, modelo, Constantes.DT_TICK);
			if (!enZonaSimulable(mundo, siguiente.posicion())) {
				break;
			}
			Optional<EstadoCinematico> impacto = impactoEnTramo(mundo, actual, siguiente, null);
			if (impacto.isPresent()) {
				muestras.add(impacto.get());
				return new Trazado(muestras, impacto);
			}
			muestras.add(siguiente);
			actual = siguiente;
		}
		return new Trazado(muestras, Optional.empty());
	}
}

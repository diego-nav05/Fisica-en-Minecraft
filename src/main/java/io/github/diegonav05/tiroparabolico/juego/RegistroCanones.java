package io.github.diegonav05.tiroparabolico.juego;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import io.github.diegonav05.tiroparabolico.bloque.CanonBlockEntity;

/**
 * Cañones cargados en el servidor y cañón seleccionado por cada jugador. Permite manejar los
 * cañones desde lejos y actuar sobre todos a la vez. Solo guarda cañones del lado del servidor.
 */
public final class RegistroCanones {
	/** Referencias débiles: un cañón descargado o roto desaparece solo. */
	private static final Set<CanonBlockEntity> CARGADOS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<UUID, Seleccion> SELECCION = new HashMap<>();

	private record Seleccion(RegistryKey<World> mundo, BlockPos pos) {
	}

	private RegistroCanones() {
	}

	public static synchronized void registrar(CanonBlockEntity canon) {
		CARGADOS.add(canon);
	}

	public static synchronized void quitar(CanonBlockEntity canon) {
		CARGADOS.remove(canon);
	}

	/** Verdadero si el cañón sigue existiendo en su mundo (no se ha roto ni descargado). */
	private static boolean vigente(CanonBlockEntity canon, ServerWorld mundo) {
		return !canon.isRemoved() && canon.getWorld() == mundo && mundo.isChunkLoaded(canon.getPos())
				&& mundo.getBlockEntity(canon.getPos()) == canon;
	}

	/** Cañones cargados en un mundo, ordenados por posición para que la lista sea estable. */
	public static synchronized List<CanonBlockEntity> enMundo(ServerWorld mundo) {
		List<CanonBlockEntity> lista = new ArrayList<>();
		for (CanonBlockEntity canon : CARGADOS) {
			if (vigente(canon, mundo)) {
				lista.add(canon);
			}
		}
		lista.sort(Comparator.comparingInt((CanonBlockEntity c) -> c.getPos().getX())
				.thenComparingInt(c -> c.getPos().getZ())
				.thenComparingInt(c -> c.getPos().getY()));
		return lista;
	}

	/** El cañón cargado más cercano a un punto, dentro de un radio. */
	public static Optional<CanonBlockEntity> masCercano(ServerWorld mundo, Vec3d punto, double radio) {
		CanonBlockEntity mejor = null;
		double mejorDistancia = radio * radio;
		for (CanonBlockEntity canon : enMundo(mundo)) {
			double d = canon.getPos().toCenterPos().squaredDistanceTo(punto);
			if (d <= mejorDistancia) {
				mejorDistancia = d;
				mejor = canon;
			}
		}
		return Optional.ofNullable(mejor);
	}

	public static synchronized void seleccionar(UUID jugador, CanonBlockEntity canon) {
		SELECCION.put(jugador, new Seleccion(canon.getWorld().getRegistryKey(), canon.getPos().toImmutable()));
	}

	/** El cañón que el jugador seleccionó, si sigue existiendo en ese mundo. */
	public static synchronized Optional<CanonBlockEntity> seleccionado(UUID jugador, ServerWorld mundo) {
		Seleccion s = SELECCION.get(jugador);
		if (s == null || !s.mundo().equals(mundo.getRegistryKey()) || !mundo.isChunkLoaded(s.pos())) {
			return Optional.empty();
		}
		if (mundo.getBlockEntity(s.pos()) instanceof CanonBlockEntity canon) {
			return Optional.of(canon);
		}
		return Optional.empty();
	}

	/** Verdadero si este cañón es el que tiene seleccionado el jugador. */
	public static synchronized boolean esSeleccionado(UUID jugador, CanonBlockEntity canon) {
		Seleccion s = SELECCION.get(jugador);
		return s != null && canon.getWorld() != null && s.mundo().equals(canon.getWorld().getRegistryKey())
				&& s.pos().equals(canon.getPos());
	}
}

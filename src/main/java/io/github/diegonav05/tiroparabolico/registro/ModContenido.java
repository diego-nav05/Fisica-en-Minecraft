package io.github.diegonav05.tiroparabolico.registro;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import io.github.diegonav05.tiroparabolico.TiroParabolico;
import io.github.diegonav05.tiroparabolico.bloque.CanonBlock;
import io.github.diegonav05.tiroparabolico.bloque.CanonBlockEntity;
import io.github.diegonav05.tiroparabolico.entidad.ProyectilEntity;

/** Registro de bloques, objetos y entidades del mod. */
public final class ModContenido {
	public static final Block CANON = Registry.register(Registries.BLOCK, TiroParabolico.id("canon"),
			new CanonBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.IRON_GRAY)
					.strength(3.0f, 6.0f)
					.sounds(BlockSoundGroup.METAL)));

	public static final Item CANON_OBJETO = Registry.register(Registries.ITEM, TiroParabolico.id("canon"),
			new BlockItem(CANON, new Item.Settings()));

	/** Solo se usa como aspecto del proyectil en vuelo; no aparece en el inventario creativo. */
	public static final Item BALA = Registry.register(Registries.ITEM, TiroParabolico.id("bala"),
			new Item(new Item.Settings()));

	public static final BlockEntityType<CanonBlockEntity> CANON_ENTIDAD = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, TiroParabolico.id("canon"),
			BlockEntityType.Builder.create(CanonBlockEntity::new, CANON).build(null));

	public static final EntityType<ProyectilEntity> PROYECTIL = Registry.register(
			Registries.ENTITY_TYPE, TiroParabolico.id("proyectil"),
			EntityType.Builder.<ProyectilEntity>create(ProyectilEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f)
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.build("proyectil"));

	private ModContenido() {
	}

	public static void registrar() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entradas -> entradas.add(CANON_OBJETO));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entradas -> entradas.add(CANON_OBJETO));
	}
}

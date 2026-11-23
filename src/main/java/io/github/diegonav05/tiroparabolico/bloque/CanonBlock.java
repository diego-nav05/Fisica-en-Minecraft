package io.github.diegonav05.tiroparabolico.bloque;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import io.github.diegonav05.tiroparabolico.juego.Mensajes;
import io.github.diegonav05.tiroparabolico.juego.RegistroCanones;
import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/**
 * Cañón de laboratorio. Clic derecho: dispara. Agachado y con la mano vacía: muestra la
 * configuración y las predicciones (y lo selecciona para manejarlo desde lejos). Con redstone
 * dispara al recibir la señal y, si tiene cadencia, sigue disparando mientras la mantenga.
 */
public class CanonBlock extends Block implements BlockEntityProvider {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty POWERED = Properties.POWERED;

	public CanonBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// El cañón apunta hacia donde mira quien lo coloca.
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CanonBlockEntity(pos, state);
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient || type != ModContenido.CANON_ENTIDAD) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<CanonBlockEntity>) CanonBlockEntity::tickServidor;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world instanceof ServerWorld serverWorld && world.getBlockEntity(pos) instanceof CanonBlockEntity canon
				&& player instanceof ServerPlayerEntity jugador) {
			canon.setOperador(jugador.getUuid());
			if (player.isSneaking()) {
				RegistroCanones.seleccionar(jugador.getUuid(), canon);
				Mensajes.enviarFicha(jugador, canon);
			} else {
				canon.disparar(serverWorld, jugador);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
			boolean notify) {
		if (world.isClient) {
			return;
		}
		boolean alimentado = world.isReceivingRedstonePower(pos);
		if (alimentado != state.get(POWERED)) {
			world.setBlockState(pos, state.with(POWERED, alimentado), Block.NOTIFY_ALL);
			// En el flanco de subida dispara; mientras siga la señal, la cadencia hace el resto.
			if (alimentado && world instanceof ServerWorld serverWorld
					&& world.getBlockEntity(pos) instanceof CanonBlockEntity canon) {
				canon.alActivarseRedstone(serverWorld);
			}
		}
	}
}

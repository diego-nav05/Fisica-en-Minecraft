package io.github.diegonav05.tiroparabolico.bloque;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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

import io.github.diegonav05.tiroparabolico.juego.Mensajes;

/**
 * Cañón de laboratorio. Clic derecho: dispara. Agachado y con la mano vacía: muestra la
 * configuración y las predicciones. También dispara al recibir una señal de redstone.
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
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world instanceof ServerWorld serverWorld && world.getBlockEntity(pos) instanceof CanonBlockEntity canon
				&& player instanceof ServerPlayerEntity jugador) {
			if (player.isSneaking()) {
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
			// Solo se dispara en el flanco de subida de la señal.
			if (alimentado && world instanceof ServerWorld serverWorld
					&& world.getBlockEntity(pos) instanceof CanonBlockEntity canon) {
				canon.disparar(serverWorld, null);
			}
		}
	}
}

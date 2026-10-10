package com.mchoe.elevmod.block;

import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.item.ElevItem;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 기계실 장비: 권상기(기어드/기어리스), 조속기, 유압 파워유닛 */
public class MachineBlock extends BaseEntityBlock {
    public enum Kind { GEARED, GEARLESS, GOVERNOR, HYDRAULIC }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 1° 단위로 돌려 놓았으면 블록 모델 대신 블록엔티티 렌더러가 돌려서 그림 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ROTATED = net.minecraft.world.level.block.state.properties.BooleanProperty.create("rotated");
    public final Kind kind;
    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 14, 16);
    private static final VoxelShape GOV = Block.box(3, 0, 3, 13, 14, 13);

    public MachineBlock(Properties p, Kind kind) {
        super(p);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROTATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ROTATED); }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()); }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return kind == Kind.GOVERNOR ? GOV : FULL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModRegistry.MACHINE_BE.get(), MachineBlockEntity::clientTick) : null;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof ElevItem) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity m) {
            String k = "elevmod.machine." + kind.name().toLowerCase(java.util.Locale.ROOT);
            player.displayClientMessage(Component.translatable("elevmod.msg.machine_info", Component.translatable(k),
                    String.format("%.1f", Math.abs(m.speed) * 1200)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity m
                && m.ctrl != null && level.getBlockEntity(m.ctrl) instanceof ControllerBlockEntity c) {
            c.machines.remove(pos);
            c.setChanged();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}

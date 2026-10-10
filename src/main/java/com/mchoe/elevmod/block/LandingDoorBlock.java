package com.mchoe.elevmod.block;

import com.mchoe.elevmod.elevator.ControllerBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 승강장 문(1칸). 여러 칸을 이어 붙여 폭/높이를 만들고 링커로 제어반에 등록한다. FACING = 승강장(홀) 쪽. */
public class LandingDoorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public LandingDoorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, OPEN); }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Shapes2.wall(s.getValue(FACING), 0, 0, 16, 16, 4);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        if (!s.getValue(OPEN)) return Shapes2.wall(s.getValue(FACING), 0, 0, 16, 16, 4);
        // 열렸을 때도 유효 개구 밖(쟘·상부 틀)은 막혀 있어야 함
        return g.getBlockEntity(p) instanceof LandingDoorBlockEntity be ? openShape(s.getValue(FACING), be) : Shapes.empty();
    }

    /** 충돌 모양이 블록 엔티티(출입구 크기)에 따라 달라지므로 상태별 캐시를 쓰지 않음 */
    @Override
    public boolean hasDynamicShape() { return true; }

    /** 문이 열린 칸의 충돌 모양: 출입구(유효 개구) 바깥 부분만 막음 */
    static VoxelShape openShape(Direction facing, LandingDoorBlockEntity be) {
        float cw = Math.min(be.clearW, be.w), chh = Math.min(be.clearH, be.h);
        double x1 = -0.5 + (be.w - cw) / 2.0, x2 = x1 + cw;          // 출입구 전체 기준 좌표 (칸 가운데 = col)
        double c0 = be.col - 0.5;
        double a = Math.max(0, Math.min(1, x1 - c0)), b = Math.max(0, Math.min(1, x2 - c0));
        double top = Math.max(0, Math.min(1, chh - be.row));
        VoxelShape out = Shapes.empty();
        if (a > 0.01) out = Shapes.or(out, along(facing, 0, a, 0, 1));
        if (b < 0.99) out = Shapes.or(out, along(facing, b, 1, 0, 1));
        if (top < 0.99 && b - a > 0.01) out = Shapes.or(out, along(facing, a, b, top, 1));
        return out;
    }

    /** 출입구 가로 방향(u: 승강장에서 보는 축) 구간 → 블록 안의 상자 (두께는 칸 전체) */
    private static VoxelShape along(Direction facing, double u1, double u2, double y1, double y2) {
        Direction ax = facing.getCounterClockWise();
        return switch (ax) {
            case EAST -> Shapes.box(u1, y1, 0, u2, y2, 1);
            case WEST -> Shapes.box(1 - u2, y1, 0, 1 - u1, y2, 1);
            case SOUTH -> Shapes.box(0, y1, u1, 1, y2, u2);
            default -> Shapes.box(0, y1, 1 - u2, 1, y2, 1 - u1);
        };
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) { return true; }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).isEmpty()
                && level.getBlockEntity(pos) instanceof LandingDoorBlockEntity be) {
            player.displayClientMessage(Component.translatable(be.ctrl == null ? "elevmod.msg.door_unlinked" : "elevmod.msg.door_info"), true);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LandingDoorBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.LANDING_DOOR_BE.get(), LandingDoorBlockEntity::tick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LandingDoorBlockEntity be
                && be.ctrl != null && level.getBlockEntity(be.ctrl) instanceof ControllerBlockEntity c) {
            c.unlinkDoor((net.minecraft.server.level.ServerLevel) level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static void setOpen(Level level, BlockPos pos, boolean open) {
        if (!level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof LandingDoorBlock && s.getValue(OPEN) != open) level.setBlock(pos, s.setValue(OPEN, open), 3);
    }
}

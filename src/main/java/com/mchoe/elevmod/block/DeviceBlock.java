package com.mchoe.elevmod.block;

import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.item.ElevItem;
import com.mchoe.elevmod.network.Net;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 승강장 / 벽부착 장치 */
public class DeviceBlock extends BaseEntityBlock {
    public enum Kind {
        HALL_BUTTON(6.5, 1, 9.5, 7, 0.6),
        HALL_PANEL(5.5, 0, 10.5, 10, 0.6),
        IND_SEGMENT(4, 2, 12, 5, 1),
        IND_DOT(3.5, 2, 12.5, 5.5, 1),
        IND_LCD(4.5, 1, 11.5, 6.5, 1),
        IND_LAMP(0, 3, 16, 5, 1),
        LANTERN(5, 2, 11, 4.5, 1.2),
        FIRE_SWITCH(5.5, 1, 10.5, 7, 1.5),
        DESTINATION(4.5, 0, 11.5, 9, 1.2),
        CALL_RECEIVER(6.5, 6, 9.5, 9, 0.5);

        public final double x1, y1, x2, y2, depth;
        Kind(double x1, double y1, double x2, double y2, double depth) { this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.depth = depth; }

        public boolean indicator() { return this == IND_SEGMENT || this == IND_DOT || this == IND_LCD || this == IND_LAMP; }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final Kind kind;

    public DeviceBlock(Properties p, Kind kind) {
        super(p);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction f = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Shapes2.wall(s.getValue(FACING), kind.x1, kind.y1, kind.x2, kind.y2, kind.depth);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DeviceBlockEntity(pos, state); }

    @Nullable
    private static ControllerBlockEntity controller(Level level, DeviceBlockEntity d) {
        if (d.ctrl == null || !level.isLoaded(d.ctrl)) return null;
        return level.getBlockEntity(d.ctrl) instanceof ControllerBlockEntity c ? c : null;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean fireKeyOnSwitch = kind == Kind.FIRE_SWITCH && player.getItemInHand(hand).is(ModRegistry.FIRE_KEY.get());
        if (player.getItemInHand(hand).getItem() instanceof ElevItem && !fireKeyOnSwitch) return InteractionResult.PASS;
        if (player.getItemInHand(hand).getItem() instanceof com.mchoe.elevmod.item.DesignToolItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DeviceBlockEntity d)) return InteractionResult.PASS;
        ServerLevel sl = (ServerLevel) level;
        ControllerBlockEntity c = controller(level, d);
        if (c == null) {
            player.displayClientMessage(Component.translatable("elevmod.msg.device_unlinked").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        switch (kind) {
            case HALL_BUTTON, HALL_PANEL -> {
                double ly = hit.getLocation().y - pos.getY();
                double split = kind == Kind.HALL_PANEL ? 0.175 : 0.245;
                boolean up = ly >= split;
                if (!d.hasUp) up = false;
                if (!d.hasDown) up = true;
                c.hallCall(sl, d.floorY, up, player);
            }
            case FIRE_SWITCH -> {
                boolean key = player.getItemInHand(hand).is(ModRegistry.FIRE_KEY.get()) || player.isCreative();
                if (!key) { player.displayClientMessage(Component.translatable("elevmod.msg.need_fire_key").withStyle(ChatFormatting.RED), true); break; }
                c.fireService(sl, !c.mode.fire(), d.floorY);
            }
            case DESTINATION -> Net.openDestination((ServerPlayer) player, pos, c);
            case CALL_RECEIVER -> c.hallCallDefault(sl, d.floorY);
            default -> player.displayClientMessage(Component.translatable("elevmod.msg.indicator_info", d.letter, d.text), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(BlockState s, Level level, BlockPos pos, Block b, BlockPos from, boolean moving) {
        if (level.isClientSide || (kind != Kind.FIRE_SWITCH && kind != Kind.CALL_RECEIVER)) return;
        if (!(level.getBlockEntity(pos) instanceof DeviceBlockEntity d)) return;
        boolean on = level.hasNeighborSignal(pos);
        if (on == d.redstoneLast) return;
        d.redstoneLast = on;
        d.setChanged();
        ControllerBlockEntity c = controller(level, d);
        if (c == null) return;
        if (kind == Kind.FIRE_SWITCH) c.fireService((ServerLevel) level, on, d.floorY); // 화재감지기 연동
        else if (on) c.hallCallDefault((ServerLevel) level, d.floorY);      // 월패드/외부 호출
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof DeviceBlockEntity d
                && d.ctrl != null && level.getBlockEntity(d.ctrl) instanceof ControllerBlockEntity c) {
            c.devices.remove(pos);
            c.setChanged();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}

package com.mchoe.elevmod.block;

import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MachineBlockEntity extends BlockEntity {
    @Nullable public BlockPos ctrl;
    public float speed;
    public int drive;
    public float angle, anglePrev;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MACHINE_BE.get(), pos, state);
    }

    public void link(BlockPos c) { ctrl = c; sync(); }

    public void setSpeed(float s, int drive) {
        if (Math.abs(s - speed) < 0.002f && this.drive == drive && !(s == 0 && speed != 0)) return;
        speed = s;
        this.drive = drive;
        sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.anglePrev = be.angle;
        // 쉬브 직경 약 0.6m 기준 회전각
        be.angle += (float) Math.toDegrees(be.speed / 0.3);
        if (be.angle > 3600 || be.angle < -3600) { be.angle %= 360; be.anglePrev = be.angle; }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (ctrl != null) t.putLong("ctrl", ctrl.asLong());
        t.putFloat("speed", speed);
        t.putInt("drive", drive);
        t.putLong("origin", worldPosition.asLong());
        t.putFloat("yaw", yaw);
        if (!Double.isNaN(pivX)) { t.putDouble("pvx", pivX); t.putDouble("pvz", pivZ); }
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        yaw = t.getFloat("yaw");
        pivX = t.contains("pvx") ? t.getDouble("pvx") : Double.NaN; pivZ = t.contains("pvz") ? t.getDouble("pvz") : Double.NaN;
        ctrl = t.contains("ctrl") ? BlockPos.of(t.getLong("ctrl")) : null;
        speed = t.getFloat("speed");
        drive = t.getInt("drive");
        if (t.contains("origin") && ctrl != null) {
            BlockPos o = BlockPos.of(t.getLong("origin"));
            if (!o.equals(worldPosition)) { ctrl = ctrl.offset(worldPosition.subtract(o)); moved = true; }
        }
    }

    /** Ctrl + / - 로 돌린 각도(도, 세로축) */
    public float yaw;
    /** 회전기로 묶어 돌릴 때의 회전축 (월드 x, z). NaN = 블록 자체 가운데 */
    public double pivX = Double.NaN, pivZ = Double.NaN;

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        net.minecraft.world.phys.AABB b = new net.minecraft.world.phys.AABB(worldPosition).inflate(1);
        if (yaw == 0 || Double.isNaN(pivX)) return b;
        // 회전기로 돌리면 회전축에서 떨어진 만큼 그려지는 곳이 옮겨짐
        double r = Math.hypot(worldPosition.getX() + 0.5 - pivX, worldPosition.getZ() + 0.5 - pivZ);
        return b.inflate(r + 1);
    }

    /** 회전기: 묶음 회전 (yaw 0 + 축 NaN = 해제) */
    public void setGroupRot(float y, double px, double pz) { pivX = px; pivZ = pz; setYaw(y); }


    public void setYaw(float y) {
        yaw = y;
        setChanged();
        if (level != null && !level.isClientSide && getBlockState().hasProperty(MachineBlock.ROTATED) && getBlockState().getValue(MachineBlock.ROTATED) != (y != 0))
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.ROTATED, y != 0), 3);
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** 복사로 좌표가 옮겨졌으면 새 좌표가 저장되도록 표시 */
    private boolean moved;

    @Override
    public void onLoad() {
        super.onLoad();
        if (moved && level != null && !level.isClientSide) { moved = false; setChanged(); }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

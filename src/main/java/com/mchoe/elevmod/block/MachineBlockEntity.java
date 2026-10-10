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
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        ctrl = t.contains("ctrl") ? BlockPos.of(t.getLong("ctrl")) : null;
        speed = t.getFloat("speed");
        drive = t.getInt("drive");
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

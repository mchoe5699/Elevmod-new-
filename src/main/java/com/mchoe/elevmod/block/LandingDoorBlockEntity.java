package com.mchoe.elevmod.block;

import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class LandingDoorBlockEntity extends BlockEntity {
    @Nullable public BlockPos ctrl;
    public boolean master = true;
    public int col, row, w = 1, h = 1;
    public int doorType = 0, theme = 0;
    public boolean window, rightHand;
    public int jamb = 0;
    public float clearW = 0.9f, clearH = 0.95f;
    public float target, speed = 0.025f;
    /** 디자인 변경기로 지정한 이 층 문 마감/쟘 (-1 = 제어반 설정을 따름) */
    public int finish = -1, jambOverride = -1;
    public int manualTicks;
    /** 삼각키로 수동 개방 중 (카 도어와 무관하게 움직임) */
    public boolean manual;
    // 클라이언트 애니메이션
    public float animPrev, anim;

    public LandingDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LANDING_DOOR_BE.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(LandingDoorBlock.FACING); }

    public Spec.DoorType type() { Spec.DoorType[] v = Spec.DoorType.values(); return v[Math.max(0, Math.min(doorType, v.length - 1))]; }

    public Spec.Theme themeE() {
        Spec.Theme[] v = Spec.Theme.values();
        int t = finish >= 0 ? finish : theme;
        return v[Math.max(0, Math.min(t, v.length - 1))];
    }

    public void cycleFinish() {
        finish++;
        if (finish >= Spec.Theme.values().length) finish = -1;
        sync();
    }

    public void cycleJambOverride() {
        jambOverride++;
        if (jambOverride >= Spec.Jamb.values().length) jambOverride = -1;
        sync();
    }

    public BlockPos masterPos() {
        Direction a = facing().getCounterClockWise();
        return worldPosition.relative(a, -col).below(row);
    }

    public void link(BlockPos c, boolean isMaster, int col, int row, int w, int h) {
        this.ctrl = c; this.master = isMaster; this.col = col; this.row = row; this.w = w; this.h = h;
        sync();
    }

    public void style(Spec.DoorType t, Spec.Theme th, boolean win, Spec.Jamb jb, boolean right, float cw, float chh) {
        if (doorType == t.ordinal() && theme == th.ordinal() && window == win && jamb == jb.ordinal() && rightHand == right
                && clearW == cw && clearH == chh) return;
        doorType = t.ordinal(); theme = th.ordinal(); window = win; jamb = jb.ordinal(); rightHand = right; clearW = cw; clearH = chh;
        sync();
    }

    public Spec.Jamb jambE() { Spec.Jamb[] v = Spec.Jamb.values(); int j = jambOverride >= 0 ? jambOverride : jamb; return v[Math.max(0, Math.min(j, v.length - 1))]; }

    public void unlink() {
        ctrl = null; master = true; col = 0; row = 0; w = 1; h = 1; target = 0; clearW = 0.9f; clearH = 0.95f;
        sync();
    }

    public void command(boolean open, float spd) { commandTarget(open ? 1f : 0f, spd); }

    public void commandTarget(float t, float spd) {
        if (target == t && speed == spd) return;
        target = t;
        speed = spd;
        sync();
    }

    /** 삼각키(도어키)로 승강장 문 개방 */
    public void manualOpen(Player p) {
        if (!(level instanceof ServerLevel sl)) return;
        if (ctrl == null) { p.displayClientMessage(Component.translatable("elevmod.msg.door_unlinked"), true); return; }
        BlockPos m = masterPos();
        if (!(sl.getBlockEntity(m) instanceof LandingDoorBlockEntity mb)) return;
        Direction a = facing().getCounterClockWise();
        for (int i = 0; i < mb.w; i++) for (int j = 0; j < mb.h; j++) LandingDoorBlock.setOpen(sl, m.relative(a, i).above(j), true);
        mb.manualTicks = 100;
        mb.manual = true;
        mb.command(true, 0.03f);
        sl.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.7f);
        if (sl.getBlockEntity(ctrl) instanceof ControllerBlockEntity c) {
            c.doorLockOpened(sl, m.getY(), 110);
            int idx = c.idxOfY(m.getY());
            boolean carHere = idx >= 0 && Math.abs(c.floorS[idx] - c.s) < 0.6;
            if (!carHere) p.displayClientMessage(Component.translatable("elevmod.msg.shaft_danger").withStyle(ChatFormatting.RED), false);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LandingDoorBlockEntity be) {
        if (level.isClientSide) {
            be.animPrev = be.anim;
            float d = be.target - be.anim;
            be.anim = Math.abs(d) <= be.speed ? be.target : be.anim + Math.signum(d) * be.speed;
            return;
        }
        if (be.manualTicks > 0 && --be.manualTicks == 0) {
            be.manual = false;
            be.command(false, 0.03f);
            Direction a = be.facing().getCounterClockWise();
            boolean carHere = false;
            if (be.ctrl != null && level.getBlockEntity(be.ctrl) instanceof ControllerBlockEntity c) {
                int idx = c.idxOfY(pos.getY());
                carHere = idx >= 0 && c.atFloor() && c.cur == idx && c.doorPos > 0;
            }
            if (!carHere) for (int i = 0; i < be.w; i++) for (int j = 0; j < be.h; j++) LandingDoorBlock.setOpen(level, pos.relative(a, i).above(j), false);
        }
    }

    public float anim(float pt) { return animPrev + (anim - animPrev) * pt; }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public AABB getRenderBoundingBox() {
        int r = Math.max(w, h) + 2;
        return new AABB(worldPosition).inflate(r);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (ctrl != null) t.putLong("ctrl", ctrl.asLong());
        t.putBoolean("master", master);
        t.putInt("col", col); t.putInt("row", row); t.putInt("w", w); t.putInt("h", h);
        t.putInt("type", doorType); t.putInt("theme", theme); t.putBoolean("win", window);
        t.putInt("jamb", jamb); t.putBoolean("rh", rightHand); t.putFloat("cw", clearW); t.putFloat("ch", clearH);
        t.putFloat("target", target); t.putFloat("speed", speed);
        t.putInt("finish", finish); t.putInt("jambO", jambOverride);
        t.putBoolean("manual", manual);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        ctrl = t.contains("ctrl") ? BlockPos.of(t.getLong("ctrl")) : null;
        master = !t.contains("master") || t.getBoolean("master");
        col = t.getInt("col"); row = t.getInt("row");
        w = Math.max(1, t.getInt("w")); h = Math.max(1, t.getInt("h"));
        doorType = t.getInt("type"); theme = t.getInt("theme"); window = t.getBoolean("win");
        jamb = t.getInt("jamb"); rightHand = t.getBoolean("rh");
        clearW = t.contains("cw") ? t.getFloat("cw") : Math.max(0.6f, w - 0.1f);
        clearH = t.contains("ch") ? t.getFloat("ch") : h - 0.05f;
        target = t.getFloat("target");
        speed = t.contains("speed") ? t.getFloat("speed") : 0.025f;
        finish = t.contains("finish") ? t.getInt("finish") : -1;
        manual = t.getBoolean("manual");
        jambOverride = t.contains("jambO") ? t.getInt("jambO") : -1;
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

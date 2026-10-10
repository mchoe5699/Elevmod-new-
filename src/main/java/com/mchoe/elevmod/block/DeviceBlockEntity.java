package com.mchoe.elevmod.block;

import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 승강장 장치(호출버튼, 층표시기, 홀랜턴, 소방스위치, 행선층 패널, 원격호출 수신기) 공용 블록엔티티 */
public class DeviceBlockEntity extends BlockEntity {
    @Nullable public BlockPos ctrl;
    public int floorY;
    // 제어반이 밀어주는 표시 정보
    public String text = "", status = "", letter = "", floorName = "";
    public int dir;
    public boolean moving, lanUp, lanDown, lanAny, upLit, downLit, hasUp = true, hasDown = true, dumbwaiter;
    public final List<String> names = new ArrayList<>();
    public boolean redstoneLast;
    private int hash;

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DEVICE_BE.get(), pos, state);
    }

    public void link(BlockPos c, int fy) {
        ctrl = c;
        floorY = fy;
        hash = 0;
        sync();
    }

    public void unlink() {
        ctrl = null;
        text = ""; status = ""; letter = ""; floorName = "";
        upLit = downLit = lanUp = lanDown = lanAny = false;
        names.clear();
        sync();
    }

    public void applyDisplay(String text, int dir, String status, boolean moving, boolean lanUp, boolean lanDown, boolean lanAny,
                             boolean upLit, boolean downLit, boolean hasUp, boolean hasDown, String letter, boolean dumbwaiter,
                             String floorName, List<String> names) {
        int h = Objects.hash(text, dir, status, moving, lanUp, lanDown, lanAny, upLit, downLit, hasUp, hasDown, letter, dumbwaiter, floorName, names);
        if (h == hash) return;
        hash = h;
        this.text = text; this.dir = dir; this.status = status; this.moving = moving;
        this.lanUp = lanUp; this.lanDown = lanDown; this.lanAny = lanAny;
        this.upLit = upLit; this.downLit = downLit; this.hasUp = hasUp; this.hasDown = hasDown;
        this.letter = letter; this.dumbwaiter = dumbwaiter; this.floorName = floorName;
        this.names.clear();
        this.names.addAll(names);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (ctrl != null) t.putLong("ctrl", ctrl.asLong());
        t.putInt("fy", floorY);
        t.putString("text", text);
        t.putString("st", status);
        t.putString("let", letter);
        t.putString("fn", floorName);
        t.putInt("dir", dir);
        int flags = (moving ? 1 : 0) | (lanUp ? 2 : 0) | (lanDown ? 4 : 0) | (lanAny ? 8 : 0) | (upLit ? 16 : 0) | (downLit ? 32 : 0)
                | (hasUp ? 64 : 0) | (hasDown ? 128 : 0) | (dumbwaiter ? 256 : 0) | (redstoneLast ? 512 : 0);
        t.putInt("fl", flags);
        ListTag l = new ListTag();
        for (String n : names) l.add(StringTag.valueOf(n));
        t.put("names", l);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        ctrl = t.contains("ctrl") ? BlockPos.of(t.getLong("ctrl")) : null;
        floorY = t.getInt("fy");
        text = t.getString("text");
        status = t.getString("st");
        letter = t.getString("let");
        floorName = t.getString("fn");
        dir = t.getInt("dir");
        int f = t.getInt("fl");
        moving = (f & 1) != 0; lanUp = (f & 2) != 0; lanDown = (f & 4) != 0; lanAny = (f & 8) != 0;
        upLit = (f & 16) != 0; downLit = (f & 32) != 0; hasUp = (f & 64) != 0; hasDown = (f & 128) != 0;
        dumbwaiter = (f & 256) != 0; redstoneLast = (f & 512) != 0;
        names.clear();
        ListTag l = t.getList("names", Tag.TAG_STRING);
        for (Tag x : l) names.add(x.getAsString());
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

package com.mchoe.elevmod.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 승강장(층) 하나. 층은 승강장 문의 바닥 Y 로 구분한다. */
public class Floor {
    public int y;
    public String customName = "";
    public final List<Door> doors = new ArrayList<>();

    // 계산값
    public double ax, ay, az;
    public String name = "?";

    public Floor(int y) { this.y = y; }

    public Vec3 anchor() { return new Vec3(ax, ay, az); }

    public boolean hasFront() { for (Door d : doors) if (!d.rear) return true; return false; }
    public boolean hasRear() { for (Door d : doors) if (d.rear) return true; return false; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("y", y);
        t.putString("name", customName);
        ListTag l = new ListTag();
        for (Door d : doors) l.add(d.save());
        t.put("doors", l);
        return t;
    }

    public static Floor load(CompoundTag t) {
        Floor f = new Floor(t.getInt("y"));
        f.customName = t.getString("name");
        ListTag l = t.getList("doors", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) f.doors.add(Door.load(l.getCompound(i)));
        return f;
    }

    /** 승강장 문 한 조(여러 칸) */
    public static class Door {
        public BlockPos master;
        public Direction facing; // 승강장(홀) 쪽 방향
        public int w, h;
        public final List<BlockPos> cells = new ArrayList<>();
        public boolean rear;
        public float off;

        /** 문이 카와 맞닿는 면의 중심(바닥 높이) */
        public Vec3 plane() {
            Direction a = facing.getCounterClockWise();
            double cx = master.getX() + 0.5 + a.getStepX() * (w - 1) / 2.0 - facing.getStepX() * 0.5;
            double cz = master.getZ() + 0.5 + a.getStepZ() * (w - 1) / 2.0 - facing.getStepZ() * 0.5;
            return new Vec3(cx, master.getY(), cz);
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("m", master.asLong());
            t.putInt("f", facing.get2DDataValue());
            t.putInt("w", w);
            t.putInt("h", h);
            t.putBoolean("r", rear);
            ListTag l = new ListTag();
            for (BlockPos p : cells) l.add(LongTag.valueOf(p.asLong()));
            t.put("c", l);
            return t;
        }

        public static Door load(CompoundTag t) {
            Door d = new Door();
            d.master = BlockPos.of(t.getLong("m"));
            d.facing = Direction.from2DDataValue(t.getInt("f"));
            d.w = t.getInt("w");
            d.h = t.getInt("h");
            d.rear = t.getBoolean("r");
            ListTag l = t.getList("c", Tag.TAG_LONG);
            for (Tag x : l) d.cells.add(BlockPos.of(((LongTag) x).getAsLong()));
            return d;
        }
    }
}

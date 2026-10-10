package com.mchoe.elevmod.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 엘리베이터 고유번호(호기 번호) 등록부 – 서버 전체에서 번호가 겹치지 않게 관리한다.
 * 제어반을 복사·붙여넣기(월드에딧 등)하면 같은 번호가 생기므로, 새 제어반은 이 등록부를 보고 새 번호를 받는다.
 */
public class SerialRegistry extends SavedData {
    private final Map<String, GlobalPos> map = new HashMap<>();

    public static SerialRegistry get(ServerLevel any) {
        ServerLevel ow = any.getServer().overworld();
        return ow.getDataStorage().computeIfAbsent(SerialRegistry::load, SerialRegistry::new, "elevmod_serials").at(any);
    }

    /** 이 번호를 다른 제어반이 쓰고 있는지 */
    public boolean taken(ServerLevel lvl, String serial, BlockPos self) {
        GlobalPos g = map.get(serial);
        if (g == null) return false;
        if (g.dimension().equals(lvl.dimension()) && g.pos().equals(self)) return false;
        ServerLevel other = lvl.getServer().getLevel(g.dimension());
        if (other == null) { map.remove(serial); setDirty(); return false; }
        if (other.isLoaded(g.pos())) {
            // 실제로 그 자리에 같은 번호의 제어반이 있어야 사용 중
            if (other.getBlockEntity(g.pos()) instanceof ControllerBlockEntity c && serial.equals(c.serial)) return true;
            map.remove(serial);
            setDirty();
            return false;
        }
        return true; // 로드되지 않은 곳의 제어반은 있다고 본다
    }

    public void register(String serial, BlockPos pos) {
        if (serial.isEmpty() || current == null) return;
        map.put(serial, GlobalPos.of(current, pos.immutable()));
        setDirty();
    }

    public void unregister(String serial, BlockPos pos) {
        GlobalPos g = map.get(serial);
        if (g != null && g.pos().equals(pos)) { map.remove(serial); setDirty(); }
    }

    // 등록 시 차원 정보 – get() 호출자와 같은 차원
    private ResourceKey<Level> current;

    public SerialRegistry at(ServerLevel lvl) { current = lvl.dimension(); return this; }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        map.forEach((k, g) -> {
            CompoundTag e = new CompoundTag();
            e.putString("s", k);
            e.putString("d", g.dimension().location().toString());
            e.putLong("p", g.pos().asLong());
            l.add(e);
        });
        t.put("list", l);
        return t;
    }

    public static SerialRegistry load(CompoundTag t) {
        SerialRegistry r = new SerialRegistry();
        for (Tag x : t.getList("list", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) x;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(e.getString("d")));
            r.map.put(e.getString("s"), GlobalPos.of(dim, BlockPos.of(e.getLong("p"))));
        }
        return r;
    }
}

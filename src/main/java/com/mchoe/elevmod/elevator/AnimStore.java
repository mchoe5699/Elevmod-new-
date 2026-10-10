package com.mchoe.elevmod.elevator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mchoe.elevmod.ElevMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 도트 애니메이션 라이브러리 (서버, 월드 폴더/elevmod_indicator_anims.json) */
public final class AnimStore {
    private AnimStore() {}

    public static final int MAX = 200;
    private static final Map<String, DotAnim> MAP = new LinkedHashMap<>();
    private static Path file;

    public static synchronized void load(MinecraftServer s) {
        MAP.clear();
        file = s.getWorldPath(LevelResource.ROOT).resolve("elevmod_indicator_anims.json");
        try {
            if (Files.exists(file)) {
                JsonArray a = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : a) { DotAnim d = DotAnim.fromJson(e.getAsJsonObject()); if (!d.id.isEmpty()) MAP.put(d.id, d); }
            }
        } catch (Exception e) { ElevMod.LOG.error("[elevmod] animation library load failed", e); }
    }

    public static synchronized void clear() { MAP.clear(); file = null; }

    public static synchronized List<DotAnim> all() { return new ArrayList<>(MAP.values()); }

    public static synchronized DotAnim get(String id) { return MAP.get(id); }

    public static synchronized boolean put(DotAnim a) {
        if (!a.id.matches("a_[a-z0-9]{4,24}")) return false;
        if (!MAP.containsKey(a.id) && MAP.size() >= MAX) return false;
        MAP.put(a.id, a);
        save();
        return true;
    }

    public static synchronized boolean delete(String id) {
        if (MAP.remove(id) == null) return false;
        save();
        return true;
    }

    public static synchronized String json() {
        JsonArray a = new JsonArray();
        for (DotAnim d : MAP.values()) a.add(d.toJson());
        return new Gson().toJson(a);
    }

    private static void save() {
        if (file == null) return;
        try { Files.writeString(file, json(), StandardCharsets.UTF_8); } catch (Exception e) { ElevMod.LOG.error("[elevmod] animation library save failed", e); }
    }
}

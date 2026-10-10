package com.mchoe.elevmod.elevator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mchoe.elevmod.ElevMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 사용자 커스텀 디자인 저장소 (서버). 월드 폴더/elevmod_designs/&lt;id&gt;.zip 에
 * design.json + 사진 텍스처(png)를 묶어 저장하고, 접속한 모든 플레이어에게 내려준다.
 */
public final class DesignStore {
    private DesignStore() {}

    public static final int MAX_ZIP = 12 * 1024 * 1024;

    public static final class Entry {
        public final String id;
        public String name;
        public final Set<String> parts;
        public final byte[] zip;
        public final String owner;
        public final long version;

        Entry(String id, String name, Set<String> parts, byte[] zip, String owner, long version) {
            this.id = id; this.name = name; this.parts = parts; this.zip = zip; this.owner = owner; this.version = version;
        }
    }

    private static final Map<String, Entry> MAP = new LinkedHashMap<>();
    private static Path dir;

    public static synchronized void load(MinecraftServer server) {
        MAP.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("elevmod_designs");
        try {
            Files.createDirectories(dir);
            JsonObject meta = new JsonObject();
            Path mp = dir.resolve("index.json");
            if (Files.exists(mp)) meta = JsonParser.parseString(Files.readString(mp, StandardCharsets.UTF_8)).getAsJsonObject();
            try (var st = Files.list(dir)) {
                for (Path p : (Iterable<Path>) st::iterator) {
                    String fn = p.getFileName().toString();
                    if (!fn.endsWith(".zip")) continue;
                    String id = fn.substring(0, fn.length() - 4);
                    if (!validId(id)) continue;
                    byte[] zip = Files.readAllBytes(p);
                    JsonObject json = designJson(zip);
                    if (json == null) continue;
                    JsonObject m = meta.has(id) ? meta.getAsJsonObject(id) : new JsonObject();
                    String name = m.has("name") ? m.get("name").getAsString() : CustomDesignIndex.nameOf(json, id);
                    String owner = m.has("owner") ? m.get("owner").getAsString() : "";
                    MAP.put(id, new Entry(id, name, CustomDesignIndex.parts(json), zip, owner, Files.getLastModifiedTime(p).toMillis()));
                }
            }
            ElevMod.LOG.info("[elevmod] loaded {} custom designs", MAP.size());
        } catch (IOException e) {
            ElevMod.LOG.error("[elevmod] design store load failed", e);
        }
    }

    public static synchronized void clear() { MAP.clear(); dir = null; }

    public static boolean validId(String id) { return id.matches("u_[a-z0-9]{4,24}"); }

    /** zip 안의 design.json (검증 포함). 잘못된 묶음이면 null */
    public static JsonObject designJson(byte[] zip) {
        if (zip.length > MAX_ZIP) return null;
        JsonObject json = null;
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                byte[] data = z.readNBytes(8 * 1024 * 1024 + 1);
                total += data.length;
                if (data.length > 8 * 1024 * 1024 || total > 40 * 1024 * 1024) return null;
                if (n.equals("design.json")) json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
                else if (!n.matches("[a-z0-9_]{1,40}\\.png")) return null;
            }
        } catch (Exception ex) {
            return null;
        }
        return json;
    }

    public static synchronized List<String> ids() { return new ArrayList<>(MAP.keySet()); }

    public static synchronized List<String> ids(String part) {
        List<String> out = new ArrayList<>();
        MAP.forEach((k, v) -> { if (v.parts.contains(part)) out.add(k); });
        return out;
    }

    public static synchronized String name(String id) { Entry e = MAP.get(id); return e == null ? id : e.name; }

    public static synchronized Entry get(String id) { return MAP.get(id); }

    public static synchronized List<Entry> all() { return new ArrayList<>(MAP.values()); }

    public static synchronized boolean put(String id, String name, byte[] zip, UUID owner) {
        if (dir == null || !validId(id)) return false;
        JsonObject json = designJson(zip);
        if (json == null) return false;
        try {
            Files.write(dir.resolve(id + ".zip"), zip);
            MAP.put(id, new Entry(id, clean(name), CustomDesignIndex.parts(json), zip, owner.toString(), System.currentTimeMillis()));
            saveMeta();
            return true;
        } catch (IOException e) {
            ElevMod.LOG.error("[elevmod] design save failed", e);
            return false;
        }
    }

    public static synchronized boolean rename(String id, String name) {
        Entry e = MAP.get(id);
        if (e == null) return false;
        e.name = clean(name);
        saveMeta();
        return true;
    }

    public static synchronized boolean delete(String id) {
        if (MAP.remove(id) == null) return false;
        try { Files.deleteIfExists(dir.resolve(id + ".zip")); } catch (IOException ignored) {}
        saveMeta();
        return true;
    }

    private static String clean(String n) {
        n = n.replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "Custom";
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    private static void saveMeta() {
        if (dir == null) return;
        JsonObject meta = new JsonObject();
        MAP.forEach((k, v) -> {
            JsonObject m = new JsonObject();
            m.addProperty("name", v.name);
            m.addProperty("owner", v.owner);
            meta.add(k, m);
        });
        try { Files.writeString(dir.resolve("index.json"), new Gson().toJson(meta), StandardCharsets.UTF_8); }
        catch (IOException e) { ElevMod.LOG.error("[elevmod] design index save failed", e); }
    }

    public static Set<String> partsOf(String id) {
        Entry e = get(id);
        return e == null ? new HashSet<>() : e.parts;
    }
}

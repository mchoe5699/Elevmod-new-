package com.mchoe.elevmod.elevator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mchoe.elevmod.ElevMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 커스텀 디자인 목록 – 모드에 들어 있는 디자인(assets/elevmod/designs) + 월드에 저장된 사용자 디자인(DesignStore).
 * 서버/클라이언트 공용. 부위 이름: car, carDoor, landing, cop, copAcc, indicator, hallButton, hallPanel, lantern
 */
public final class CustomDesignIndex {
    private CustomDesignIndex() {}

    public static final String[] PARTS = {"car", "carDoor", "landing", "cop", "copAcc", "indicator", "hallButton", "hallPanel", "lantern"};

    private static Map<String, Set<String>> builtin;
    private static final Map<String, String> builtinNames = new HashMap<>();

    /** JSON 최상위 키로 이 디자인이 정의하는 부위를 판단 */
    public static Set<String> parts(JsonObject o) {
        Set<String> s = new HashSet<>();
        if (o.has("wall") || o.has("ceiling") || o.has("frontWall") || o.has("floor") || o.has("backWall")) s.add("car");
        if (o.has("door")) s.add("carDoor");
        if (o.has("landingDoor") || o.has("door") || o.has("jamb") || o.has("jambFace")) s.add("landing");
        if (o.has("copPanel") || o.has("cop")) s.add("cop");
        for (String k : new String[]{"copAcc", "indicator", "hallButton", "hallPanel", "lantern"}) if (o.has(k)) s.add(k);
        return s;
    }

    public static String nameOf(JsonObject o, String fallback) {
        if (o.has("name") && o.get("name").isJsonObject()) {
            JsonObject n = o.getAsJsonObject("name");
            if (n.has("ko")) return n.get("ko").getAsString();
            if (n.has("en")) return n.get("en").getAsString();
        }
        return fallback;
    }

    private static synchronized Map<String, Set<String>> builtin() {
        if (builtin != null) return builtin;
        builtin = new LinkedHashMap<>();
        try (InputStream in = CustomDesignIndex.class.getResourceAsStream("/assets/elevmod/designs/index.json")) {
            if (in != null) {
                JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                a.forEach(e -> {
                    String id = e.getAsString();
                    try (InputStream d = CustomDesignIndex.class.getResourceAsStream("/assets/elevmod/designs/" + id + ".json")) {
                        if (d == null) return;
                        JsonObject o = JsonParser.parseReader(new InputStreamReader(d, StandardCharsets.UTF_8)).getAsJsonObject();
                        builtin.put(id, parts(o));
                        builtinNames.put(id, nameOf(o, id));
                    } catch (Exception ex) { ElevMod.LOG.error("[elevmod] design " + id, ex); }
                });
            }
        } catch (Exception e) {
            ElevMod.LOG.error("[elevmod] design index load failed", e);
        }
        return builtin;
    }

    /** 모든 디자인 id (내장 + 월드) */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(builtin().keySet());
        out.addAll(DesignStore.ids());
        return out;
    }

    /** 특정 부위를 정의한 디자인 id */
    public static List<String> ids(String part) {
        List<String> out = new ArrayList<>();
        builtin().forEach((k, v) -> { if (v.contains(part)) out.add(k); });
        out.addAll(DesignStore.ids(part));
        return out;
    }

    public static String name(String id) {
        builtin();
        String n = builtinNames.get(id);
        return n != null ? n : DesignStore.name(id);
    }
}

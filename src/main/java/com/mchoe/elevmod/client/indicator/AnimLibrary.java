package com.mchoe.elevmod.client.indicator;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mchoe.elevmod.elevator.DotAnim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 도트 애니메이션 라이브러리 (클라이언트 사본) */
public final class AnimLibrary {
    private AnimLibrary() {}

    private static final Map<String, DotAnim> MAP = new LinkedHashMap<>();
    public static int version;

    public static void sync(String json) {
        MAP.clear();
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                DotAnim a = DotAnim.fromJson(e.getAsJsonObject());
                if (!a.id.isEmpty()) MAP.put(a.id, a);
            }
        } catch (Exception ignored) {}
        version++;
    }

    public static DotAnim get(String id) { return id == null || id.isEmpty() ? null : MAP.get(id); }

    public static List<DotAnim> all() { return new ArrayList<>(MAP.values()); }
}

package com.mchoe.elevmod.client.design;

import com.google.gson.JsonParser;
import com.mchoe.elevmod.ElevMod;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 커스텀 디자인 로더/캐시 (클라이언트) */
public final class CustomDesigns {
    private CustomDesigns() {}

    private static final Map<String, Optional<CustomDesign>> CACHE = new HashMap<>();

    @Nullable
    public static CustomDesign get(String id) {
        if (id == null || id.isEmpty()) return null;
        return CACHE.computeIfAbsent(id, k -> {
            if (!k.matches("[a-z0-9_]+")) return Optional.empty();
            try (InputStream in = CustomDesigns.class.getResourceAsStream("/assets/elevmod/designs/" + k + ".json")) {
                if (in == null) return Optional.empty();
                return Optional.of(CustomDesign.parse(k, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()));
            } catch (Exception e) {
                ElevMod.LOG.error("[elevmod] custom design " + k + " failed to load", e);
                return Optional.empty();
            }
        }).orElse(null);
    }
}

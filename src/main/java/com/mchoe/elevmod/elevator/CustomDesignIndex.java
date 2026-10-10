package com.mchoe.elevmod.elevator;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mchoe.elevmod.ElevMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 커스텀 디자인 목록 (assets/elevmod/designs/index.json) – 서버/클라이언트 공용 */
public final class CustomDesignIndex {
    private CustomDesignIndex() {}

    private static List<String> ids;

    public static synchronized List<String> ids() {
        if (ids != null) return ids;
        List<String> out = new ArrayList<>();
        try (InputStream in = CustomDesignIndex.class.getResourceAsStream("/assets/elevmod/designs/index.json")) {
            if (in != null) {
                JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                a.forEach(e -> out.add(e.getAsString()));
            }
        } catch (Exception e) {
            ElevMod.LOG.error("[elevmod] design index load failed", e);
        }
        ids = Collections.unmodifiableList(out);
        return ids;
    }
}

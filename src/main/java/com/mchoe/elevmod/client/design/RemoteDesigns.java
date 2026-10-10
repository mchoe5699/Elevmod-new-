package com.mchoe.elevmod.client.design;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.network.DesignNet;
import com.mchoe.elevmod.network.Net;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 월드에 저장된 사용자 디자인 (클라이언트 캐시) – 처음 쓰일 때 서버에 요청해 받는다 */
public final class RemoteDesigns {
    private RemoteDesigns() {}

    /** id → {이름, 부위들} */
    public static final Map<String, String[]> INDEX = new LinkedHashMap<>();
    private static final Map<String, CustomDesign> LOADED = new HashMap<>();
    private static final Map<String, ByteArrayOutputStream> PARTIAL = new HashMap<>();
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static final Set<ResourceLocation> TEXTURES = new HashSet<>();

    public static void index(List<String[]> rows) {
        INDEX.clear();
        for (String[] r : rows) INDEX.put(r[0], new String[]{r[1], r[2]});
    }

    public static String name(String id) { String[] r = INDEX.get(id); return r == null ? id : r[0]; }

    @Nullable
    public static CustomDesign get(String id) {
        CustomDesign d = LOADED.get(id);
        if (d != null) return d;
        if (!INDEX.containsKey(id) || Minecraft.getInstance().getConnection() == null) return null;
        long now = System.currentTimeMillis();
        Long last = REQUESTED.get(id);
        if (last == null || now - last > 15000) {
            REQUESTED.put(id, now);
            Net.CH.sendToServer(new DesignNet.Request(id));
        }
        return null;
    }

    public static void invalidate(String id) {
        LOADED.remove(id);
        REQUESTED.remove(id);
    }

    public static void chunk(String id, int idx, int total, byte[] data) {
        ByteArrayOutputStream b = PARTIAL.computeIfAbsent(id, k -> new ByteArrayOutputStream());
        if (idx == 0) b.reset();
        b.writeBytes(data);
        if (idx < total - 1) return;
        PARTIAL.remove(id);
        try {
            install(id, b.toByteArray());
        } catch (Exception e) {
            ElevMod.LOG.error("[elevmod] design " + id + " install failed", e);
        }
    }

    /** zip 을 풀어 텍스처를 등록하고 디자인을 만든다 (스튜디오에서 만든 직후 미리보기에도 사용) */
    public static CustomDesign install(String id, byte[] zip) throws Exception {
        JsonObject json = null;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                byte[] data = z.readAllBytes();
                String n = e.getName();
                if (n.equals("design.json")) json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
                else if (n.endsWith(".png")) {
                    ResourceLocation rl = textureId(id, n.substring(0, n.length() - 4));
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
                    TEXTURES.add(rl);
                }
            }
        }
        if (json == null) throw new IllegalStateException("design.json missing");
        CustomDesign d = CustomDesign.parse(id, json);
        String[] r = INDEX.get(id);
        if (r != null) { d.nameKo = r[0]; d.nameEn = r[0]; }
        LOADED.put(id, d);
        return d;
    }

    public static ResourceLocation textureId(String design, String tex) {
        return new ResourceLocation(ElevMod.MODID, "dyn/" + design + "/" + tex);
    }

    /** 등록된 사진 텍스처인지 (아직 내려받는 중이면 false) */
    public static boolean ready(ResourceLocation rl) { return TEXTURES.contains(rl); }

    public static void clear() {
        LOADED.clear(); PARTIAL.clear(); REQUESTED.clear(); INDEX.clear();
    }
}

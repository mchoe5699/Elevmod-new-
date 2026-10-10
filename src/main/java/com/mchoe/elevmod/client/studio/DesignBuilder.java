package com.mchoe.elevmod.client.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 사진들 → 커스텀 디자인(design.json + 사진 텍스처) 묶음 */
public final class DesignBuilder {
    private DesignBuilder() {}

    public static byte[] build(String name, List<Photo> photos) throws Exception {
        JsonObject root = new JsonObject();
        JsonObject n = new JsonObject();
        n.addProperty("ko", name);
        n.addProperty("en", name);
        root.add("name", n);
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
            for (int i = 0; i < photos.size(); i++) {
                Photo p = photos.get(i);
                String tn = "p" + i;
                int max = p.target.panel ? 384 : p.target == Target.CAR_FLOOR || p.target == Target.CAR_CEILING ? 512 : 640;
                BufferedImage w = p.warped(max);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(w, "png", png);
                zip.putNextEntry(new ZipEntry(tn + ".png"));
                zip.write(png.toByteArray());
                zip.closeEntry();
                put(root, p, w, "~" + tn);
            }
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            zip.putNextEntry(new ZipEntry("design.json"));
            zip.write(g.toJson(root).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return zipBytes.toByteArray();
    }

    private static void put(JsonObject root, Photo p, BufferedImage w, String tex) {
        Target t = p.target;
        if (t.panel) {
            Analyzer.Result r = p.analysis();
            JsonObject o = new JsonObject();
            JsonObject face = new JsonObject();
            face.addProperty("tex", tex);
            o.add("face", face);
            JsonArray bs = new JsonArray();
            for (int i = 0; i < r.buttons.size(); i++) if (!p.excluded.contains(i)) { float[] b = r.buttons.get(i); bs.add(arr(b[0], b[1], b[2])); }
            o.add("buttons", bs);
            if (r.display != null) o.add("display", arr(r.display));
            JsonArray ls = new JsonArray();
            for (float[] l : r.lamps) ls.add(arr(l));
            o.add("lamps", ls);
            o.addProperty("type", p.dispType);
            o.addProperty("labels", p.labels);
            if (r.digit != 0) o.addProperty("digit", hex(r.digit));
            if (r.lit != 0) o.addProperty("lit", hex(r.lit));
            if (r.lcdBg != 0 && p.dispType.equals("lcd")) o.addProperty("lcdBg", hex(r.lcdBg));
            float h = p.height > 0 ? p.height : t.defHeight;
            o.add("size", arr(h * p.aspect(), h));
            root.add(t.key, o);
            return;
        }
        if (t == Target.CAR_FLOOR) { root.addProperty("floor", tex); return; }
        Analyzer.Result r = p.analysis();
        JsonObject s = new JsonObject();
        s.addProperty("tex", tex);
        JsonArray els = new JsonArray();
        float depth = 0.004f + 0.003f * p.strength;
        for (float x : r.seamX) els.add(el("groove", x - 0.004f, 0, x + 0.004f, 1, 0.004f, 0));
        for (float y : r.seamY) els.add(el("groove", 0, y - 0.004f, 1, y + 0.004f, 0.004f, 0));
        for (float[] pn : r.panels) els.add(el(pn[4] > 0 ? "raise" : "recess", pn[0], pn[1], pn[2], pn[3], depth, 0.012f));
        for (float[] c : r.curves) {
            JsonObject e = el("curve", c[0], c[1], c[2], c[3], Math.signum(c[5]) * (0.008f + 0.004f * p.strength), 0);
            e.addProperty("axis", c[4] == 0 ? "x" : "y");
            els.add(e);
        }
        s.add("elements", els);
        root.add(t.key, s);
        switch (t) {
            case CAR_WALL -> root.addProperty("panelWidth", 50);
            case CAR_FRONT -> root.addProperty("frontSpan", true);
            case CAR_DOOR -> root.addProperty("doorSpan", true);
            case LANDING_DOOR -> root.addProperty("landingSpan", true);
            case CAR_BACK -> root.addProperty("mirror", false);
            default -> {}
        }
    }

    private static JsonObject el(String shape, float x1, float y1, float x2, float y2, float depth, float bevel) {
        JsonObject e = new JsonObject();
        e.addProperty("shape", shape);
        e.add("x", arr(x1, x2));
        e.add("y", arr(y1, y2));
        e.addProperty("depth", depth);
        if (bevel > 0) e.addProperty("bevel", bevel);
        return e;
    }

    private static JsonArray arr(float... v) {
        JsonArray a = new JsonArray();
        for (float f : v) a.add(Math.round(f * 10000f) / 10000f);
        return a;
    }

    private static String hex(int c) { return String.format("#%06X", c & 0xFFFFFF); }
}

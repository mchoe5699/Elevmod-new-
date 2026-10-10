package com.mchoe.elevmod.elevator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 도트매트릭스 애니메이션 – 장면(프레임)마다 도트를 직접 켜고 끈 그림과 장면 시간(초).
 * 도트 값 0 = 꺼짐, 1~8 = 팔레트 색.
 */
public final class DotAnim {
    public static final int MAX_FRAMES = 64;

    public String id = "", name = "";
    public int cols = 5, rows = 7;
    public boolean loop = true;
    /** true = 재생하는 동안 표시기 전체(층 숫자 자리까지)를 덮음, false = 화살표 자리에만 */
    public boolean full = false;
    public int[] palette = {0xFFFF8A18, 0xFFFF2A10, 0xFF40FF60, 0xFF40A8FF, 0xFFFFFFFF, 0xFFFFE040, 0xFFFF40C0, 0xFF60FFFF};
    public final List<Frame> frames = new ArrayList<>();

    public static final class Frame {
        public float sec = 0.15f;
        public byte[] dots;

        public Frame(int n) { dots = new byte[n]; }

        public Frame copy() { Frame f = new Frame(dots.length); f.sec = sec; System.arraycopy(dots, 0, f.dots, 0, dots.length); return f; }
    }

    public int get(Frame f, int x, int y) { return x < 0 || y < 0 || x >= cols || y >= rows ? 0 : f.dots[y * cols + x]; }

    public float totalSec() { float t = 0; for (Frame f : frames) t += Math.max(0.02f, f.sec); return t; }

    /** 시작 후 t초에 보일 장면 */
    public Frame frameAt(float t) {
        if (frames.isEmpty()) return null;
        float tot = totalSec();
        if (loop) t = t % tot;
        else if (t >= tot) return frames.get(frames.size() - 1);
        for (Frame f : frames) { t -= Math.max(0.02f, f.sec); if (t < 0) return f; }
        return frames.get(frames.size() - 1);
    }

    /** 크기 변경 (왼쪽 위 기준으로 보존) */
    public void resize(int nc, int nr) {
        for (Frame f : frames) {
            byte[] d = new byte[nc * nr];
            for (int y = 0; y < Math.min(rows, nr); y++) for (int x = 0; x < Math.min(cols, nc); x++) d[y * nc + x] = f.dots[y * cols + x];
            f.dots = d;
        }
        cols = nc; rows = nr;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("cols", cols);
        o.addProperty("rows", rows);
        o.addProperty("loop", loop);
        o.addProperty("full", full);
        JsonArray pal = new JsonArray();
        for (int c : palette) pal.add(String.format("#%06X", c & 0xFFFFFF));
        o.add("palette", pal);
        JsonArray fr = new JsonArray();
        for (Frame f : frames) {
            JsonObject j = new JsonObject();
            j.addProperty("t", f.sec);
            StringBuilder sb = new StringBuilder();
            for (byte b : f.dots) sb.append((char) ('0' + b));
            j.addProperty("d", sb.toString());
            fr.add(j);
        }
        o.add("frames", fr);
        return o;
    }

    public static DotAnim fromJson(JsonObject o) {
        DotAnim a = new DotAnim();
        a.id = o.has("id") ? o.get("id").getAsString() : "";
        a.name = o.has("name") ? o.get("name").getAsString() : a.id;
        a.cols = Math.max(1, Math.min(32, o.has("cols") ? o.get("cols").getAsInt() : 5));
        a.rows = Math.max(1, Math.min(32, o.has("rows") ? o.get("rows").getAsInt() : 7));
        a.loop = !o.has("loop") || o.get("loop").getAsBoolean();
        a.full = o.has("full") && o.get("full").getAsBoolean();
        if (o.has("palette")) {
            JsonArray p = o.getAsJsonArray("palette");
            for (int i = 0; i < Math.min(8, p.size()); i++) a.palette[i] = IndicatorProfile.color(p.get(i).getAsString());
        }
        if (o.has("frames")) for (JsonElement e : o.getAsJsonArray("frames")) {
            if (a.frames.size() >= MAX_FRAMES) break;
            JsonObject j = e.getAsJsonObject();
            Frame f = new Frame(a.cols * a.rows);
            f.sec = j.has("t") ? Math.max(0.02f, Math.min(60f, j.get("t").getAsFloat())) : 0.15f;
            String d = j.has("d") ? j.get("d").getAsString() : "";
            for (int i = 0; i < Math.min(d.length(), f.dots.length); i++) f.dots[i] = (byte) Math.max(0, Math.min(8, d.charAt(i) - '0'));
            a.frames.add(f);
        }
        if (a.frames.isEmpty()) a.frames.add(new Frame(a.cols * a.rows));
        return a;
    }
}

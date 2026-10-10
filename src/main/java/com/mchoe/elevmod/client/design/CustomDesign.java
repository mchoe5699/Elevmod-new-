package com.mchoe.elevmod.client.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 커스텀 디자인 – assets/elevmod/designs/&lt;id&gt;.json.
 * 각 면(벽 판넬, 문짝, 천장, 운전반, 트랜섬)을 '바탕 + 요소' 로 기술한다.
 * 요소는 볼록(raise), 오목(recess), 홈(groove), 곡면(curve: +볼록 / -오목), 평면(flat), 봉(rod), 점 패턴(dots).
 * 좌표: x/y = 면 크기에 대한 비율(0~1), xm/ym = 왼쪽/아래에서 m, xr/yt = 오른쪽/위에서 m, xi/yi = [왼/아래 여백 m, 오른/위 여백 m].
 */
public final class CustomDesign {
    public final String id;
    public String nameKo = "", nameEn = "";
    @Nullable public Surface wall, door, landingDoor, ceiling, cop, transom, frontWall;
    @Nullable public String floorTex, jambTex, sillTex, kickTex;
    public float panelWidth = 0;          // 벽 판넬 폭(m), 0 = 기본(약 0.72m)
    public boolean mirror = true;         // 후면 거울 표시 여부
    @Nullable public Rail handrail;

    public CustomDesign(String id) { this.id = id; }

    public String name() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return lang.startsWith("ko") ? (nameKo.isEmpty() ? id : nameKo) : (nameEn.isEmpty() ? nameKo : nameEn);
    }

    public static final class Surface {
        public String tex = "panel_stainless";
        public int color = 0xFFFFFFFF;
        public boolean tile;           // true = 1m 반복, false = 면 전체에 늘여 붙임
        public final List<El> els = new ArrayList<>();

        /** 볼록 요소의 최대 높이 (앞에 붙는 운전반·표시기를 그만큼 띄움) */
        public float maxRaise() {
            float m = 0;
            for (El e : els) {
                switch (e.shape) {
                    case "raise", "dots" -> m = Math.max(m, Math.abs(e.depth) + (Float.isNaN(e.z) ? 0 : e.z));
                    case "curve" -> { if (e.depth > 0) m = Math.max(m, e.depth); }
                    case "rod" -> m = Math.max(m, e.radius * 2 + (Float.isNaN(e.z) ? 0 : e.z));
                    case "flat" -> m = Math.max(m, Float.isNaN(e.z) ? 0.0012f : e.z);
                    default -> {}
                }
            }
            return m;
        }

        /** 오목 요소의 최대 깊이 (바탕을 그만큼 앞으로 내려야 할 때) */
        public float maxRecess() {
            float m = 0;
            for (El e : els) if (e.hole()) m = Math.max(m, Math.abs(e.depth));
            return m;
        }
    }

    public static final class El {
        public String shape = "flat";
        public float[] x = {0, 1}, y = {0, 1};
        public String xMode = "f", yMode = "f";  // f 비율, m 왼쪽/아래 기준 m, r 오른쪽/위 기준 m
        public float depth = 0.01f, bevel = 0, z = Float.NaN, radius = 0.015f;
        public char axis = 'x';
        @Nullable public String tex;
        public int color = 0xFFFFFFFF;
        public boolean glow, ownUv;
        public int rows = 1, cols = 1;
        public float gap = 0.01f;

        public boolean hole() {
            return shape.equals("recess") || shape.equals("groove") || (shape.equals("curve") && depth < 0);
        }
    }

    public static final class Rail {
        public String shape = "round"; // round / flat / none
        public float y = 0.9f, r = 0.018f;
        public int color = 0xFFD8DCE0;
        public boolean back = true, sides = true;
    }

    // ---------------------------------------------------------------- 파싱
    static int col(JsonObject o, String k, int def) {
        if (!o.has(k)) return def;
        String s = o.get(k).getAsString().replace("#", "");
        long v = Long.parseLong(s, 16);
        return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    static float[] range(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    static Surface surface(JsonObject o) {
        Surface s = new Surface();
        if (o.has("tex")) s.tex = o.get("tex").getAsString();
        s.color = col(o, "color", 0xFFFFFFFF);
        s.tile = o.has("tile") && o.get("tile").getAsBoolean();
        if (o.has("elements")) for (JsonElement je : o.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            El el = new El();
            el.shape = e.has("shape") ? e.get("shape").getAsString().toLowerCase(Locale.ROOT) : "flat";
            if (e.has("x")) el.x = range(e.get("x"));
            if (e.has("xm")) { el.x = range(e.get("xm")); el.xMode = "m"; }
            if (e.has("xr")) { el.x = range(e.get("xr")); el.xMode = "r"; }
            if (e.has("xi")) { el.x = range(e.get("xi")); el.xMode = "i"; }
            if (e.has("y")) el.y = range(e.get("y"));
            if (e.has("ym")) { el.y = range(e.get("ym")); el.yMode = "m"; }
            if (e.has("yt")) { el.y = range(e.get("yt")); el.yMode = "r"; }
            if (e.has("yi")) { el.y = range(e.get("yi")); el.yMode = "i"; }
            if (e.has("depth")) el.depth = e.get("depth").getAsFloat();
            if (e.has("bevel")) el.bevel = e.get("bevel").getAsFloat();
            if (e.has("z")) el.z = e.get("z").getAsFloat();
            if (e.has("radius")) el.radius = e.get("radius").getAsFloat();
            if (e.has("axis")) el.axis = e.get("axis").getAsString().charAt(0);
            if (e.has("tex")) el.tex = e.get("tex").getAsString();
            el.color = col(e, "color", 0xFFFFFFFF);
            el.glow = e.has("glow") && e.get("glow").getAsBoolean();
            el.ownUv = e.has("uv") && e.get("uv").getAsString().equals("own");
            if (e.has("rows")) el.rows = Math.max(1, e.get("rows").getAsInt());
            if (e.has("cols")) el.cols = Math.max(1, e.get("cols").getAsInt());
            if (e.has("gap")) el.gap = e.get("gap").getAsFloat();
            s.els.add(el);
        }
        return s;
    }

    static CustomDesign parse(String id, JsonObject o) {
        CustomDesign d = new CustomDesign(id);
        if (o.has("name")) {
            JsonObject n = o.getAsJsonObject("name");
            if (n.has("ko")) d.nameKo = n.get("ko").getAsString();
            if (n.has("en")) d.nameEn = n.get("en").getAsString();
        }
        if (o.has("wall")) d.wall = surface(o.getAsJsonObject("wall"));
        if (o.has("frontWall")) d.frontWall = surface(o.getAsJsonObject("frontWall"));
        if (o.has("door")) d.door = surface(o.getAsJsonObject("door"));
        if (o.has("landingDoor")) d.landingDoor = surface(o.getAsJsonObject("landingDoor"));
        if (o.has("ceiling")) d.ceiling = surface(o.getAsJsonObject("ceiling"));
        if (o.has("cop")) d.cop = surface(o.getAsJsonObject("cop"));
        if (o.has("transom")) d.transom = surface(o.getAsJsonObject("transom"));
        if (o.has("floor")) d.floorTex = o.get("floor").getAsString();
        if (o.has("jamb")) d.jambTex = o.get("jamb").getAsString();
        if (o.has("sill")) d.sillTex = o.get("sill").getAsString();
        if (o.has("kick")) d.kickTex = o.get("kick").getAsString();
        if (o.has("panelWidth")) d.panelWidth = o.get("panelWidth").getAsFloat();
        if (o.has("mirror")) d.mirror = o.get("mirror").getAsBoolean();
        if (o.has("handrail")) {
            JsonObject h = o.getAsJsonObject("handrail");
            Rail r = new Rail();
            if (h.has("shape")) r.shape = h.get("shape").getAsString();
            if (h.has("y")) r.y = h.get("y").getAsFloat();
            if (h.has("r")) r.r = h.get("r").getAsFloat();
            r.color = col(h, "color", r.color);
            if (h.has("back")) r.back = h.get("back").getAsBoolean();
            if (h.has("sides")) r.sides = h.get("sides").getAsBoolean();
            d.handrail = r;
        }
        return d;
    }
}

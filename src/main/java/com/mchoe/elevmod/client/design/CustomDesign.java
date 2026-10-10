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
    @Nullable public Surface wall, backWall, door, landingDoor, ceiling, cop, transom, frontWall, jambFace;
    @Nullable public String floorTex, jambTex, sillTex, kickTex;
    /** 문 사진이 출입구 전체(닫힌 문짝 모두)에 걸쳐 있음 → 문짝마다 자기 위치의 사진 조각을 씀 */
    public boolean doorSpan, landingSpan, frontSpan;
    /** 운전반·표시기·버튼 패널 부품 */
    @Nullable public PanelPart copPart, accPart, indicator, hallButton, hallPanel, lantern;
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

    /**
     * 판(운전반, 층표시기, 호출버튼, 호출패널, 홀랜턴): 앞면(face) + 실제로 동작하는 부품 위치.
     * 좌표는 판에 대한 비율, y 는 아래 → 위.
     */
    public static final class PanelPart {
        @Nullable public Surface face;
        /** 버튼: {중심x, 중심y, 반지름(판 폭 비율)} – 위→아래, 왼→오른 순 */
        public final List<float[]> buttons = new ArrayList<>();
        /** 표시창 {x1,y1,x2,y2} */
        @Nullable public float[] display;
        /** 홀랜턴 램프 {x1,y1,x2,y2} – 0 = 상승, 1 = 하강 */
        public final List<float[]> lamps = new ArrayList<>();
        public String dispType = "seg";   // seg / dot / lcd
        public int digit = 0xFFFF2A10, lit = 0xFFFFB020, lcdBg = 0xFF0E2E66;
        /** 실제 크기(m) {폭, 높이}, 0 = 기본 */
        public float w, h;
        /** 사진에 이미 층 숫자가 있어도 모드의 층 이름을 버튼 위에 표시할지 */
        public boolean labels = true;
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

    /** "~이름" = 이 디자인에 들어 있는 사진 텍스처 */
    private String tx(String t) { return t.startsWith("~") ? "@" + id + "/" + t.substring(1) : t; }

    Surface surface(JsonObject o) {
        Surface s = new Surface();
        if (o.has("tex")) s.tex = tx(o.get("tex").getAsString());
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
            if (e.has("tex")) el.tex = tx(e.get("tex").getAsString());
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

    PanelPart panel(JsonObject o) {
        PanelPart p = new PanelPart();
        if (o.has("face")) p.face = surface(o.getAsJsonObject("face"));
        if (o.has("buttons")) for (JsonElement e : o.getAsJsonArray("buttons")) {
            JsonArray a = e.getAsJsonArray();
            p.buttons.add(new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.size() > 2 ? a.get(2).getAsFloat() : 0.08f});
        }
        if (o.has("lamps")) for (JsonElement e : o.getAsJsonArray("lamps")) p.lamps.add(rect4(e));
        if (o.has("display")) p.display = rect4(o.get("display"));
        if (o.has("type")) p.dispType = o.get("type").getAsString();
        p.digit = col(o, "digit", p.digit);
        p.lit = col(o, "lit", p.lit);
        p.lcdBg = col(o, "lcdBg", p.lcdBg);
        if (o.has("size")) { float[] r = range(o.get("size")); p.w = r[0]; p.h = r[1]; }
        if (o.has("labels")) p.labels = o.get("labels").getAsBoolean();
        return p;
    }

    static float[] rect4(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()};
    }

    /** 이 디자인이 해당 부위를 정의하는지 (부위 목록: car, carDoor, landing, cop, copAcc, indicator, hallButton, hallPanel, lantern) */
    public boolean has(String part) {
        return switch (part) {
            case "car" -> wall != null || ceiling != null || frontWall != null || floorTex != null || backWall != null;
            case "carDoor" -> door != null;
            case "landing" -> landingDoor != null || door != null || jambTex != null || jambFace != null;
            case "cop" -> copPart != null || cop != null;
            case "copAcc" -> accPart != null;
            case "indicator" -> indicator != null;
            case "hallButton" -> hallButton != null;
            case "hallPanel" -> hallPanel != null;
            case "lantern" -> lantern != null;
            default -> false;
        };
    }

    public static CustomDesign parse(String id, JsonObject o) {
        CustomDesign d = new CustomDesign(id);
        if (o.has("name")) {
            JsonObject n = o.getAsJsonObject("name");
            if (n.has("ko")) d.nameKo = n.get("ko").getAsString();
            if (n.has("en")) d.nameEn = n.get("en").getAsString();
        }
        if (o.has("wall")) d.wall = d.surface(o.getAsJsonObject("wall"));
        if (o.has("frontWall")) d.frontWall = d.surface(o.getAsJsonObject("frontWall"));
        if (o.has("door")) d.door = d.surface(o.getAsJsonObject("door"));
        if (o.has("landingDoor")) d.landingDoor = d.surface(o.getAsJsonObject("landingDoor"));
        if (o.has("ceiling")) d.ceiling = d.surface(o.getAsJsonObject("ceiling"));
        if (o.has("cop")) d.cop = d.surface(o.getAsJsonObject("cop"));
        if (o.has("transom")) d.transom = d.surface(o.getAsJsonObject("transom"));
        if (o.has("backWall")) d.backWall = d.surface(o.getAsJsonObject("backWall"));
        if (o.has("jambFace")) d.jambFace = d.surface(o.getAsJsonObject("jambFace"));
        if (o.has("floor")) d.floorTex = d.tx(o.get("floor").getAsString());
        if (o.has("jamb")) d.jambTex = d.tx(o.get("jamb").getAsString());
        if (o.has("sill")) d.sillTex = d.tx(o.get("sill").getAsString());
        if (o.has("kick")) d.kickTex = d.tx(o.get("kick").getAsString());
        d.doorSpan = o.has("doorSpan") && o.get("doorSpan").getAsBoolean();
        d.landingSpan = o.has("landingSpan") && o.get("landingSpan").getAsBoolean();
        d.frontSpan = o.has("frontSpan") && o.get("frontSpan").getAsBoolean();
        if (o.has("copPanel")) d.copPart = d.panel(o.getAsJsonObject("copPanel"));
        if (o.has("copAcc")) d.accPart = d.panel(o.getAsJsonObject("copAcc"));
        if (o.has("indicator")) d.indicator = d.panel(o.getAsJsonObject("indicator"));
        if (o.has("hallButton")) d.hallButton = d.panel(o.getAsJsonObject("hallButton"));
        if (o.has("hallPanel")) d.hallPanel = d.panel(o.getAsJsonObject("hallPanel"));
        if (o.has("lantern")) d.lantern = d.panel(o.getAsJsonObject("lantern"));
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

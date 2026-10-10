package com.mchoe.elevmod.client.indicator;

import com.mchoe.elevmod.client.render.Glyphs;
import com.mchoe.elevmod.client.render.RenderUtil;
import com.mchoe.elevmod.elevator.DotAnim;
import com.mchoe.elevmod.elevator.IndicatorProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 층표시기 그리기 – 소자(7/9/14/16세그, 도트매트릭스, LCD), 층 전환 효과(스크롤·페이드·깜빡임·닦아내기),
 * 상승/하강/정지 도트 애니메이션, 색 설정을 모두 처리한다.
 */
public final class IndicatorDraw {
    private IndicatorDraw() {}

    public static final IndicatorProfile DEFAULT = new IndicatorProfile();

    /** 표시기마다 전환 상태 */
    private static final class St {
        String cur, prev = "";
        float changeT = -99, dirT = 0;
        int lastDir, moveDir;
        long seen;
    }

    private static final Map<Object, St> STATES = new HashMap<>();

    private static float now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (mc.level.getGameTime() + mc.getFrameTime()) / 20f;
    }

    private static St state(@Nullable Object key, String text, int dir, float t) {
        if (key == null) return null;
        St s = STATES.get(key);
        if (s == null) {
            if (STATES.size() > 2000) STATES.clear();
            s = new St();
            s.cur = text;
            s.lastDir = dir;
            STATES.put(key, s);
        }
        if (!text.equals(s.cur)) { s.prev = s.cur; s.cur = text; s.changeT = t; s.moveDir = dir != 0 ? dir : s.lastDir; }
        if (dir != s.lastDir) { s.lastDir = dir; s.dirT = t; }
        return s;
    }

    /**
     * kind: "seg", "dot", "lcd". base = 장치/의장의 기본 {켜진색, 바탕색} (프로필 색이 0 이면 사용).
     * key = 전환 효과 상태를 기억할 대상(같은 표시기는 같은 key), null 이면 효과 없음.
     */
    public static void draw(PoseStack ps, MultiBufferSource buf, @Nullable IndicatorProfile prof, String kind, int baseOn, int baseBg,
                            float x1, float y1, float x2, float y2, float z, int light, String text, int dir, boolean moving, @Nullable Object key) {
        IndicatorProfile p = prof == null ? DEFAULT : prof;
        float t = now();
        St st = state(key, text, dir, t);
        float w = x2 - x1, h = y2 - y1;
        int on, off, bg;
        switch (kind) {
            case "dot" -> { on = IndicatorProfile.or(p.dotOn, baseOn); bg = IndicatorProfile.or(p.dotBg, baseBg); off = IndicatorProfile.or(p.dotOff, Glyphs.dim(on, 0.1f)); }
            case "lcd" -> { on = IndicatorProfile.or(p.lcdFg, 0xFFFFFFFF); bg = IndicatorProfile.or(p.lcdBg, baseBg); off = IndicatorProfile.or(p.lcdArrow, baseOn); }
            default -> { on = IndicatorProfile.or(p.segOn, baseOn); bg = IndicatorProfile.or(p.segBg, baseBg); off = IndicatorProfile.or(p.segOff, Glyphs.dim(on, 0.13f)); }
        }
        Glyphs.rect(ps, buf, x1, y1, x2, y2, z, RenderUtil.FULL, bg);
        float zz = z + 0.0004f;
        // 애니메이션 고르기
        String animId = dir > 0 ? p.animUp : dir < 0 ? p.animDown : p.animIdle;
        DotAnim anim = AnimLibrary.get(animId);
        float animT = st == null ? t : t - st.dirT;
        if (anim != null && anim.full) {
            // 표시기 전체를 덮는 애니메이션
            drawAnim(ps, buf, anim, animT, x1 + w * 0.04f, y1 + h * 0.08f, x2 - w * 0.04f, y2 - h * 0.08f, zz, light, p.dotRound, kind.equals("dot") ? off : Glyphs.dim(on, 0.1f));
            return;
        }
        // 화살표 칸 / 숫자 칸
        float ax2 = x1 + w * 0.3f;
        float[] num = {ax2, y1 + h * 0.08f, x2 - w * 0.03f, y2 - h * 0.08f};
        if (anim != null) drawAnim(ps, buf, anim, animT, x1 + w * 0.04f, y1 + h * 0.1f, ax2 - w * 0.02f, y2 - h * 0.1f, zz, light, p.dotRound, Glyphs.dim(anim.palette[0], 0.1f));
        else builtinArrow(ps, buf, p, kind, x1, y1, ax2, y2, zz, light, on, off, dir, moving, t);
        // 층 숫자 (+ 전환 효과)
        float prog = st == null || p.trans == 0 ? 1f : Math.max(0, Math.min(1, (t - st.changeT) / Math.max(0.05f, p.transSec)));
        if (prog >= 1 || st == null || st.prev.isEmpty()) { number(ps, buf, p, kind, text, num, 0, 0, null, zz, light, on, off, 1f); return; }
        int d = p.transDir == 0 ? (st.moveDir >= 0 ? 1 : 2) : p.transDir; // 1 위로 2 아래로 3 왼쪽 4 오른쪽
        String type = IndicatorProfile.TRANS[p.trans];
        if (type.equals("scroll") && !kind.equals("lcd")) {
            float nw = num[2] - num[0], nh = num[3] - num[1];
            float sx = d == 3 ? -1 : d == 4 ? 1 : 0, sy = d == 1 ? 1 : d == 2 ? -1 : 0;
            if (kind.equals("dot")) {
                // 도트는 한 줄(칸)씩 밀림
                number(ps, buf, p, kind, st.prev, num, sx * prog, sy * prog, num, zz, light, on, off, 1f);
                number(ps, buf, p, kind, text, num, sx * (prog - 1), sy * (prog - 1), num, zz, light, on, off, 1f);
            } else {
                number(ps, buf, p, kind, st.prev, num, sx * prog, sy * prog, num, zz, light, on, off, 1f);
                number(ps, buf, p, kind, text, num, sx * (prog - 1), sy * (prog - 1), num, zz, light, on, off, 1f);
            }
        } else if (type.equals("blink")) {
            boolean vis = ((int) (prog * 6)) % 2 == 1 || prog >= 1;
            if (vis) number(ps, buf, p, kind, text, num, 0, 0, null, zz, light, on, off, 1f);
            else number(ps, buf, p, kind, text, num, 0, 0, null, zz, light, off, off, 1f);
        } else if (type.equals("wipe") && !kind.equals("lcd")) {
            float nw = num[2] - num[0], nh = num[3] - num[1];
            float[] newClip, oldClip;
            switch (d) {
                case 1 -> { newClip = new float[]{num[0], num[1], num[2], num[1] + nh * prog}; oldClip = new float[]{num[0], num[1] + nh * prog, num[2], num[3]}; }
                case 2 -> { newClip = new float[]{num[0], num[3] - nh * prog, num[2], num[3]}; oldClip = new float[]{num[0], num[1], num[2], num[3] - nh * prog}; }
                case 3 -> { newClip = new float[]{num[2] - nw * prog, num[1], num[2], num[3]}; oldClip = new float[]{num[0], num[1], num[2] - nw * prog, num[3]}; }
                default -> { newClip = new float[]{num[0], num[1], num[0] + nw * prog, num[3]}; oldClip = new float[]{num[0] + nw * prog, num[1], num[2], num[3]}; }
            }
            number(ps, buf, p, kind, st.prev, num, 0, 0, oldClip, zz, light, on, off, 1f);
            number(ps, buf, p, kind, text, num, 0, 0, newClip, zz, light, on, off, 1f);
        } else {
            // 페이드 (LCD 의 스크롤/닦아내기도 페이드로)
            if (prog < 0.5f) number(ps, buf, p, kind, st.prev, num, 0, 0, null, zz, light, mix(on, off, prog * 2), off, 1 - prog * 2);
            else number(ps, buf, p, kind, text, num, 0, 0, null, zz, light, mix(off, on, prog * 2 - 1), off, prog * 2 - 1);
        }
    }

    static int mix(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** 층 숫자. (ox, oy) = 칸 크기 비율만큼 밀어서 그림, clip 사각형 밖은 그리지 않음 */
    private static void number(PoseStack ps, MultiBufferSource buf, IndicatorProfile p, String kind, String text, float[] r,
                               float ox, float oy, @Nullable float[] clip, float z, int light, int on, int off, float alpha) {
        float w = r[2] - r[0], h = r[3] - r[1], cx = (r[0] + r[2]) / 2 + ox * w, cy = (r[1] + r[3]) / 2 + oy * h;
        switch (kind) {
            case "dot" -> dotText(ps, buf, p, text, r, ox, oy, clip, z, light, on, off);
            case "lcd" -> {
                int a = Math.max(8, (int) (alpha * 255));
                RenderUtil.text(ps, buf, text, cx, cy, z + 0.0006f, h * 0.8f, (a << 24) | (on & 0xFFFFFF));
            }
            default -> {
                String t = text.length() >= 2 ? text : " " + text;
                float cw = Segments.width(p.segType), gh = Math.min(h * 0.9f, w / (t.length() * cw + (t.length() - 1) * 0.2f));
                float[] cl = clip != null ? clip : r;
                Segments.text(ps, buf, t, p.segType, p.segShape, p.segSlant, p.segThick, cx, cy, gh, z, light, on, off, cl, p.segLead1);
            }
        }
    }

    /** 도트 글자 (설정한 도트 크기로 5×7 글꼴을 늘려 찍음) */
    private static void dotText(PoseStack ps, MultiBufferSource buf, IndicatorProfile p, String text, float[] r, float ox, float oy,
                                @Nullable float[] clip, float z, int light, int on, int off) {
        if (text.isEmpty()) text = " ";
        int cols = p.dotCols, rows = p.dotRows, n = text.length();
        int total = n * cols + (n - 1);
        float w = r[2] - r[0], h = r[3] - r[1];
        float pitch = Math.min(w / total, h / rows);
        float gx = (r[0] + r[2]) / 2 - total * pitch / 2, gy = (r[1] + r[3]) / 2 + rows * pitch / 2;
        // 칸 단위로 밀기 (실제 도트는 한 칸씩 움직임)
        int sx = Math.round(ox * total), sy = Math.round(oy * rows);
        for (int i = 0; i < n; i++) {
            String[] g = Glyphs.dotGlyph(text.charAt(i));
            for (int yy = 0; yy < rows; yy++) for (int xx = 0; xx < cols; xx++) {
                int fx = xx * 5 / cols, fy = yy * 7 / rows;
                boolean lit = g[fy].charAt(fx) == '#';
                int gxi = i * (cols + 1) + xx + sx, gyi = yy - sy;
                if (gxi < 0 || gxi >= total || gyi < 0 || gyi >= rows) continue;
                float cx = gx + gxi * pitch + pitch / 2, cy = gy - gyi * pitch - pitch / 2;
                dot(ps, buf, cx, cy, pitch * 0.4f, z, lit, light, on, off, p.dotRound, clip);
            }
        }
    }

    static void dot(PoseStack ps, MultiBufferSource buf, float cx, float cy, float r, float z, boolean lit, int light, int on, int off, boolean round, @Nullable float[] clip) {
        if (clip != null && (cx < clip[0] || cx > clip[2] || cy < clip[1] || cy > clip[3])) return;
        int lt = lit ? RenderUtil.FULL : light;
        int c = lit ? on : off;
        float zz = z + (lit ? 0.0006f : 0);
        if (round) {
            float[] poly = new float[16];
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8 + Math.PI / 8;
                poly[k * 2] = cx + (float) Math.cos(a) * r;
                poly[k * 2 + 1] = cy + (float) Math.sin(a) * r;
            }
            Segments.fan(ps, buf, poly, zz, lt, c);
        } else Glyphs.rect(ps, buf, cx - r, cy - r, cx + r, cy + r, zz, lt, c);
    }

    /** 도트 애니메이션 그리기 – 영역에 맞게 도트 크기 결정 */
    public static void drawAnim(PoseStack ps, MultiBufferSource buf, DotAnim a, float t, float x1, float y1, float x2, float y2, float z, int light, boolean round, int offColor) {
        DotAnim.Frame f = a.frameAt(Math.max(0, t));
        if (f == null) return;
        float pitch = Math.min((x2 - x1) / a.cols, (y2 - y1) / a.rows);
        float gx = (x1 + x2) / 2 - a.cols * pitch / 2, gy = (y1 + y2) / 2 + a.rows * pitch / 2;
        for (int y = 0; y < a.rows; y++) for (int x = 0; x < a.cols; x++) {
            int v = a.get(f, x, y);
            dot(ps, buf, gx + x * pitch + pitch / 2, gy - y * pitch - pitch / 2, pitch * 0.4f, z, v > 0, light, v > 0 ? a.palette[v - 1] : 0, offColor, round, null);
        }
    }

    /** 기본 화살표 (애니메이션을 지정하지 않았을 때) */
    private static void builtinArrow(PoseStack ps, MultiBufferSource buf, IndicatorProfile p, String kind, float x1, float y1, float x2, float y2,
                                     float z, int light, int on, int off, int dir, boolean moving, float t) {
        float w = x2 - x1, h = y2 - y1, cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
        switch (kind) {
            case "dot" -> {
                if (dir == 0) return;
                int cols = p.dotCols, rows = p.dotRows;
                float pitch = Math.min(w * 0.85f / cols, h * 0.84f / rows);
                float gx = cx - cols * pitch / 2, gy = cy + rows * pitch / 2;
                int shift = moving ? (int) (t * 9) % rows : 0;
                String[] g = Glyphs.dotGlyph(dir > 0 ? '^' : 'v');
                for (int yy = 0; yy < rows; yy++) for (int xx = 0; xx < cols; xx++) {
                    int src = Math.floorMod(yy + (dir > 0 ? shift : -shift), rows);
                    boolean lit = g[src * 7 / rows].charAt(xx * 5 / cols) == '#';
                    dot(ps, buf, gx + xx * pitch + pitch / 2, gy - yy * pitch - pitch / 2, pitch * 0.4f, z, lit, light, on, off, p.dotRound, null);
                }
            }
            case "lcd" -> RenderUtil.text(ps, buf, RenderUtil.arrow(dir), cx, cy, z + 0.0006f, h * 0.55f, off);
            default -> {
                float s = Math.min(w * 0.6f, h * 0.36f);
                Glyphs.arrow(ps, buf, cx, cy + h * 0.2f, s, 1, z, dir > 0 ? RenderUtil.FULL : light, dir > 0 ? on : off);
                Glyphs.arrow(ps, buf, cx, cy - h * 0.2f, s, -1, z, dir < 0 ? RenderUtil.FULL : light, dir < 0 ? on : off);
            }
        }
    }
}

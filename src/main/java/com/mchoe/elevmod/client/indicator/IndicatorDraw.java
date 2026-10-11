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
        draw(ps, buf, prof, kind, baseOn, baseBg, x1, y1, x2, y2, z, light, text, dir, moving, false, key);
    }

    /** open = 카 문이 열리는 중·열려 있음 → 문열림 애니메이션 */
    public static void draw(PoseStack ps, MultiBufferSource buf, @Nullable IndicatorProfile prof, String kind, int baseOn, int baseBg,
                            float x1, float y1, float x2, float y2, float z, int light, String text, int dir, boolean moving, boolean open, @Nullable Object key) {
        IndicatorProfile p = prof == null ? DEFAULT : prof;
        float t = now();
        St st = state(key, text, dir, t);
        float w = x2 - x1, h = y2 - y1;
        // 소자 조합: 숫자·화살표를 각각 다른 소자(도트/세그먼트/LCD)로
        String nk = p.numKind == 0 ? kind : IndicatorProfile.ELEM[p.numKind];
        String ak = p.arrowKind == 0 ? kind : IndicatorProfile.ELEM[p.arrowKind];
        int[] cb = colors(p, kind, kind, baseOn, baseBg), cn = colors(p, nk, kind, baseOn, baseBg), ca = colors(p, ak, kind, baseOn, baseBg);
        int bg = cb[2];
        Glyphs.rect(ps, buf, x1, y1, x2, y2, z, RenderUtil.FULL, bg);
        float zz = z + 0.0004f;
        // 애니메이션 고르기
        // 화살표 애니메이션은 '상승' 하나만 만들고, 하강은 180도 돌려서 거꾸로 움직이게 함
        // 문이 열리는 중·열려 있으면 '문열림' 애니메이션 (지정했을 때)
        DotAnim openAnim = open && !moving && dir != 0 ? AnimLibrary.get(p.animOpen) : null;
        if (openAnim != null && openAnim.isFont()) openAnim = null;
        String animId = dir != 0 ? p.animUp : p.animIdle;
        DotAnim anim = openAnim != null ? openAnim : AnimLibrary.get(animId);
        if (anim != null && anim.isFont()) anim = null;
        boolean rot = dir < 0;
        boolean still = openAnim == null && dir != 0 && !moving;   // 정차 중이지만 갈 방향이 있음 → 정지 장면
        float animT = st == null ? t : t - st.dirT;
        // 번갈아 표시 모드: 한 칸에서 화살표 ↔ 숫자
        if (p.altMode) {
            alternate(ps, buf, p, nk, ak, anim, animT, rot, still, x1, y1, x2, y2, zz, light, text, dir, moving, st, t, cn, ca);
            return;
        }
        if (anim != null && anim.full) {
            // 표시기 전체를 덮는 애니메이션
            drawAnim(ps, buf, anim, animT, x1 + w * 0.04f, y1 + h * 0.08f, x2 - w * 0.04f, y2 - h * 0.08f, zz, light, p.dotRound, ak.equals("dot") ? ca[1] : Glyphs.dim(ca[0], 0.1f), rot, still);
            return;
        }
        // 화살표 칸 / 숫자 칸 (가로 배치: 화살표 왼쪽, 세로 배치: 화살표 위)
        float[] num, arrowR, arrowBox;
        if (p.vertical) {
            float ay1 = y2 - h * p.arrowH / 100f;
            num = scaleRect(new float[]{x1 + w * 0.06f, y1 + h * 0.04f, x2 - w * 0.06f, ay1 - h * 0.02f}, p.numScale / 100f, w * p.numX / 100f, h * p.numY / 100f);
            arrowR = scaleRect(new float[]{x1 + w * 0.1f, ay1 + h * 0.02f, x2 - w * 0.1f, y2 - h * 0.04f}, p.arrowScale / 100f, 0, 0);
            arrowBox = scaleRect(new float[]{x1, ay1, x2, y2}, p.arrowScale / 100f, 0, 0);
        } else {
            float ax2 = x1 + w * p.arrowW / 100f;
            num = scaleRect(new float[]{ax2, y1 + h * 0.08f, x2 - w * 0.03f, y2 - h * 0.08f}, p.numScale / 100f, w * p.numX / 100f, h * p.numY / 100f);
            arrowR = scaleRect(new float[]{x1 + w * 0.04f, y1 + h * 0.1f, ax2 - w * 0.02f, y2 - h * 0.1f}, p.arrowScale / 100f, 0, 0);
            arrowBox = scaleRect(new float[]{x1, y1, ax2, y2}, p.arrowScale / 100f, 0, 0);
        }
        boolean hasArrow = p.vertical ? p.arrowH > 0 : p.arrowW > 0;
        if (hasArrow) {
            if (anim != null) drawAnim(ps, buf, anim, animT, arrowR[0], arrowR[1], arrowR[2], arrowR[3], zz, light, p.dotRound, Glyphs.dim(anim.palette[0], 0.1f), rot, still);
            else builtinArrow(ps, buf, p, ak, arrowBox[0], arrowBox[1], arrowBox[2], arrowBox[3], zz, light, ca[0], ca[1], dir, moving, t);
        }
        numberWithTransition(ps, buf, p, nk, text, num, zz, light, cn[0], cn[1], st, t);
    }

    /** 소자별 {켜진색, 꺼진색, 바탕색}. 표시기 기본 소자와 다른 소자면 그 소자의 기본색 */
    private static int[] colors(IndicatorProfile p, String k, String deviceKind, int baseOn, int baseBg) {
        int defOn = k.equals(deviceKind) ? baseOn : switch (k) { case "dot" -> 0xFFFF8A18; case "lcd" -> 0xFF7FE3FF; default -> 0xFFFF2A10; };
        return switch (k) {
            case "dot" -> { int on = IndicatorProfile.or(p.dotOn, defOn); yield new int[]{on, IndicatorProfile.or(p.dotOff, Glyphs.dim(on, 0.1f)), IndicatorProfile.or(p.dotBg, baseBg)}; }
            case "lcd" -> new int[]{IndicatorProfile.or(p.lcdFg, 0xFFFFFFFF), IndicatorProfile.or(p.lcdArrow, defOn), IndicatorProfile.or(p.lcdBg, baseBg)};
            default -> { int on = IndicatorProfile.or(p.segOn, defOn); yield new int[]{on, IndicatorProfile.or(p.segOff, Glyphs.dim(on, 0.13f)), IndicatorProfile.or(p.segBg, baseBg)}; }
        };
    }

    /** 층 숫자 + 층이 바뀔 때 전환 효과 */
    private static void numberWithTransition(PoseStack ps, MultiBufferSource buf, IndicatorProfile p, String kind, String text, float[] num, float zz, int light,
                                             int on, int off, @Nullable St st, float t) {
        float prog = st == null || p.trans == 0 ? 1f : Math.max(0, Math.min(1, (t - st.changeT) / Math.max(0.05f, p.transSec)));
        if (prog >= 1 || st == null || st.prev.isEmpty()) { number(ps, buf, p, kind, text, num, 0, 0, null, zz, light, on, off, 1f); return; }
        int d = p.transDir == 0 ? (st.moveDir >= 0 ? 1 : 2) : p.transDir; // 1 위로 2 아래로 3 왼쪽 4 오른쪽
        String type = IndicatorProfile.TRANS[p.trans];
        if (type.equals("scroll") && !kind.equals("lcd")) {
            float sx = d == 3 ? -1 : d == 4 ? 1 : 0, sy = d == 1 ? 1 : d == 2 ? -1 : 0;
            number(ps, buf, p, kind, st.prev, num, sx * prog, sy * prog, num, zz, light, on, off, 1f);
            number(ps, buf, p, kind, text, num, sx * (prog - 1), sy * (prog - 1), num, zz, light, on, off, 1f);
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

    // ================================================================== 번갈아 표시 (화살표 ↔ 숫자)

    /** 도트 한 장면 (0 = 꺼짐, 그 밖 = 색) */
    private static final class Grid {
        final int cols, rows;
        final int[] c;

        Grid(int cols, int rows) { this.cols = cols; this.rows = rows; c = new int[cols * rows]; }

        int get(int x, int y) { return x < 0 || y < 0 || x >= cols || y >= rows ? 0 : c[y * cols + x]; }

        void set(int x, int y, int v) { if (x >= 0 && y >= 0 && x < cols && y < rows) c[y * cols + x] = v; }
    }

    /** 층 숫자를 도트 장면으로 (가운데 정렬) */
    private static Grid textGrid(IndicatorProfile p, String text, int cols, int rows, int on) {
        Grid g = new Grid(cols, rows);
        DotAnim font = AnimLibrary.get(p.dotFont);
        if (font != null && !font.isFont()) font = null;
        int gc = font != null ? font.cols : p.dotCols, gr = font != null ? font.rows : p.dotRows;
        int n = Math.max(1, text.length());
        int total = n * gc + (n - 1);
        int ox = (cols - total) / 2, oy = (rows - Math.min(rows, gr)) / 2;
        for (int i = 0; i < text.length(); i++) {
            String[] bm = Glyphs.dotGlyph(text.charAt(i));
            DotAnim.Frame fg = font == null ? null : font.glyph(text.charAt(i));
            if (fg != null) { boolean any = false; for (byte b : fg.dots) if (b != 0) { any = true; break; } if (!any) fg = null; }
            for (int yy = 0; yy < Math.min(rows, gr); yy++) for (int xx = 0; xx < gc; xx++) {
                int v = 0;
                if (fg != null) { int f = font.get(fg, xx, yy); v = f == 0 ? 0 : f == 1 ? on : font.palette[f - 1]; }
                else if (bm[yy * 7 / gr].charAt(xx * 5 / gc) == '#') v = on;
                if (v != 0) g.set(ox + i * (gc + 1) + xx, oy + yy, v);
            }
        }
        return g;
    }

    /** 화살표(또는 화살표 애니메이션)를 도트 장면으로 */
    private static Grid arrowGrid(IndicatorProfile p, @Nullable DotAnim anim, float animT, boolean rot, boolean still, int cols, int rows, int dir, boolean moving, float t, int on) {
        Grid g = new Grid(cols, rows);
        if (anim != null) {
            DotAnim.Frame f = still ? anim.stopFrame() : anim.frameAt(Math.max(0, animT));
            if (f == null) return g;
            int ox = (cols - anim.cols) / 2, oy = (rows - anim.rows) / 2;
            for (int y = 0; y < anim.rows; y++) for (int x = 0; x < anim.cols; x++) {
                int v = rot ? anim.get(f, anim.cols - 1 - x, anim.rows - 1 - y) : anim.get(f, x, y);
                if (v > 0) g.set(ox + x, oy + y, anim.palette[v - 1]);
            }
            return g;
        }
        if (dir == 0) return g;
        int ac = Math.min(cols, Math.max(p.dotCols, 5)), ar = rows;
        int shift = moving ? (int) (t * 9) % ar : 0;
        String[] bm = Glyphs.dotGlyph(dir > 0 ? '^' : 'v');
        int ox = (cols - ac) / 2;
        for (int yy = 0; yy < ar; yy++) for (int xx = 0; xx < ac; xx++) {
            int src = Math.floorMod(yy + (dir > 0 ? shift : -shift), ar);
            if (bm[src * 7 / ar].charAt(xx * 5 / ac) == '#') g.set(ox + xx, yy, on);
        }
        return g;
    }

    /** 두 장면 사이 전환 (type = TRANS, d: 1 위 2 아래 3 왼쪽 4 오른쪽) */
    private static Grid blend(Grid a, Grid b, float prog, String type, int d, int off) {
        if (prog >= 1 || type.equals("none")) return b;
        Grid o = new Grid(b.cols, b.rows);
        int R = b.rows, C = b.cols;
        for (int y = 0; y < R; y++) for (int x = 0; x < C; x++) {
            int v;
            switch (type) {
                case "scroll" -> {
                    int kr = Math.round(prog * R), kc = Math.round(prog * C);
                    v = switch (d) {
                        case 1 -> y + kr < R ? a.get(x, y + kr) : b.get(x, y + kr - R);          // 위로 밀려 올라감
                        case 2 -> y - kr >= 0 ? a.get(x, y - kr) : b.get(x, y - kr + R);
                        case 3 -> x + kc < C ? a.get(x + kc, y) : b.get(x + kc - C, y);
                        default -> x - kc >= 0 ? a.get(x - kc, y) : b.get(x - kc + C, y);
                    };
                }
                case "wipe" -> {
                    int kr = Math.round(prog * R), kc = Math.round(prog * C);
                    boolean nb = switch (d) { case 1 -> y >= R - kr; case 2 -> y < kr; case 3 -> x >= C - kc; default -> x < kc; };
                    v = nb ? b.get(x, y) : a.get(x, y);
                }
                case "blink" -> v = ((int) (prog * 6)) % 2 == 1 ? b.get(x, y) : 0;
                default -> {   // fade
                    int av = a.get(x, y), bv = b.get(x, y);
                    if (prog < 0.5f) v = av == 0 ? 0 : mix(av, off, prog * 2);
                    else v = bv == 0 ? 0 : mix(off, bv, prog * 2 - 1);
                }
            }
            o.c[y * C + x] = v;
        }
        return o;
    }

    /** 번갈아 표시 모드: 갈 방향이 있으면 화살표(정한 초) → 숫자(정한 초) 를 되풀이, 없으면 숫자만 */
    private static void alternate(PoseStack ps, MultiBufferSource buf, IndicatorProfile p, String nk, String ak, @Nullable DotAnim anim, float animT,
                                  boolean rot, boolean still, float x1, float y1, float x2, float y2, float zz, int light, String text, int dir,
                                  boolean moving, @Nullable St st, float t, int[] cn, int[] ca) {
        float w = x2 - x1, h = y2 - y1;
        boolean arrow = dir != 0 || anim != null;
        float A = Math.max(0.1f, p.altArrowSec), N = Math.max(0.1f, p.altNumSec), ts = Math.max(0, p.altTransSec);
        float tt = st == null ? t : t - st.dirT;
        float u = tt % (A + N);
        boolean inArrow = arrow && u < A;
        float prog = 1;
        if (arrow && ts > 0) {
            float since = inArrow ? u : u - A;
            if (since < ts && (tt >= A + N || !inArrow)) prog = since / ts;
        }
        int d = p.altTransDir == 0 ? (dir >= 0 ? 1 : 2) : p.altTransDir;
        if (nk.equals("dot")) {
            // 도트: 한 장면씩 도트 단위로 밀리며 바뀜
            DotAnim font = AnimLibrary.get(p.dotFont);
            int rows = font != null && font.isFont() ? font.rows : p.dotRows;
            if (anim != null) rows = Math.max(rows, anim.rows);
            float pitch = h * 0.9f / rows;
            int cols = Math.max(1, (int) Math.floor(w * 0.94f / pitch));
            Grid numG = textGrid(p, text, cols, rows, cn[0]);
            // 숫자 칸에서는 층이 바뀔 때 전환 효과도
            if (st != null && !st.prev.isEmpty() && p.trans != 0) {
                float np = Math.max(0, Math.min(1, (t - st.changeT) / Math.max(0.05f, p.transSec)));
                int nd = p.transDir == 0 ? (st.moveDir >= 0 ? 1 : 2) : p.transDir;
                numG = blend(textGrid(p, st.prev, cols, rows, cn[0]), numG, np, IndicatorProfile.TRANS[p.trans], nd, cn[1]);
            }
            Grid out;
            if (!arrow) out = numG;
            else {
                Grid arG = arrowGrid(p, anim, animT, rot, still, cols, rows, dir, moving, t, ca[0]);
                out = inArrow ? blend(numG, arG, prog, IndicatorProfile.TRANS[p.altTrans], d, cn[1]) : blend(arG, numG, prog, IndicatorProfile.TRANS[p.altTrans], d, cn[1]);
            }
            float gx = (x1 + x2) / 2 - cols * pitch / 2, gy = (y1 + y2) / 2 + rows * pitch / 2;
            for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) {
                int v = out.get(x, y);
                dot(ps, buf, gx + x * pitch + pitch / 2, gy - y * pitch - pitch / 2, pitch * 0.4f, zz, v != 0, light, v, cn[1], p.dotRound, null);
            }
            return;
        }
        // 세그먼트·LCD: 장면을 통째로 바꿈 (페이드)
        float[] full = {x1 + w * 0.05f, y1 + h * 0.08f, x2 - w * 0.05f, y2 - h * 0.08f};
        if (inArrow) {
            if (anim != null) drawAnim(ps, buf, anim, animT, full[0], full[1], full[2], full[3], zz, light, p.dotRound, Glyphs.dim(anim.palette[0], 0.1f), rot, still);
            else builtinArrow(ps, buf, p, ak, x1, y1, x2, y2, zz, light, prog < 1 ? mix(ca[1], ca[0], prog) : ca[0], ca[1], dir, moving, t);
        } else numberWithTransition(ps, buf, p, nk, text, full, zz, light, prog < 1 ? mix(cn[1], cn[0], prog) : cn[0], cn[1], st, t);
    }

    /** 가운데 기준으로 k 배, (dx, dy) 만큼 이동 */
    static float[] scaleRect(float[] r, float k, float dx, float dy) {
        float cx = (r[0] + r[2]) / 2 + dx, cy = (r[1] + r[3]) / 2 + dy, hw = (r[2] - r[0]) / 2 * k, hh = (r[3] - r[1]) / 2 * k;
        return new float[]{cx - hw, cy - hh, cx + hw, cy + hh};
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
        DotAnim font = AnimLibrary.get(p.dotFont);
        if (font != null && !font.isFont()) font = null;
        int cols = font != null ? font.cols : p.dotCols, rows = font != null ? font.rows : p.dotRows, n = text.length();
        int total = n * cols + (n - 1);
        float w = r[2] - r[0], h = r[3] - r[1];
        float pitch = Math.min(w / total, h / rows);
        float gx = (r[0] + r[2]) / 2 - total * pitch / 2, gy = (r[1] + r[3]) / 2 + rows * pitch / 2;
        // 칸 단위로 밀기 (실제 도트는 한 칸씩 움직임)
        int sx = Math.round(ox * total), sy = Math.round(oy * rows);
        for (int i = 0; i < n; i++) {
            String[] g = Glyphs.dotGlyph(text.charAt(i));
            DotAnim.Frame fg = font == null ? null : font.glyph(text.charAt(i));
            if (fg != null) { boolean any = false; for (byte b : fg.dots) if (b != 0) { any = true; break; } if (!any) fg = null; }  // 안 그린 글자는 기본 글꼴
            for (int yy = 0; yy < rows; yy++) for (int xx = 0; xx < cols; xx++) {
                boolean lit;
                int col = on;
                if (fg != null) {
                    // 사용자가 직접 찍은 도트 글꼴 (1번 색 = 표시기 색, 나머지 = 글꼴 팔레트)
                    int v = font.get(fg, xx, yy);
                    lit = v > 0;
                    if (v > 1) col = font.palette[v - 1];
                } else {
                    int fx = xx * 5 / cols, fy = yy * 7 / rows;
                    lit = g[fy].charAt(fx) == '#';
                }
                int gxi = i * (cols + 1) + xx + sx, gyi = yy - sy;
                if (gxi < 0 || gxi >= total || gyi < 0 || gyi >= rows) continue;
                float cx = gx + gxi * pitch + pitch / 2, cy = gy - gyi * pitch - pitch / 2;
                dot(ps, buf, cx, cy, pitch * 0.4f, z, lit, light, col, off, p.dotRound, clip);
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
        drawAnim(ps, buf, a, t, x1, y1, x2, y2, z, light, round, offColor, false, false);
    }

    /** rot = 180도 돌려 그림(하강), still = 정지 장면 */
    public static void drawAnim(PoseStack ps, MultiBufferSource buf, DotAnim a, float t, float x1, float y1, float x2, float y2, float z, int light, boolean round, int offColor,
                                boolean rot, boolean still) {
        DotAnim.Frame f = still ? a.stopFrame() : a.frameAt(Math.max(0, t));
        if (f == null) return;
        float pitch = Math.min((x2 - x1) / a.cols, (y2 - y1) / a.rows);
        float gx = (x1 + x2) / 2 - a.cols * pitch / 2, gy = (y1 + y2) / 2 + a.rows * pitch / 2;
        for (int y = 0; y < a.rows; y++) for (int x = 0; x < a.cols; x++) {
            int v = rot ? a.get(f, a.cols - 1 - x, a.rows - 1 - y) : a.get(f, x, y);
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

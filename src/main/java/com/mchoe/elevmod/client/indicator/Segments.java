package com.mchoe.elevmod.client.indicator;

import com.mchoe.elevmod.client.render.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 세그먼트 표시 소자 – 7세그, 9세그(위쪽 V 대각선), 14세그, 16세그.
 * 모양: 0 각진(양끝 뾰족한 육각형), 1 둥근(캡슐), 2 평평(직사각형). 기울기(°), 획 두께(높이 비율) 조절.
 * 꺼진 세그먼트도 희미하게 그린다(실제 LED 필터 뒤 모습). 스크롤 효과를 위해 사각형으로 잘라 그릴 수 있다.
 */
public final class Segments {
    private Segments() {}

    // 16세그 이름 순서: a1 a2 b c d1 d2 e f g1 g2 h i j k l m
    private static final String NAMES = "a1,a2,b,c,d1,d2,e,f,g1,g2,h,i,j,k,l,m";
    private static final Map<String, Integer> BIT = new HashMap<>();
    /** 단위 칸(0..1, 아래→위) 안의 세그먼트 중심선 {x0,y0,x1,y1} */
    private static final float[][] L16 = {
            {0, 1, .5f, 1}, {.5f, 1, 1, 1}, {1, 1, 1, .5f}, {1, .5f, 1, 0}, {0, 0, .5f, 0}, {.5f, 0, 1, 0}, {0, 0, 0, .5f}, {0, .5f, 0, 1},
            {0, .5f, .5f, .5f}, {.5f, .5f, 1, .5f}, {0, 1, .5f, .5f}, {.5f, 1, .5f, .5f}, {1, 1, .5f, .5f}, {0, 0, .5f, .5f}, {.5f, 0, .5f, .5f}, {1, 0, .5f, .5f}};
    private static final Map<Character, Integer> MAP16 = new HashMap<>();

    static {
        String[] n = NAMES.split(",");
        for (int i = 0; i < n.length; i++) BIT.put(n[i], i);
        String[] defs = {
                "0:a1 a2 b c d1 d2 e f", "1:b c", "2:a1 a2 b g1 g2 e d1 d2", "3:a1 a2 b c d1 d2 g2 g1", "4:f g1 g2 b c",
                "5:a1 a2 f g1 g2 c d1 d2", "6:a1 a2 f e d1 d2 c g1 g2", "7:a1 a2 b c", "8:a1 a2 b c d1 d2 e f g1 g2", "9:a1 a2 b c d1 d2 f g1 g2",
                "A:a1 a2 b c e f g1 g2", "B:a1 a2 b c d1 d2 i l g2", "C:a1 a2 f e d1 d2", "D:a1 a2 b c d1 d2 i l", "E:a1 a2 f e d1 d2 g1",
                "F:a1 a2 f e g1", "G:a1 a2 f e d1 d2 c g2", "H:f e b c g1 g2", "I:a1 a2 i l d1 d2", "J:b c d1 d2 e", "K:f e g1 j m",
                "L:f e d1 d2", "M:f e b c h j", "N:f e b c h m", "O:a1 a2 b c d1 d2 e f", "P:a1 a2 b f e g1 g2", "Q:a1 a2 b c d1 d2 e f m",
                "R:a1 a2 b f e g1 g2 m", "S:a1 a2 f g1 g2 c d1 d2", "T:a1 a2 i l", "U:f e d1 d2 c b", "V:f e k j", "W:f e b c k m",
                "X:h j k m", "Y:h j l", "Z:a1 a2 j k d1 d2", "-:g1 g2", "_:d1 d2", "+:g1 g2 i l", "/:j k", "*:g1 g2 h i j k l m"};
        for (String d : defs) {
            int m = 0;
            for (String s : d.substring(2).split(" ")) m |= 1 << BIT.get(s);
            MAP16.put(d.charAt(0), m);
        }
    }

    /** 종류별 (세그먼트 선, 16세그 비트 → 이 종류 세그먼트 인덱스 매핑) */
    private static List<float[]> lines(int type) {
        List<float[]> out = new ArrayList<>();
        switch (type) {
            case 2 -> { // 14세그: a, d 통짜
                out.add(new float[]{0, 1, 1, 1}); out.add(L16[2]); out.add(L16[3]); out.add(new float[]{0, 0, 1, 0}); out.add(L16[6]); out.add(L16[7]);
                for (int i = 8; i < 16; i++) out.add(L16[i]);
            }
            case 3 -> { for (float[] l : L16) out.add(l); }
            default -> { // 7세그 (+9세그: 위쪽 V)
                out.add(new float[]{0, 1, 1, 1}); out.add(L16[2]); out.add(L16[3]); out.add(new float[]{0, 0, 1, 0}); out.add(L16[6]); out.add(L16[7]);
                out.add(new float[]{0, .5f, 1, .5f});
                if (type == 1) { out.add(L16[10]); out.add(L16[12]); }
            }
        }
        return out;
    }

    /** 글자 → 이 종류에서 켜질 세그먼트 비트 (lines() 순서) */
    public static int mask(int type, char ch) {
        ch = Character.toUpperCase(ch);
        Integer m16 = MAP16.get(ch);
        if (m16 == null) return 0;
        int m = m16;
        if (type == 3) return m;
        boolean a = (m & 3) != 0, d = (m & (3 << 4)) != 0;
        int b = (m >> 2) & 1, c = (m >> 3) & 1, e = (m >> 6) & 1, f = (m >> 7) & 1;
        if (type == 2) {
            int out = (a ? 1 : 0) | b << 1 | c << 2 | (d ? 1 : 0) << 3 | e << 4 | f << 5;
            for (int i = 8; i < 16; i++) if ((m & (1 << i)) != 0) out |= 1 << (i - 2);
            return out;
        }
        // 7/9세그: 가운데 g = g1|g2, 대각/세로 중앙선이 필요한 글자는 비슷한 모양으로
        boolean g = (m & (3 << 8)) != 0;
        int out = (a ? 1 : 0) | b << 1 | c << 2 | (d ? 1 : 0) << 3 | e << 4 | f << 5 | (g ? 1 : 0) << 6;
        if (type == 1) {
            if ((m & (1 << 10)) != 0) out |= 1 << 7;   // h → 위 왼쪽 대각
            if ((m & (1 << 12)) != 0) out |= 1 << 8;   // j → 위 오른쪽 대각
        }
        // 7세그로 못 그리는 글자 보정
        switch (ch) {
            case 'B' -> out = 0b1111100;   // b
            case 'D' -> out = 0b1011110;   // d
            case 'R' -> out = 0b1010000;   // r
            case 'N' -> out = type == 1 ? out : 0b1010100;
            case 'M' -> out = type == 1 ? 0b0110110 | (3 << 7) : 0b0110111;
            case 'T' -> out = 0b1111000;
            case 'K' -> out = 0b1110110;
            default -> {}
        }
        return out;
    }

    /** 글자 칸 폭(높이 대비) */
    public static float width(int type) { return type >= 2 ? 0.64f : 0.56f; }

    // ------------------------------------------------------------------ 그리기
    /**
     * 문자열 그리기. (cx, cy) 가운데, h = 글자 높이. clip = {x1,y1,x2,y2} (null 이면 자르지 않음).
     * lead1 = 맨 앞 '1'을 반 칸짜리로(18.8 형 표시기).
     */
    public static void text(PoseStack ps, MultiBufferSource buf, String s, int type, int shape, float slantDeg, float thick,
                            float cx, float cy, float h, float z, int light, int on, int off, @Nullable float[] clip, boolean lead1) {
        s = s.toUpperCase(Locale.ROOT);
        float dw = h * width(type), gap = h * 0.2f;
        boolean half = lead1 && s.length() >= 2 && s.charAt(0) == '1';
        float total = 0;
        for (int i = 0; i < s.length(); i++) total += (i == 0 && half ? dw * 0.35f : dw) + (i > 0 ? gap : 0);
        float x = cx - total / 2;
        for (int i = 0; i < s.length(); i++) {
            boolean hh = i == 0 && half;
            float w = hh ? dw * 0.35f : dw;
            if (hh) {
                // 반 칸 '1': 오른쪽 세로 두 획만
                digit(ps, buf, 0b110, 0, shape, slantDeg, thick, x - dw + w, cy - h / 2, dw, h, z, light, on, off, clip, true);
            } else digit(ps, buf, mask(type, s.charAt(i)), type, shape, slantDeg, thick, x, cy - h / 2, dw, h, z, light, on, off, clip, false);
            x += w + gap;
        }
    }

    static void digit(PoseStack ps, MultiBufferSource buf, int mask, int type, int shape, float slantDeg, float thick,
                      float x, float y, float w, float h, float z, int light, int on, int off, @Nullable float[] clip, boolean onlyLit) {
        List<float[]> ls = lines(type);
        float t = h * thick, t2 = t / 2, g = t * 0.22f;
        float sl = (float) Math.tan(Math.toRadians(slantDeg));
        for (int i = 0; i < ls.size(); i++) {
            boolean lit = (mask & (1 << i)) != 0;
            if (onlyLit && !lit) continue;
            float[] l = ls.get(i);
            // 칸 안쪽으로 반 획만큼 들임
            float ax = x + t2 + l[0] * (w - t), ay = y + t2 + l[1] * (h - t), bx = x + t2 + l[2] * (w - t), by = y + t2 + l[3] * (h - t);
            float[] poly = shape(ax, ay, bx, by, t2, g, shape, l[0] != l[2] && l[1] != l[3]);
            for (int k = 0; k < poly.length; k += 2) poly[k] += (poly[k + 1] - y - h / 2) * sl;
            if (clip != null) { poly = clip(poly, clip); if (poly.length < 6) continue; }
            fan(ps, buf, poly, z + (lit ? 0.0006f : 0f), lit ? RenderUtil.FULL : light, lit ? on : off);
        }
    }

    /** 세그먼트 한 획의 외곽 다각형 */
    private static float[] shape(float ax, float ay, float bx, float by, float t2, float g, int shape, boolean diagonal) {
        float dx = bx - ax, dy = by - ay, len = (float) Math.hypot(dx, dy);
        if (len < 1e-5f) return new float[0];
        float ux = dx / len, uy = dy / len, nx = -uy, ny = ux;
        float tip = diagonal ? t2 * 0.6f : t2;
        float sax = ax + ux * (g + (diagonal ? t2 : 0)), say = ay + uy * (g + (diagonal ? t2 : 0));
        float sbx = bx - ux * (g + (diagonal ? t2 : 0)), sby = by - uy * (g + (diagonal ? t2 : 0));
        float w = diagonal ? t2 * 0.8f : t2;
        switch (shape) {
            case 1 -> { // 둥근: 양끝 반원(5점씩)
                float cax = sax + ux * w, cay = say + uy * w, cbx = sbx - ux * w, cby = sby - uy * w;
                float[] o = new float[20];
                for (int k = 0; k <= 4; k++) {
                    double a = Math.PI / 2 + Math.PI * k / 4;
                    o[k * 2] = cax + (float) (Math.cos(a) * ux * w + Math.sin(a) * nx * w);
                    o[k * 2 + 1] = cay + (float) (Math.cos(a) * uy * w + Math.sin(a) * ny * w);
                }
                for (int k = 0; k <= 4; k++) {
                    double a = -Math.PI / 2 + Math.PI * k / 4;
                    o[10 + k * 2] = cbx + (float) (Math.cos(a) * ux * w + Math.sin(a) * nx * w);
                    o[10 + k * 2 + 1] = cby + (float) (Math.cos(a) * uy * w + Math.sin(a) * ny * w);
                }
                return o;
            }
            case 2 -> { // 평평
                return new float[]{sax + nx * w, say + ny * w, sbx + nx * w, sby + ny * w, sbx - nx * w, sby - ny * w, sax - nx * w, say - ny * w};
            }
            default -> { // 각진(육각)
                return new float[]{sax, say, sax + ux * tip + nx * w, say + uy * tip + ny * w, sbx - ux * tip + nx * w, sby - uy * tip + ny * w,
                        sbx, sby, sbx - ux * tip - nx * w, sby - uy * tip - ny * w, sax + ux * tip - nx * w, say + uy * tip - ny * w};
            }
        }
    }

    /** 다각형을 사각형으로 자르기 (Sutherland–Hodgman) */
    public static float[] clip(float[] p, float[] r) {
        float[] out = p;
        for (int edge = 0; edge < 4; edge++) {
            List<Float> res = new ArrayList<>();
            int n = out.length / 2;
            if (n == 0) break;
            for (int i = 0; i < n; i++) {
                float x0 = out[i * 2], y0 = out[i * 2 + 1], x1 = out[((i + 1) % n) * 2], y1 = out[((i + 1) % n) * 2 + 1];
                boolean in0 = inside(x0, y0, edge, r), in1 = inside(x1, y1, edge, r);
                if (in0) { res.add(x0); res.add(y0); }
                if (in0 != in1) {
                    float tt;
                    switch (edge) {
                        case 0 -> tt = (r[0] - x0) / (x1 - x0);
                        case 1 -> tt = (r[2] - x0) / (x1 - x0);
                        case 2 -> tt = (r[1] - y0) / (y1 - y0);
                        default -> tt = (r[3] - y0) / (y1 - y0);
                    }
                    res.add(x0 + (x1 - x0) * tt); res.add(y0 + (y1 - y0) * tt);
                }
            }
            out = new float[res.size()];
            for (int k = 0; k < out.length; k++) out[k] = res.get(k);
        }
        return out;
    }

    private static boolean inside(float x, float y, int edge, float[] r) {
        return switch (edge) { case 0 -> x >= r[0]; case 1 -> x <= r[2]; case 2 -> y >= r[1]; default -> y <= r[3]; };
    }

    /** 볼록 다각형을 부채꼴 사각형들로 그림 */
    public static void fan(PoseStack ps, MultiBufferSource buf, float[] p, float z, int light, int c) {
        VertexConsumer vc = RenderUtil.solid(buf, "white");
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        int n = p.length / 2;
        if (light == RenderUtil.FULL) { /* 발광: 위쪽 법선이 가장 밝음 */ }
        float ny = light == RenderUtil.FULL ? 1 : 0, nz = light == RenderUtil.FULL ? 0 : 1;
        for (int i = 1; i + 1 < n; i += 2) {
            int j = Math.min(i + 2, n - 1);
            v(vc, m, nm, p[0], p[1], z, light, c, ny, nz);
            v(vc, m, nm, p[i * 2], p[i * 2 + 1], z, light, c, ny, nz);
            v(vc, m, nm, p[(i + 1) * 2], p[(i + 1) * 2 + 1], z, light, c, ny, nz);
            v(vc, m, nm, p[j * 2], p[j * 2 + 1], z, light, c, ny, nz);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int light, int c, float ny, float nz) {
        vc.vertex(m, x, y, z).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, ny, nz).endVertex();
    }
}

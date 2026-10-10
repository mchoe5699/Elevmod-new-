package com.mchoe.elevmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 실제 표시기 소자처럼 그리는 글자: 7세그먼트 LED, 5×7 도트매트릭스 LED.
 * 꺼진 세그먼트/도트도 필터 뒤에 희미하게 보이도록 그린다(실제 LED 표시기의 모습).
 * 좌표: 현재 PoseStack 의 z 평면, +Z 를 바라봄. (cx, cy) = 글자열 중심, h = 글자 높이.
 */
public final class Glyphs {
    private Glyphs() {}

    // -------------------------------------------------------------- 공용 사각형/삼각형
    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int light, int c) {
        vc.vertex(m, x, y, z).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).uv(0.5f, 0.5f)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
    }

    /** z 평면 단색 사각형(+Z 방향) */
    public static void rect(PoseStack ps, MultiBufferSource buf, float x1, float y1, float x2, float y2, float z, int light, int c) {
        VertexConsumer vc = RenderUtil.solid(buf, "white");
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        v(vc, m, n, x1, y1, z, light, c); v(vc, m, n, x2, y1, z, light, c);
        v(vc, m, n, x2, y2, z, light, c); v(vc, m, n, x1, y2, z, light, c);
    }

    /** 사각형 네 꼭짓점 직접 지정 (반시계) */
    public static void quad(PoseStack ps, MultiBufferSource buf, float[] p, float z, int light, int c) {
        VertexConsumer vc = RenderUtil.solid(buf, "white");
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (int i = 0; i < 4; i++) v(vc, m, n, p[i * 2], p[i * 2 + 1], z, light, c);
    }

    /** 삼각 화살표 (dir>0 위, dir<0 아래) */
    public static void arrow(PoseStack ps, MultiBufferSource buf, float cx, float cy, float h, int dir, float z, int light, int c) {
        float w = h * 0.9f;
        if (dir > 0) quad(ps, buf, new float[]{cx - w / 2, cy - h / 2, cx + w / 2, cy - h / 2, cx, cy + h / 2, cx, cy + h / 2}, z, light, c);
        else quad(ps, buf, new float[]{cx, cy - h / 2, cx, cy - h / 2, cx + w / 2, cy + h / 2, cx - w / 2, cy + h / 2}, z, light, c);
    }

    /** 색의 밝기를 k 배로 (꺼진 소자) */
    public static int dim(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // -------------------------------------------------------------- 7세그먼트
    //  aaa      bit0=a 1=b 2=c 3=d 4=e 5=f 6=g
    // f   b
    //  ggg
    // e   c
    //  ddd
    private static final Map<Character, Integer> SEG = new HashMap<>();

    static {
        String[] d = {"0:63", "1:6", "2:91", "3:79", "4:102", "5:109", "6:125", "7:39", "8:127", "9:111",
                "A:119", "B:124", "C:57", "D:94", "E:121", "F:113", "G:61", "H:118", "I:6", "J:30", "K:118", "L:56",
                "M:55", "N:84", "O:63", "P:115", "Q:103", "R:80", "S:109", "T:120", "U:62", "V:62", "Y:110", "Z:91", "-:64", "_:8"};
        for (String s : d) SEG.put(s.charAt(0), Integer.parseInt(s.substring(2)));
    }

    public static int segMask(char ch) {
        Integer m = SEG.get(Character.toUpperCase(ch));
        return m == null ? 0 : m;
    }

    /** 7세그 한 자리 폭 비율 */
    public static float segWidth(float h) { return h * 0.56f; }

    /** 7세그먼트 문자열. 공백 문자는 한 자리 비움. 반환값 = 그린 폭 */
    public static float seg(PoseStack ps, MultiBufferSource buf, String s, float cx, float cy, float h, float z, int light, int on, float offK) {
        s = s.toUpperCase(Locale.ROOT);
        float dw = segWidth(h), gap = h * 0.2f;
        int n = s.length();
        float total = n * dw + (n - 1) * gap;
        float x = cx - total / 2;
        int off = dim(on, offK);
        for (int i = 0; i < n; i++) {
            digit(ps, buf, segMask(s.charAt(i)), x, cy - h / 2, dw, h, z, light, on, off);
            x += dw + gap;
        }
        return total;
    }

    private static void digit(PoseStack ps, MultiBufferSource buf, int mask, float x, float y, float w, float h, float z, int light, int on, int off) {
        float t = h * 0.12f, g = t * 0.18f;  // 획 두께, 세그먼트 사이 틈
        float sl = h * 0.08f;                // 기울임(이탤릭) 정도
        float mid = y + h / 2;
        // 각 세그먼트: 육각형 근사 대신 양끝을 깎은 사각형(사다리꼴 2개)
        float[][] segs = new float[7][];
        segs[0] = hseg(x + g, y + h - t, x + w - g, y + h, t);          // a
        segs[1] = vseg(x + w - t, mid + g, x + w, y + h - g, t);        // b
        segs[2] = vseg(x + w - t, y + g, x + w, mid - g, t);            // c
        segs[3] = hseg(x + g, y, x + w - g, y + t, t);                  // d
        segs[4] = vseg(x, y + g, x + t, mid - g, t);                    // e
        segs[5] = vseg(x, mid + g, x + t, y + h - g, t);                // f
        segs[6] = hseg(x + g, mid - t / 2, x + w - g, mid + t / 2, t);  // g
        for (int i = 0; i < 7; i++) {
            float[] p = segs[i];
            boolean lit = (mask & (1 << i)) != 0;
            // 기울임: y 에 비례해 x 이동
            float[] q = new float[8];
            for (int k = 0; k < 4; k++) { q[k * 2] = p[k * 2] + (p[k * 2 + 1] - y) / h * sl; q[k * 2 + 1] = p[k * 2 + 1]; }
            quad(ps, buf, q, z + (lit ? 0.0006f : 0f), lit ? RenderUtil.FULL : light, lit ? on : off);
        }
    }

    /** 가로 세그먼트(끝을 비스듬히 깎은 사다리꼴 근사: 4점) */
    private static float[] hseg(float x1, float y1, float x2, float y2, float t) {
        float c = t * 0.5f;
        return new float[]{x1 + c, y1, x2 - c, y1, x2 - c * 0.2f, y2, x1 + c * 0.2f, y2};
    }

    private static float[] vseg(float x1, float y1, float x2, float y2, float t) {
        float c = t * 0.5f;
        return new float[]{x1, y1 + c * 0.2f, x2, y1 + c, x2, y2 - c, x1, y2 - c * 0.2f};
    }

    // -------------------------------------------------------------- 5×7 도트매트릭스
    private static final Map<Character, String[]> DOT = new HashMap<>();

    private static void d(char c, String... rows) { DOT.put(c, rows); }

    static {
        d('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        d('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        d('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        d('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.");
        d('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        d('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        d('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
        d('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        d('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        d('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
        d('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        d('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        d('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        d('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        d('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        d('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        d('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        d('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        d('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        d('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        d('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        d('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        d('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#");
        d('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        d('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        d('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        d('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        d('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        d('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        d('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        d('^', "..#..", ".###.", "#.#.#", "..#..", "..#..", "..#..", "..#..");   // 위 화살표
        d('v', "..#..", "..#..", "..#..", "..#..", "#.#.#", ".###.", "..#..");   // 아래 화살표
        d(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }

    /** 도트 한 글자 폭(5칸) */
    public static float dotWidth(float h) { return h * 5f / 7f; }

    /** 5×7 도트매트릭스. '^' '▲' = 위 화살표, 'v' '▼' = 아래 화살표. scroll = 위아래로 흐르는 행 오프셋(0..1) */
    public static float dots(PoseStack ps, MultiBufferSource buf, String s, float cx, float cy, float h, float z, int light, int on, float offK, float scroll) {
        float pitch = h / 7f, gap = pitch;
        int n = s.length();
        float total = n * 5 * pitch + (n - 1) * gap;
        float x0 = cx - total / 2;
        int off = dim(on, offK);
        float r = pitch * 0.38f;
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (ch == '▲') ch = '^';
            else if (ch == '▼') ch = 'v';
            String[] g = DOT.get(ch == 'v' || ch == '^' ? ch : Character.toUpperCase(ch));
            if (g == null) g = DOT.get(' ');
            boolean arrow = ch == '^' || ch == 'v';
            int shift = arrow ? Math.round(scroll * 7) : 0;
            for (int row = 0; row < 7; row++) {
                int src = arrow ? Math.floorMod(row + (ch == '^' ? shift : -shift), 7) : row;
                String line = g[src];
                for (int col = 0; col < 5; col++) {
                    boolean lit = line.charAt(col) == '#';
                    float x = x0 + col * pitch + pitch / 2, y = cy + h / 2 - row * pitch - pitch / 2;
                    rect(ps, buf, x - r, y - r, x + r, y + r, z + (lit ? 0.0006f : 0f), lit ? RenderUtil.FULL : light, lit ? on : off);
                }
            }
            x0 += 5 * pitch + gap;
        }
        return total;
    }
}

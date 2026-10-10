package com.mchoe.elevmod.client.design;

import com.mchoe.elevmod.client.render.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 커스텀 디자인 면 그리기 – 볼록/오목/홈/곡면/평면/봉/점 요소를 실제 입체로 만든다.
 * 면 좌표계: x ∈ [0,W] (보는 사람 기준 왼쪽→오른쪽), y ∈ [0,H] (아래→위), z = 면에서 바깥(보는 사람 쪽) 높이.
 */
public final class Relief {
    private Relief() {}

    /** 면 → 렌더 좌표: P = O + x·U + y·V + z·N */
    public record Frame(float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz, float nx, float ny, float nz) {
        float[] p(float x, float y, float z) {
            return new float[]{ox + x * ux + y * vx + z * nx, oy + x * uy + y * vy + z * ny, oz + x * uz + y * vz + z * nz};
        }

        float[] d(float a, float b, float c) {
            float x = a * ux + b * vx + c * nx, y = a * uy + b * vy + c * ny, z = a * uz + b * vz + c * nz;
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            if (l < 1e-6f) return new float[]{nx, ny, nz};
            return new float[]{x / l, y / l, z / l};
        }
    }

    private static final class Ctx {
        PoseStack ps; MultiBufferSource buf; Frame f; float W, H; CustomDesign.Surface s; int light;
    }

    private static void quad(Ctx c, VertexConsumer vc, float[][] pts, float[][] uv, float[] n, int light, int col) {
        // 발광 요소: 방향 음영 없이 최대 밝기 (위를 향한 법선이 가장 밝게 그려짐)
        if (light == RenderUtil.FULL) n = new float[]{0, 1, 0};
        Matrix4f m = c.ps.last().pose();
        Matrix3f nm = c.ps.last().normal();
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, pts[i][0], pts[i][1], pts[i][2]).color((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >>> 24) & 255)
                    .uv(uv[i][0], uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, n[0], n[1], n[2]).endVertex();
        }
    }

    /** 면 좌표 (x,y) → 텍스처 좌표 */
    private static float[] uv(Ctx c, float x, float y, @Nullable float[] own) {
        if (own != null) return new float[]{(x - own[0]) / Math.max(1e-4f, own[2] - own[0]), 1 - (y - own[1]) / Math.max(1e-4f, own[3] - own[1])};
        if (c.s.tile) return new float[]{x, c.H - y};
        return new float[]{x / c.W, 1 - y / c.H};
    }

    /** 면과 평행한 사각형 (높이 z) */
    private static void flat(Ctx c, VertexConsumer vc, float x1, float y1, float x2, float y2, float z, int light, int col, @Nullable float[] own) {
        Frame f = c.f;
        quad(c, vc, new float[][]{f.p(x1, y1, z), f.p(x2, y1, z), f.p(x2, y2, z), f.p(x1, y2, z)},
                new float[][]{uv(c, x1, y1, own), uv(c, x2, y1, own), uv(c, x2, y2, own), uv(c, x1, y2, own)}, f.d(0, 0, 1), light, col);
    }

    /** 일반 사각형 – 네 꼭짓점 (x,y,z) 직접, 법선 (a,b,c) 면 좌표계 */
    private static void face(Ctx c, VertexConsumer vc, float[][] q, float[] nl, int light, int col, @Nullable float[] own) {
        Frame f = c.f;
        float[][] pts = new float[4][], uvs = new float[4][];
        for (int i = 0; i < 4; i++) { pts[i] = f.p(q[i][0], q[i][1], q[i][2]); uvs[i] = uv(c, q[i][0], q[i][1], own); }
        quad(c, vc, pts, uvs, f.d(nl[0], nl[1], nl[2]), light, col);
    }

    // ------------------------------------------------------------------ 요소 좌표
    private static float res(float v, String mode, float size, int which, float[] pair) {
        return switch (mode) {
            case "m" -> v;
            case "r" -> size - v;
            case "i" -> which == 0 ? v : size - v;   // 안쪽 여백: [왼/아래 m, 오른/위 m]
            default -> v * size;
        };
    }

    static float[] rect(CustomDesign.El e, float W, float H) {
        float a = res(e.x[0], e.xMode, W, 0, e.x), b = res(e.x[1], e.xMode, W, 1, e.x);
        float cc = res(e.y[0], e.yMode, H, 0, e.y), d = res(e.y[1], e.yMode, H, 1, e.y);
        return new float[]{Math.max(0, Math.min(a, b)), Math.max(0, Math.min(cc, d)), Math.min(W, Math.max(a, b)), Math.min(H, Math.max(cc, d))};
    }

    /** rows × cols 로 나눈 하위 사각형들 */
    static List<float[]> cells(CustomDesign.El e, float W, float H) {
        float[] r = rect(e, W, H);
        List<float[]> out = new ArrayList<>();
        if (e.shape.equals("dots")) { out.add(r); return out; }
        float cw = (r[2] - r[0] - e.gap * (e.cols - 1)) / e.cols, ch = (r[3] - r[1] - e.gap * (e.rows - 1)) / e.rows;
        if (cw <= 0 || ch <= 0) return out;
        for (int i = 0; i < e.cols; i++) for (int j = 0; j < e.rows; j++) {
            float x = r[0] + i * (cw + e.gap), y = r[1] + j * (ch + e.gap);
            out.add(new float[]{x, y, x + cw, y + ch});
        }
        return out;
    }

    // ------------------------------------------------------------------ 그리기
    /**
     * 면 하나를 그린다. hole = 추가로 비울 구멍(방범창 등, 면 좌표) – 그 자리는 바탕을 그리지 않는다.
     */
    public static void draw(PoseStack ps, MultiBufferSource buf, CustomDesign.Surface s, Frame f, float W, float H, int light, @Nullable float[] hole) {
        Ctx c = new Ctx();
        c.ps = ps; c.buf = buf; c.f = f; c.W = W; c.H = H; c.s = s; c.light = light;
        // 1) 구멍 목록
        List<float[]> holes = new ArrayList<>();
        for (CustomDesign.El e : s.els) if (e.hole()) for (float[] r : cells(e, W, H)) if (!hits(r, hole)) holes.add(r);
        if (hole != null) holes.add(hole);
        // 2) 바탕: 구멍 가장자리로 격자를 나눠 구멍이 아닌 칸만 그림
        TreeSet<Float> xs = new TreeSet<>(), ys = new TreeSet<>();
        xs.add(0f); xs.add(W); ys.add(0f); ys.add(H);
        for (float[] h : holes) { xs.add(h[0]); xs.add(h[2]); ys.add(h[1]); ys.add(h[3]); }
        Float[] X = xs.toArray(new Float[0]), Y = ys.toArray(new Float[0]);
        VertexConsumer base = RenderUtil.solid(buf, s.tex);
        for (int i = 0; i < X.length - 1; i++) for (int j = 0; j < Y.length - 1; j++) {
            float cx = (X[i] + X[i + 1]) / 2, cy = (Y[j] + Y[j + 1]) / 2;
            boolean in = false;
            for (float[] h : holes) if (cx > h[0] && cx < h[2] && cy > h[1] && cy < h[3]) { in = true; break; }
            if (!in) flat(c, base, X[i], Y[j], X[i + 1], Y[j + 1], 0, light, s.color, null);
        }
        // 3) 요소
        for (CustomDesign.El e : s.els) {
            VertexConsumer vc = RenderUtil.solid(buf, e.tex != null ? e.tex : s.tex);
            int lt = e.glow ? RenderUtil.FULL : light;
            for (float[] r : cells(e, W, H)) {
                if (hits(r, hole)) continue; // 방범창과 겹치는 장식은 생략
                float[] own = e.ownUv ? r : null;
                switch (e.shape) {
                    case "raise" -> box(c, vc, r, Math.abs(e.depth), e.bevel, false, lt, e.color, own, zOf(e, 0));
                    case "recess" -> box(c, vc, r, Math.abs(e.depth), e.bevel, true, lt, e.color, own, 0);
                    case "groove" -> box(c, vc, r, Math.abs(e.depth), Math.min(r[2] - r[0], r[3] - r[1]) * 0.49f, true, lt, e.color, own, 0);
                    case "curve" -> curve(c, vc, r, e.depth, e.axis, lt, e.color, own);
                    case "rod" -> rod(c, vc, r, e.radius, e.axis, zOf(e, 0), lt, e.color, own);
                    case "dots" -> dots(c, vc, r, e, lt, own);
                    default -> flat(c, vc, r[0], r[1], r[2], r[3], zOf(e, 0.0012f), lt, e.color, own);
                }
            }
        }
    }

    private static boolean hits(float[] r, @Nullable float[] h) {
        return h != null && r[0] < h[2] && r[2] > h[0] && r[1] < h[3] && r[3] > h[1];
    }

    private static float zOf(CustomDesign.El e, float def) { return Float.isNaN(e.z) ? def : e.z; }

    /** 볼록(raise) 또는 오목(recess) 상자. b = 모따기 폭. z0 = 시작 높이 */
    private static void box(Ctx c, VertexConsumer vc, float[] r, float d, float b, boolean in, int lt, int col, @Nullable float[] own, float z0) {
        float x1 = r[0], y1 = r[1], x2 = r[2], y2 = r[3];
        b = Math.max(0, Math.min(b, Math.min(x2 - x1, y2 - y1) / 2 - 1e-4f));
        float zt = in ? z0 - d : z0 + d;           // 윗면(볼록) 또는 바닥(오목) 높이
        float ix1 = x1 + b, iy1 = y1 + b, ix2 = x2 - b, iy2 = y2 - b;
        float zb = z0 + (in ? 0 : 0.0004f);
        // 윗면/바닥
        flat(c, vc, ix1, iy1, ix2, iy2, zt, lt, col, own);
        float s = in ? -1 : 1;          // 볼록: 바깥쪽 법선, 오목: 구멍 안쪽을 향함
        float nb = b > 0 ? b : 0, nd = d;
        // 왼쪽
        face(c, vc, new float[][]{{x1, y1, zb}, {ix1, iy1, zt}, {ix1, iy2, zt}, {x1, y2, zb}}, new float[]{-s * nd, 0, nb}, lt, col, own);
        // 오른쪽
        face(c, vc, new float[][]{{x2, y1, zb}, {x2, y2, zb}, {ix2, iy2, zt}, {ix2, iy1, zt}}, new float[]{s * nd, 0, nb}, lt, col, own);
        // 아래
        face(c, vc, new float[][]{{x1, y1, zb}, {x2, y1, zb}, {ix2, iy1, zt}, {ix1, iy1, zt}}, new float[]{0, -s * nd, nb}, lt, col, own);
        // 위
        face(c, vc, new float[][]{{x1, y2, zb}, {ix1, iy2, zt}, {ix2, iy2, zt}, {x2, y2, zb}}, new float[]{0, s * nd, nb}, lt, col, own);
    }

    /** 곡면: depth > 0 볼록, < 0 오목. axis 'x' = 좌우로 휘는 원통(세로 기둥형), 'y' = 위아래로 휨 */
    private static void curve(Ctx c, VertexConsumer vc, float[] r, float depth, char axis, int lt, int col, @Nullable float[] own) {
        int n = 14;
        boolean ax = axis == 'x';
        float a1 = ax ? r[0] : r[1], a2 = ax ? r[2] : r[3];
        float b1 = ax ? r[1] : r[0], b2 = ax ? r[3] : r[2];
        float L = a2 - a1;
        float lift = depth > 0 ? 0.0004f : 0;
        for (int i = 0; i < n; i++) {
            float t0 = (float) i / n, t1 = (float) (i + 1) / n;
            float p0 = a1 + L * t0, p1 = a1 + L * t1;
            float z0 = depth * (float) Math.sin(Math.PI * t0) + lift, z1 = depth * (float) Math.sin(Math.PI * t1) + lift;
            float tm = (t0 + t1) / 2;
            float slope = depth * (float) (Math.PI / L * Math.cos(Math.PI * tm));
            float[] nl = ax ? new float[]{-slope, 0, 1} : new float[]{0, -slope, 1};
            float[][] q = ax ? new float[][]{{p0, b1, z0}, {p1, b1, z1}, {p1, b2, z1}, {p0, b2, z0}}
                    : new float[][]{{b1, p0, z0}, {b2, p0, z0}, {b2, p1, z1}, {b1, p1, z1}};
            face(c, vc, q, nl, lt, col, own);
            // 끝 마구리(옆면) – 곡면과 바탕 사이를 막음
            for (int k = 0; k < 2; k++) {
                float bb = k == 0 ? b1 : b2;
                float sgn = k == 0 ? -1 : 1;
                float[][] cap = ax ? new float[][]{{p0, bb, lift}, {p1, bb, lift}, {p1, bb, z1}, {p0, bb, z0}}
                        : new float[][]{{bb, p0, lift}, {bb, p1, lift}, {bb, p1, z1}, {bb, p0, z0}};
                float[] cn = ax ? new float[]{0, depth > 0 ? sgn : -sgn, 0} : new float[]{depth > 0 ? sgn : -sgn, 0, 0};
                face(c, vc, cap, cn, lt, col, own);
            }
        }
    }

    /** 봉(손잡이, 몰딩): 팔각 단면, axis 'x' 가로 / 'y' 세로. z = 면에서 띄운 거리 */
    private static void rod(Ctx c, VertexConsumer vc, float[] r, float rad, char axis, float zoff, int lt, int col, @Nullable float[] own) {
        boolean ax = axis == 'x';
        float a1 = ax ? r[0] : r[1], a2 = ax ? r[2] : r[3];
        float cb = ax ? (r[1] + r[3]) / 2 : (r[0] + r[2]) / 2;
        float cz = zoff + rad;
        int n = 8;
        for (int i = 0; i < n; i++) {
            double t0 = 2 * Math.PI * i / n, t1 = 2 * Math.PI * (i + 1) / n, tm = (t0 + t1) / 2;
            float b0 = cb + rad * (float) Math.cos(t0), z0 = cz + rad * (float) Math.sin(t0);
            float b1 = cb + rad * (float) Math.cos(t1), z1 = cz + rad * (float) Math.sin(t1);
            float nb = (float) Math.cos(tm), nz = (float) Math.sin(tm);
            float[][] q = ax ? new float[][]{{a1, b0, z0}, {a2, b0, z0}, {a2, b1, z1}, {a1, b1, z1}}
                    : new float[][]{{b0, a1, z0}, {b1, a1, z1}, {b1, a2, z1}, {b0, a2, z0}};
            face(c, vc, q, ax ? new float[]{0, nb, nz} : new float[]{nb, 0, nz}, lt, col, own);
        }
    }

    /** 점 패턴(미끄럼방지 돌기, 타공 장식 등): cols × rows 작은 볼록 사각 */
    private static void dots(Ctx c, VertexConsumer vc, float[] r, CustomDesign.El e, int lt, @Nullable float[] own) {
        float cw = (r[2] - r[0]) / e.cols, ch = (r[3] - r[1]) / e.rows;
        float sz = Math.min(cw, ch) * 0.45f;
        for (int i = 0; i < e.cols; i++) for (int j = 0; j < e.rows; j++) {
            float cx = r[0] + cw * (i + 0.5f), cy = r[1] + ch * (j + 0.5f);
            box(c, vc, new float[]{cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2}, Math.abs(e.depth), sz * 0.25f, e.depth < 0, lt, e.color, own, 0);
        }
    }
}

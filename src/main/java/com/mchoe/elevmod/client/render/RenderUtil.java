package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.ElevMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public final class RenderUtil {
    private RenderUtil() {}

    public static final int FULL = LightTexture.FULL_BRIGHT;
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();

    public static ResourceLocation tex(String name) {
        return TEX.computeIfAbsent(name, n -> new ResourceLocation(ElevMod.MODID, "textures/entity/" + n + ".png"));
    }

    /**
     * MultiBufferSource 는 고정 버퍼가 아닌 렌더타입끼리 하나의 BufferBuilder 를 공유하므로,
     * 다른 텍스처를 요청하면 이전에 받은 VertexConsumer 가 엉뚱한 텍스처로 그려진다.
     * 그래서 정점마다 해당 렌더타입 버퍼를 다시 가져오는 지연(lazy) 소비자를 돌려준다.
     */
    private record Lazy(MultiBufferSource buf, RenderType rt) implements VertexConsumer {
        private VertexConsumer t() { return buf.getBuffer(rt); }
        @Override public VertexConsumer vertex(double x, double y, double z) { return t().vertex(x, y, z); }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return t().color(r, g, b, a); }
        @Override public VertexConsumer uv(float u, float v) { return t().uv(u, v); }
        @Override public VertexConsumer overlayCoords(int u, int v) { return t().overlayCoords(u, v); }
        @Override public VertexConsumer uv2(int u, int v) { return t().uv2(u, v); }
        @Override public VertexConsumer normal(float x, float y, float z) { return t().normal(x, y, z); }
        @Override public void endVertex() { t().endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { t().defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { t().unsetDefaultColor(); }
    }

    public static VertexConsumer solid(MultiBufferSource buf, String tex) { return new Lazy(buf, RenderType.entityCutoutNoCull(tex(tex))); }

    public static VertexConsumer glass(MultiBufferSource buf, String tex) { return new Lazy(buf, RenderType.entityTranslucent(tex(tex))); }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv,
                          float nx, float ny, float nz, int light, int c) {
        vc.vertex(m, x, y, z).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    /** 텍스처를 1블록 단위로 반복하는 상자 */
    public static void box(PoseStack ps, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int light, int argb) {
        boxUV(ps, vc, x1, y1, z1, x2, y2, z2, light, argb, 1f);
    }

    public static void boxUV(PoseStack ps, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int light, int c, float tile) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float dx = (x2 - x1) * tile, dy = (y2 - y1) * tile, dz = (z2 - z1) * tile;
        // 위
        v(vc, m, n, x1, y2, z1, 0, 0, 0, 1, 0, light, c); v(vc, m, n, x1, y2, z2, 0, dz, 0, 1, 0, light, c);
        v(vc, m, n, x2, y2, z2, dx, dz, 0, 1, 0, light, c); v(vc, m, n, x2, y2, z1, dx, 0, 0, 1, 0, light, c);
        // 아래
        v(vc, m, n, x1, y1, z1, 0, 0, 0, -1, 0, light, c); v(vc, m, n, x2, y1, z1, dx, 0, 0, -1, 0, light, c);
        v(vc, m, n, x2, y1, z2, dx, dz, 0, -1, 0, light, c); v(vc, m, n, x1, y1, z2, 0, dz, 0, -1, 0, light, c);
        // -Z
        v(vc, m, n, x1, y1, z1, dx, dy, 0, 0, -1, light, c); v(vc, m, n, x1, y2, z1, dx, 0, 0, 0, -1, light, c);
        v(vc, m, n, x2, y2, z1, 0, 0, 0, 0, -1, light, c); v(vc, m, n, x2, y1, z1, 0, dy, 0, 0, -1, light, c);
        // +Z
        v(vc, m, n, x1, y1, z2, 0, dy, 0, 0, 1, light, c); v(vc, m, n, x2, y1, z2, dx, dy, 0, 0, 1, light, c);
        v(vc, m, n, x2, y2, z2, dx, 0, 0, 0, 1, light, c); v(vc, m, n, x1, y2, z2, 0, 0, 0, 0, 1, light, c);
        // -X
        v(vc, m, n, x1, y1, z1, 0, dy, -1, 0, 0, light, c); v(vc, m, n, x1, y1, z2, dz, dy, -1, 0, 0, light, c);
        v(vc, m, n, x1, y2, z2, dz, 0, -1, 0, 0, light, c); v(vc, m, n, x1, y2, z1, 0, 0, -1, 0, 0, light, c);
        // +X
        v(vc, m, n, x2, y1, z1, dz, dy, 1, 0, 0, light, c); v(vc, m, n, x2, y2, z1, dz, 0, 1, 0, 0, light, c);
        v(vc, m, n, x2, y2, z2, 0, 0, 1, 0, 0, light, c); v(vc, m, n, x2, y1, z2, 0, dy, 1, 0, 0, light, c);
    }

    /** 텍스처를 면마다 한 장으로 늘여 붙이는 상자 (패널, 문짝) */
    public static void panel(PoseStack ps, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int light, int c) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        v(vc, m, n, x1, y2, z1, 0, 0, 0, 1, 0, light, c); v(vc, m, n, x1, y2, z2, 0, 1, 0, 1, 0, light, c);
        v(vc, m, n, x2, y2, z2, 1, 1, 0, 1, 0, light, c); v(vc, m, n, x2, y2, z1, 1, 0, 0, 1, 0, light, c);
        v(vc, m, n, x1, y1, z1, 0, 0, 0, -1, 0, light, c); v(vc, m, n, x2, y1, z1, 1, 0, 0, -1, 0, light, c);
        v(vc, m, n, x2, y1, z2, 1, 1, 0, -1, 0, light, c); v(vc, m, n, x1, y1, z2, 0, 1, 0, -1, 0, light, c);
        v(vc, m, n, x1, y1, z1, 1, 1, 0, 0, -1, light, c); v(vc, m, n, x1, y2, z1, 1, 0, 0, 0, -1, light, c);
        v(vc, m, n, x2, y2, z1, 0, 0, 0, 0, -1, light, c); v(vc, m, n, x2, y1, z1, 0, 1, 0, 0, -1, light, c);
        v(vc, m, n, x1, y1, z2, 0, 1, 0, 0, 1, light, c); v(vc, m, n, x2, y1, z2, 1, 1, 0, 0, 1, light, c);
        v(vc, m, n, x2, y2, z2, 1, 0, 0, 0, 1, light, c); v(vc, m, n, x1, y2, z2, 0, 0, 0, 0, 1, light, c);
        v(vc, m, n, x1, y1, z1, 0, 1, -1, 0, 0, light, c); v(vc, m, n, x1, y1, z2, 1, 1, -1, 0, 0, light, c);
        v(vc, m, n, x1, y2, z2, 1, 0, -1, 0, 0, light, c); v(vc, m, n, x1, y2, z1, 0, 0, -1, 0, 0, light, c);
        v(vc, m, n, x2, y1, z1, 1, 1, 1, 0, 0, light, c); v(vc, m, n, x2, y2, z1, 1, 0, 1, 0, 0, light, c);
        v(vc, m, n, x2, y2, z2, 0, 0, 1, 0, 0, light, c); v(vc, m, n, x2, y1, z2, 0, 1, 1, 0, 0, light, c);
    }

    /**
     * 방범창(투명 유리창)이 뚫린 문짝: 텍스처를 한 장으로 늘여 붙인 상자에서 (wx1,wy1)-(wx2,wy2) 구멍을 뺀 모양.
     * 구멍 안쪽 면(창틀)과 고무 개스킷, 투명 유리까지 그린다.
     */
    public static void panelWindow(PoseStack ps, MultiBufferSource buf, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2,
                                   float wx1, float wy1, float wx2, float wy2, int light, int c) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float W = x2 - x1, H = y2 - y1;
        // 앞(+Z)·뒤(-Z) 면: 창 둘레 4조각
        float[][] parts = {{x1, y1, x2, wy1}, {x1, wy2, x2, y2}, {x1, wy1, wx1, wy2}, {wx2, wy1, x2, wy2}};
        for (float[] q : parts) {
            if (q[2] - q[0] <= 0 || q[3] - q[1] <= 0) continue;
            float ua = (q[0] - x1) / W, ub = (q[2] - x1) / W, va = (y2 - q[1]) / H, vb = (y2 - q[3]) / H;
            v(vc, m, n, q[0], q[1], z2, ua, va, 0, 0, 1, light, c); v(vc, m, n, q[2], q[1], z2, ub, va, 0, 0, 1, light, c);
            v(vc, m, n, q[2], q[3], z2, ub, vb, 0, 0, 1, light, c); v(vc, m, n, q[0], q[3], z2, ua, vb, 0, 0, 1, light, c);
            float ra = (x2 - q[0]) / W, rb = (x2 - q[2]) / W;
            v(vc, m, n, q[0], q[1], z1, ra, va, 0, 0, -1, light, c); v(vc, m, n, q[0], q[3], z1, ra, vb, 0, 0, -1, light, c);
            v(vc, m, n, q[2], q[3], z1, rb, vb, 0, 0, -1, light, c); v(vc, m, n, q[2], q[1], z1, rb, va, 0, 0, -1, light, c);
        }
        // 바깥 테두리 면 (위/아래/좌/우)
        v(vc, m, n, x1, y2, z1, 0, 0, 0, 1, 0, light, c); v(vc, m, n, x1, y2, z2, 0, 1, 0, 1, 0, light, c);
        v(vc, m, n, x2, y2, z2, 1, 1, 0, 1, 0, light, c); v(vc, m, n, x2, y2, z1, 1, 0, 0, 1, 0, light, c);
        v(vc, m, n, x1, y1, z1, 0, 0, 0, -1, 0, light, c); v(vc, m, n, x2, y1, z1, 1, 0, 0, -1, 0, light, c);
        v(vc, m, n, x2, y1, z2, 1, 1, 0, -1, 0, light, c); v(vc, m, n, x1, y1, z2, 0, 1, 0, -1, 0, light, c);
        v(vc, m, n, x1, y1, z1, 0, 1, -1, 0, 0, light, c); v(vc, m, n, x1, y1, z2, 1, 1, -1, 0, 0, light, c);
        v(vc, m, n, x1, y2, z2, 1, 0, -1, 0, 0, light, c); v(vc, m, n, x1, y2, z1, 0, 0, -1, 0, 0, light, c);
        v(vc, m, n, x2, y1, z1, 1, 1, 1, 0, 0, light, c); v(vc, m, n, x2, y2, z1, 1, 0, 1, 0, 0, light, c);
        v(vc, m, n, x2, y2, z2, 0, 0, 1, 0, 0, light, c); v(vc, m, n, x2, y1, z2, 0, 1, 1, 0, 0, light, c);
        // 창틀(구멍 안쪽 면) – 어두운 고무 개스킷 색
        int g = 0xFF1E2024;
        fill(ps, buf, wx1 - 0.008f, wy1 - 0.008f, z1 - 0.0015f, wx2 + 0.008f, wy1, z2 + 0.0015f, light, g);
        fill(ps, buf, wx1 - 0.008f, wy2, z1 - 0.0015f, wx2 + 0.008f, wy2 + 0.008f, z2 + 0.0015f, light, g);
        fill(ps, buf, wx1 - 0.008f, wy1, z1 - 0.0015f, wx1, wy2, z2 + 0.0015f, light, g);
        fill(ps, buf, wx2, wy1, z1 - 0.0015f, wx2 + 0.008f, wy2, z2 + 0.0015f, light, g);
        // 투명 유리 (양면에서 보임)
        quadZ(ps, glass(buf, "window_glass"), wx1, wy1, wx2, wy2, (z1 + z2) / 2, true, light, 0xFFFFFFFF);
    }

    /** 문짝 하나의 방범창 위치 (없으면 null). 문짝 중앙, 바닥에서 1.0~1.65m 의 세로로 긴 창 */
    public static float[] windowRect(boolean on, float x1, float x2, float y1, float y2) {
        if (!on || y2 - y1 < 1.5f || x2 - x1 < 0.22f) return null;
        float cx = (x1 + x2) / 2, hw = Math.min(0.065f, (x2 - x1) * 0.3f);
        float wy1 = y1 + 1.0f, wy2 = Math.min(y1 + 1.65f, y2 - 0.15f);
        if (wy2 - wy1 < 0.2f) return null;
        return new float[]{cx - hw, wy1, cx + hw, wy2};
    }

    /** z 평면에 놓인 사각형 (front=true 면 +Z 를 바라봄) */
    public static void quadZ(PoseStack ps, VertexConsumer vc, float x1, float y1, float x2, float y2, float z, boolean front, int light, int c) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float nz = front ? 1 : -1;
        if (front) {
            v(vc, m, n, x1, y1, z, 0, 1, 0, 0, nz, light, c); v(vc, m, n, x2, y1, z, 1, 1, 0, 0, nz, light, c);
            v(vc, m, n, x2, y2, z, 1, 0, 0, 0, nz, light, c); v(vc, m, n, x1, y2, z, 0, 0, 0, 0, nz, light, c);
        } else {
            v(vc, m, n, x2, y1, z, 0, 1, 0, 0, nz, light, c); v(vc, m, n, x1, y1, z, 1, 1, 0, 0, nz, light, c);
            v(vc, m, n, x1, y2, z, 1, 0, 0, 0, nz, light, c); v(vc, m, n, x2, y2, z, 0, 0, 0, 0, nz, light, c);
        }
    }

    /** y 평면 사각형 (down=true 면 아래를 바라봄, 천장 조명용) */
    public static void quadY(PoseStack ps, VertexConsumer vc, float x1, float z1, float x2, float z2, float y, boolean down, int light, int c) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float ny = down ? -1 : 1;
        v(vc, m, n, x1, y, z1, 0, 0, 0, ny, 0, light, c); v(vc, m, n, x2, y, z1, 1, 0, 0, ny, 0, light, c);
        v(vc, m, n, x2, y, z2, 1, 1, 0, ny, 0, light, c); v(vc, m, n, x1, y, z2, 0, 1, 0, ny, 0, light, c);
    }

    /** 둥근 봉(핸드레일 등) – 축 방향 'x','y','z', 팔각 근사 */
    public static void rod(PoseStack ps, MultiBufferSource buf, char axis, float a1, float a2, float c1, float c2, float r, int light, int color) {
        ps.pushPose();
        switch (axis) {
            case 'x' -> { ps.translate(0, c1, c2); }
            case 'z' -> { ps.translate(c1, c2, 0); }
            default -> { ps.translate(c1, 0, c2); }
        }
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            float k = r * 0.92f;
            switch (axis) {
                case 'x' -> { ps.mulPose(Axis.XP.rotationDegrees(i * 45)); fill(ps, buf, a1, -k, -k, a2, k, k, light, color); }
                case 'z' -> { ps.mulPose(Axis.ZP.rotationDegrees(i * 45)); fill(ps, buf, -k, -k, a1, k, k, a2, light, color); }
                default -> { ps.mulPose(Axis.YP.rotationDegrees(i * 45)); fill(ps, buf, -k, a1, -k, k, a2, k, light, color); }
            }
            ps.popPose();
        }
        ps.popPose();
    }

    public static VertexConsumer cut(MultiBufferSource buf, String tex) { return new Lazy(buf, RenderType.entityCutoutNoCull(tex(tex))); }

    /** 단색 상자 (흰 텍스처에 색 입힘) */
    public static void fill(PoseStack ps, MultiBufferSource buf, float x1, float y1, float z1, float x2, float y2, float z2, int light, int argb) {
        boxUV(ps, solid(buf, "white"), x1, y1, z1, x2, y2, z2, light, argb, 1f);
    }

    /**
     * 현재 좌표계의 (x, y, z) 지점에, +Z 방향을 바라보는 평면에 글자를 가운데 정렬로 그린다.
     * size = 글자 높이(블록).
     */
    public static void text(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, float size, int color) {
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(x, y, z);
        float sc = size / 9f;
        ps.scale(sc, -sc, sc);
        float w = font.width(s);
        font.drawInBatch(s, -w / 2f, -4f, color, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, FULL);
        ps.popPose();
    }

    /** -Z 방향(반대편)을 바라보는 글자 */
    public static void textBack(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, float size, int color) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        text(ps, buf, s, 0, 0, 0, size, color);
        ps.popPose();
    }

    public static String arrow(int dir) { return dir > 0 ? "▲" : dir < 0 ? "▼" : ""; }
}

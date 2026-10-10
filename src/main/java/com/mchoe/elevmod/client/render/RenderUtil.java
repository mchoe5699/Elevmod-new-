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

    public static VertexConsumer solid(MultiBufferSource buf, String tex) { return buf.getBuffer(RenderType.entityCutoutNoCull(tex(tex))); }

    public static VertexConsumer glass(MultiBufferSource buf, String tex) { return buf.getBuffer(RenderType.entityTranslucent(tex(tex))); }

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

    public static VertexConsumer cut(MultiBufferSource buf, String tex) { return buf.getBuffer(RenderType.entityCutoutNoCull(tex(tex))); }

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

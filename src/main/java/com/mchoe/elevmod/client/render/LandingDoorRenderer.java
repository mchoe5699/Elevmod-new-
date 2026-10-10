package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import java.util.List;

/**
 * 승강장 출입구: 쟘(틀) + 측면/상부 마감(리빌) + 알루미늄 실(문턱) + 승강장 문짝.
 * 좌표: 칸 중심 기준, +Z = 승강장(홀) 쪽, -Z(-0.5) = 승강로 쪽 면. 문짝은 승강로 쪽에 매달려 있다.
 */
public class LandingDoorRenderer implements BlockEntityRenderer<LandingDoorBlockEntity> {
    private static final int WHITE = 0xFFFFFFFF;
    // 문짝 레이어 z (0 = 가장 빠른 문짝, 승강로/카 쪽)
    private static final float PANEL_Z0 = -0.462f, PANEL_T = 0.028f, PANEL_STEP = 0.034f;

    public LandingDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LandingDoorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!be.master) return;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-be.facing().toYRot()));
        float p = be.anim(pt);
        int W = be.w, H = be.h;
        float cw = Math.min(be.clearW, W), chh = Math.min(be.clearH, H);
        Spec.DoorType t = be.type();
        Spec.Theme th = be.themeE();
        Spec.Jamb jb = be.jambE();
        float x1 = -0.5f + (W - cw) / 2f, x2 = x1 + cw;      // 유효 개구
        float left = -0.5f, right = W - 0.5f;                  // 문 칸 전체
        float revealZ = -0.32f;                                // 리빌 마감 시작(문짝 바로 앞)
        String jt = th == Spec.Theme.GOLD ? "jamb_gold" : "jamb";
        VertexConsumer jamb = RenderUtil.solid(buf, jt);

        // ---- 칸 안의 남는 부분(유효 개구 밖) 채움 + 리빌 마감
        if (x1 > left + 0.001f) {
            RenderUtil.box(ps, jamb, left, 0, revealZ, x1, H, 0.5f, light, WHITE);
            RenderUtil.box(ps, jamb, left, 0, -0.5f, x1, H, revealZ, light, 0xFF8A8E92); // 수납부(문짝이 숨는 곳)
        } else RenderUtil.box(ps, jamb, x1 - 0.015f, 0, revealZ, x1, chh, 0.5f, light, WHITE);
        if (x2 < right - 0.001f) {
            RenderUtil.box(ps, jamb, x2, 0, revealZ, right, H, 0.5f, light, WHITE);
            RenderUtil.box(ps, jamb, x2, 0, -0.5f, right, H, revealZ, light, 0xFF8A8E92);
        } else RenderUtil.box(ps, jamb, x2, 0, revealZ, x2 + 0.015f, chh, 0.5f, light, WHITE);
        if (chh < H - 0.001f) {
            RenderUtil.box(ps, jamb, x1, chh, revealZ, x2, H, 0.5f, light, WHITE);
            RenderUtil.box(ps, jamb, x1, chh, -0.5f, x2, H, revealZ, light, 0xFF8A8E92);
        } else RenderUtil.box(ps, jamb, x1, chh, revealZ, x2, chh + 0.015f, 0.5f, light, WHITE);

        // ---- 쟘(홀 쪽 테두리 틀)
        float jf = jb.face();
        if (jf > 0) {
            float fz1 = 0.5f, fz2 = 0.52f;
            float head = jf * 1.3f;
            RenderUtil.box(ps, jamb, x1 - jf, 0, fz1, x1, chh + head, fz2, light, WHITE);
            RenderUtil.box(ps, jamb, x2, 0, fz1, x2 + jf, chh + head, fz2, light, WHITE);
            RenderUtil.box(ps, jamb, x1, chh, fz1, x2, chh + head, fz2, light, WHITE);
            // 쟘 안쪽 모서리 그림자선
            RenderUtil.fill(ps, buf, x1 - 0.006f, 0, fz2, x1, chh, fz2 + 0.001f, light, 0xFF6A6E72);
            RenderUtil.fill(ps, buf, x2, 0, fz2, x2 + 0.006f, chh, fz2 + 0.001f, light, 0xFF6A6E72);
        }

        // ---- 알루미늄 실(문턱): 승강로 면부터 홀 쪽까지
        RenderUtil.box(ps, RenderUtil.solid(buf, "sill"), x1 - 0.03f, -0.004f, -0.5f, x2 + 0.03f, 0.004f, -0.3f, light, WHITE);

        // ---- 문짝
        boolean glass = th == Spec.Theme.GLASS;
        String tex = glass ? "door_glass" : "door_" + th.tex();
        VertexConsumer vc = glass ? RenderUtil.glass(buf, tex) : RenderUtil.solid(buf, tex);
        if (t == Spec.DoorType.SWING) {
            ps.pushPose();
            ps.translate(x1, 0, 0.42f);
            ps.mulPose(Axis.YP.rotationDegrees(-90 * p));
            RenderUtil.panel(ps, vc, 0, 0, -0.045f, cw, chh, 0, light, WHITE);
            RenderUtil.fill(ps, buf, cw - 0.12f, 0.95f, 0, cw - 0.08f, 1.08f, 0.05f, light, 0xFF303030);
            if (be.window && chh >= 1.8f) RenderUtil.fill(ps, buf, cw / 2 - 0.1f, 1.2f, 0.001f, cw / 2 + 0.1f, 1.6f, 0.004f, light, 0xFF1C2833);
            ps.popPose();
        } else {
            boolean mirror = be.rightHand && t.sideOpening();
            List<float[]> rects = DoorLayout.panels(t, cw, chh, p);
            for (float[] r : rects) {
                float a1 = mirror ? cw - r[1] : r[0], a2 = mirror ? cw - r[0] : r[1];
                float px1 = x1 + a1, px2 = x1 + a2;
                float z1 = PANEL_Z0 + r[4] * PANEL_STEP, z2 = z1 + PANEL_T;
                if (r[5] == 1) { RenderUtil.fill(ps, buf, px1, r[2], z1, px2, r[3], z2, light, 0xFF6E7378); continue; }
                RenderUtil.panel(ps, vc, px1, r[2], z1, px2, r[3], z2, light, WHITE);
                if (be.window && r[3] - r[2] > 1.5f && px2 - px1 > 0.25f) {
                    float cx = (px1 + px2) / 2;
                    RenderUtil.fill(ps, buf, cx - 0.06f, 1.2f, z2, cx + 0.06f, 1.55f, z2 + 0.003f, light, 0xFF1C2833);
                }
            }
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LandingDoorBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 96; }
}

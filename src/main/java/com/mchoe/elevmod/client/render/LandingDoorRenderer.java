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

public class LandingDoorRenderer implements BlockEntityRenderer<LandingDoorBlockEntity> {
    public LandingDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LandingDoorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!be.master) return;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-be.facing().toYRot()));
        float p = be.anim(pt);
        float W = be.w, H = be.h;
        Spec.DoorType t = be.type();
        Spec.Theme th = be.themeE();
        boolean glass = th == Spec.Theme.GLASS;
        String tex = glass ? "door_glass" : "door_" + th.tex();
        VertexConsumer vc = glass ? RenderUtil.glass(buf, tex) : RenderUtil.solid(buf, tex);
        // 문턱(실)
        RenderUtil.fill(ps, buf, -0.5f, 0, -0.5f, W - 0.5f, 0.02f, -0.22f, light, 0xFFB0B4B8);
        if (t == Spec.DoorType.SWING) {
            // 여닫이: 홀 쪽으로 열림
            ps.pushPose();
            ps.translate(-0.5, 0, 0.3);
            ps.mulPose(Axis.YP.rotationDegrees(-90 * p));
            RenderUtil.box(ps, vc, 0, 0, -0.05f, W, H - 0.02f, 0, light, 0xFFFFFFFF);
            RenderUtil.fill(ps, buf, W - 0.15f, 0.95f, 0, W - 0.1f, 1.05f, 0.05f, light, 0xFF303030);
            if (be.window && H >= 2) RenderUtil.fill(ps, buf, W / 2 - 0.12f, 1.2f, 0.001f, W / 2 + 0.12f, 1.6f, 0.004f, light, 0xFF1C2833);
            ps.popPose();
        } else {
            List<float[]> rects = DoorLayout.panels(t, W, H, p);
            for (float[] r : rects) {
                float x1 = -0.5f + r[0], x2 = -0.5f + r[1];
                float z1 = -0.47f + r[4] * 0.07f, z2 = z1 + 0.06f;
                if (r[5] == 1) { RenderUtil.fill(ps, buf, x1, r[2], z1, x2, r[3], z2, light, 0xFF6E7378); continue; }
                RenderUtil.box(ps, vc, x1, r[2], z1, x2, r[3], z2, light, 0xFFFFFFFF);
                if (be.window && r[3] - r[2] > 1.5f && x2 - x1 > 0.3f) {
                    float cx = (x1 + x2) / 2;
                    RenderUtil.fill(ps, buf, cx - 0.07f, 1.2f, z2, cx + 0.07f, 1.6f, z2 + 0.003f, light, 0xFF1C2833);
                }
            }
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LandingDoorBlockEntity be) { return true; }
}

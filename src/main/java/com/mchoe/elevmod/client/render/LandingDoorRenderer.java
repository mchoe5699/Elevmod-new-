package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import com.mchoe.elevmod.client.ClientEvents;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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
        float p = progress(be, pt);
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
            float[] sw = glass ? null : RenderUtil.windowRect(be.window, 0, cw, 0, chh);
            if (sw == null) RenderUtil.panel(ps, vc, 0, 0, -0.045f, cw, chh, 0, light, WHITE);
            else RenderUtil.panelWindow(ps, buf, vc, 0, 0, -0.045f, cw, chh, 0, sw[0], sw[1], sw[2], sw[3], light, WHITE);
            RenderUtil.fill(ps, buf, cw - 0.12f, 0.95f, 0, cw - 0.08f, 1.08f, 0.05f, light, 0xFF303030);
            ps.popPose();
        } else {
            boolean mirror = be.rightHand && t.sideOpening();
            List<float[]> rects = DoorLayout.panels(t, cw, chh, p);
            for (float[] r : rects) {
                float a1 = mirror ? cw - r[1] : r[0], a2 = mirror ? cw - r[0] : r[1];
                float px1 = x1 + a1, px2 = x1 + a2;
                float z1 = PANEL_Z0 + r[4] * PANEL_STEP, z2 = z1 + PANEL_T;
                if (r[5] == 1) { RenderUtil.fill(ps, buf, px1, r[2], z1, px2, r[3], z2, light, 0xFF6E7378); continue; }
                float[] win = glass ? null : RenderUtil.windowRect(be.window, px1, px2, r[2], r[3]);
                if (win == null) RenderUtil.panel(ps, vc, px1, r[2], z1, px2, r[3], z2, light, WHITE);
                else RenderUtil.panelWindow(ps, buf, vc, px1, r[2], z1, px2, r[3], z2, win[0], win[1], win[2], win[3], light, WHITE);
            }
        }
        ps.popPose();
    }

    /**
     * 실제 엘리베이터는 카 도어의 베인(클러치)이 승강장 문의 롤러를 물고 함께 움직이므로
     * 카가 이 층에 착상해 있으면 승강장 문은 카 도어와 완전히 같은 위치로 움직인다.
     * (삼각키로 수동 개방 중이거나 카가 없으면 자체 애니메이션)
     */
    static float progress(LandingDoorBlockEntity be, float pt) {
        float own = be.anim(pt);
        if (be.ctrl == null || be.manual) return own;
        BlockPos pos = be.getBlockPos();
        for (ElevatorCarEntity car : ClientEvents.cars()) {
            if (car.isRemoved() || car.cCur == null || car.cPrev == null || !be.ctrl.equals(car.controller())) continue;
            Vec3 o = car.cPrev.lerp(car.cCur, pt);
            if (Math.abs(o.y - pos.getY()) > 0.35) continue;
            Direction cf = car.facing();
            boolean front = be.facing() == cf, rear = be.facing() == cf.getOpposite();
            if (!front && !rear) continue;
            // 문 평면과 카 중심 사이 거리(정면 방향) – 같은 승강로인지 확인
            double along = (pos.getX() + 0.5 - o.x) * cf.getStepX() + (pos.getZ() + 0.5 - o.z) * cf.getStepZ();
            if (Math.abs(along) > car.carD() / 2 + 1.6) continue;
            boolean open = front ? car.state().getBoolean("fo2") : car.state().getBoolean("ro2");
            if (!open) return own;
            return car.doorPrev + (car.doorCur - car.doorPrev) * pt;
        }
        return own;
    }

    @Override
    public boolean shouldRenderOffScreen(LandingDoorBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 96; }
}

package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.MachineBlock;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** 권상기 쉬브 / 조속기 풀리 회전 */
public class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    public MachineRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MachineBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!(be.getBlockState().getBlock() instanceof MachineBlock mb)) return;
        boolean rotated = be.getBlockState().getValue(MachineBlock.ROTATED);
        ps.pushPose();
        boolean grp = RenderUtil.groupRotate(ps, be.getBlockPos(), be.yaw, be.pivX, be.pivZ);
        if (rotated) ControllerRenderer.renderRotated(be.getBlockState().setValue(MachineBlock.ROTATED, false), grp ? 0 : be.yaw, ps, buf, light, overlay);
        if (mb.kind == MachineBlock.Kind.HYDRAULIC) { ps.popPose(); return; }
        float angle = be.anglePrev + (be.angle - be.anglePrev) * pt;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        if (rotated && !grp) ps.mulPose(Axis.YP.rotationDegrees(-be.yaw));
        ps.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(MachineBlock.FACING).toYRot()));
        VertexConsumer vc = RenderUtil.solid(buf, "sheave");
        switch (mb.kind) {
            case GEARED -> { ps.translate(0.38, 0.6, 0); ps.mulPose(Axis.XP.rotationDegrees(angle)); disc(ps, vc, 0.06f, 0.3f, light); }
            case GEARLESS -> { ps.translate(0, 0.55, 0.45); ps.mulPose(Axis.ZP.rotationDegrees(angle)); discZ(ps, vc, 0.06f, 0.36f, light); }
            case GOVERNOR -> { ps.translate(0, 0.72, 0); ps.mulPose(Axis.XP.rotationDegrees(angle * 2)); disc(ps, vc, 0.04f, 0.22f, light); }
            default -> {}
        }
        ps.popPose();
        ps.popPose();
    }

    /** X 축 회전 원판(팔각 근사) */
    private void disc(PoseStack ps, VertexConsumer vc, float halfT, float r, int light) {
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(i * 45));
            RenderUtil.box(ps, vc, -halfT, -r, -r * 0.42f, halfT, r, r * 0.42f, light, 0xFFFFFFFF);
            RenderUtil.box(ps, vc, -halfT, -r * 0.42f, -r, halfT, r * 0.42f, r, light, 0xFFFFFFFF);
            ps.popPose();
        }
    }

    private void discZ(PoseStack ps, VertexConsumer vc, float halfT, float r, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(90));
        disc(ps, vc, halfT, r, light);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(MachineBlockEntity be) { return true; }
}

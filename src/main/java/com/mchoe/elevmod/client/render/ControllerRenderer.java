package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.ControllerBlock;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** 1° 단위로 돌린 제어반: 블록 모델을 돌려서 그림 */
public class ControllerRenderer implements BlockEntityRenderer<ControllerBlockEntity> {
    public ControllerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ControllerBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!st.hasProperty(ControllerBlock.ROTATED) || !st.getValue(ControllerBlock.ROTATED)) return;
        ps.pushPose();
        boolean grp = RenderUtil.groupRotate(ps, be.getBlockPos(), be.yaw, be.pivX, be.pivZ);
        renderRotated(st.setValue(ControllerBlock.ROTATED, false), grp ? 0 : be.yaw, ps, buf, light, overlay);
        ps.popPose();
    }

    static void renderRotated(BlockState st, float yaw, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, overlay, ModelData.EMPTY, null);
        ps.popPose();
    }
}

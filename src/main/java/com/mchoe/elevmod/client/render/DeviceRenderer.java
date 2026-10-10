package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.DeviceBlock;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;

public class DeviceRenderer implements BlockEntityRenderer<DeviceBlockEntity> {
    private static final int FULL = RenderUtil.FULL;
    private static final int BLACK = 0xFF080808, AMBER = 0xFFFFB020, OFF = 0xFFC8CCD0, RED = 0xFFFF2A1A, ORANGE = 0xFFFF8A20;

    public DeviceRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static String status(DeviceBlockEntity d) {
        return d.status.isEmpty() ? "" : I18n.get("elevmod.status." + d.status);
    }

    @Override
    public void render(DeviceBlockEntity d, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!(d.getBlockState().getBlock() instanceof DeviceBlock db)) return;
        DeviceBlock.Kind k = db.kind;
        Direction f = d.getBlockState().getValue(DeviceBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        float z = -0.5f + (float) k.depth / 16f + 0.001f;
        float hw = (float) (k.x2 - k.x1) / 32f;
        float y1 = (float) k.y1 / 16f, y2 = (float) k.y2 / 16f;
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        String arrow = RenderUtil.arrow(d.dir);
        boolean linked = d.ctrl != null;
        switch (k) {
            case HALL_BUTTON -> buttons(ps, buf, d, z, 0.52f, 0.2f, light);
            case HALL_PANEL -> {
                RenderUtil.fill(ps, buf, -hw + 0.04f, 0.62f, z, hw - 0.04f, y2 - 0.05f, z + 0.002f, FULL, BLACK);
                if (linked) {
                    RenderUtil.text(ps, buf, arrow + d.text, 0, 0.77f, z + 0.003f, 0.14f, ORANGE);
                    String s = status(d);
                    if (!s.isEmpty()) RenderUtil.text(ps, buf, s, 0, 0.66f, z + 0.003f, 0.04f, 0xFFFFD040);
                }
                buttons(ps, buf, d, z, 0.36f, 0.17f, light);
            }
            case IND_SEGMENT, IND_DOT -> {
                RenderUtil.fill(ps, buf, -hw + 0.03f, y1 + 0.03f, z, hw - 0.03f, y2 - 0.03f, z + 0.002f, FULL, BLACK);
                if (!linked) break;
                int col = k == DeviceBlock.Kind.IND_SEGMENT ? RED : ORANGE;
                float ay = 0.5f;
                if (k == DeviceBlock.Kind.IND_DOT && d.moving && d.dir != 0) ay += ((time % 10) / 10f - 0.5f) * 0.08f * d.dir; // 흐르는 화살표
                RenderUtil.text(ps, buf, arrow, -0.25f, ay, z + 0.003f, 0.16f, col);
                RenderUtil.text(ps, buf, d.text, 0.08f, 0.5f, z + 0.003f, 0.22f, col);
                String s = status(d);
                if (!s.isEmpty()) RenderUtil.text(ps, buf, s, 0, 0.32f, z + 0.003f, 0.045f, col);
            }
            case IND_LCD -> {
                RenderUtil.fill(ps, buf, -hw + 0.03f, y1 + 0.03f, z, hw - 0.03f, y2 - 0.03f, z + 0.002f, FULL, 0xFF123A78);
                if (!linked) break;
                RenderUtil.text(ps, buf, arrow, -0.28f, 0.55f, z + 0.003f, 0.18f, 0xFF7FE3FF);
                RenderUtil.text(ps, buf, d.text, 0.06f, 0.55f, z + 0.003f, 0.24f, 0xFFFFFFFF);
                String s = status(d);
                long dayTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getDayTime() % 24000;
                int hh = (int) ((dayTime / 1000 + 6) % 24), mm = (int) (dayTime % 1000 * 60 / 1000);
                String line2 = !s.isEmpty() ? s : d.letter + I18n.get("elevmod.lcd.car") + "  " + String.format("%02d:%02d", hh, mm);
                RenderUtil.text(ps, buf, line2, 0, 0.28f, z + 0.003f, 0.05f, 0xFFDDEEFF);
            }
            case IND_LAMP -> {
                RenderUtil.fill(ps, buf, -0.5f, y1 + 0.02f, z, 0.5f, y2 - 0.02f, z + 0.002f, light, 0xFF2A2018);
                int n = d.names.size();
                if (n == 0) break;
                float cell = 0.84f / n;
                for (int i = 0; i < n; i++) {
                    String nm = d.names.get(i);
                    boolean on = nm.equals(d.text);
                    RenderUtil.text(ps, buf, nm, -0.42f + cell * (i + 0.5f), 0.5f, z + 0.003f, Math.min(0.1f, cell * 0.8f), on ? 0xFFFFD060 : 0xFF5A5040);
                }
                RenderUtil.text(ps, buf, "▲", 0.47f, 0.53f, z + 0.003f, 0.05f, d.dir > 0 ? 0xFFFFD060 : 0xFF5A5040);
                RenderUtil.text(ps, buf, "▼", 0.47f, 0.45f, z + 0.003f, 0.05f, d.dir < 0 ? 0xFFFFD060 : 0xFF5A5040);
            }
            case LANTERN -> {
                RenderUtil.fill(ps, buf, -hw + 0.02f, y1 + 0.02f, z, hw - 0.02f, y2 - 0.02f, z + 0.002f, light, 0xFF1A1A1A);
                boolean up = d.lanUp || d.lanAny, dn = d.lanDown || d.lanAny;
                RenderUtil.text(ps, buf, "▲", -0.15f, 0.5f, z + 0.003f, 0.2f, up ? 0xFF40FF70 : 0xFF404040);
                RenderUtil.text(ps, buf, "▼", 0.15f, 0.5f, z + 0.003f, 0.2f, dn ? 0xFFFF4030 : 0xFF404040);
            }
            case FIRE_SWITCH -> {
                boolean on = "fire".equals(d.status);
                RenderUtil.text(ps, buf, I18n.get("elevmod.label.fire"), 0, 0.62f, z + 0.003f, 0.06f, 0xFFFFFFFF);
                RenderUtil.fill(ps, buf, -0.06f, 0.36f, z, 0.06f, 0.48f, z + 0.01f, on ? FULL : light, on ? 0xFFFF2020 : 0xFF601010);
            }
            case DESTINATION -> {
                RenderUtil.fill(ps, buf, -hw + 0.05f, 0.5f, z, hw - 0.05f, y2 - 0.08f, z + 0.002f, FULL, 0xFF0E1A2A);
                RenderUtil.text(ps, buf, I18n.get(d.dumbwaiter ? "elevmod.label.send" : "elevmod.label.destination"), 0, 0.78f, z + 0.003f, 0.06f, 0xFFFFFFFF);
                if (linked) RenderUtil.text(ps, buf, d.letter + " " + arrow + d.text, 0, 0.62f, z + 0.003f, 0.08f, 0xFF7FE3FF);
            }
            case CALL_RECEIVER -> RenderUtil.fill(ps, buf, -0.03f, 0.47f, z, 0.03f, 0.53f, z + 0.004f, linked ? FULL : light, linked ? 0xFF30FF60 : 0xFF305030);
        }
        ps.popPose();
    }

    private void buttons(PoseStack ps, MultiBufferSource buf, DeviceBlockEntity d, float z, float cy, float gap, int light) {
        boolean both = d.hasUp && d.hasDown;
        float s = 0.065f;
        if (d.hasUp) {
            float y = both ? cy + gap / 2 : cy;
            RenderUtil.fill(ps, buf, -s, y - s, z, s, y + s, z + 0.015f, d.upLit ? FULL : light, d.upLit ? AMBER : OFF);
            RenderUtil.text(ps, buf, "▲", 0, y, z + 0.016f, 0.08f, 0xFF202020);
        }
        if (d.hasDown) {
            float y = both ? cy - gap / 2 : cy;
            RenderUtil.fill(ps, buf, -s, y - s, z, s, y + s, z + 0.015f, d.downLit ? FULL : light, d.downLit ? AMBER : OFF);
            RenderUtil.text(ps, buf, "▼", 0, y, z + 0.016f, 0.08f, 0xFF202020);
        }
    }
}

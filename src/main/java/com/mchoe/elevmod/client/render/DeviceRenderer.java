package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.DeviceBlock;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;

/** 승강장 장치 표시면 (원형 호출버튼, 층표시기, 홀랜턴 렌즈 등) */
public class DeviceRenderer implements BlockEntityRenderer<DeviceBlockEntity> {
    private static final int FULL = RenderUtil.FULL;
    private static final int BLACK = 0xFF060608, RED = 0xFFFF2A1A, ORANGE = 0xFFFF9020, WHITE = 0xFFFFFFFF;

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
        float z = -0.5f + (float) k.depth / 16f + 0.0015f;
        float hw = (float) (k.x2 - k.x1) / 32f;
        float y1 = (float) k.y1 / 16f, y2 = (float) k.y2 / 16f;
        float cy = (y1 + y2) / 2;
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        String arrow = RenderUtil.arrow(d.dir);
        boolean linked = d.ctrl != null;
        switch (k) {
            case HALL_BUTTON -> buttons(ps, buf, d, z, 0.31f, 0.18f, 0.085f, light);
            case HALL_PANEL -> {
                RenderUtil.fill(ps, buf, -hw + 0.03f, 0.38f, z, hw - 0.03f, 0.58f, z + 0.002f, FULL, BLACK);
                if (linked) {
                    RenderUtil.text(ps, buf, arrow, -0.07f, 0.49f, z + 0.003f, 0.07f, RED);
                    RenderUtil.text(ps, buf, d.text, 0.035f, 0.49f, z + 0.003f, 0.09f, RED);
                    String s = status(d);
                    if (!s.isEmpty()) RenderUtil.text(ps, buf, s, 0, 0.405f, z + 0.003f, 0.022f, 0xFFFFD040);
                }
                buttons(ps, buf, d, z, 0.24f, 0.11f, 0.08f, light);
            }
            case IND_SEGMENT, IND_DOT -> {
                RenderUtil.fill(ps, buf, -hw + 0.012f, y1 + 0.012f, z, hw - 0.012f, y2 - 0.012f, z + 0.002f, FULL, BLACK);
                if (!linked) break;
                int col = k == DeviceBlock.Kind.IND_SEGMENT ? RED : ORANGE;
                float ay = cy;
                if (k == DeviceBlock.Kind.IND_DOT && d.moving && d.dir != 0) ay += ((time % 10) / 10f - 0.5f) * 0.05f * d.dir; // 흐르는 화살표
                float th = (y2 - y1) * 0.62f;
                RenderUtil.text(ps, buf, arrow, -hw * 0.5f, ay, z + 0.003f, th * 0.8f, col);
                RenderUtil.text(ps, buf, d.text, hw * 0.18f, cy, z + 0.003f, th, col);
                String s = status(d);
                if (!s.isEmpty()) RenderUtil.text(ps, buf, s, hw * 0.18f, y1 + 0.03f, z + 0.003f, 0.02f, col);
            }
            case IND_LCD -> {
                RenderUtil.fill(ps, buf, -hw + 0.015f, y1 + 0.015f, z, hw - 0.015f, y2 - 0.015f, z + 0.002f, FULL, 0xFF0E2E66);
                if (!linked) break;
                RenderUtil.text(ps, buf, arrow, -hw * 0.55f, cy + 0.04f, z + 0.003f, 0.09f, 0xFF7FE3FF);
                RenderUtil.text(ps, buf, d.text, hw * 0.15f, cy + 0.04f, z + 0.003f, 0.14f, WHITE);
                String s = status(d);
                long dayTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getDayTime() % 24000;
                int hh = (int) ((dayTime / 1000 + 6) % 24), mm = (int) (dayTime % 1000 * 60 / 1000);
                String line2 = !s.isEmpty() ? s : d.letter + I18n.get("elevmod.lcd.car") + "  " + String.format("%02d:%02d", hh, mm);
                RenderUtil.fill(ps, buf, -hw + 0.015f, y1 + 0.015f, z + 0.002f, hw - 0.015f, y1 + 0.075f, z + 0.0025f, FULL, 0xFF0A2350);
                RenderUtil.text(ps, buf, line2, 0, y1 + 0.045f, z + 0.003f, 0.028f, 0xFFDDEEFF);
            }
            case IND_LAMP -> {
                RenderUtil.fill(ps, buf, -0.48f, y1 + 0.01f, z, 0.48f, y2 - 0.01f, z + 0.002f, light, 0xFF2A2018);
                int n = d.names.size();
                if (n == 0) break;
                float cell = 0.84f / n;
                for (int i = 0; i < n; i++) {
                    String nm = d.names.get(i);
                    boolean on = nm.equals(d.text);
                    float x = -0.42f + cell * (i + 0.5f);
                    if (on) RenderUtil.fill(ps, buf, x - cell * 0.4f, y1 + 0.02f, z + 0.002f, x + cell * 0.4f, y2 - 0.02f, z + 0.0025f, FULL, 0xFF5A4010);
                    RenderUtil.text(ps, buf, nm, x, cy, z + 0.003f, Math.min(0.07f, cell * 0.7f), on ? 0xFFFFE070 : 0xFF6A6050);
                }
                RenderUtil.text(ps, buf, "▲", 0.465f, cy + 0.025f, z + 0.003f, 0.035f, d.dir > 0 ? 0xFFFFE070 : 0xFF5A5040);
                RenderUtil.text(ps, buf, "▼", 0.465f, cy - 0.025f, z + 0.003f, 0.035f, d.dir < 0 ? 0xFFFFE070 : 0xFF5A5040);
            }
            case LANTERN -> {
                RenderUtil.fill(ps, buf, -hw + 0.01f, y1 + 0.01f, z, hw - 0.01f, y2 - 0.01f, z + 0.002f, light, 0xFF141414);
                boolean up = d.lanUp || d.lanAny, dn = d.lanDown || d.lanAny;
                float s = (y2 - y1) * 0.36f;
                RenderUtil.quadZ(ps, RenderUtil.cut(buf, up ? "lens_up_on" : "lens_up_off"), -0.09f - s, cy - s, -0.09f + s, cy + s, z + 0.003f, true, up ? FULL : light, WHITE);
                RenderUtil.quadZ(ps, RenderUtil.cut(buf, dn ? "lens_dn_on" : "lens_dn_off"), 0.09f - s, cy - s, 0.09f + s, cy + s, z + 0.003f, true, dn ? FULL : light, WHITE);
            }
            case FIRE_SWITCH -> {
                boolean on = "fire".equals(d.status);
                RenderUtil.text(ps, buf, I18n.get("elevmod.label.fire"), 0, y2 - 0.07f, z + 0.002f, 0.04f, WHITE);
                RenderUtil.quadZ(ps, RenderUtil.cut(buf, "btn"), -0.04f, cy - 0.06f, 0.04f, cy + 0.02f, z + 0.002f, true, light, 0xFFE0E0E0);
                RenderUtil.fill(ps, buf, -0.004f, cy - 0.035f, z + 0.003f, 0.004f, cy - 0.005f, z + 0.004f, light, 0xFF202020);
                RenderUtil.fill(ps, buf, -0.025f, y1 + 0.02f, z, 0.025f, y1 + 0.05f, z + 0.006f, on ? FULL : light, on ? 0xFFFF2020 : 0xFF601010);
            }
            case DESTINATION -> {
                RenderUtil.fill(ps, buf, -hw + 0.03f, 0.14f, z, hw - 0.03f, y2 - 0.03f, z + 0.002f, FULL, 0xFF0E1A2A);
                RenderUtil.text(ps, buf, I18n.get(d.dumbwaiter ? "elevmod.label.send" : "elevmod.label.destination"), 0, y2 - 0.07f, z + 0.003f, 0.03f, WHITE);
                // 키패드
                for (int r = 0; r < 4; r++) for (int c = 0; c < 3; c++) {
                    float x = -0.07f + c * 0.07f, y = 0.42f - r * 0.065f;
                    RenderUtil.fill(ps, buf, x - 0.026f, y - 0.022f, z + 0.002f, x + 0.026f, y + 0.022f, z + 0.0025f, FULL, 0xFF24405E);
                }
                if (linked) RenderUtil.text(ps, buf, d.letter + " " + arrow + d.text, 0, y2 - 0.12f, z + 0.003f, 0.035f, 0xFF7FE3FF);
            }
            case CALL_RECEIVER -> RenderUtil.fill(ps, buf, -0.015f, cy - 0.015f, z, 0.015f, cy + 0.015f, z + 0.004f, linked ? FULL : light, linked ? 0xFF30FF60 : 0xFF305030);
        }
        ps.popPose();
    }

    /** 원형 호출 버튼 (상/하) */
    private void buttons(PoseStack ps, MultiBufferSource buf, DeviceBlockEntity d, float z, float upY, float dnY, float size, int light) {
        boolean both = d.hasUp && d.hasDown;
        float mid = (upY + dnY) / 2;
        VertexConsumer off = RenderUtil.cut(buf, "btn"), on = RenderUtil.cut(buf, "btn_lit");
        float s = size / 2;
        if (d.hasUp) {
            float y = both ? upY : mid;
            RenderUtil.quadZ(ps, d.upLit ? on : off, -s, y - s, s, y + s, z + 0.003f, true, d.upLit ? FULL : light, WHITE);
            RenderUtil.text(ps, buf, "▲", 0, y, z + 0.004f, size * 0.38f, d.upLit ? 0xFFFFB020 : 0xFF303030);
        }
        if (d.hasDown) {
            float y = both ? dnY : mid;
            RenderUtil.quadZ(ps, d.downLit ? on : off, -s, y - s, s, y + s, z + 0.003f, true, d.downLit ? FULL : light, WHITE);
            RenderUtil.text(ps, buf, "▼", 0, y, z + 0.004f, size * 0.38f, d.downLit ? 0xFFFFB020 : 0xFF303030);
        }
    }
}

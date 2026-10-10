package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.block.DeviceBlock;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import com.mojang.blaze3d.vertex.PoseStack;
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
        int des = d.design;
        switch (k) {
            case HALL_BUTTON -> {
                Spec.ButtonStyle bs = bstyle(des);
                plate(ps, buf, bs, -hw, y1, hw, y2, z, light);
                buttons(ps, buf, d, z, 0.31f, 0.18f, bs == Spec.ButtonStyle.CLASSIC ? 0.1f : 0.085f, light, bs);
            }
            case HALL_PANEL -> {
                Spec.ButtonStyle bs = bstyle(des);
                plate(ps, buf, bs, -hw, y1, hw, y2, z, light);
                Glyphs.rect(ps, buf, -hw + 0.03f, 0.38f, hw - 0.03f, 0.58f, z + 0.002f, FULL, 0xFF140404);
                if (linked) {
                    segDisplay(ps, buf, d, -hw + 0.03f, 0.40f, hw - 0.03f, 0.56f, z + 0.0025f, RED, light);
                    String s = status(d);
                    if (!s.isEmpty()) RenderUtil.text(ps, buf, s, 0, 0.395f, z + 0.004f, 0.018f, 0xFFFFD040);
                }
                buttons(ps, buf, d, z, 0.24f, 0.11f, 0.08f, light, bs);
            }
            case IND_SEGMENT -> {
                Glyphs.rect(ps, buf, -hw + 0.012f, y1 + 0.012f, hw - 0.012f, y2 - 0.012f, z + 0.002f, FULL, 0xFF120404);
                if (!linked) break;
                int col = SEG_COLORS[Math.floorMod(des, SEG_COLORS.length)];
                segDisplay(ps, buf, d, -hw + 0.02f, y1 + 0.03f, hw - 0.02f, y2 - 0.03f, z + 0.0025f, col, light);
                String s = status(d);
                if (!s.isEmpty()) RenderUtil.text(ps, buf, s, 0, y1 + 0.02f, z + 0.004f, 0.018f, col);
            }
            case IND_DOT -> {
                Glyphs.rect(ps, buf, -hw + 0.012f, y1 + 0.012f, hw - 0.012f, y2 - 0.012f, z + 0.002f, FULL, 0xFF0C0806);
                int col = DOT_COLORS[Math.floorMod(des, DOT_COLORS.length)];
                float x1 = -hw + 0.03f, x2 = hw - 0.03f, h = (y2 - y1) - 0.07f;
                float w = x2 - x1;
                // 화살표 칸 (위아래로 흐름) + 층 글자
                float scroll = d.moving && d.dir != 0 ? (time % 14) / 14f : 0;
                String t = linked ? d.text : "";
                Glyphs.dots(ps, buf, linked && d.dir != 0 ? (d.dir > 0 ? "^" : "v") : " ", x1 + w * 0.16f, cy, Math.min(h, w * 0.22f * 7 / 5), z + 0.0025f, light, col, 0.09f, scroll);
                float tw = w * 0.66f;
                float gh = t.isEmpty() ? h : Math.min(h, tw / (t.length() * 5f / 7f + (t.length() - 1) / 7f));
                Glyphs.dots(ps, buf, t.isEmpty() ? "  " : t, x1 + w * 0.64f, cy, gh, z + 0.0025f, light, col, 0.09f, 0);
                String s = linked ? status(d) : "";
                if (!s.isEmpty()) RenderUtil.text(ps, buf, s, 0, y1 + 0.02f, z + 0.004f, 0.018f, col);
            }
            case IND_LCD -> {
                int[] pal = LCD_PAL[Math.floorMod(des, LCD_PAL.length)];
                Glyphs.rect(ps, buf, -hw + 0.015f, y1 + 0.015f, hw - 0.015f, y2 - 0.015f, z + 0.002f, FULL, pal[0]);
                if (!linked) break;
                RenderUtil.text(ps, buf, arrow, -hw * 0.55f, cy + 0.04f, z + 0.003f, 0.09f, pal[1]);
                RenderUtil.text(ps, buf, d.text, hw * 0.15f, cy + 0.04f, z + 0.003f, 0.14f, pal[2]);
                String s = status(d);
                long dayTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getDayTime() % 24000;
                int hh = (int) ((dayTime / 1000 + 6) % 24), mm = (int) (dayTime % 1000 * 60 / 1000);
                String line2 = !s.isEmpty() ? s : d.letter + I18n.get("elevmod.lcd.car") + "  " + String.format("%02d:%02d", hh, mm);
                Glyphs.rect(ps, buf, -hw + 0.015f, y1 + 0.015f, hw - 0.015f, y1 + 0.075f, z + 0.0025f, FULL, pal[3]);
                RenderUtil.text(ps, buf, line2, 0, y1 + 0.045f, z + 0.003f, 0.028f, pal[4]);
            }
            case IND_LAMP -> {
                boolean white = Math.floorMod(des, 2) == 1;
                RenderUtil.fill(ps, buf, -0.48f, y1 + 0.01f, z, 0.48f, y2 - 0.01f, z + 0.002f, light, white ? 0xFF2C2C2E : 0xFF2A2018);
                int n = d.names.size();
                if (n == 0) break;
                int onBg = white ? 0xFF6A6A70 : 0xFF5A4010, onTx = white ? 0xFFFFFFFF : 0xFFFFE070, offTx = white ? 0xFF707074 : 0xFF6A6050;
                float cell = 0.84f / n;
                for (int i = 0; i < n; i++) {
                    String nm = d.names.get(i);
                    boolean on = nm.equals(d.text);
                    float x = -0.42f + cell * (i + 0.5f);
                    if (on) RenderUtil.fill(ps, buf, x - cell * 0.4f, y1 + 0.02f, z + 0.002f, x + cell * 0.4f, y2 - 0.02f, z + 0.0025f, FULL, onBg);
                    RenderUtil.text(ps, buf, nm, x, cy, z + 0.003f, Math.min(0.07f, cell * 0.7f), on ? onTx : offTx);
                }
                Glyphs.arrow(ps, buf, 0.465f, cy + 0.025f, 0.03f, 1, z + 0.003f, d.dir > 0 ? FULL : light, d.dir > 0 ? onTx : offTx);
                Glyphs.arrow(ps, buf, 0.465f, cy - 0.025f, 0.03f, -1, z + 0.003f, d.dir < 0 ? FULL : light, d.dir < 0 ? onTx : offTx);
            }
            case LANTERN -> {
                boolean up = d.lanUp || d.lanAny, dn = d.lanDown || d.lanAny;
                int style = Math.floorMod(des, 3);
                int green = 0xFF30FF60, red = 0xFFFF3020;
                if (style == 0) {
                    RenderUtil.fill(ps, buf, -hw + 0.01f, y1 + 0.01f, z, hw - 0.01f, y2 - 0.01f, z + 0.002f, light, 0xFF141414);
                    float s = (y2 - y1) * 0.36f;
                    RenderUtil.quadZ(ps, RenderUtil.cut(buf, up ? "lens_up_on" : "lens_up_off"), -0.09f - s, cy - s, -0.09f + s, cy + s, z + 0.003f, true, up ? FULL : light, WHITE);
                    RenderUtil.quadZ(ps, RenderUtil.cut(buf, dn ? "lens_dn_on" : "lens_dn_off"), 0.09f - s, cy - s, 0.09f + s, cy + s, z + 0.003f, true, dn ? FULL : light, WHITE);
                } else if (style == 1) {
                    // 원형 램프: 스테인리스 판에 둥근 렌즈 2개
                    RenderUtil.fill(ps, buf, -hw + 0.01f, y1 + 0.01f, z, hw - 0.01f, y2 - 0.01f, z + 0.002f, light, 0xFFB8BCC0);
                    float s = (y2 - y1) * 0.34f;
                    for (int i = 0; i < 2; i++) {
                        boolean on = i == 0 ? up : dn;
                        float x = i == 0 ? -0.09f : 0.09f;
                        RenderUtil.quadZ(ps, RenderUtil.cut(buf, "btn"), x - s, cy - s, x + s, cy + s, z + 0.003f, true, light, 0xFF404448);
                        RenderUtil.quadZ(ps, RenderUtil.cut(buf, "btn_touch_lit"), x - s * 0.8f, cy - s * 0.8f, x + s * 0.8f, cy + s * 0.8f, z + 0.004f, true,
                                on ? FULL : light, on ? (i == 0 ? green : red) : 0xFF303030);
                        Glyphs.arrow(ps, buf, x, cy, s * 0.8f, i == 0 ? 1 : -1, z + 0.005f, on ? FULL : light, on ? 0xFFFFFFFF : 0xFF505050);
                    }
                } else {
                    // 가로 바(라인) 램프: 위 = 상승, 아래 = 하강
                    RenderUtil.fill(ps, buf, -hw + 0.01f, y1 + 0.01f, z, hw - 0.01f, y2 - 0.01f, z + 0.002f, light, 0xFF1A1A1C);
                    float mid = cy, g = 0.006f;
                    Glyphs.rect(ps, buf, -hw + 0.03f, mid + g, hw - 0.03f, y2 - 0.025f, z + 0.003f, up ? FULL : light, up ? green : Glyphs.dim(green, 0.15f));
                    Glyphs.rect(ps, buf, -hw + 0.03f, y1 + 0.025f, hw - 0.03f, mid - g, z + 0.003f, dn ? FULL : light, dn ? red : Glyphs.dim(red, 0.15f));
                }
            }
            case FIRE_SWITCH -> {
                boolean on = "fire".equals(d.status);
                RenderUtil.text(ps, buf, I18n.get("elevmod.label.fire"), 0, y2 - 0.07f, z + 0.002f, 0.04f, WHITE);
                RenderUtil.quadZ(ps, RenderUtil.cut(buf, "btn"), -0.04f, cy - 0.06f, 0.04f, cy + 0.02f, z + 0.002f, true, light, 0xFFE0E0E0);
                RenderUtil.fill(ps, buf, -0.004f, cy - 0.035f, z + 0.003f, 0.004f, cy - 0.005f, z + 0.004f, light, 0xFF202020);
                RenderUtil.fill(ps, buf, -0.025f, y1 + 0.02f, z, 0.025f, y1 + 0.05f, z + 0.006f, on ? FULL : light, on ? 0xFFFF2020 : 0xFF601010);
            }
            case DESTINATION -> {
                boolean lightUi = Math.floorMod(des, 2) == 1;
                RenderUtil.fill(ps, buf, -hw + 0.03f, 0.14f, z, hw - 0.03f, y2 - 0.03f, z + 0.002f, FULL, lightUi ? 0xFFE8ECF0 : 0xFF0E1A2A);
                RenderUtil.text(ps, buf, I18n.get(d.dumbwaiter ? "elevmod.label.send" : "elevmod.label.destination"), 0, y2 - 0.07f, z + 0.003f, 0.03f, lightUi ? 0xFF102030 : WHITE);
                // 키패드
                for (int r = 0; r < 4; r++) for (int c = 0; c < 3; c++) {
                    float x = -0.07f + c * 0.07f, y = 0.42f - r * 0.065f;
                    RenderUtil.fill(ps, buf, x - 0.026f, y - 0.022f, z + 0.002f, x + 0.026f, y + 0.022f, z + 0.0025f, FULL, lightUi ? 0xFFB0BCC8 : 0xFF24405E);
                }
                if (linked) RenderUtil.text(ps, buf, d.letter + " " + arrow + d.text, 0, y2 - 0.12f, z + 0.003f, 0.035f, lightUi ? 0xFF1060B0 : 0xFF7FE3FF);
            }
            case CALL_RECEIVER -> RenderUtil.fill(ps, buf, -0.015f, cy - 0.015f, z, 0.015f, cy + 0.015f, z + 0.004f, linked ? FULL : light, linked ? 0xFF30FF60 : 0xFF305030);
        }
        ps.popPose();
    }

    private static final int[] SEG_COLORS = {0xFFFF2A10, 0xFF30FF50, 0xFFFF9A10, 0xFF40A8FF};
    private static final int[] DOT_COLORS = {0xFFFF8A18, 0xFFFF2A10, 0xFF40FF60, 0xFFF0F0FF};
    /** LCD 팔레트: 배경, 화살표, 층, 하단띠, 하단글자 */
    private static final int[][] LCD_PAL = {
            {0xFF0E2E66, 0xFF7FE3FF, 0xFFFFFFFF, 0xFF0A2350, 0xFFDDEEFF},
            {0xFF060608, 0xFF40E0FF, 0xFFFFFFFF, 0xFF1A1A20, 0xFFC0C0C8},
            {0xFFEEF2F6, 0xFF1060C0, 0xFF101820, 0xFFC8D2DC, 0xFF203040}};

    private static Spec.ButtonStyle bstyle(int d) {
        Spec.ButtonStyle[] v = Spec.ButtonStyle.values();
        return v[Math.floorMod(d, v.length)];
    }

    /** 7세그먼트 위치표시 (화살표 LED + 2자리) */
    private void segDisplay(PoseStack ps, MultiBufferSource buf, DeviceBlockEntity d, float x1, float y1, float x2, float y2, float z, int col, int light) {
        float w = x2 - x1, h = y2 - y1, cy = (y1 + y2) / 2;
        String t = d.text.length() >= 2 ? d.text : " " + d.text;
        float tw = w * 0.66f;
        float gh = Math.min(h * 0.86f, tw / (t.length() * 0.56f + (t.length() - 1) * 0.2f));
        float ax = x1 + w * 0.15f;
        int off = Glyphs.dim(col, 0.13f);
        Glyphs.arrow(ps, buf, ax, cy + gh * 0.28f, gh * 0.38f, 1, z, d.dir > 0 ? FULL : light, d.dir > 0 ? col : off);
        Glyphs.arrow(ps, buf, ax, cy - gh * 0.28f, gh * 0.38f, -1, z, d.dir < 0 ? FULL : light, d.dir < 0 ? col : off);
        Glyphs.seg(ps, buf, t, x1 + w * 0.63f, cy, gh, z, light, col, 0.13f);
    }

    /** 버튼판(스테인리스 기본 모델 위에 의장별 판) */
    private void plate(PoseStack ps, MultiBufferSource buf, Spec.ButtonStyle bs, float x1, float y1, float x2, float y2, float z, int light) {
        switch (bs) {
            case TOUCH -> Glyphs.rect(ps, buf, x1 + 0.005f, y1 + 0.005f, x2 - 0.005f, y2 - 0.005f, z + 0.0012f, light, 0xFF0C0E10);
            case CLASSIC -> {
                Glyphs.rect(ps, buf, x1, y1, x2, y2, z + 0.0012f, light, 0xFFB08A3A);
                Glyphs.rect(ps, buf, x1 + 0.01f, y1 + 0.01f, x2 - 0.01f, y2 - 0.01f, z + 0.0014f, light, 0xFF8C8E90);
            }
            default -> {}
        }
    }

    /** 호출 버튼 (상/하) – 의장별 버튼 텍스처 */
    private void buttons(PoseStack ps, MultiBufferSource buf, DeviceBlockEntity d, float z, float upY, float dnY, float size, int light, Spec.ButtonStyle bs) {
        boolean both = d.hasUp && d.hasDown;
        float mid = (upY + dnY) / 2;
        String offT = switch (bs) { case SQUARE -> "btn_sq"; case TOUCH -> "btn_touch"; case CLASSIC -> "btn_classic"; default -> "btn"; };
        String onT = offT + "_lit";
        float s = size / 2;
        int arrowOff = bs == Spec.ButtonStyle.TOUCH ? 0xFFC8CCD0 : 0xFF303030;
        int arrowOn = bs == Spec.ButtonStyle.TOUCH ? 0xFF7FE8FF : bs == Spec.ButtonStyle.CLASSIC ? 0xFFFF5A20 : 0xFFFFB020;
        if (d.hasUp) {
            float y = both ? upY : mid;
            RenderUtil.quadZ(ps, RenderUtil.cut(buf, d.upLit ? onT : offT), -s, y - s, s, y + s, z + 0.003f, true, d.upLit ? FULL : light, WHITE);
            Glyphs.arrow(ps, buf, 0, y, size * 0.32f, 1, z + 0.0045f, d.upLit ? FULL : light, d.upLit ? arrowOn : arrowOff);
        }
        if (d.hasDown) {
            float y = both ? dnY : mid;
            RenderUtil.quadZ(ps, RenderUtil.cut(buf, d.downLit ? onT : offT), -s, y - s, s, y + s, z + 0.003f, true, d.downLit ? FULL : light, WHITE);
            Glyphs.arrow(ps, buf, 0, y, size * 0.32f, -1, z + 0.0045f, d.downLit ? FULL : light, d.downLit ? arrowOn : arrowOff);
        }
    }
}

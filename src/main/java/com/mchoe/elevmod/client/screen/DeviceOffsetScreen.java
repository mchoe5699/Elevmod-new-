package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 승강장 장치(층표시기·호출버튼·홀랜턴 등) 설치 위치 조정.
 * 블록 칸 수가 짝수인 출입구 위에서는 표시기를 두 블록 사이(½칸)에 둘 수 있다.
 */
public class DeviceOffsetScreen extends Screen {
    private final BlockPos pos;

    public DeviceOffsetScreen(BlockPos pos) {
        super(Component.translatable("elevmod.screen.offset"));
        this.pos = pos;
    }

    private void move(int dx, int dy, boolean reset) { Net.CH.sendToServer(new Net.OffsetMsg(pos, dx, dy, reset)); }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        // ½칸 이동
        addRenderableWidget(Button.builder(Component.literal("◀◀ ½"), b -> move(-8, 0, false)).bounds(cx - 150, cy - 10, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("½ ▶▶"), b -> move(8, 0, false)).bounds(cx + 100, cy - 10, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▲▲ ½"), b -> move(0, 8, false)).bounds(cx - 25, cy - 80, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼▼ ½"), b -> move(0, -8, false)).bounds(cx - 25, cy + 60, 50, 20).build());
        // 1/16칸 이동
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> move(-1, 0, false)).bounds(cx - 90, cy - 10, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> move(1, 0, false)).bounds(cx + 60, cy - 10, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> move(0, 1, false)).bounds(cx - 15, cy - 50, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> move(0, -1, false)).bounds(cx - 15, cy + 30, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.offset.reset"), b -> move(0, 0, true)).bounds(cx - 40, cy + 95, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 40, height - 28, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int ox = 0, oy = 0;
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DeviceBlockEntity d) { ox = d.offX; oy = d.offY; }
        g.drawCenteredString(font, I18n.get("elevmod.offset.value", fmt(ox), fmt(oy)), width / 2, height / 2 - 4, 0xFFFFD040);
        g.drawCenteredString(font, I18n.get("elevmod.offset.hint"), width / 2, height / 2 + 125, 0xFFAAAAAA);
        super.render(g, mx, my, pt);
    }

    private static String fmt(int v) {
        if (v == 0) return "0";
        if (Math.abs(v) % 8 == 0) return (v > 0 ? "+" : "-") + (Math.abs(v) / 8 == 1 ? "½" : String.valueOf(Math.abs(v) / 16.0));
        return (v > 0 ? "+" : "") + v + "/16";
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

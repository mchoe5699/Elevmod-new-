package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** 행선층 예약 패널 / 외부 운전반(덤웨이터 보내기) */
public class DestinationScreen extends Screen {
    private final BlockPos panel;
    private final CompoundTag data;

    public DestinationScreen(BlockPos panel, CompoundTag data) {
        super(Component.translatable(data.getBoolean("dd") ? "elevmod.screen.destination" : "elevmod.screen.send"));
        this.panel = panel;
        this.data = data;
    }

    @Override
    protected void init() {
        ListTag names = data.getList("names", Tag.TAG_STRING);
        ListTag ys = data.getList("ys", Tag.TAG_INT);
        int n = names.size();
        int cols = n > 24 ? 8 : n > 12 ? 6 : 4;
        int bw = 40, bh = 20, gap = 4;
        int rows = Math.max(1, (n + cols - 1) / cols);
        int x0 = (width - cols * (bw + gap)) / 2;
        int y0 = Math.max(30, (height - rows * (bh + gap)) / 2);
        int here = data.contains("here") ? data.getInt("here") : Integer.MIN_VALUE;
        boolean dd = data.getBoolean("dd");
        for (int i = 0; i < n; i++) {
            final int y = ys.getInt(i);
            int r = (n - 1 - i) / cols, c = i % cols;
            Button b = Button.builder(Component.literal(names.getString(i)), btn -> {
                Net.CH.sendToServer(new Net.DestCallMsg(panel, y));
                onClose();
            }).bounds(x0 + c * (bw + gap), y0 + r * (bh + gap), bw, bh).build();
            if (dd && y == here) b.active = false;
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

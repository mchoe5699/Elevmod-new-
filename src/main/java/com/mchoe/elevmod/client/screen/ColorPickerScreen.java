package com.mchoe.elevmod.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 색 고르기(팔레트). 칸을 누르거나 #RRGGBB 를 직접 입력. "기본" = 장치·의장 기본색.
 * 결과는 "#RRGGBB" 또는 "" 로 돌려준다.
 */
public class ColorPickerScreen extends Screen {
    /** 표시기에 흔한 색 + 일반 팔레트 */
    public static final int[] PALETTE = {
            0xFF2A10, 0xFF0000, 0xC81400, 0x8A0A00, 0xFF5A1A, 0xFF8A18, 0xFFA000, 0xFFC040,
            0xFFE040, 0xFFFF60, 0xC8FF40, 0x80FF40, 0x40FF60, 0x00E070, 0x00A050, 0x006030,
            0x60FFFF, 0x40D0FF, 0x40A8FF, 0x2A70FF, 0x1A40D0, 0x0E2E66, 0x6040FF, 0xA040FF,
            0xFF40C0, 0xFF80D0, 0xFFFFFF, 0xD0D0D0, 0x909090, 0x505050, 0x202020, 0x000000,
            0x7FE3FF, 0xB0F0FF, 0x10161C, 0x1A1208, 0x2A0A06, 0x06200A, 0x060A20, 0x301800};

    private final Screen parent;
    private final Consumer<String> done;
    private int cur;
    private EditBox hex;

    public ColorPickerScreen(Screen parent, String title, int current, Consumer<String> done) {
        super(Component.literal(title));
        this.parent = parent;
        this.done = done;
        this.cur = current;
    }

    public Screen parent() { return parent; }

    private int left() { return width / 2 - 104; }

    private int top() { return 44; }

    @Override
    protected void init() {
        hex = new EditBox(font, left(), top() + 5 * 26 + 8, 80, 20, Component.literal("hex"));
        hex.setMaxLength(7);
        hex.setValue(cur == 0 ? "" : String.format("#%06X", cur & 0xFFFFFF));
        hex.setResponder(s -> {
            String t = s.trim().replace("#", "");
            if (t.length() == 6) try { cur = 0xFF000000 | Integer.parseInt(t, 16); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(hex);
        addRenderableWidget(Button.builder(Component.translatable("elevmod.color.default"), b -> finish(""))
                .bounds(left() + 86, top() + 5 * 26 + 8, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.set.apply").withStyle(ChatFormatting.GREEN), b -> finish(cur == 0 ? "" : String.format("#%06X", cur & 0xFFFFFF)))
                .bounds(left() + 150, top() + 5 * 26 + 8, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    private void finish(String v) {
        done.accept(v);
        onClose();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < PALETTE.length; i++) {
            int x = left() + (i % 8) * 26, y = top() + (i / 8) * 26;
            if (mx >= x && mx < x + 24 && my >= y && my < y + 24) {
                cur = 0xFF000000 | PALETTE[i];
                hex.setValue(String.format("#%06X", PALETTE[i]));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && hex.isFocused()) { finish(cur == 0 ? "" : String.format("#%06X", cur & 0xFFFFFF)); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        g.drawCenteredString(font, I18n.get("elevmod.color.hint"), width / 2, 28, 0xFF888888);
        for (int i = 0; i < PALETTE.length; i++) {
            int x = left() + (i % 8) * 26, y = top() + (i / 8) * 26;
            boolean sel = (cur & 0xFFFFFF) == PALETTE[i] && cur != 0;
            g.fill(x - 1, y - 1, x + 25, y + 25, sel ? 0xFFFFFF00 : 0xFF404040);
            g.fill(x, y, x + 24, y + 24, 0xFF000000 | PALETTE[i]);
        }
        int sx = left() + 214, sy = top();
        g.fill(sx - 1, sy - 1, sx + 41, sy + 41, 0xFF808080);
        if (cur == 0) {
            g.fill(sx, sy, sx + 40, sy + 40, 0xFF303030);
            g.drawCenteredString(font, I18n.get("elevmod.color.default"), sx + 20, sy + 16, 0xFFAAAAAA);
        } else g.fill(sx, sy, sx + 40, sy + 40, cur);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** 버튼에 쓰는 색 견본 글자 */
    public static Component swatch(int c) {
        if (c == 0) return Component.translatable("elevmod.color.default").withStyle(ChatFormatting.GRAY);
        return Component.literal("■ ").withStyle(s -> s.withColor(c & 0xFFFFFF)).append(Component.literal(String.format("#%06X", c & 0xFFFFFF)).withStyle(ChatFormatting.WHITE));
    }
}

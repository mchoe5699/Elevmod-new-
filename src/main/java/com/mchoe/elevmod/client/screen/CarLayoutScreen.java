package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mchoe.elevmod.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 카 안 표시기 배치: 문 위(트랜섬) 위치표시기와 운전반 표시창의 위치·크기 (cm).
 * 디자인 변경기 위치 조정 모드로 카 안 표시기·운전반을 우클릭하면 열린다.
 * 화살표 버튼 = 1cm (Shift 5cm), 칸에 직접 입력 후 Enter/적용.
 */
public class CarLayoutScreen extends Screen {
    private final ElevatorCarEntity car;
    private int target;   // 0 = 문 위 표시기, 1 = 운전반 표시창
    private final Map<String, EditBox> boxes = new HashMap<>();
    private static final String[][] KEYS = {{"indx", "indy", "indw", "indh"}, {"copdx", "copdy", "copdw", "copdh"}};
    private static final String[] LABELS = {"elevmod.layout.x", "elevmod.layout.y", "elevmod.layout.w", "elevmod.layout.h"};

    public CarLayoutScreen(ElevatorCarEntity car, int target) {
        super(Component.translatable("elevmod.screen.layout"));
        this.car = car;
        this.target = target;
    }

    private CompoundTag st() { return car.state(); }

    private float value(String key) {
        String sk = switch (key) {
            case "indx" -> "indx"; case "indy" -> "indy"; case "indw" -> "indw"; case "indh" -> "indh";
            case "copdx" -> "cdx"; case "copdy" -> "cdy"; case "copdw" -> "cdw"; default -> "cdh";
        };
        return st().getFloat(sk);
    }

    private void send(String key, float v) {
        Net.CH.sendToServer(new Net.EditMsg(car.controller(), key, 0, String.format(java.util.Locale.ROOT, "%.1f", v)));
    }

    private int left() { return width / 2 - 130; }

    @Override
    protected void init() {
        boxes.clear();
        int l = left(), t = 40;
        String[] tn = {"elevmod.layout.transom", "elevmod.layout.cop"};
        for (int i = 0; i < 2; i++) {
            final int ti = i;
            addRenderableWidget(Button.builder(Component.translatable(tn[i]).withStyle(i == target ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> { target = ti; rebuildWidgets(); })
                    .bounds(l + i * 132, t, 128, 20).build());
        }
        for (int r = 0; r < 4; r++) {
            final String key = KEYS[target][r];
            int y = t + 32 + r * 26;
            addRenderableWidget(Button.builder(Component.literal("−"), b -> send(key, value(key) - (Screen.hasShiftDown() ? 5 : 1))).bounds(l + 110, y, 20, 20).build());
            EditBox box = new EditBox(font, l + 134, y, 56, 20, Component.literal(key));
            box.setMaxLength(7);
            box.setValue(String.format(java.util.Locale.ROOT, "%.1f", value(key)));
            boxes.put(key, box);
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("+"), b -> send(key, value(key) + (Screen.hasShiftDown() ? 5 : 1))).bounds(l + 194, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("elevmod.set.apply"), b -> {
                try { send(key, Float.parseFloat(box.getValue().trim().replace(",", "."))); } catch (NumberFormatException ignored) {}
            }).bounds(l + 218, y, 40, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("elevmod.offset.reset"), b -> Net.CH.sendToServer(new Net.EditMsg(car.controller(), "layout_reset", 0, "")))
                .bounds(l, t + 32 + 4 * 26 + 6, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 40, height - 28, 80, 20).build());
    }

    private CompoundTag seen;

    @Override
    public void tick() {
        // 서버에서 값이 바뀌면 입력 칸 갱신 (입력 중인 칸은 그대로)
        if (seen != st()) {
            seen = st();
            for (Map.Entry<String, EditBox> e : boxes.entrySet())
                if (!e.getValue().isFocused()) e.getValue().setValue(String.format(java.util.Locale.ROOT, "%.1f", value(e.getKey())));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            for (Map.Entry<String, EditBox> e : boxes.entrySet()) if (e.getValue().isFocused()) {
                try { send(e.getKey(), Float.parseFloat(e.getValue().getValue().trim().replace(",", "."))); } catch (NumberFormatException ignored) {}
                e.getValue().setFocused(false);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 배경을 어둡게 하지 않아 표시기가 움직이는 모습을 바로 볼 수 있게 함
        g.fill(left() - 8, 32, left() + 270, 40 + 32 + 4 * 26 + 34, 0xA0000000);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        for (int r = 0; r < 4; r++) g.drawString(font, I18n.get(LABELS[r]), left(), 40 + 32 + r * 26 + 6, 0xFFDDDDDD, false);
        g.drawString(font, I18n.get(target == 0 ? "elevmod.layout.hint_transom" : "elevmod.layout.hint_cop"), left(), 40 + 32 + 4 * 26 + 30, 0xFFAAAAAA, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

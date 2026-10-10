package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.elevator.CarAction;
import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mchoe.elevmod.network.Net;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 카 내부 운전반(COP / 장애인용 운전반) */
public class CopScreen extends Screen {
    private final ElevatorCarEntity car;
    private final boolean accessible;
    private final List<Button> floorButtons = new ArrayList<>();
    private int heldAction, tick, lastFloorClick = -1, lastFloorTick;
    private int panelX, panelY, panelW, panelH, floorCount, initMode;
    private boolean initTenkey, initButtons;
    /** 텐키 입력 중인 층(가예약층) */
    private String tkInput = "";
    private int tkIdle, tkMsgTicks;
    private String tkMsg = "";

    public CopScreen(ElevatorCarEntity car, boolean accessible) {
        super(Component.translatable(accessible ? "elevmod.screen.cop_acc" : "elevmod.screen.cop"));
        this.car = car;
        this.accessible = accessible;
    }

    private CompoundTag st() { return car.state(); }

    private void send(int action, int arg) { Net.CH.sendToServer(new Net.CarActionMsg(car.controller(), action, arg)); }

    private boolean has(net.minecraft.world.item.Item item) {
        return minecraft != null && minecraft.player != null && (minecraft.player.getInventory().contains(new ItemStack(item)) || minecraft.player.isCreative());
    }

    @Override
    protected void init() {
        floorButtons.clear();
        CompoundTag st = st();
        ListTag names = st.getList("n", Tag.TAG_STRING);
        floorCount = names.size();
        initMode = st.getInt("mode");
        initTenkey = st.getBoolean("tenkey");
        initButtons = !st.contains("cbtn") || st.getBoolean("cbtn");
        boolean tenkey = initTenkey && initButtons;
        int cols = tenkey ? 3 : floorCount > 40 ? 8 : floorCount > 20 ? 6 : 4;
        int bw = tenkey ? 44 : 34, bh = 18, gap = 3;
        int shown = !initButtons ? 0 : tenkey ? 15 : floorCount;
        int rows = Math.max(1, (shown + cols - 1) / cols);
        panelW = Math.max(tenkey ? 270 : 220, cols * (bw + gap) + 20);
        panelH = 70 + rows * (bh + gap) + 90;
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        int gridX = tenkey ? panelX + 14 : panelX + (panelW - cols * (bw + gap) + gap) / 2;
        int gridY = panelY + 62;
        Spec.Usage usage = car.usage();
        if (tenkey) {
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "B", "0", "C"};
            for (int i = 0; i < keys.length; i++) {
                final String k = keys[i];
                int r = i / 3, c = i % 3;
                addRenderableWidget(Button.builder(Component.literal(k.equals("C") ? I18n.get("elevmod.tenkey.clear") : k), b -> tenkey(k))
                        .bounds(gridX + c * (bw + gap), gridY + r * (bh + gap), bw, bh).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("elevmod.tenkey.enter").withStyle(ChatFormatting.AQUA), b -> tenkeyEnter())
                    .bounds(gridX, gridY + 4 * (bh + gap), 3 * bw + 2 * gap, bh).build());
        }
        // 층 버튼 – 아래층이 아래에 오도록
        for (int i = 0; i < (initButtons && !tenkey ? floorCount : 0); i++) {
            final int idx = i;
            int r = (floorCount - 1 - i) / cols, c = i % cols;
            Button b = Button.builder(Component.literal(names.getString(i)), btn -> pressFloor(idx))
                    .bounds(gridX + c * (bw + gap), gridY + r * (bh + gap), bw, bh).build();
            floorButtons.add(addRenderableWidget(b));
        }
        int y = gridY + rows * (bh + gap) + 6;
        int x = panelX + 10;
        int w3 = (panelW - 20 - 8) / 3;
        addRenderableWidget(new HoldButton(x, y, w3, 20, Component.literal("◀ ▶ ").append(Component.translatable("elevmod.cop.open")), CarAction.OPEN, CarAction.OPEN_HOLD));
        addRenderableWidget(new HoldButton(x + w3 + 4, y, w3, 20, Component.literal("▶ ◀ ").append(Component.translatable("elevmod.cop.close")), CarAction.CLOSE, CarAction.CLOSE_HOLD));
        addRenderableWidget(Button.builder(Component.translatable("elevmod.cop.alarm").withStyle(ChatFormatting.GOLD), b -> send(CarAction.ALARM, 0))
                .bounds(x + 2 * (w3 + 4), y, w3, 20).build());
        y += 24;
        List<Button> extra = new ArrayList<>();
        if (usage == Spec.Usage.FREIGHT || usage == Spec.Usage.PASSENGER_FREIGHT || usage == Spec.Usage.CAR)
            extra.add(Button.builder(Component.translatable("elevmod.cop.hold"), b -> send(CarAction.HOLD, 0)).size(64, 20).build());
        if (st.getBoolean("stop"))
            extra.add(Button.builder(Component.translatable("elevmod.cop.stop").withStyle(ChatFormatting.RED), b -> send(CarAction.STOP_SWITCH, 0)).size(64, 20).build());
        if (has(ModRegistry.OPERATOR_KEY.get())) {
            extra.add(Button.builder(Component.translatable("elevmod.cop.independent"), b -> send(CarAction.KEY_INDEPENDENT, 0)).size(64, 20).build());
            extra.add(Button.builder(Component.translatable("elevmod.cop.inspection"), b -> send(CarAction.KEY_INSPECTION, 0)).size(64, 20).build());
        }
        if (has(ModRegistry.FIRE_KEY.get())) {
            extra.add(Button.builder(Component.translatable("elevmod.cop.fire1").withStyle(ChatFormatting.RED), b -> send(CarAction.KEY_FIRE1, 0)).size(64, 20).build());
            extra.add(Button.builder(Component.translatable("elevmod.cop.fire2").withStyle(ChatFormatting.RED), b -> send(CarAction.KEY_FIRE2, 0)).size(64, 20).build());
        }
        boolean run = usage == Spec.Usage.WHEELCHAIR_LIFT || st.getInt("mode") == Spec.Mode.INSPECTION.ordinal();
        if (run) {
            extra.add(new HoldButton(0, 0, 64, 20, Component.literal("▲ ").append(Component.translatable("elevmod.cop.run_up")), CarAction.RUN_UP, CarAction.RUN_UP));
            extra.add(new HoldButton(0, 0, 64, 20, Component.literal("▼ ").append(Component.translatable("elevmod.cop.run_down")), CarAction.RUN_DOWN, CarAction.RUN_DOWN));
        }
        int perRow = Math.max(1, (panelW - 20) / 68);
        for (int i = 0; i < extra.size(); i++) {
            Button b = extra.get(i);
            b.setX(panelX + 10 + (i % perRow) * 68);
            b.setY(y + (i / perRow) * 22);
            addRenderableWidget(b);
        }
        panelH = Math.max(panelH, y - panelY + ((extra.size() + perRow - 1) / perRow) * 22 + 8);
    }

    /** 텐키 숫자/B/정정 */
    private void tenkey(String k) {
        tkIdle = 0;
        if (k.equals("C")) { if (!tkInput.isEmpty()) tkInput = tkInput.substring(0, tkInput.length() - 1); return; }
        if (k.equals("B")) { tkInput = tkInput.startsWith("B") ? tkInput : "B" + tkInput; return; }
        if (tkInput.replace("B", "").length() >= 3) return;
        tkInput += k;
    }

    /** 등록: 입력한 층 이름과 같은 층을 찾아 등록(이미 등록된 층이면 취소) */
    private void tenkeyEnter() {
        if (tkInput.isEmpty()) return;
        ListTag names = st().getList("n", Tag.TAG_STRING);
        int idx = TenKey.match(tkInput, names);
        if (idx < 0) {
            tkMsg = I18n.get("elevmod.tenkey.no_floor", tkInput);
        } else {
            byte[] calls = st().getByteArray("c");
            boolean lit = idx < calls.length && (calls[idx] & 1) != 0;
            if (lit) { send(CarAction.CANCEL, idx); tkMsg = I18n.get("elevmod.tenkey.cancelled", names.getString(idx)); }
            else { send(accessible ? CarAction.ACCESSIBLE_FLOOR : CarAction.FLOOR, idx); tkMsg = I18n.get("elevmod.tenkey.registered", names.getString(idx)); }
        }
        tkMsgTicks = 40;
        tkInput = "";
        tkIdle = 0;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (initTenkey && initButtons) {
            if (ch >= '0' && ch <= '9') { tenkey(String.valueOf(ch)); return true; }
            if (ch == 'b' || ch == 'B') { tenkey("B"); return true; }
        }
        return super.charTyped(ch, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (initTenkey && initButtons) {
            if (key == 257 || key == 335) { tenkeyEnter(); return true; }      // Enter
            if (key == 259) { tenkey("C"); return true; }                      // Backspace
        }
        return super.keyPressed(key, scan, mods);
    }

    private void pressFloor(int idx) {
        byte[] calls = st().getByteArray("c");
        boolean lit = idx < calls.length && (calls[idx] & 1) != 0;
        if (lit && lastFloorClick == idx && tick - lastFloorTick < 15) {
            send(CarAction.CANCEL, idx); // 두 번 눌러 취소
            lastFloorClick = -1;
            return;
        }
        lastFloorClick = idx;
        lastFloorTick = tick;
        if (!lit) send(accessible ? CarAction.ACCESSIBLE_FLOOR : CarAction.FLOOR, idx);
    }

    @Override
    public void tick() {
        tick++;
        if (car.isRemoved() || minecraft == null || minecraft.player == null || minecraft.player.distanceTo(car) > 8) { onClose(); return; }
        if (heldAction != 0 && tick % 2 == 0) send(heldAction, 0);
        CompoundTag st = st();
        if (st.getList("n", Tag.TAG_STRING).size() != floorCount || st.getInt("mode") != initMode || st.getBoolean("tenkey") != initTenkey
                || (!st.contains("cbtn") || st.getBoolean("cbtn")) != initButtons) { heldAction = 0; rebuildWidgets(); return; }
        // 텐키: 일정 시간 추가 입력이 없으면 자동 등록
        if (!tkInput.isEmpty() && ++tkIdle >= 40) tenkeyEnter();
        if (tkMsgTicks > 0) tkMsgTicks--;
        byte[] calls = st.getByteArray("c");
        ListTag names = st.getList("n", Tag.TAG_STRING);
        for (int i = 0; i < floorButtons.size(); i++) {
            boolean lit = i < calls.length && (calls[i] & 1) != 0;
            Component c = Component.literal(names.getString(i));
            floorButtons.get(i).setMessage(lit ? c.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD) : c);
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        heldAction = 0;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0202428);
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xF0585E66);
        CompoundTag st = st();
        ListTag names = st.getList("n", Tag.TAG_STRING);
        int n = names.size();
        String cur = n > 0 ? names.getString(Math.max(0, Math.min(st.getInt("cur"), n - 1))) : "-";
        int dir = st.getInt("dir");
        // 표시창
        g.fill(panelX + 10, panelY + 8, panelX + panelW - 10, panelY + 40, 0xFF050505);
        String arrow = dir > 0 ? "▲" : dir < 0 ? "▼" : " ";
        g.pose().pushPose();
        g.pose().translate(panelX + 18, panelY + 13, 0);
        g.pose().scale(2.2f, 2.2f, 1);
        g.drawString(font, arrow + " " + cur, 0, 0, 0xFFFF6A1A, false);
        g.pose().popPose();
        String status = st.getString("st");
        if (!status.isEmpty()) g.drawString(font, I18n.get("elevmod.status." + status), panelX + panelW / 2, panelY + 12, 0xFFFFD040, false);
        if (st.getBoolean("air")) g.drawString(font, "Air-Clean", panelX + panelW / 2, panelY + 26, 0xFF60D0FF, false);
        int kg = st.getInt("kg");
        String spec = I18n.get("elevmod.cop.spec", Spec.persons(kg), kg, st.getInt("spd"));
        g.drawString(font, spec, panelX + 10, panelY + 44, 0xFFE0E0E0, false);
        String ser = I18n.get("elevmod.cop.serial", st.getString("let"), st.getString("ser"));
        g.drawString(font, ser, panelX + panelW - 10 - font.width(ser), panelY + 44, 0xFFB0B0B0, false);
        if (initTenkey && initButtons) {
            int gx = panelX + panelW - 10 - 96, gy = panelY + 62;
            g.fill(gx, gy, gx + 96, gy + 40, 0xFF0A1E3C);
            g.drawString(font, I18n.get("elevmod.tenkey.input"), gx + 4, gy + 3, 0xFF9FD8FF, false);
            g.pose().pushPose();
            g.pose().translate(gx + 6, gy + 15, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawString(font, tkInput.isEmpty() ? "_" : tkInput, 0, 0, 0xFFFFFFFF, false);
            g.pose().popPose();
            // 예약층(등록된 층)
            byte[] calls = st.getByteArray("c");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) if (i < calls.length && (calls[i] & 1) != 0) sb.append(names.getString(i)).append(' ');
            g.fill(gx, gy + 44, gx + 96, gy + 72, 0xFF0A1E3C);
            g.drawString(font, I18n.get("elevmod.tenkey.reserved"), gx + 4, gy + 47, 0xFF9FD8FF, false);
            g.drawString(font, sb.length() == 0 ? "-" : sb.toString().trim(), gx + 4, gy + 59, 0xFFFFD040, false);
            if (tkMsgTicks > 0) g.drawString(font, tkMsg, gx, gy + 78, 0xFFFFFFFF, false);
            g.drawString(font, I18n.get("elevmod.tenkey.hint"), panelX + 10, panelY + panelH - 10, 0xFFB0B0B0, false);
        }
        if (st.getBoolean("dd") && st.getInt("mode") == Spec.Mode.NORMAL.ordinal() && !initButtons)
            g.drawCenteredString(font, I18n.get("elevmod.msg.dd_mode"), width / 2, panelY + panelH + 4, 0xFFFFD040);
        if (car.usage() == Spec.Usage.WHEELCHAIR_LIFT)
            g.drawCenteredString(font, I18n.get("elevmod.cop.lift_hint"), width / 2, panelY + panelH + 4, 0xFFFFD040);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** 누르고 있는 동안 계속 신호를 보내는 버튼 */
    private class HoldButton extends Button {
        private final int press, hold;

        HoldButton(int x, int y, int w, int h, Component msg, int press, int hold) {
            super(x, y, w, h, msg, b -> {}, DEFAULT_NARRATION);
            this.press = press;
            this.hold = hold;
        }

        @Override
        public void onPress() {
            send(press, 0);
            heldAction = hold;
        }
    }
}

package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.item.RotatorItem;
import com.mchoe.elevmod.network.GroupRotateMsg;
import com.mchoe.elevmod.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 회전기 화면: 두 지점으로 잡은 영역 안의 엘리베이터 부품(승강장 문·장치·제어반·기계, 그 승강장 문의 카)을
 * 하나로 묶어 영역 가운데를 축으로 0~360° 돌린다. 각도는 직접 입력하거나 ±1°/±15° 버튼.
 */
public class RotatorScreen extends Screen {
    private final InteractionHand hand;
    private EditBox angleBox;
    private float angle;
    private int parts = -1;

    public RotatorScreen(InteractionHand hand) {
        super(Component.translatable("elevmod.screen.rotator"));
        this.hand = hand;
    }

    private ItemStack stack() { return minecraft.player.getItemInHand(hand); }

    @Override
    protected void init() {
        BlockPos a = RotatorItem.point(stack(), 1), b = RotatorItem.point(stack(), 2);
        if (parts < 0) scan(a, b);
        int cx = width / 2, y = height / 2 - 20;
        angleBox = new EditBox(font, cx - 30, y, 60, 20, Component.literal("angle"));
        angleBox.setMaxLength(6);
        angleBox.setValue(fmt(angle));
        angleBox.setResponder(s -> { try { angle = norm(Float.parseFloat(s.trim().replace(",", "."))); } catch (NumberFormatException ignored) {} });
        addRenderableWidget(angleBox);
        addRenderableWidget(Button.builder(Component.literal("-15°"), x -> step(-15)).bounds(cx - 110, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-1°"), x -> step(-1)).bounds(cx - 70, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1°"), x -> step(1)).bounds(cx + 34, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+15°"), x -> step(15)).bounds(cx + 74, y, 36, 20).build());
        boolean ok = a != null && b != null;
        Button apply = Button.builder(Component.translatable("elevmod.rotator.apply").withStyle(ChatFormatting.GREEN), x -> send(false)).bounds(cx - 110, y + 30, 106, 20).build();
        Button release = Button.builder(Component.translatable("elevmod.rotator.release"), x -> send(true)).bounds(cx + 4, y + 30, 106, 20).build();
        apply.active = ok; release.active = ok;
        addRenderableWidget(apply);
        addRenderableWidget(release);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), x -> onClose()).bounds(cx - 40, height - 28, 80, 20).build());
    }

    private static float norm(float v) { return ((v % 360) + 360) % 360; }

    private static String fmt(float v) { return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format("%.1f", v); }

    private void step(float d) {
        angle = norm(angle + d);
        angleBox.setValue(fmt(angle));
        send(false);
    }

    private void send(boolean release) {
        Net.CH.sendToServer(new GroupRotateMsg(angle, release));
        if (release) { angle = 0; angleBox.setValue("0"); }
    }

    /** 영역 안 부품 수 세기 + 지금 각도 읽기 */
    private void scan(BlockPos a, BlockPos b) {
        parts = 0;
        if (a == null || b == null || minecraft.level == null) return;
        int x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
        int y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
        int z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());
        if (x2 - x1 >= GroupRotateMsg.MAX_XZ || z2 - z1 >= GroupRotateMsg.MAX_XZ) { parts = -2; return; }
        boolean first = true;
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
            if (!minecraft.level.hasChunk(cx, cz)) continue;
            for (BlockEntity be : minecraft.level.getChunk(cx, cz).getBlockEntities().values()) {
                BlockPos q = be.getBlockPos();
                if (q.getX() < x1 || q.getX() > x2 || q.getY() < y1 || q.getY() > y2 || q.getZ() < z1 || q.getZ() > z2) continue;
                float yaw;
                if (be instanceof DeviceBlockEntity d) yaw = d.yaw;
                else if (be instanceof LandingDoorBlockEntity l) yaw = l.yaw;
                else if (be instanceof MachineBlockEntity m) yaw = m.yaw;
                else if (be instanceof ControllerBlockEntity c) yaw = c.yaw;
                else continue;
                parts++;
                if (first) { angle = yaw; first = false; }
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && angleBox.isFocused()) { send(false); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(width / 2 - 125, height / 2 - 100, width / 2 + 125, height / 2 + 64, 0xB0000000);
        g.drawCenteredString(font, title, width / 2, height / 2 - 92, 0xFFFFFF);
        BlockPos a = RotatorItem.point(stack(), 1), b = RotatorItem.point(stack(), 2);
        g.drawCenteredString(font, "① " + (a == null ? I18n.get("elevmod.rotator.unset") : a.toShortString()), width / 2, height / 2 - 76, 0xFFAADDFF);
        g.drawCenteredString(font, "② " + (b == null ? I18n.get("elevmod.rotator.unset") : b.toShortString()), width / 2, height / 2 - 64, 0xFFAADDFF);
        String info = a == null || b == null ? I18n.get("elevmod.rotator.need_points")
                : parts == -2 ? I18n.get("elevmod.rotator.too_big", GroupRotateMsg.MAX_XZ) : I18n.get("elevmod.rotator.parts", parts);
        g.drawCenteredString(font, info, width / 2, height / 2 - 48, 0xFFFFD040);
        g.drawCenteredString(font, I18n.get("elevmod.rotator.angle"), width / 2, height / 2 - 32, 0xFFDDDDDD);
        g.drawCenteredString(font, I18n.get("elevmod.rotator.hint"), width / 2, height / 2 + 56, 0xFF999999);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

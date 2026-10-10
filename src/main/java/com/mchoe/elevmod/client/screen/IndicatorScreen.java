package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.client.indicator.AnimLibrary;
import com.mchoe.elevmod.client.indicator.IndicatorDraw;
import com.mchoe.elevmod.client.render.RenderUtil;
import com.mchoe.elevmod.elevator.DotAnim;
import com.mchoe.elevmod.elevator.IndicatorProfile;
import com.mchoe.elevmod.network.Net;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 층표시기 설정 화면: 전환 효과·화살표 시점 / 도트·세그먼트 모양 / 색(팔레트) / 애니메이션 / 동작(문 열림 지연, 취소, 위치).
 * 시간·크기 같은 값은 직접 입력한다.
 */
public class IndicatorScreen extends Screen {
    public final BlockPos pos;
    private final Screen parent;
    private CompoundTag data;
    private int page = 0, previewKind = 0;
    private static final String[] PAGES = {"elevmod.ind.page.trans", "elevmod.ind.page.shape", "elevmod.ind.page.color", "elevmod.ind.page.anim", "elevmod.ind.page.behavior"};
    private static final String[] KINDS = {"seg", "dot", "lcd"};
    private final List<Label> labels = new ArrayList<>();
    private final Map<String, EditBox> boxes = new HashMap<>();

    private record Label(String text, int x, int y) {}

    public IndicatorScreen(BlockPos pos, Screen parent, CompoundTag data) {
        super(Component.translatable("elevmod.screen.indicator"));
        this.pos = pos;
        this.parent = parent;
        this.data = data;
    }

    public void update(CompoundTag t) {
        this.data = t;
        if (parent instanceof ControllerScreen cs) cs.update(t);
        // 입력 중인 칸은 그대로 둠
        Map<String, String> keep = new HashMap<>();
        String focused = null;
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) if (e.getValue().isFocused()) { keep.put(e.getKey(), e.getValue().getValue()); focused = e.getKey(); }
        rebuildWidgets();
        for (Map.Entry<String, String> e : keep.entrySet()) if (boxes.containsKey(e.getKey())) boxes.get(e.getKey()).setValue(e.getValue());
        if (focused != null && boxes.containsKey(focused)) { setFocused(boxes.get(focused)); boxes.get(focused).setFocused(true); }
    }

    private IndicatorProfile prof() { return IndicatorProfile.load(data.getCompound("ind")); }

    private void edit(String key, int delta, String text) { Net.CH.sendToServer(new Net.EditMsg(pos, key, delta, text)); }

    private int left() { return width / 2 - 175; }

    private static String f1(float v) { return String.format("%.2f", v).replaceAll("0$", ""); }

    @Override
    protected void init() {
        labels.clear();
        boxes.clear();
        int left = left(), top = 28;
        for (int i = 0; i < PAGES.length; i++) {
            final int pi = i;
            addRenderableWidget(Button.builder(Component.translatable(PAGES[i]).withStyle(i == page ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> { page = pi; rebuildWidgets(); })
                    .bounds(left + i * 70, top, 68, 18).build());
        }
        IndicatorProfile p = prof();
        int y = top + 26;
        switch (page) {
            case 0 -> {
                y = cycle(y, "ind.trans", "elevmod.ind.trans", Component.translatable("elevmod.trans." + IndicatorProfile.TRANS[p.trans]));
                y = cycle(y, "ind.transdir", "elevmod.ind.transdir", Component.translatable("elevmod.transdir." + IndicatorProfile.TRANS_DIR[p.transDir]));
                y = typed(y, "ind.transsec", "elevmod.ind.transsec", f1(p.transSec), "elevmod.unit.sec");
                y = cycle(y, "ind.arrowmode", "elevmod.ind.arrowmode", Component.translatable("elevmod.arrowmode." + p.arrowMode));
                if (p.arrowMode != 0) y = typed(y, "ind.arrowsec", "elevmod.ind.arrowsec", f1(p.arrowSec), "elevmod.unit.sec");
                labels.add(new Label(I18n.get("elevmod.ind.arrow_hint"), left, y + 6));
            }
            case 1 -> {
                y = typed(y, "ind.dotsize", "elevmod.ind.dotsize", p.dotCols + "x" + p.dotRows, null);
                y = cycle(y, "ind.dotround", "elevmod.ind.dotround", Component.translatable(p.dotRound ? "elevmod.dot.round" : "elevmod.dot.square"));
                y = cycle(y, "ind.segtype", "elevmod.ind.segtype", Component.translatable("elevmod.segtype." + p.segType));
                y = cycle(y, "ind.segshape", "elevmod.ind.segshape", Component.translatable("elevmod.segshape." + p.segShape));
                y = typed(y, "ind.segslant", "elevmod.ind.segslant", f1(p.segSlant), "elevmod.unit.deg");
                y = typed(y, "ind.segthick", "elevmod.ind.segthick", f1(p.segThick), null);
                y = cycle(y, "ind.seglead", "elevmod.ind.seglead", Component.translatable(p.segLead1 ? "elevmod.on" : "elevmod.off"));
            }
            case 2 -> {
                String[][] cs = {{"dotOn", "elevmod.ind.c.dotOn"}, {"dotOff", "elevmod.ind.c.dotOff"}, {"dotBg", "elevmod.ind.c.dotBg"},
                        {"segOn", "elevmod.ind.c.segOn"}, {"segOff", "elevmod.ind.c.segOff"}, {"segBg", "elevmod.ind.c.segBg"},
                        {"lcdFg", "elevmod.ind.c.lcdFg"}, {"lcdArrow", "elevmod.ind.c.lcdArrow"}, {"lcdBg", "elevmod.ind.c.lcdBg"}};
                int[] vals = {p.dotOn, p.dotOff, p.dotBg, p.segOn, p.segOff, p.segBg, p.lcdFg, p.lcdArrow, p.lcdBg};
                for (int i = 0; i < cs.length; i++) {
                    final String key = "ind." + cs[i][0], lab = cs[i][1];
                    final int v = vals[i];
                    labels.add(new Label(I18n.get(lab), left, y + 6));
                    addRenderableWidget(Button.builder(ColorPickerScreen.swatch(v), b -> minecraft.setScreen(new ColorPickerScreen(this, I18n.get(lab), v, s -> edit(key, 0, s))))
                            .bounds(left + 110, y, 100, 20).build());
                    y += 22;
                }
            }
            case 3 -> {
                y = anim(y, "ind.animUp", "elevmod.ind.animUp", p.animUp);
                y = anim(y, "ind.animDown", "elevmod.ind.animDown", p.animDown);
                y = anim(y, "ind.animIdle", "elevmod.ind.animIdle", p.animIdle);
                addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.open_editor").withStyle(ChatFormatting.AQUA), b -> minecraft.setScreen(new DotAnimEditorScreen(this, null)))
                        .bounds(left, y + 4, 200, 20).build());
                labels.add(new Label(I18n.get("elevmod.ind.anim_hint"), left, y + 30));
            }
            case 4 -> {
                y = typed(y, "doordelay", "elevmod.ind.doordelay", f1(data.getFloat("doorDelay")), "elevmod.unit.sec");
                y = cycle(y, "hallcancel", "elevmod.ind.hallcancel", Component.translatable(data.getBoolean("hallCancel") ? "elevmod.on" : "elevmod.off"));
                if (data.getBoolean("hallCancel")) y = typed(y, "hallcancelN", "elevmod.ind.cancelN", String.valueOf(data.getInt("hallCancelN")), "elevmod.unit.times");
                y = cycle(y, "carcancel", "elevmod.ind.carcancel", Component.translatable(data.getBoolean("carCancel") ? "elevmod.on" : "elevmod.off"));
                if (data.getBoolean("carCancel")) y = typed(y, "carcancelN", "elevmod.ind.cancelN", String.valueOf(data.getInt("carCancelN")), "elevmod.unit.times");
                y = typed(y, "indx", "elevmod.ind.indx", f1(data.getFloat("indOffX")), "elevmod.unit.cm");
                y = typed(y, "indy", "elevmod.ind.indy", f1(data.getFloat("indOffY")), "elevmod.unit.cm");
            }
            default -> {}
        }
        // 미리보기 종류
        addRenderableWidget(Button.builder(Component.translatable("elevmod.ind.preview." + KINDS[previewKind]), b -> { previewKind = (previewKind + 1) % KINDS.length; rebuildWidgets(); })
                .bounds(left + 262, top + 26 + 78, 88, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    private int cycle(int y, String key, String label, Component value) {
        labels.add(new Label(I18n.get(label), left(), y + 6));
        addRenderableWidget(Button.builder(value, b -> edit(key, Screen.hasShiftDown() ? -1 : 1, "")).bounds(left() + 110, y, 100, 20).build());
        return y + 22;
    }

    private int typed(int y, String key, String label, String value, String unit) {
        labels.add(new Label(I18n.get(label), left(), y + 6));
        EditBox box = new EditBox(font, left() + 110, y, 60, 20, Component.literal(key));
        box.setMaxLength(8);
        box.setValue(value);
        boxes.put(key, box);
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.translatable("elevmod.set.apply"), b -> edit(key, 0, box.getValue())).bounds(left() + 174, y, 36, 20).build());
        if (unit != null) labels.add(new Label(I18n.get(unit), left() + 214, y + 6));
        return y + 22;
    }

    private int anim(int y, String key, String label, String cur) {
        labels.add(new Label(I18n.get(label), left(), y + 6));
        DotAnim a = AnimLibrary.get(cur);
        Component v = a == null ? Component.translatable("elevmod.anim.builtin") : Component.literal(a.name).withStyle(ChatFormatting.AQUA);
        addRenderableWidget(Button.builder(v, b -> {
            // 기본 화살표 → 라이브러리 애니메이션들 순서로 돌기
            List<String> ids = new ArrayList<>();
            ids.add("");
            for (DotAnim x : AnimLibrary.all()) ids.add(x.id);
            int i = Math.max(0, ids.indexOf(cur));
            i = Math.floorMod(i + (Screen.hasShiftDown() ? -1 : 1), ids.size());
            edit(key, 0, ids.get(i));
        }).bounds(left() + 110, y, 140, 20).build());
        return y + 22;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            for (Map.Entry<String, EditBox> e : boxes.entrySet()) if (e.getValue().isFocused()) { edit(e.getKey(), 0, e.getValue().getValue()); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), 0xFFDDDDDD, false);
        super.render(g, mx, my, pt);
        renderPreview(g, left() + 262, 54, 88, 72);
    }

    /** 미리보기: 1→5→1 층을 오가는 모습 */
    private void renderPreview(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF505050);
        g.fill(x, y, x + w, y + h, 0xFF101010);
        float t = minecraft.level == null ? (System.currentTimeMillis() % 1000000) / 1000f : (minecraft.level.getGameTime() + minecraft.getFrameTime()) / 20f;
        int step = (int) (t / 2.5f) % 8;
        int[] fl = {1, 2, 3, 4, 5, 4, 3, 2};
        int[] dr = {1, 1, 1, 1, -1, -1, -1, -1};
        String text = String.valueOf(fl[step]);
        int dir = dr[step];
        String kind = KINDS[previewKind];
        int on = switch (kind) { case "dot" -> 0xFFFF8A18; case "lcd" -> 0xFF7FE3FF; default -> 0xFFFF2A10; };
        int bg = kind.equals("lcd") ? 0xFF0E2E66 : 0xFF080808;
        float S = w;   // 1 단위 = 미리보기 폭
        float bw = 1f, bh = (float) h / w;
        g.pose().pushPose();
        g.pose().translate(x, y + h, 50);
        g.pose().scale(S, -S, S);
        Lighting.setupForFlatItems();
        IndicatorDraw.draw(g.pose(), g.bufferSource(), prof(), kind, on, bg, 0.04f, 0.06f, bw - 0.04f, bh - 0.06f, 0, RenderUtil.FULL, text, dir, true, "gui_preview_" + kind);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

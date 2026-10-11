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
    private static final String[] PAGES = {"elevmod.ind.page.trans", "elevmod.ind.page.shape", "elevmod.ind.page.color", "elevmod.ind.page.anim", "elevmod.ind.page.behavior", "elevmod.ind.page.celllayout", "elevmod.ind.page.alt"};

    /**
     * 무엇의 디자인을 바꾸는지: 제어반(기본값) / 승강장 표시기 하나 / 카 문 위 표시기 / 운전반 표시창.
     * 개별 디자인은 처음 바꿀 때 제어반 설정을 복사해 시작하고, "제어반 설정 따르기" 로 되돌린다.
     */
    private int target = 0;          // 0 제어반, 1 승강장 장치, 2 카 문 위 표시기, 3 운전반 표시창
    private com.mchoe.elevmod.entity.ElevatorCarEntity car;
    private boolean lantern;
    private CompoundTag seenTag;

    /** 승강장 표시기·홀랜턴 하나의 개별 디자인 */
    public static IndicatorScreen forDevice(BlockPos devicePos, boolean lantern, String kind) {
        IndicatorScreen s = new IndicatorScreen(devicePos, null, new CompoundTag());
        s.target = 1;
        s.lantern = lantern;
        s.previewKind = Math.max(0, java.util.Arrays.asList(KINDS).indexOf(kind));
        if (lantern) s.page = 4;
        return s;
    }

    /** 카 안 표시기(part 2 = 문 위, 3 = 운전반 표시창)의 개별 디자인 */
    public static IndicatorScreen forCar(com.mchoe.elevmod.entity.ElevatorCarEntity car, int part) {
        IndicatorScreen s = new IndicatorScreen(car.controller(), null, new CompoundTag());
        s.target = part;
        s.car = car;
        int disp = car.state().getInt("disp");
        s.previewKind = disp == 1 ? 1 : disp == 2 ? 2 : 0;
        return s;
    }

    public boolean isController() { return target == 0; }

    /** 지금 보여 줄 설정 태그 */
    private CompoundTag tag() {
        switch (target) {
            case 1 -> {
                if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof com.mchoe.elevmod.block.DeviceBlockEntity d)
                    return d.ownInd.isEmpty() ? d.indTag : d.ownInd;
                return new CompoundTag();
            }
            case 2, 3 -> {
                CompoundTag st = car.state();
                String k = target == 2 ? "indT" : "indC";
                return st.contains(k) ? st.getCompound(k) : st.getCompound("ind");
            }
            default -> { return data.getCompound("ind"); }
        }
    }

    /** 개별 디자인을 쓰는 중인지 */
    private boolean own() {
        if (target == 1) return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof com.mchoe.elevmod.block.DeviceBlockEntity d && !d.ownInd.isEmpty();
        if (target >= 2) return car.state().contains(target == 2 ? "indT" : "indC");
        return false;
    }

    /** 표시기 설정 한 항목 보내기 (field = 항목 이름) */
    private void sendInd(String field, int delta, String text) {
        switch (target) {
            case 1 -> Net.CH.sendToServer(new Net.DevIndMsg(pos, field, delta, text));
            case 2 -> Net.CH.sendToServer(new Net.EditMsg(pos, "indT." + field, delta, text));
            case 3 -> Net.CH.sendToServer(new Net.EditMsg(pos, "indC." + field, delta, text));
            default -> Net.CH.sendToServer(new Net.EditMsg(pos, "ind." + field, delta, text));
        }
    }
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

    public void page(int p) { page = p; }

    @Override
    public void tick() {
        // 개별 디자인은 블록·카 상태가 바뀌면 다시 그림 (입력 중인 칸은 유지)
        if (target != 0 && tag() != seenTag) refresh();
    }

    private void refresh() {
        Map<String, String> keep = new HashMap<>();
        String focused = null;
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) if (e.getValue().isFocused()) { keep.put(e.getKey(), e.getValue().getValue()); focused = e.getKey(); }
        rebuildWidgets();
        for (Map.Entry<String, String> e : keep.entrySet()) if (boxes.containsKey(e.getKey())) boxes.get(e.getKey()).setValue(e.getValue());
        if (focused != null && boxes.containsKey(focused)) { setFocused(boxes.get(focused)); boxes.get(focused).setFocused(true); }
    }

    private IndicatorProfile prof() { return IndicatorProfile.load(tag()); }

    /** "ind.항목" 이면 대상의 표시기 설정, 그 밖은 제어반 설정 */
    private void edit(String key, int delta, String text) {
        if (key.startsWith("ind.")) sendInd(key.substring(4), delta, text);
        else Net.CH.sendToServer(new Net.EditMsg(pos, key, delta, text));
    }

    private int left() { return width / 2 - 175; }

    private static String f1(float v) { return String.format("%.2f", v).replaceAll("0$", ""); }

    @Override
    protected void init() {
        labels.clear();
        boxes.clear();
        int left = left(), top = 28;
        for (int i = 0; i < PAGES.length; i++) {
            final int pi = i;
            if (i == 4 && target != 0 && !lantern) continue;   // 동작 페이지: 제어반(전체)과 홀랜턴만
            addRenderableWidget(Button.builder(Component.translatable(i == 4 && target != 0 ? "elevmod.ind.page.lantern" : PAGES[i]).withStyle(i == page ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> { page = pi; rebuildWidgets(); })
                    .bounds(left + i * 50, top, 49, 18).build());
        }
        if (page == 4 && target != 0 && !lantern) page = 0;
        IndicatorProfile p = prof();
        seenTag = tag();
        int y = top + 26;
        if (target != 0) {
            // 개별 디자인 / 제어반 설정 따름
            labels.add(new Label(I18n.get(own() ? "elevmod.ind.own_on" : "elevmod.ind.own_off"), left, height - 50));
            addRenderableWidget(Button.builder(Component.translatable("elevmod.ind.follow"), b -> sendInd("follow", 0, ""))
                    .bounds(left + 200, height - 56, 140, 20).build());
        }
        switch (page) {
            case 0 -> {
                y = cycle(y, "ind.trans", "elevmod.ind.trans", Component.translatable("elevmod.trans." + IndicatorProfile.TRANS[p.trans]));
                y = cycle(y, "ind.transdir", "elevmod.ind.transdir", Component.translatable("elevmod.transdir." + IndicatorProfile.TRANS_DIR[p.transDir]));
                y = typed(y, "ind.transsec", "elevmod.ind.transsec", f1(p.transSec), "elevmod.unit.sec");
                y = cycle(y, "ind.arrowmode", "elevmod.ind.arrowmode", Component.translatable("elevmod.arrowmode." + p.arrowMode));
                if (p.arrowMode != 0) y = typed(y, "ind.arrowsec", "elevmod.ind.arrowsec", f1(p.arrowSec), "elevmod.unit.sec");
                // 목적층이 없을 때 화살표가 꺼지는 시점
                y = cycle(y, "ind.offmode", "elevmod.ind.offmode", Component.translatable("elevmod.arrowmode." + p.offMode));
                if (p.offMode != 0) y = typed(y, "ind.offsec", "elevmod.ind.offsec", f1(p.offSec), "elevmod.unit.sec");
                y = cycle(y, "ind.openarrow", "elevmod.ind.openarrow", Component.translatable(p.openArrow ? "elevmod.on" : "elevmod.off"));
                labels.add(new Label(I18n.get("elevmod.ind.arrow_hint"), left, y + 6));
            }
            case 1 -> {
                y = cycle(y, "ind.digits", "elevmod.ind.digits", Component.translatable("elevmod.ind.digits.n", p.digits));
                y = cycle(y, "ind.align", "elevmod.ind.align", Component.translatable("elevmod.align." + IndicatorProfile.ALIGN[p.align]));
                y = typed(y, "ind.dotsize", "elevmod.ind.dotsize", p.dotCols + "x" + p.dotRows, null);
                y = cycle(y, "ind.dotround", "elevmod.ind.dotround", Component.translatable(p.dotRound ? "elevmod.dot.round" : "elevmod.dot.square"));
                y = fontRow(y, p.dotFont);
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
                labels.add(new Label(I18n.get("elevmod.ind.anim_down_note"), left() + 4, y + 2));
                y += 14;
                y = anim(y, "ind.animOpen", "elevmod.ind.animOpen", p.animOpen);
                y = anim(y, "ind.animIdle", "elevmod.ind.animIdle", p.animIdle);
                addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.open_editor").withStyle(ChatFormatting.AQUA), b -> minecraft.setScreen(new DotAnimEditorScreen(this, null)))
                        .bounds(left, y + 4, 200, 20).build());
                labels.add(new Label(I18n.get("elevmod.ind.anim_hint"), left, y + 30));
            }
            case 4 -> {
                y = cycle(y, "ind.lanblink", "elevmod.ind.lanblink", Component.translatable(p.lanBlink ? "elevmod.on" : "elevmod.off"));
                if (p.lanBlink) y = typed(y, "ind.lanblinksec", "elevmod.ind.lanblinksec", f1(p.lanBlinkSec), "elevmod.unit.sec");
                if (target != 0) break;
                y = typed(y, "doordelay", "elevmod.ind.doordelay", f1(data.getFloat("doorDelay")), "elevmod.unit.sec");
                y = cycle(y, "hallcancel", "elevmod.ind.hallcancel", Component.translatable(data.getBoolean("hallCancel") ? "elevmod.on" : "elevmod.off"));
                if (data.getBoolean("hallCancel")) y = typed(y, "hallcancelN", "elevmod.ind.cancelN", String.valueOf(data.getInt("hallCancelN")), "elevmod.unit.times");
                y = cycle(y, "carcancel", "elevmod.ind.carcancel", Component.translatable(data.getBoolean("carCancel") ? "elevmod.on" : "elevmod.off"));
                if (data.getBoolean("carCancel")) y = typed(y, "carcancelN", "elevmod.ind.cancelN", String.valueOf(data.getInt("carCancelN")), "elevmod.unit.times");
            }
            case 5 -> {
                // 표시 칸 배치: 화살표 칸 폭·크기, 숫자 위치·크기 (모든 표시기에 적용)
                y = typed(y, "ind.arroww", "elevmod.ind.arroww", String.valueOf(p.arrowW), "elevmod.unit.pct");
                y = typed(y, "ind.arrowscale", "elevmod.ind.arrowscale", String.valueOf(p.arrowScale), "elevmod.unit.pct");
                y = typed(y, "ind.numx", "elevmod.ind.numx", String.valueOf(p.numX), "elevmod.unit.pct");
                y = typed(y, "ind.numy", "elevmod.ind.numy", String.valueOf(p.numY), "elevmod.unit.pct");
                y = typed(y, "ind.numscale", "elevmod.ind.numscale", String.valueOf(p.numScale), "elevmod.unit.pct");
                y = cycle(y, "ind.vertical", "elevmod.ind.vertical", Component.translatable(p.vertical ? "elevmod.ind.layout.v" : "elevmod.ind.layout.h"));
                if (p.vertical) y = typed(y, "ind.arrowh", "elevmod.ind.arrowh", String.valueOf(p.arrowH), "elevmod.unit.pct");
                y = cycle(y, "ind.numkind", "elevmod.ind.numkind", Component.translatable("elevmod.elem." + IndicatorProfile.ELEM[p.numKind]));
                y = cycle(y, "ind.arrowkind", "elevmod.ind.arrowkind", Component.translatable("elevmod.elem." + IndicatorProfile.ELEM[p.arrowKind]));
                labels.add(new Label(I18n.get("elevmod.ind.cell_hint"), left, y + 6));
            }
            case 6 -> {
                // 번갈아 표시: 한 칸에서 화살표 ↔ 숫자 (도트는 도트 단위로 밀리며 전환)
                y = cycle(y, "ind.altmode", "elevmod.ind.altmode", Component.translatable(p.altMode ? "elevmod.on" : "elevmod.off"));
                y = typed(y, "ind.altarrowsec", "elevmod.ind.altarrowsec", f1(p.altArrowSec), "elevmod.unit.sec");
                y = typed(y, "ind.altnumsec", "elevmod.ind.altnumsec", f1(p.altNumSec), "elevmod.unit.sec");
                y = cycle(y, "ind.alttrans", "elevmod.ind.alttrans", Component.translatable("elevmod.trans." + IndicatorProfile.TRANS[p.altTrans]));
                y = cycle(y, "ind.alttransdir", "elevmod.ind.alttransdir", Component.translatable("elevmod.transdir." + IndicatorProfile.TRANS_DIR[p.altTransDir]));
                y = typed(y, "ind.alttranssec", "elevmod.ind.alttranssec", f1(p.altTransSec), "elevmod.unit.sec");
                labels.add(new Label(I18n.get("elevmod.ind.alt_hint"), left, y + 6));
            }
            default -> {}
        }
        // 미리보기 종류
        addRenderableWidget(Button.builder(Component.translatable("elevmod.ind.preview." + KINDS[previewKind]), b -> { previewKind = (previewKind + 1) % KINDS.length; rebuildWidgets(); })
                .bounds(left + 262, top + 26 + 92, 88, 18).build());
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

    /** 도트 글꼴 고르기 (기본 → 저장한 글꼴들) */
    private int fontRow(int y, String cur) {
        labels.add(new Label(I18n.get("elevmod.ind.dotfont"), left(), y + 6));
        DotAnim a = AnimLibrary.get(cur);
        Component v = a == null || !a.isFont() ? Component.translatable("elevmod.font.builtin") : Component.literal(a.name).withStyle(ChatFormatting.AQUA);
        addRenderableWidget(Button.builder(v, b -> {
            List<String> ids = new ArrayList<>();
            ids.add("");
            for (DotAnim x : AnimLibrary.all()) if (x.isFont()) ids.add(x.id);
            int i = Math.max(0, ids.indexOf(cur));
            i = Math.floorMod(i + (Screen.hasShiftDown() ? -1 : 1), ids.size());
            edit("ind.dotfont", 0, ids.get(i));
        }).bounds(left() + 110, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.font.edit"), b -> minecraft.setScreen(new DotAnimEditorScreen(this, a != null && a.isFont() ? a.id : null))).bounds(left() + 212, y, 44, 20).build());
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
            for (DotAnim x : AnimLibrary.all()) if (!x.isFont()) ids.add(x.id);
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
    public void onClose() {
        if (parent instanceof ControllerScreen cs) cs.update(data);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        String tt = switch (target) { case 1 -> I18n.get(lantern ? "elevmod.ind.title.lantern" : "elevmod.ind.title.device"); case 2 -> I18n.get("elevmod.ind.title.transom"); case 3 -> I18n.get("elevmod.ind.title.cop"); default -> title.getString(); };
        g.drawCenteredString(font, tt, width / 2, 12, 0xFFFFFF);
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), 0xFFDDDDDD, false);
        super.render(g, mx, my, pt);
        if (prof().vertical) renderPreview(g, left() + 280, 54, 52, 84);
        else renderPreview(g, left() + 262, 54, 88, 72);
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

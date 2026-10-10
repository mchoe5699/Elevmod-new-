package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 제어반 설정 화면 */
public class ControllerScreen extends Screen {
    public final BlockPos pos;
    private CompoundTag data;
    private int tab = 0, renameIdx = 0;
    private EditBox nameBox;
    private final List<String> info = new ArrayList<>();
    private static final String[] TABS = {"elevmod.tab.spec", "elevmod.tab.options", "elevmod.tab.floors", "elevmod.tab.operation", "elevmod.tab.info", "elevmod.tab.size"};

    public ControllerScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("elevmod.screen.controller"));
        this.pos = pos;
        this.data = data;
    }

    public void update(CompoundTag t) {
        this.data = t;
        String keep = nameBox != null ? nameBox.getValue() : "";
        rebuildWidgets();
        if (nameBox != null) nameBox.setValue(keep);
    }

    private void edit(String key, int delta, String text) { Net.CH.sendToServer(new Net.EditMsg(pos, key, delta, text)); }

    private static <E extends Enum<E>> E en(E[] v, int i) { return v[Math.max(0, Math.min(i, v.length - 1))]; }

    private Component onOff(String k) {
        return data.getBoolean(k) ? Component.translatable("elevmod.on").withStyle(ChatFormatting.GREEN) : Component.translatable("elevmod.off").withStyle(ChatFormatting.GRAY);
    }

    private String floorName(int y) {
        if (y == Integer.MIN_VALUE) return I18n.get("elevmod.none");
        ListTag ys = data.getList("ys", Tag.TAG_INT), names = data.getList("names", Tag.TAG_STRING);
        for (int i = 0; i < ys.size(); i++) if (ys.getInt(i) == y) return names.getString(i);
        return I18n.get("elevmod.none");
    }

    private Component size(CompoundTag t, String key, String actual) {
        float v = t.getFloat(key);
        if (v > 0) return Component.translatable("elevmod.size.m", String.format("%.1f", v)).withStyle(ChatFormatting.AQUA);
        if (actual != null) return Component.translatable("elevmod.size.auto_val", String.format("%.2f", t.getFloat(actual)));
        return Component.translatable("elevmod.size.auto");
    }

    private record Row(String key, String label, Function<CompoundTag, Component> value) {}

    private List<Row> rows() {
        List<Row> r = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                r.add(new Row("usage", "elevmod.set.usage", t -> Component.translatable(en(Spec.Usage.values(), t.getInt("usage")).key())));
                r.add(new Row("drive", "elevmod.set.drive", t -> Component.translatable(en(Spec.Drive.values(), t.getInt("drive")).key())));
                r.add(new Row("cap", "elevmod.set.cap", t -> {
                    int kg = Spec.CAPACITY_KG[Math.max(0, Math.min(t.getInt("cap"), Spec.CAPACITY_KG.length - 1))];
                    return Component.translatable("elevmod.cap", Spec.persons(kg), kg);
                }));
                r.add(new Row("speed", "elevmod.set.speed", t -> {
                    int mpm = Spec.SPEED_MPM[Math.max(0, Math.min(t.getInt("speed"), Spec.SPEED_MPM.length - 1))];
                    return Component.translatable("elevmod.speed", mpm, Component.translatable(Spec.speedClass(mpm)));
                }));
                r.add(new Row("door", "elevmod.set.door", t -> Component.translatable(en(Spec.DoorType.values(), t.getInt("doorType")).key())));
                r.add(new Row("theme", "elevmod.set.theme", t -> Component.translatable(en(Spec.Theme.values(), t.getInt("theme")).key())));
            }
            case 1 -> {
                r.add(new Row("voice", "elevmod.set.voice", t -> Component.translatable(en(Spec.Voice.values(), t.getInt("voice")).key())));
                r.add(new Row("beep", "elevmod.set.beep", t -> onOff("beep")));
                r.add(new Row("dwell", "elevmod.set.dwell", t -> Component.translatable("elevmod.seconds", t.getInt("dwell"))));
                r.add(new Row("return", "elevmod.set.return", t -> t.getInt("return") == 0 ? Component.translatable("elevmod.off") : Component.translatable("elevmod.seconds", t.getInt("return"))));
                r.add(new Row("ard", "elevmod.set.ard", t -> onOff("ard")));
                r.add(new Row("ropebrake", "elevmod.set.ropebrake", t -> onOff("ropebrake")));
                r.add(new Row("preopen", "elevmod.set.preopen", t -> onOff("preopen")));
                r.add(new Row("window", "elevmod.set.window", t -> onOff("window")));
                r.add(new Row("airclean", "elevmod.set.airclean", t -> onOff("airclean")));
                r.add(new Row("aircon", "elevmod.set.aircon", t -> onOff("aircon")));
                r.add(new Row("stopswitch", "elevmod.set.stopswitch", t -> onOff("stopswitch")));
                r.add(new Row("acccop", "elevmod.set.acccop", t -> onOff("acccop")));
                r.add(new Row("dd", "elevmod.set.dd", t -> onOff("dd")));
                r.add(new Row("inclined", "elevmod.set.inclined", t -> onOff("inclined")));
                r.add(new Row("rs", "elevmod.set.rs", t -> Component.translatable(en(Spec.RedstoneFn.values(), t.getInt("rs")).key())));
            }
            case 2 -> {
                r.add(new Row("lobby", "elevmod.set.lobby", t -> Component.literal(floorName(t.getInt("lobby")))));
                r.add(new Row("home", "elevmod.set.home", t -> Component.literal(floorName(t.getInt("home")))));
                r.add(new Row("fire", "elevmod.set.fire", t -> Component.literal(floorName(t.getInt("fire")))));
                r.add(new Row("fourf", "elevmod.set.fourf", t -> onOff("fourf")));
                r.add(new Row("skip4", "elevmod.set.skip4", t -> onOff("skip4")));
                r.add(new Row("skip13", "elevmod.set.skip13", t -> onOff("skip13")));
            }
            case 5 -> {
                r.add(new Row("carw", "elevmod.set.carw", t -> size(t, "setW", "cw")));
                r.add(new Row("card", "elevmod.set.card", t -> size(t, "setD", "cd")));
                r.add(new Row("carh", "elevmod.set.carh", t -> size(t, "setH", "ch")));
                r.add(new Row("clearw", "elevmod.set.clearw", t -> size(t, "clearW", null)));
                r.add(new Row("clearh", "elevmod.set.clearh", t -> size(t, "clearH", null)));
                r.add(new Row("jamb", "elevmod.set.jamb", t -> Component.translatable(en(Spec.Jamb.values(), t.getInt("jamb")).key())));
                r.add(new Row("ceiling", "elevmod.set.ceiling", t -> Component.translatable(en(Spec.Ceiling.values(), t.getInt("ceiling")).key())));
                r.add(new Row("copside", "elevmod.set.copside", t -> Component.translatable(en(Spec.Side.values(), t.getInt("copSide")).key())));
                r.add(new Row("hand", "elevmod.set.hand", t -> Component.translatable(en(Spec.Side.values(), Math.max(1, t.getInt("doorHand"))).key())));
            }
            default -> {}
        }
        return r;
    }

    @Override
    protected void init() {
        int left = width / 2 - 170;
        int top = 28;
        for (int i = 0; i < TABS.length; i++) {
            final int ti = i;
            Button b = Button.builder(Component.translatable(TABS[i]).withStyle(i == tab ? ChatFormatting.YELLOW : ChatFormatting.WHITE), x -> { tab = ti; rebuildWidgets(); })
                    .bounds(left + i * 57, top, 55, 18).build();
            addRenderableWidget(b);
        }
        int y0 = top + 26;
        List<Row> rows = rows();
        int perCol = 8;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int col = i / perCol, ri = i % perCol;
            int x = left + col * 172 + 92;
            int y = y0 + ri * 22;
            addRenderableWidget(Button.builder(row.value().apply(data), b -> edit(row.key(), Screen.hasShiftDown() ? -1 : 1, ""))
                    .bounds(x, y, 78, 20).build());
        }
        nameBox = null;
        if (tab == 2) {
            ListTag names = data.getList("names", Tag.TAG_STRING);
            int y = y0 + 6 * 22 + 6;
            if (names.size() > 0) {
                renameIdx = Math.min(renameIdx, names.size() - 1);
                addRenderableWidget(Button.builder(Component.translatable("elevmod.set.rename_floor", names.getString(renameIdx)), b -> {
                    renameIdx = (renameIdx + (Screen.hasShiftDown() ? -1 : 1) + names.size()) % names.size();
                    rebuildWidgets();
                }).bounds(left, y, 110, 20).build());
                nameBox = new EditBox(font, left + 114, y, 70, 20, Component.literal("name"));
                nameBox.setMaxLength(6);
                addRenderableWidget(nameBox);
                addRenderableWidget(Button.builder(Component.translatable("elevmod.set.apply"), b -> edit("rename", renameIdx, nameBox.getValue()))
                        .bounds(left + 188, y, 60, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("elevmod.set.reset_name"), b -> edit("rename", renameIdx, ""))
                        .bounds(left + 252, y, 70, 20).build());
            }
        }
        if (tab == 5) {
            addRenderableWidget(Button.builder(Component.translatable("elevmod.set.size_auto"), b -> edit("size_auto", 0, ""))
                    .bounds(left, y0 + 8 * 22 + 6, 110, 20).build());
        }
        if (tab == 3) {
            String[][] ops = {
                    {"mode_normal", "elevmod.op.normal"}, {"mode_parking", "elevmod.op.parking"}, {"mode_sabbath", "elevmod.op.sabbath"},
                    {"mode_shuttle", "elevmod.op.shuttle"}, {"mode_quake", "elevmod.op.quake"}, {"mode_fire", "elevmod.op.fire"},
                    {"power", "elevmod.op.power"}, {"rescue", "elevmod.op.rescue"}, {"reset", "elevmod.op.reset"},
                    {"sim_rope", "elevmod.op.sim_rope"}, {"sim_brake", "elevmod.op.sim_brake"}, {"inspect", "elevmod.op.inspect"},
                    {"respawn", "elevmod.op.respawn"}, {"unlink_group", "elevmod.op.unlink_group"}};
            for (int i = 0; i < ops.length; i++) {
                String[] op = ops[i];
                ChatFormatting c = op[0].startsWith("sim") ? ChatFormatting.RED : ChatFormatting.WHITE;
                addRenderableWidget(Button.builder(Component.translatable(op[1]).withStyle(c), b -> edit(op[0], 0, ""))
                        .bounds(left + (i % 3) * 114, y0 + 34 + (i / 3) * 22, 110, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int left = width / 2 - 170;
        g.drawCenteredString(font, Component.translatable("elevmod.screen.controller_title", data.getString("serial"), data.getString("letter")), width / 2, 10, 0xFFFFFF);
        int y0 = 54;
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            int col = i / 8, ri = i % 8;
            g.drawString(font, I18n.get(rows.get(i).label()), left + col * 172, y0 + ri * 22 + 6, 0xFFDDDDDD, false);
        }
        String state = I18n.get(data.getString("modeK")) + " / " + data.getString("state") + (data.getBoolean("powerOff") ? " / " + I18n.get("elevmod.status.power") : "")
                + (!"elevmod.fault.none".equals(data.getString("faultK")) ? " / " + I18n.get(data.getString("faultK")) : "");
        if (tab == 3) g.drawString(font, I18n.get("elevmod.info.state", state), left, y0 + 6, 0xFFFFD040, false);
        if (tab == 5) {
            g.drawString(font, I18n.get("elevmod.info.dims", String.format("%.2f", data.getFloat("cw")), String.format("%.2f", data.getFloat("cd")), String.format("%.2f", data.getFloat("ch"))),
                    left + 120, y0 + 8 * 22 + 12, 0xFFAAAAAA, false);
            g.drawString(font, I18n.get("elevmod.info.size_hint"), left, y0 + 8 * 22 + 32, 0xFF888888, false);
        }
        if (tab == 0) {
            g.drawString(font, I18n.get("elevmod.info.dims", String.format("%.2f", data.getFloat("cw")), String.format("%.2f", data.getFloat("cd")), String.format("%.2f", data.getFloat("ch"))),
                    left, y0 + 6 * 22 + 8, 0xFFAAAAAA, false);
            g.drawString(font, I18n.get("elevmod.info.effective", data.getInt("mpm")), left, y0 + 6 * 22 + 20, 0xFFAAAAAA, false);
            g.drawString(font, I18n.get("elevmod.info.hint_cycle"), left, y0 + 6 * 22 + 32, 0xFF888888, false);
        }
        if (tab == 2) {
            ListTag names = data.getList("names", Tag.TAG_STRING);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < names.size(); i++) { if (i > 0) sb.append("  "); sb.append(names.getString(i)); }
            g.drawString(font, I18n.get("elevmod.info.floors", names.size()), left + 180, y0 + 6, 0xFFDDDDDD, false);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(sb.toString()), 160);
            for (int i = 0; i < Math.min(lines.size(), 7); i++) g.drawString(font, lines.get(i), left + 180, y0 + 20 + i * 10, 0xFFFFB060, false);
        }
        if (tab == 4) {
            int y = y0;
            String[] lines = {
                    I18n.get("elevmod.info.state", state),
                    I18n.get("elevmod.info.load", data.getInt("load"), data.getInt("rated")),
                    I18n.get("elevmod.info.position", data.getString("cur"), String.format("%.1f", data.getDouble("rise"))),
                    I18n.get("elevmod.info.group", data.getString("letter"), data.getInt("groupSize")),
                    I18n.get("elevmod.info.devices", data.getInt("devices"), data.getInt("machines")),
                    I18n.get("elevmod.info.stats", data.getLong("trips"), String.format("%.1f", data.getDouble("dist"))),
            };
            for (String l : lines) { g.drawString(font, l, left, y, 0xFFDDDDDD, false); y += 12; }
            y += 4;
            g.drawString(font, I18n.get("elevmod.info.log"), left, y, 0xFFFFD040, false);
            y += 12;
            ListTag log = data.getList("log", Tag.TAG_STRING);
            for (int i = log.size() - 1; i >= 0; i--) {
                String[] p = log.getString(i).split("\\|", 2);
                if (p.length == 2) { g.drawString(font, I18n.get("elevmod.info.log_line", p[0], I18n.get(p[1])), left + 6, y, 0xFFBBBBBB, false); y += 11; }
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

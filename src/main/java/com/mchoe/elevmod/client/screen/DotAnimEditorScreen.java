package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.client.indicator.AnimLibrary;
import com.mchoe.elevmod.elevator.DotAnim;
import com.mchoe.elevmod.network.AnimNet;
import com.mchoe.elevmod.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * 도트매트릭스 애니메이션 편집기.
 * 왼쪽: 애니메이션 목록(새로/복제/삭제), 가운데: 도트 칸(왼쪽 클릭·끌기 = 켜기, 오른쪽 = 끄기), 오른쪽: 팔레트·장면·시간.
 * 장면마다 도트를 하나하나 직접 켜고 끄며, 장면이 넘어가는 시간(초)은 직접 입력한다.
 */
public class DotAnimEditorScreen extends Screen {
    private final Screen parent;
    private DotAnim anim;          // 편집 중인 사본
    private int frame = 0, color = 1, listTop = 0;
    private boolean dirty, playing, painting;
    private int paintValue;
    private int seenVersion = -1;
    private EditBox nameBox, secBox, sizeBox;
    private static final int LIST_ROWS = 7;

    public DotAnimEditorScreen(Screen parent, @Nullable String id) {
        super(Component.translatable("elevmod.screen.anim_editor"));
        this.parent = parent;
        DotAnim a = id == null ? null : AnimLibrary.get(id);
        if (a == null && !AnimLibrary.all().isEmpty()) a = AnimLibrary.all().get(0);
        anim = a == null ? fresh() : copy(a);
    }

    public Screen parent() { return parent; }

    private static DotAnim copy(DotAnim a) { return DotAnim.fromJson(a.toJson()); }

    private static String newId() {
        String al = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder("a_");
        for (int i = 0; i < 10; i++) sb.append(al.charAt(r.nextInt(al.length())));
        return sb.toString();
    }

    /** 새 애니메이션: 5×7, 위 화살표가 한 칸씩 올라가는 3장면 예시 */
    private static DotAnim fresh() {
        DotAnim a = new DotAnim();
        a.id = newId();
        a.name = I18n.get("elevmod.anim.new_name");
        String[] arrow = {"..1..", ".111.", "1.1.1", "..1..", "..1..", "..1..", "..1.."};
        for (int s = 0; s < 3; s++) {
            DotAnim.Frame f = new DotAnim.Frame(a.cols * a.rows);
            f.sec = 0.25f;
            for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) {
                int sy = y + 2 - s * 2;  // 아래에서 위로 흘러감
                sy = Math.floorMod(sy, 7);
                if (arrow[sy].charAt(x) == '1') f.dots[y * 5 + x] = 1;
            }
            a.frames.add(f);
        }
        return a;
    }

    /** 새 도트 글꼴: 기본 글꼴을 본떠 0~9, A~Z, - 를 미리 채움 */
    private static DotAnim freshFont() {
        DotAnim a = new DotAnim();
        a.id = newId();
        a.name = I18n.get("elevmod.anim.new_font");
        a.kind = "font";
        for (int i = 0; i < DotAnim.CHARS.length(); i++) {
            DotAnim.Frame f = new DotAnim.Frame(35);
            String[] g = com.mchoe.elevmod.client.render.Glyphs.dotGlyph(DotAnim.CHARS.charAt(i));
            for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) if (g[y].charAt(x) == '#') f.dots[y * 5 + x] = 1;
            a.frames.add(f);
        }
        return a;
    }

    /** frame = -1 이면 정지 장면 */
    private DotAnim.Frame cur() {
        if (frame == -1 && !anim.isFont()) {
            if (anim.stop == null) anim.stop = anim.frames.get(0).copy();
            return anim.stop;
        }
        frame = Math.max(0, Math.min(frame, anim.frames.size() - 1));
        return anim.frames.get(frame);
    }

    private int left() { return width / 2 - 200; }

    private int top() { return 30; }

    // 도트 칸 영역
    private int gridX() { return left() + 104; }

    private int gridY() { return top() + 4; }

    private int cell() { return Math.max(4, Math.min(180 / anim.cols, 150 / anim.rows)); }

    @Override
    protected void init() {
        secBox = null;
        seenVersion = AnimLibrary.version;
        int l = left(), t = top();
        // ---- 목록
        List<DotAnim> all = AnimLibrary.all();
        listTop = Math.max(0, Math.min(listTop, Math.max(0, all.size() - LIST_ROWS)));
        for (int i = 0; i < Math.min(LIST_ROWS, all.size() - listTop); i++) {
            DotAnim a = all.get(listTop + i);
            boolean sel = a.id.equals(anim.id);
            addRenderableWidget(Button.builder(Component.literal((a.isFont() ? "[" + I18n.get("elevmod.anim.font_tag") + "] " : "") + a.name).withStyle(sel ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> { anim = copy(a); frame = 0; dirty = false; rebuildWidgets(); })
                    .bounds(l, t + i * 20, 96, 19).build());
        }
        int ly = t + LIST_ROWS * 20 + 2;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> { listTop--; rebuildWidgets(); }).bounds(l, ly, 47, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> { listTop++; rebuildWidgets(); }).bounds(l + 49, ly, 47, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.new"), b -> { anim = fresh(); frame = 0; dirty = true; rebuildWidgets(); }).bounds(l, ly + 18, 47, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.dup"), b -> {
            DotAnim c = copy(anim); c.id = newId(); c.name = anim.name + " 2"; anim = c; dirty = true; rebuildWidgets();
        }).bounds(l + 49, ly + 18, 47, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.new_font_btn"), b -> { anim = freshFont(); frame = 0; dirty = true; rebuildWidgets(); }).bounds(l, ly + 38, 96, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.delete").withStyle(ChatFormatting.RED), b -> {
            if (AnimLibrary.get(anim.id) != null) Net.CH.sendToServer(new AnimNet.Delete(anim.id));
            anim = fresh(); frame = 0; rebuildWidgets();
        }).bounds(l, ly + 58, 96, 18).build());

        // ---- 오른쪽: 이름·크기·옵션
        int rx = gridX() + 190;
        nameBox = new EditBox(font, rx, t, 130, 18, Component.literal("name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(anim.name);
        nameBox.setResponder(s -> { anim.name = s; dirty = true; });
        addRenderableWidget(nameBox);
        sizeBox = new EditBox(font, rx, t + 22, 70, 18, Component.literal("size"));
        sizeBox.setMaxLength(7);
        sizeBox.setValue(anim.cols + "x" + anim.rows);
        addRenderableWidget(sizeBox);
        addRenderableWidget(Button.builder(Component.translatable("elevmod.set.apply"), b -> applySize()).bounds(rx + 74, t + 22, 56, 18).build());
        if (!anim.isFont()) addRenderableWidget(Button.builder(Component.translatable(anim.loop ? "elevmod.anim.loop" : "elevmod.anim.once"), b -> { anim.loop = !anim.loop; dirty = true; rebuildWidgets(); })
                .bounds(rx, t + 44, 64, 18).build());
        if (!anim.isFont()) addRenderableWidget(Button.builder(Component.translatable(anim.full ? "elevmod.anim.full" : "elevmod.anim.arrow"), b -> { anim.full = !anim.full; dirty = true; rebuildWidgets(); })
                .bounds(rx + 66, t + 44, 64, 18).build());

        // ---- 장면
        int fy = t + 104;
        if (anim.isFont()) {
            // 글자 고르기: 0~9, A~Z, -
            for (int i = 0; i < DotAnim.CHARS.length(); i++) {
                final int ci = i;
                boolean sel = i == frame;
                addRenderableWidget(Button.builder(Component.literal(String.valueOf(DotAnim.CHARS.charAt(i))).withStyle(sel ? ChatFormatting.YELLOW : ChatFormatting.WHITE),
                        b -> { frame = ci; rebuildWidgets(); }).bounds(rx + (i % 10) * 13, fy + (i / 10) * 15, 13, 14).build());
            }
        } else {
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> { frame = Math.max(-1, frame - 1); rebuildWidgets(); }).bounds(rx, fy, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> { frame = Math.min(anim.frames.size() - 1, frame + 1); rebuildWidgets(); }).bounds(rx + 110, fy, 20, 18).build());
        secBox = new EditBox(font, rx, fy + 22, 60, 18, Component.literal("sec"));
        secBox.setMaxLength(6);
        secBox.setValue(String.valueOf(cur().sec));
        secBox.visible = frame >= 0;
        addRenderableWidget(secBox);
        addRenderableWidget(Button.builder(Component.translatable("elevmod.set.apply"), b -> applySec()).bounds(rx + 66, fy + 22, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.f_add"), b -> {
            if (anim.frames.size() >= DotAnim.MAX_FRAMES) return;
            DotAnim.Frame f = new DotAnim.Frame(anim.cols * anim.rows); f.sec = cur().sec;
            anim.frames.add(frame + 1, f); frame++; dirty = true; rebuildWidgets();
        }).bounds(rx, fy + 44, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.f_dup"), b -> {
            if (anim.frames.size() >= DotAnim.MAX_FRAMES) return;
            anim.frames.add(frame + 1, cur().copy()); frame++; dirty = true; rebuildWidgets();
        }).bounds(rx + 66, fy + 44, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.f_del"), b -> {
            if (anim.frames.size() <= 1) { java.util.Arrays.fill(cur().dots, (byte) 0); }
            else { anim.frames.remove(frame); frame = Math.max(0, frame - 1); }
            dirty = true; rebuildWidgets();
        }).bounds(rx, fy + 66, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.f_move"), b -> {
            // 이 장면을 앞으로 (Shift: 뒤로)
            int to = Screen.hasShiftDown() ? frame + 1 : frame - 1;
            if (to < 0 || to >= anim.frames.size()) return;
            DotAnim.Frame f = anim.frames.remove(frame); anim.frames.add(to, f); frame = to; dirty = true; rebuildWidgets();
        }).bounds(rx + 66, fy + 66, 64, 18).build());
        // 정지 장면: 정차 중인데 갈 방향이 있을 때(호출·목적층 있음) 보이는 한 장면
        addRenderableWidget(Button.builder(Component.translatable(frame == -1 ? "elevmod.anim.stop_editing" : "elevmod.anim.stop_frame").withStyle(frame == -1 ? ChatFormatting.YELLOW : ChatFormatting.AQUA),
                b -> { frame = frame == -1 ? 0 : -1; rebuildWidgets(); }).bounds(rx, fy + 88, 130, 18).build());
        }

        // ---- 도구 (도트 칸 아래)
        int ty = gridY() + 156;
        int gx = gridX();
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.clear"), b -> { java.util.Arrays.fill(cur().dots, (byte) 0); dirty = true; }).bounds(gx, ty, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.fill"), b -> { java.util.Arrays.fill(cur().dots, (byte) color); dirty = true; }).bounds(gx + 46, ty, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.invert"), b -> {
            byte[] d = cur().dots; for (int i = 0; i < d.length; i++) d[i] = (byte) (d[i] == 0 ? color : 0); dirty = true;
        }).bounds(gx + 92, ty, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.flip"), b -> {
            DotAnim.Frame f = cur(); byte[] n = new byte[f.dots.length];
            for (int y = 0; y < anim.rows; y++) for (int x = 0; x < anim.cols; x++) n[(anim.rows - 1 - y) * anim.cols + x] = f.dots[y * anim.cols + x];
            f.dots = n; dirty = true;
        }).bounds(gx + 138, ty, 44, 18).build());
        String[] arrows = {"←", "→", "↑", "↓"};
        int[][] dv = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            final int[] d = dv[i];
            addRenderableWidget(Button.builder(Component.literal(arrows[i]), b -> shift(d[0], d[1])).bounds(gx + i * 23, ty + 20, 21, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(playing ? "elevmod.anim.stop" : "elevmod.anim.play"), b -> { playing = !playing; rebuildWidgets(); })
                .bounds(gx + 92, ty + 20, 90, 18).build());

        // ---- 저장 / 닫기
        addRenderableWidget(Button.builder(Component.translatable("elevmod.anim.save").withStyle(ChatFormatting.GREEN), b -> save())
                .bounds(width / 2 - 124, height - 26, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 + 44, height - 26, 80, 20).build());
    }

    private void applySize() {
        String[] s = sizeBox.getValue().toLowerCase().replace("×", "x").replace("*", "x").split("x");
        if (s.length != 2) return;
        try {
            int c = Math.max(1, Math.min(32, Integer.parseInt(s[0].trim()))), r = Math.max(1, Math.min(32, Integer.parseInt(s[1].trim())));
            anim.resize(c, r);
            dirty = true;
            rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void applySec() {
        if (secBox == null || frame < 0) return;
        try {
            cur().sec = Math.max(0.02f, Math.min(60f, Float.parseFloat(secBox.getValue().trim().replace(",", "."))));
            dirty = true;
        } catch (NumberFormatException ignored) {}
    }

    private void shift(int dx, int dy) {
        DotAnim.Frame f = cur();
        byte[] n = new byte[f.dots.length];
        for (int y = 0; y < anim.rows; y++) for (int x = 0; x < anim.cols; x++) {
            int sx = Math.floorMod(x - dx, anim.cols), sy = Math.floorMod(y - dy, anim.rows);
            n[y * anim.cols + x] = f.dots[sy * anim.cols + sx];
        }
        f.dots = n;
        dirty = true;
    }

    private void save() {
        if (secBox != null && secBox.isFocused()) applySec();
        if (anim.name.isBlank()) anim.name = anim.id;
        Net.CH.sendToServer(new AnimNet.Save(anim.toJson().toString()));
        dirty = false;
    }

    // ------------------------------------------------------------ 도트 칠하기
    private int[] cellAt(double mx, double my) {
        int c = cell();
        int x = (int) Math.floor((mx - gridX()) / c), y = (int) Math.floor((my - gridY()) / c);
        if (x < 0 || y < 0 || x >= anim.cols || y >= anim.rows) return null;
        return new int[]{x, y};
    }

    private void paint(double mx, double my) {
        int[] p = cellAt(mx, my);
        if (p == null) return;
        cur().dots[p[1] * anim.cols + p[0]] = (byte) paintValue;
        dirty = true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 팔레트
        int px = gridX() + 190, py = top() + 66;
        for (int i = 0; i < 8; i++) {
            int x = px + (i % 4) * 27, y = py + (i / 4) * 17;
            if (mx >= x && mx < x + 25 && my >= y && my < y + 15) {
                final int ci = i;
                if (btn == 1 || Screen.hasShiftDown()) {
                    // 팔레트 색 바꾸기
                    minecraft.setScreen(new ColorPickerScreen(this, I18n.get("elevmod.anim.palette", i + 1), anim.palette[i], s -> {
                        if (!s.isEmpty()) { anim.palette[ci] = com.mchoe.elevmod.elevator.IndicatorProfile.color(s); dirty = true; }
                    }));
                } else color = i + 1;
                return true;
            }
        }
        if (cellAt(mx, my) != null && !playing) {
            painting = true;
            int[] p = cellAt(mx, my);
            int curV = cur().dots[p[1] * anim.cols + p[0]];
            // 왼쪽: 선택 색으로 켜기(이미 같은 색이면 끄기), 오른쪽: 끄기
            paintValue = btn == 1 ? 0 : curV == color ? 0 : color;
            paint(mx, my);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (painting) { paint(mx, my); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        painting = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            if (secBox != null && secBox.isFocused()) { applySec(); return true; }
            if (sizeBox.isFocused()) { applySize(); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        // 다른 사람이 저장/삭제해 목록이 바뀌면 다시 그림 (편집 중인 내용은 유지)
        if (seenVersion != AnimLibrary.version) {
            String keepName = nameBox.getValue();
            rebuildWidgets();
            nameBox.setValue(keepName);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        int gx = gridX(), gy = gridY(), c = cell();
        DotAnim.Frame f = playing ? anim.frameAt(time()) : cur();
        if (f == null) f = cur();
        // 도트 칸
        g.fill(gx - 3, gy - 3, gx + anim.cols * c + 3, gy + anim.rows * c + 3, 0xFF3A3A3A);
        g.fill(gx - 2, gy - 2, gx + anim.cols * c + 2, gy + anim.rows * c + 2, 0xFF050505);
        for (int y = 0; y < anim.rows; y++) for (int x = 0; x < anim.cols; x++) {
            int v = f.dots[y * anim.cols + x];
            int col = v == 0 ? 0xFF262018 : anim.palette[v - 1];
            int pad = Math.max(1, c / 8);
            g.fill(gx + x * c + pad, gy + y * c + pad, gx + (x + 1) * c - pad, gy + (y + 1) * c - pad, col);
        }
        int cx = cellAt(mx, my) == null ? -1 : cellAt(mx, my)[0], cy = cellAt(mx, my) == null ? -1 : cellAt(mx, my)[1];
        if (cx >= 0 && !playing) g.renderOutline(gx + cx * c, gy + cy * c, c, c, 0xFFFFFF60);

        // 오른쪽 글자·팔레트
        int rx = gx + 190, t = top();
        g.drawString(font, I18n.get("elevmod.anim.size_label"), rx + 134, t + 27, 0xFF888888, false);
        int py = t + 66;
        for (int i = 0; i < 8; i++) {
            int x = rx + (i % 4) * 27, y = py + (i / 4) * 17;
            g.fill(x - 1, y - 1, x + 26, y + 16, color == i + 1 ? 0xFFFFFF00 : 0xFF404040);
            g.fill(x, y, x + 25, y + 15, anim.palette[i]);
        }
        g.drawString(font, I18n.get("elevmod.anim.palette_hint"), rx + 110, py + 4, 0xFF888888, false);
        if (anim.isFont()) {
            g.drawString(font, I18n.get("elevmod.anim.font_char", String.valueOf(DotAnim.CHARS.charAt(Math.max(0, Math.min(frame, DotAnim.CHARS.length() - 1))))), rx, t + 170, 0xFFFFD040, false);
            g.drawString(font, I18n.get("elevmod.anim.font_hint"), rx, t + 182, 0xFF888888, false);
        } else {
            g.drawCenteredString(font, frame == -1 ? I18n.get("elevmod.anim.stop_frame") : I18n.get("elevmod.anim.frame", frame + 1, anim.frames.size()), rx + 65, t + 109, 0xFFFFD040);
            if (frame >= 0) g.drawString(font, I18n.get("elevmod.anim.sec_label"), rx + 134, t + 131, 0xFFDDDDDD, false);
            g.drawString(font, I18n.get("elevmod.anim.total", String.format("%.2f", anim.totalSec())), rx, t + 212, 0xFFAAAAAA, false);
            g.drawString(font, I18n.get("elevmod.anim.down_hint"), gx, gy + 212, 0xFF888888, false);
        }
        if (dirty) g.drawString(font, I18n.get("elevmod.anim.unsaved"), rx, t + 222, 0xFFFF8060, false);
        g.drawString(font, I18n.get("elevmod.anim.paint_hint"), gx, gy + 200, 0xFF888888, false);

        // 작은 미리보기 (항상 재생)
        DotAnim.Frame pf = anim.isFont() ? cur() : anim.frameAt(time());
        if (pf != null) {
            int ps = Math.max(2, Math.min(50 / anim.cols, 60 / anim.rows)), ox = rx + 136, oy = t + 150;
            g.fill(ox - 2, oy - 2, ox + anim.cols * ps + 2, oy + anim.rows * ps + 2, 0xFF050505);
            for (int y = 0; y < anim.rows; y++) for (int x = 0; x < anim.cols; x++) {
                int v = pf.dots[y * anim.cols + x];
                if (v > 0) g.fill(ox + x * ps, oy + y * ps, ox + (x + 1) * ps - (ps > 2 ? 1 : 0), oy + (y + 1) * ps - (ps > 2 ? 1 : 0), anim.palette[v - 1]);
            }
        }
        super.render(g, mx, my, pt);
    }

    private float time() {
        return minecraft.level == null ? (System.currentTimeMillis() % 10_000_000) / 1000f : (minecraft.level.getGameTime() + minecraft.getFrameTime()) / 20f;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

package com.mchoe.elevmod.client.screen;

import com.mchoe.elevmod.client.design.RemoteDesigns;
import com.mchoe.elevmod.client.studio.Analyzer;
import com.mchoe.elevmod.client.studio.DesignBuilder;
import com.mchoe.elevmod.client.studio.FilePicker;
import com.mchoe.elevmod.client.studio.ImageLoader;
import com.mchoe.elevmod.client.studio.Photo;
import com.mchoe.elevmod.client.studio.Target;
import com.mchoe.elevmod.network.DesignNet;
import com.mchoe.elevmod.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 디자인 스튜디오 – 컴퓨터의 사진(여러 장)으로 커스텀 디자인을 만든다.
 * 1) 사진 추가(파일 선택 창 또는 창에 끌어다 놓기) 2) 사진마다 부위 지정 3) 네 모서리를 맞춰 원근 보정
 * 4) 이름을 정하고 만들기 → 분석해서 입체 요소·버튼·표시창을 찾고 월드에 저장(다른 사람에게도 보임).
 */
public class DesignStudioScreen extends Screen {
    @Nullable private final BlockPos ctrl;
    @Nullable private final Screen parent;
    private final List<Photo> photos = new ArrayList<>();
    private int sel = -1, listTop, drag = -1;
    private EditBox nameBox;
    private String status = "";
    private int statusColor = 0xFFFFFFFF;
    private boolean busy, showAnalysis = true;
    private int existing = 0;
    // 미리보기 영역
    private int px, py, pw, ph;
    private float scale, ox, oy;

    public DesignStudioScreen(@Nullable BlockPos ctrl, @Nullable Screen parent) {
        super(Component.translatable("elevmod.studio.title"));
        this.ctrl = ctrl;
        this.parent = parent;
    }

    private Photo cur() { return sel >= 0 && sel < photos.size() ? photos.get(sel) : null; }

    private void msg(String key, int color, Object... args) { status = I18n.get(key, args); statusColor = color; }

    @Override
    protected void init() {
        String keepName = nameBox != null ? nameBox.getValue() : "";
        int W = width, H = height;
        nameBox = new EditBox(font, 40, 20, 110, 18, Component.literal("name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(keepName.isEmpty() ? I18n.get("elevmod.studio.default_name") : keepName);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.add"), b -> pick()).bounds(155, 19, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.build").withStyle(ChatFormatting.GREEN), b -> build()).bounds(228, 19, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(W - 50, 19, 44, 20).build());

        // 왼쪽: 사진 목록
        listTop = 46;
        int rows = Math.max(1, (H - 46 - 60) / 18);
        int first = Math.max(0, Math.min(sel - rows / 2, photos.size() - rows));
        for (int i = first; i < Math.min(photos.size(), first + rows); i++) {
            final int idx = i;
            Photo p = photos.get(i);
            String label = (i + 1) + ". " + I18n.get(p.target.langKey());
            addRenderableWidget(Button.builder(Component.literal(label).withStyle(i == sel ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> { sel = idx; rebuildWidgets(); })
                    .bounds(6, listTop + (i - first) * 18, 112, 17).build());
        }

        // 오른쪽: 선택한 사진 설정
        int rx = W - 134, ry = 46;
        Photo p = cur();
        if (p != null) {
            addRenderableWidget(Button.builder(Component.translatable(p.target.langKey()), b -> {
                Target[] v = Target.values();
                p.target = v[Math.floorMod(p.target.ordinal() + (hasShiftDown() ? -1 : 1), v.length)];
                p.height = p.target.defHeight;
                p.invalidate();
                rebuildWidgets();
            }).bounds(rx, ry, 128, 18).build());
            ry += 20;
            if (p.target.hasDisplay()) {
                addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.disp." + p.dispType), b -> {
                    p.dispType = p.dispType.equals("seg") ? "dot" : p.dispType.equals("dot") ? "lcd" : "seg";
                    p.invalidate();
                    rebuildWidgets();
                }).bounds(rx, ry, 128, 18).build());
                ry += 20;
            }
            if (!p.target.panel && p.target != Target.CAR_FLOOR) {
                addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.strength", I18n.get("elevmod.studio.str" + p.strength)), b -> {
                    p.strength = (p.strength + 1) % 4;
                    p.invalidate();
                    rebuildWidgets();
                }).bounds(rx, ry, 128, 18).build());
                ry += 20;
            }
            if (p.target.panel && p.target != Target.COP) {
                addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.height", String.format("%.2f", p.height)), b -> {
                    p.height = Math.max(0.04f, Math.min(1.5f, p.height + (hasShiftDown() ? -0.02f : 0.02f)));
                    rebuildWidgets();
                }).bounds(rx, ry, 128, 18).build());
                ry += 20;
            }
            if (p.target == Target.COP || p.target == Target.COP_ACC) {
                addRenderableWidget(Button.builder(Component.translatable(p.labels ? "elevmod.studio.labels_on" : "elevmod.studio.labels_off"), b -> {
                    p.labels = !p.labels;
                    rebuildWidgets();
                }).bounds(rx, ry, 128, 18).build());
                ry += 20;
            }
            addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.rotate"), b -> { p.rotate(); rebuildWidgets(); }).bounds(rx, ry, 128, 18).build());
            ry += 20;
            addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.reset"), b -> { p.resetCorners(); }).bounds(rx, ry, 128, 18).build());
            ry += 20;
            addRenderableWidget(Button.builder(Component.translatable(showAnalysis ? "elevmod.studio.analysis_on" : "elevmod.studio.analysis_off"), b -> {
                showAnalysis = !showAnalysis;
                rebuildWidgets();
            }).bounds(rx, ry, 128, 18).build());
            ry += 20;
            addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.remove").withStyle(ChatFormatting.RED), b -> {
                p.release();
                photos.remove(p);
                sel = Math.min(sel, photos.size() - 1);
                rebuildWidgets();
            }).bounds(rx, ry, 128, 18).build());
        }

        // 아래: 이미 만든 디자인 관리
        List<String> ids = new ArrayList<>(RemoteDesigns.INDEX.keySet());
        int by = H - 30;
        if (!ids.isEmpty()) {
            existing = Math.floorMod(existing, ids.size());
            String id = ids.get(existing);
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> { existing--; rebuildWidgets(); }).bounds(6, by, 16, 18).build());
            addRenderableWidget(Button.builder(Component.literal(RemoteDesigns.name(id)), b -> {}).bounds(24, by, 120, 18).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { existing++; rebuildWidgets(); }).bounds(146, by, 16, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.rename"), b -> {
                Net.CH.sendToServer(new DesignNet.Edit(id, "rename", nameBox.getValue()));
                msg("elevmod.studio.renamed", 0xFF80FF80, nameBox.getValue());
            }).bounds(166, by, 70, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.delete").withStyle(ChatFormatting.RED), b -> {
                Net.CH.sendToServer(new DesignNet.Edit(id, "delete", ""));
                msg("elevmod.studio.deleted", 0xFFFF8080);
            }).bounds(240, by, 50, 18).build());
            if (ctrl != null) addRenderableWidget(Button.builder(Component.translatable("elevmod.studio.apply"), b -> {
                Net.CH.sendToServer(new Net.EditMsg(ctrl, "setdesign", 0, id));
                msg("elevmod.studio.applied", 0xFF80FF80, RemoteDesigns.name(id));
            }).bounds(294, by, 70, 18).build());
        }

        px = 124; py = 46; pw = Math.max(40, W - 124 - 140); ph = Math.max(40, H - 46 - 40);
    }

    // ------------------------------------------------------------------ 사진 추가
    private void pick() {
        if (!FilePicker.available()) { msg("elevmod.studio.no_dialog", 0xFFFFD040); return; }
        msg("elevmod.studio.opening", 0xFFAAAAAA);
        FilePicker.open(I18n.get("elevmod.studio.add"), paths -> minecraft.execute(() -> {
            if (paths == null) msg("elevmod.studio.no_dialog", 0xFFFFD040);
            else addFiles(paths);
        }));
    }

    @Override
    public void onFilesDrop(List<Path> files) { addFiles(files); }

    /** 촬영/외부용: 사진 선택 + 모서리 지정 (원본 960px 기준 좌표) */
    public void select(int i, @Nullable float[] corners960) {
        if (i < 0 || i >= photos.size()) return;
        sel = i;
        Photo p = photos.get(i);
        if (corners960 != null) {
            float k = p.img.getWidth() / 960f;
            for (int j = 0; j < 8; j++) p.corners[j] = corners960[j] * k;
            p.invalidate();
        }
        rebuildWidgets();
    }

    public void addFiles(List<Path> files) {
        int ok = 0;
        List<String> bad = new ArrayList<>();
        for (Path f : files) {
            try {
                photos.add(new Photo(f.getFileName().toString(), ImageLoader.load(f)));
                ok++;
            } catch (UnsupportedOperationException e) {
                bad.add(f.getFileName() + (e.getMessage().equals("heic") ? " (HEIC/AVIF)" : ""));
            } catch (Exception e) {
                bad.add(f.getFileName().toString());
            }
        }
        if (ok > 0 && sel < 0) sel = 0;
        if (bad.isEmpty()) msg("elevmod.studio.added", 0xFF80FF80, ok);
        else msg("elevmod.studio.added_bad", 0xFFFFD040, ok, String.join(", ", bad), ImageLoader.formats());
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ 만들기
    private void build() {
        if (busy) return;
        if (photos.isEmpty()) { msg("elevmod.studio.need_photo", 0xFFFFD040); return; }
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) { msg("elevmod.studio.need_name", 0xFFFFD040); return; }
        busy = true;
        msg("elevmod.studio.building", 0xFFAAAAAA);
        List<Photo> list = new ArrayList<>(photos);
        Thread t = new Thread(() -> {
            try {
                byte[] zip = DesignBuilder.build(name, list);
                String id = newId();
                minecraft.execute(() -> upload(id, name, zip));
            } catch (Exception e) {
                minecraft.execute(() -> { busy = false; msg("elevmod.studio.failed", 0xFFFF6060, e.toString()); });
            }
        }, "elevmod-design-build");
        t.setDaemon(true);
        t.start();
    }

    private static String newId() {
        String a = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder("u_");
        for (int i = 0; i < 10; i++) sb.append(a.charAt(r.nextInt(a.length())));
        return sb.toString();
    }

    private void upload(String id, String name, byte[] zip) {
        int total = Math.max(1, (zip.length + DesignNet.CHUNK - 1) / DesignNet.CHUNK);
        for (int i = 0; i < total; i++) {
            byte[] part = Arrays.copyOfRange(zip, i * DesignNet.CHUNK, Math.min(zip.length, (i + 1) * DesignNet.CHUNK));
            Net.CH.sendToServer(new DesignNet.Upload(id, name, i, total, part));
        }
        if (ctrl != null) Net.CH.sendToServer(new Net.EditMsg(ctrl, "setdesign", 0, id));
        RemoteDesigns.INDEX.put(id, new String[]{name, ""});
        busy = false;
        msg(ctrl != null ? "elevmod.studio.done_applied" : "elevmod.studio.done", 0xFF80FF80, name, zip.length / 1024);
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ 미리보기 / 모서리 맞추기
    private void layoutPreview(Photo p) {
        scale = Math.min(pw / (float) p.img.getWidth(), ph / (float) p.img.getHeight());
        ox = px + (pw - p.img.getWidth() * scale) / 2;
        oy = py + (ph - p.img.getHeight() * scale) / 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        Photo p = cur();
        if (p != null && btn == 0) {
            layoutPreview(p);
            for (int i = 0; i < 4; i++) {
                float sx = ox + p.corners[i * 2] * scale, sy = oy + p.corners[i * 2 + 1] * scale;
                if (Math.abs(mx - sx) < 7 && Math.abs(my - sy) < 7) { drag = i; return true; }
            }
            // 찾은 버튼을 눌러 '층 버튼 ↔ 기타 버튼' 전환
            if (showAnalysis && p.preview != null && p.target.hasButtons()) {
                for (int i = 0; i < p.preview.buttons.size(); i++) {
                    float[] b = p.preview.buttons.get(i);
                    float[] c = map(p, b[0], b[1]);
                    if (Math.hypot(mx - c[0], my - c[1]) < 6) {
                        if (!p.excluded.remove(i)) p.excluded.add(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        Photo p = cur();
        if (p != null && drag >= 0) {
            layoutPreview(p);
            p.corners[drag * 2] = (float) Math.max(0, Math.min(p.img.getWidth(), (mx - ox) / scale));
            p.corners[drag * 2 + 1] = (float) Math.max(0, Math.min(p.img.getHeight(), (my - oy) / scale));
            p.invalidate();
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        drag = -1;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        g.drawString(font, I18n.get("elevmod.studio.name"), 6, 25, 0xFFDDDDDD, false);
        g.fill(px, py, px + pw, py + ph, 0xFF101214);
        Photo p = cur();
        if (p == null) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("elevmod.studio.help"), pw - 12);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), px + 6, py + 6 + i * 10, 0xFFBBBBBB, false);
        } else {
            layoutPreview(p);
            int iw = Math.round(p.img.getWidth() * scale), ih = Math.round(p.img.getHeight() * scale);
            g.blit(p.texture(), Math.round(ox), Math.round(oy), iw, ih, 0, 0, p.img.getWidth(), p.img.getHeight(), p.img.getWidth(), p.img.getHeight());
            // 모서리 사각형
            int[] cs = {0xFFFF4040, 0xFF40FF40, 0xFF4080FF, 0xFFFFD040};
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                line(g, ox + p.corners[i * 2] * scale, oy + p.corners[i * 2 + 1] * scale, ox + p.corners[j * 2] * scale, oy + p.corners[j * 2 + 1] * scale, 0xFFFFFF00);
            }
            for (int i = 0; i < 4; i++) {
                int sx = Math.round(ox + p.corners[i * 2] * scale), sy = Math.round(oy + p.corners[i * 2 + 1] * scale);
                g.fill(sx - 4, sy - 4, sx + 4, sy + 4, cs[i]);
            }
            if (showAnalysis) drawAnalysis(g, p);
            g.drawString(font, p.file, px + 3, py + ph + 3, 0xFF999999, false);
        }
        g.drawString(font, status, 6, height - 10, statusColor, false);
        super.render(g, mx, my, pt);
    }

    /** 분석 결과를 펴진 사진 좌표 → 원본 사각형 위로 투영해 표시 */
    private void drawAnalysis(GuiGraphics g, Photo p) {
        if (drag >= 0) return; // 모서리를 움직이는 중에는 분석하지 않음
        Analyzer.Result r = p.analysis();
        for (float x : r.seamX) line(g, map(p, x, 0), map(p, x, 1), 0xFF00FFFF);
        for (float y : r.seamY) line(g, map(p, 0, y), map(p, 1, y), 0xFF00FFFF);
        for (float[] pn : r.panels) rect(g, p, pn, pn[4] > 0 ? 0xFF60FF60 : 0xFFFF60FF);
        for (float[] c : r.curves) rect(g, p, c, 0xFFFFA040);
        for (int i = 0; i < r.buttons.size(); i++) {
            float[] b = r.buttons.get(i);
            float[] c = map(p, b[0], b[1]);
            boolean ex = p.excluded.contains(i);
            g.fill(Math.round(c[0]) - 3, Math.round(c[1]) - 3, Math.round(c[0]) + 3, Math.round(c[1]) + 3, ex ? 0xFF808080 : 0xFFFF3030);
            if (!ex && p.target.hasButtons()) g.drawString(font, "F", Math.round(c[0]) + 4, Math.round(c[1]) - 4, 0xFFFF6060, true);
        }
        if (r.display != null) rect(g, p, r.display, 0xFFFF8000);
        for (float[] l : r.lamps) rect(g, p, l, 0xFF40FF80);
        String info = I18n.get("elevmod.studio.found", r.seamX.size() + r.seamY.size(), r.panels.size(), r.curves.size(), r.buttons.size(), r.display != null ? 1 : 0);
        g.drawString(font, info, px + 3, py + 3, 0xFFFFFF80, true);
    }

    /** 펴진 사진 좌표(u: 0..1 왼→오른, v: 0..1 아래→위) → 화면 좌표 */
    private float[] map(Photo p, float u, float v) {
        float[] c = p.corners;
        float vv = 1 - v;
        float tx = c[0] + (c[2] - c[0]) * u, ty = c[1] + (c[3] - c[1]) * u;
        float bx = c[6] + (c[4] - c[6]) * u, by = c[7] + (c[5] - c[7]) * u;
        float x = tx + (bx - tx) * vv, y = ty + (by - ty) * vv;
        return new float[]{ox + x * scale, oy + y * scale};
    }

    private void rect(GuiGraphics g, Photo p, float[] r, int col) {
        float[] a = map(p, r[0], r[1]), b = map(p, r[2], r[1]), c = map(p, r[2], r[3]), d = map(p, r[0], r[3]);
        line(g, a, b, col); line(g, b, c, col); line(g, c, d, col); line(g, d, a, col);
    }

    private void line(GuiGraphics g, float[] a, float[] b, int col) { line(g, a[0], a[1], b[0], b[1], col); }

    private static void line(GuiGraphics g, float x1, float y1, float x2, float y2, int col) {
        int n = Math.max(1, (int) Math.hypot(x2 - x1, y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x1 + (x2 - x1) * i / n), y = Math.round(y1 + (y2 - y1) * i / n);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    @Override
    public void onClose() {
        for (Photo p : photos) p.release();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

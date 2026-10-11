package com.mchoe.elevmod.client;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.photo.PhotoScenes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** CI 자동 촬영기: -Delevmod.photo=true 로 실행하면 월드를 만들고 촬영 후 종료한다. */
@Mod.EventBusSubscriber(modid = ElevMod.MODID, value = Dist.CLIENT)
public final class PhotoBooth {
    private static volatile net.minecraft.nbt.CompoundTag indData;
    private PhotoBooth() {}

    private static final boolean ON = Boolean.getBoolean("elevmod.photo");
    private static int phase, wait, idx;
    private static final java.util.List<java.nio.file.Path> SAMPLES = new java.util.ArrayList<>();
    static final float[] COP_CORNERS = {304, 5, 613, 5, 603, 699, 336, 704};

    /** CI 촬영용: 실제 엘리베이터 사진으로 커스텀 디자인을 만들어 서버에 올림 (디자인 스튜디오와 같은 경로) */
    private static void uploadSampleDesign(Minecraft mc) {
        try {
            java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("photo-samples");
            if (!java.nio.file.Files.isDirectory(dir)) return;
            java.util.List<com.mchoe.elevmod.client.studio.Photo> ph = new java.util.ArrayList<>();
            Object[][] spec = {
                    {"elevator_car_operating_panel_2.jpg", com.mchoe.elevmod.client.studio.Target.COP, COP_CORNERS},
                    {"center_opening_elevator_doors_3.jpg", com.mchoe.elevmod.client.studio.Target.LANDING_DOOR, new float[]{348, 448, 668, 448, 668, 1152, 348, 1152}},
                    {"center_opening_elevator_doors_3.jpg", com.mchoe.elevmod.client.studio.Target.CAR_DOOR, new float[]{348, 448, 668, 448, 668, 1152, 348, 1152}},
                    {"elevator_floor_indicator_5.jpg", com.mchoe.elevmod.client.studio.Target.INDICATOR, new float[]{204, 209, 780, 209, 780, 408, 204, 408}},
                    {"elevator_floor_indicator_3.jpg", com.mchoe.elevmod.client.studio.Target.HALL_PANEL, null}};
            for (Object[] sp : spec) {
                java.nio.file.Path f = dir.resolve((String) sp[0]);
                if (!java.nio.file.Files.exists(f)) continue;
                if (!SAMPLES.contains(f)) SAMPLES.add(f);
                var p = new com.mchoe.elevmod.client.studio.Photo((String) sp[0], com.mchoe.elevmod.client.studio.ImageLoader.load(f));
                p.target = (com.mchoe.elevmod.client.studio.Target) sp[1];
                p.height = p.target.defHeight;
                if (sp[2] != null) {
                    float[] c = (float[]) sp[2];
                    float k = p.img.getWidth() / 960f; // 원본 960px 기준 좌표
                    for (int i = 0; i < 8; i++) p.corners[i] = c[i] * k;
                }
                ph.add(p);
            }
            if (ph.isEmpty()) return;
            byte[] zip = com.mchoe.elevmod.client.studio.DesignBuilder.build("사진 테스트", ph);
            String id = "u_phototest01";
            int total = Math.max(1, (zip.length + com.mchoe.elevmod.network.DesignNet.CHUNK - 1) / com.mchoe.elevmod.network.DesignNet.CHUNK);
            for (int i = 0; i < total; i++) {
                byte[] part = java.util.Arrays.copyOfRange(zip, i * com.mchoe.elevmod.network.DesignNet.CHUNK, Math.min(zip.length, (i + 1) * com.mchoe.elevmod.network.DesignNet.CHUNK));
                com.mchoe.elevmod.network.Net.CH.sendToServer(new com.mchoe.elevmod.network.DesignNet.Upload(id, "사진 테스트", i, total, part));
            }
            PhotoScenes.photoDesign = id;
            ElevMod.LOG.info("[photo] sample design uploaded: {} bytes, {} photos", zip.length, ph.size());
        } catch (Throwable t) {
            ElevMod.LOG.error("[photo] sample design failed", t);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        switch (phase) {
            case 0 -> {
                if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("Onboarding")) { mc.setScreen(new TitleScreen()); return; }
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++wait > 60) {
                    ElevMod.LOG.info("[photo] creating world");
                    LevelSettings ls = new LevelSettings("elevphoto", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(ls.levelName(), ls, new WorldOptions(1234L, false, false),
                            ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                    phase = 1;
                    wait = 0;
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null && server != null && wait == 20) uploadSampleDesign(mc);
                if (mc.level != null && mc.player != null && server != null && ++wait > 80) {
                    if (mc.screen != null) mc.setScreen(null);
                    ElevMod.LOG.info("[photo] building scenes");
                    server.execute(() -> {
                        try { PhotoScenes.build(server); } catch (Throwable t) { ElevMod.LOG.error("[photo] build failed", t); PhotoScenes.ready = true; }
                    });
                    phase = 2;
                    wait = 0;
                }
            }
            case 2 -> {
                if (PhotoScenes.ready && ++wait > 120) { phase = 3; wait = 0; idx = 0; mc.options.hideGui = true; }
            }
            case 3 -> {
                if (mc.screen != null) mc.setScreen(null);
                if (idx >= PhotoScenes.SHOTS.size() || server == null) { phase = 4; wait = 0; return; }
                PhotoScenes.Shot s = PhotoScenes.SHOTS.get(idx);
                if (wait == 0) server.execute(() -> PhotoScenes.apply(server, s));
                wait++;
                if (wait == 50) Screenshot.grab(mc.gameDirectory, s.name() + ".png", mc.getMainRenderTarget(), c -> ElevMod.LOG.info("[photo] " + c.getString()));
                if (wait > 55) { idx++; wait = 0; }
            }
            case 4 -> {
                // 디자인 스튜디오 화면도 촬영 (사진을 넣고 운전반 사진을 선택한 상태)
                if (wait == 0 && !SAMPLES.isEmpty()) {
                    mc.options.hideGui = false;
                    var scr = new com.mchoe.elevmod.client.screen.DesignStudioScreen(null, null);
                    mc.setScreen(scr);
                    scr.addFiles(SAMPLES);
                    scr.select(0, COP_CORNERS);
                }
                wait++;
                if (wait == 60 && !SAMPLES.isEmpty()) Screenshot.grab(mc.gameDirectory, "90_studio.png", mc.getMainRenderTarget(), c -> ElevMod.LOG.info("[photo] " + c.getString()));
                // 표시기 설정 화면 / 도트 애니메이션 편집기
                if (wait == 70 && server != null && PhotoScenes.indCtrl != null) server.execute(() -> {
                    if (server.overworld().getBlockEntity(PhotoScenes.indCtrl) instanceof com.mchoe.elevmod.elevator.ControllerBlockEntity c) indData = c.screenTag();
                });
                String[] guiShots = {"91_ind_trans", "92_ind_shape", "93_ind_color", "94_ind_behavior", "95_anim_editor", "96_ind_anim", "98_ind_cells", "99_ind_alt"};
                int[] pages = {0, 1, 2, 4, -1, 3, 5, 6};
                for (int i = 0; i < guiShots.length; i++) {
                    int at = 90 + i * 40;
                    if (wait == at && indData != null) {
                        mc.options.hideGui = false;
                        var is = new com.mchoe.elevmod.client.screen.IndicatorScreen(PhotoScenes.indCtrl, null, indData);
                        if (pages[i] >= 0) { is.page(pages[i]); mc.setScreen(is); }
                        else mc.setScreen(new com.mchoe.elevmod.client.screen.DotAnimEditorScreen(is, "a_phototest01"));
                    }
                    if (wait == at + 30 && indData != null) Screenshot.grab(mc.gameDirectory, guiShots[i] + ".png", mc.getMainRenderTarget(), c -> ElevMod.LOG.info("[photo] " + c.getString()));
                }
                if (wait > 440) { ElevMod.LOG.info("[photo] done"); mc.stop(); }
            }
            default -> {}
        }
    }
}

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
    private PhotoBooth() {}

    private static final boolean ON = Boolean.getBoolean("elevmod.photo");
    private static int phase, wait, idx;

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
                if (++wait > 40) { ElevMod.LOG.info("[photo] done"); mc.stop(); }
            }
            default -> {}
        }
    }
}

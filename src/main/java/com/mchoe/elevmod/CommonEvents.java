package com.mchoe.elevmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CommonEvents {
    /** 카에서 내린 플레이어의 임시 비행 허용(서버측) 해제 */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        Player p = e.player;
        CompoundTag pd = p.getPersistentData();
        if (!pd.getBoolean("elevmod_mf")) return;
        long last = pd.getLong("elevmod_ride");
        long now = p.level().getGameTime();
        if (now - last > 10 || now < last) {
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
            }
            pd.remove("elevmod_mf");
        }
    }

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent e) {
        com.mchoe.elevmod.elevator.DesignStore.load(e.getServer());
        boolean webp;
        try { Class.forName("com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi"); webp = true; } catch (Throwable t) { webp = false; }
        ElevMod.LOG.info("[elevmod] extra image format libraries present: {}", webp);
    }

    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
        com.mchoe.elevmod.elevator.DesignStore.clear();
    }

    /** 접속하면 월드의 커스텀 디자인 목록을 보냄 (실제 데이터는 필요할 때 요청) */
    @SubscribeEvent
    public void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) com.mchoe.elevmod.network.DesignNet.sendIndex(sp);
    }
}

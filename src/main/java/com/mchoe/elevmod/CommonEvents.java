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
}

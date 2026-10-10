package com.mchoe.elevmod;

import com.mchoe.elevmod.network.Net;
import com.mchoe.elevmod.registry.ModRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Elevmod – 나무위키 '엘리베이터' 문서를 기준으로 만든 마인크래프트 1.20.1 엘리베이터 모드.
 */
@Mod(ElevMod.MODID)
public class ElevMod {
    public static final String MODID = "elevmod";
    public static final Logger LOG = LogUtils.getLogger();

    public ElevMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(bus);
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::init);
    }
}

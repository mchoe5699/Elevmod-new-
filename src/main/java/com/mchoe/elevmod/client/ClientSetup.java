package com.mchoe.elevmod.client;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.client.render.CarRenderer;
import com.mchoe.elevmod.client.render.DeviceRenderer;
import com.mchoe.elevmod.client.render.LandingDoorRenderer;
import com.mchoe.elevmod.client.render.MachineRenderer;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ElevMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.CAR.get(), CarRenderer::new);
        e.registerBlockEntityRenderer(ModRegistry.LANDING_DOOR_BE.get(), LandingDoorRenderer::new);
        e.registerBlockEntityRenderer(ModRegistry.DEVICE_BE.get(), DeviceRenderer::new);
        e.registerBlockEntityRenderer(ModRegistry.MACHINE_BE.get(), MachineRenderer::new);
        e.registerBlockEntityRenderer(ModRegistry.CONTROLLER_BE.get(), com.mchoe.elevmod.client.render.ControllerRenderer::new);
    }
}

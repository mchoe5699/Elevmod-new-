package com.mchoe.elevmod.client;

import com.mchoe.elevmod.client.screen.ControllerScreen;
import com.mchoe.elevmod.client.screen.CopScreen;
import com.mchoe.elevmod.client.screen.DestinationScreen;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mchoe.elevmod.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ClientHooks {
    private ClientHooks() {}

    public static void screen(int kind, BlockPos pos, CompoundTag data, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == Net.ScreenMsg.CONTROLLER) {
            if (open) mc.setScreen(new ControllerScreen(pos, data));
            else if (mc.screen instanceof ControllerScreen cs && cs.pos.equals(pos)) cs.update(data);
        } else if (kind == Net.ScreenMsg.DESTINATION) {
            mc.setScreen(new DestinationScreen(pos, data));
        }
    }

    public static void openOffset(BlockPos pos) {
        Minecraft.getInstance().setScreen(new com.mchoe.elevmod.client.screen.DeviceOffsetScreen(pos));
    }

    public static void openCop(ElevatorCarEntity car, boolean accessible) {
        Minecraft.getInstance().setScreen(new CopScreen(car, accessible));
    }
}

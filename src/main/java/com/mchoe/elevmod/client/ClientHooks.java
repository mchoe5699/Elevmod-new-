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
            else if (mc.screen instanceof com.mchoe.elevmod.client.screen.IndicatorScreen is && is.isController() && is.pos.equals(pos)) is.update(data);
            else if (mc.screen instanceof com.mchoe.elevmod.client.screen.DotAnimEditorScreen ae && ae.parent() instanceof com.mchoe.elevmod.client.screen.IndicatorScreen is && is.isController() && is.pos.equals(pos)) is.update(data);
            else if (mc.screen instanceof com.mchoe.elevmod.client.screen.ColorPickerScreen cp && cp.parent() instanceof com.mchoe.elevmod.client.screen.IndicatorScreen is && is.isController() && is.pos.equals(pos)) is.update(data);
        } else if (kind == Net.ScreenMsg.DESTINATION) {
            mc.setScreen(new DestinationScreen(pos, data));
        }
    }

    public static void openStudio() {
        Minecraft.getInstance().setScreen(new com.mchoe.elevmod.client.screen.DesignStudioScreen(null, null));
    }

    /** 승강장 표시기·홀랜턴 개별 디자인 화면 */
    public static boolean openDeviceIndicator(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockState(pos).getBlock() instanceof com.mchoe.elevmod.block.DeviceBlock db)) return false;
        String kind = switch (db.kind) {
            case IND_DOT -> "dot";
            case IND_LCD -> "lcd";
            case IND_SEGMENT, HALL_PANEL -> "seg";
            case LANTERN -> "lantern";
            default -> null;
        };
        if (kind == null) return false;
        mc.setScreen(com.mchoe.elevmod.client.screen.IndicatorScreen.forDevice(pos, kind.equals("lantern"), kind.equals("lantern") ? "seg" : kind));
        return true;
    }

    public static void openOffset(BlockPos pos) {
        Minecraft.getInstance().setScreen(new com.mchoe.elevmod.client.screen.DeviceOffsetScreen(pos));
    }

    public static void openCop(ElevatorCarEntity car, boolean accessible) {
        Minecraft.getInstance().setScreen(new CopScreen(car, accessible));
    }
}

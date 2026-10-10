package com.mchoe.elevmod.network;

import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Ctrl + / - : 엘리베이터 부품 회전 (도 단위, reset = 0°로) */
public record RotateMsg(BlockPos pos, float delta, boolean reset) {
    public void encode(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeFloat(delta); b.writeBoolean(reset); }
    public static RotateMsg decode(FriendlyByteBuf b) { return new RotateMsg(b.readBlockPos(), b.readFloat(), b.readBoolean()); }

    public static float norm(float a) {
        a = a % 360f;
        if (a > 180) a -= 360;
        if (a <= -180) a += 360;
        return Math.round(a * 10f) / 10f;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || !p.level().isLoaded(pos) || p.position().distanceTo(Vec3.atCenterOf(pos)) > 12) return;
        BlockEntity be = p.level().getBlockEntity(pos);
        if (be instanceof LandingDoorBlockEntity d && !d.master && p.level().getBlockEntity(d.masterPos()) instanceof LandingDoorBlockEntity m) be = m;
        float ny;
        if (be instanceof DeviceBlockEntity d) { d.setYaw(reset ? 0 : norm(d.yaw + delta)); ny = d.yaw; }
        else if (be instanceof LandingDoorBlockEntity d) { d.setYaw(reset ? 0 : norm(d.yaw + delta)); ny = d.yaw; }
        else if (be instanceof MachineBlockEntity d) { d.setYaw(reset ? 0 : norm(d.yaw + delta)); ny = d.yaw; }
        else if (be instanceof ControllerBlockEntity d) { d.setYaw(reset ? 0 : norm(d.yaw + delta)); ny = d.yaw; }
        else return;
        p.displayClientMessage(Component.translatable("elevmod.msg.rotated", String.format("%.0f", ny)).withStyle(ChatFormatting.AQUA), true);
    }

    static void register(int[] id) {
        Net.CH.messageBuilder(RotateMsg.class, id[0]++, NetworkDirection.PLAY_TO_SERVER).encoder(RotateMsg::encode).decoder(RotateMsg::decode).consumerMainThread(RotateMsg::handle).add();
    }
}

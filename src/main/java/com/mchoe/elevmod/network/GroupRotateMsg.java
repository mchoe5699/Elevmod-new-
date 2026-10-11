package com.mchoe.elevmod.network;

import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.item.RotatorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** 회전기: 두 지점 사이 영역의 엘리베이터 부품을 하나로 묶어 영역 가운데를 축으로 돌림 (release = 원래대로) */
public record GroupRotateMsg(float angle, boolean release) {
    public static final int MAX_XZ = 128;

    public void encode(FriendlyByteBuf b) { b.writeFloat(angle); b.writeBoolean(release); }

    public static GroupRotateMsg decode(FriendlyByteBuf b) { return new GroupRotateMsg(b.readFloat(), b.readBoolean()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        var st = p.getMainHandItem().getItem() instanceof RotatorItem ? p.getMainHandItem() : p.getOffhandItem();
        if (!(st.getItem() instanceof RotatorItem)) return;
        BlockPos a = RotatorItem.point(st, 1), b = RotatorItem.point(st, 2);
        if (a == null || b == null) { p.displayClientMessage(Component.translatable("elevmod.rotator.need_points").withStyle(ChatFormatting.RED), true); return; }
        int x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
        int y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
        int z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());
        if (x2 - x1 >= MAX_XZ || z2 - z1 >= MAX_XZ) { p.displayClientMessage(Component.translatable("elevmod.rotator.too_big", MAX_XZ).withStyle(ChatFormatting.RED), true); return; }
        Vec3 center = new Vec3((x1 + x2 + 1) / 2.0, (y1 + y2 + 1) / 2.0, (z1 + z2 + 1) / 2.0);
        if (p.position().distanceTo(center) > 96) return;
        int n = apply(p.serverLevel(), x1, y1, z1, x2, y2, z2, angle, release);
        float ang = release ? 0 : (float) (((angle % 360) + 360) % 360);
        p.displayClientMessage(release ? Component.translatable("elevmod.rotator.released", n).withStyle(ChatFormatting.GRAY)
                : Component.translatable("elevmod.rotator.done", n, String.format("%.1f", ang)).withStyle(ChatFormatting.AQUA), true);
    }

    /** 영역 안 부품을 묶어 돌림. 돌린 부품 수 */
    public static int apply(ServerLevel lvl, int x1, int y1, int z1, int x2, int y2, int z2, float angle, boolean release) {
        float ang = release ? 0 : (float) (((angle % 360) + 360) % 360);
        double px = release ? Double.NaN : (x1 + x2 + 1) / 2.0, pz = release ? Double.NaN : (z1 + z2 + 1) / 2.0;
        List<BlockEntity> found = new ArrayList<>();
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
            if (!lvl.hasChunk(cx, cz)) continue;
            LevelChunk ch = lvl.getChunk(cx, cz);
            for (BlockEntity be : ch.getBlockEntities().values()) {
                BlockPos q = be.getBlockPos();
                if (q.getX() < x1 || q.getX() > x2 || q.getY() < y1 || q.getY() > y2 || q.getZ() < z1 || q.getZ() > z2) continue;
                found.add(be);
            }
        }
        int n = 0;
        Set<BlockPos> ctrls = new HashSet<>();
        for (BlockEntity be : found) {
            if (be instanceof DeviceBlockEntity d) { d.setGroupRot(ang, px, pz); n++; }
            else if (be instanceof MachineBlockEntity m) { m.setGroupRot(ang, px, pz); n++; }
            else if (be instanceof ControllerBlockEntity c) { c.setGroupRot(ang, px, pz); n++; }
            else if (be instanceof LandingDoorBlockEntity ld) {
                ld.setGroupRot(ang, px, pz);
                n++;
                // 출입구 전체가 같이 돌도록 대표 칸도
                if (!ld.master && lvl.getBlockEntity(ld.masterPos()) instanceof LandingDoorBlockEntity m) m.setGroupRot(ang, px, pz);
                if (ld.ctrl != null) ctrls.add(ld.ctrl);
            }
        }
        // 승강장 문이 묶인 엘리베이터는 카도 같은 축·각도로
        for (BlockPos c : ctrls) if (lvl.getBlockEntity(c) instanceof ControllerBlockEntity cb) cb.setCarRot(ang, release ? 0 : px, release ? 0 : pz);
        return n;
    }

    static void register(int[] id) {
        Net.CH.messageBuilder(GroupRotateMsg.class, id[0]++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GroupRotateMsg::encode).decoder(GroupRotateMsg::decode).consumerMainThread(GroupRotateMsg::handle).add();
    }
}

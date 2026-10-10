package com.mchoe.elevmod.network;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class Net {
    private Net() {}

    private static final String VER = "3";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(ElevMod.MODID, "main"), () -> VER, VER::equals, VER::equals);

    public static void init() {
        int id = 0;
        CH.messageBuilder(CarActionMsg.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CarActionMsg::encode).decoder(CarActionMsg::decode).consumerMainThread(CarActionMsg::handle).add();
        CH.messageBuilder(EditMsg.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditMsg::encode).decoder(EditMsg::decode).consumerMainThread(EditMsg::handle).add();
        CH.messageBuilder(DestCallMsg.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DestCallMsg::encode).decoder(DestCallMsg::decode).consumerMainThread(DestCallMsg::handle).add();
        CH.messageBuilder(OffsetMsg.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OffsetMsg::encode).decoder(OffsetMsg::decode).consumerMainThread(OffsetMsg::handle).add();
        CH.messageBuilder(ScreenMsg.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScreenMsg::encode).decoder(ScreenMsg::decode).consumerMainThread(ScreenMsg::handle).add();
        int[] next = {id};
        DesignNet.register(next);
    }

    public static void sendController(ServerPlayer p, ControllerBlockEntity c, boolean open) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new ScreenMsg(ScreenMsg.CONTROLLER, c.getBlockPos(), c.screenTag(), open));
    }

    public static void openDestination(ServerPlayer p, BlockPos panel, ControllerBlockEntity c) {
        CompoundTag t = new CompoundTag();
        ListTag names = new ListTag(), ys = new ListTag();
        for (var f : c.floors) { names.add(StringTag.valueOf(f.name)); ys.add(IntTag.valueOf(f.y)); }
        t.put("names", names);
        t.put("ys", ys);
        boolean dd = false;
        for (ControllerBlockEntity m : c.members(p.level())) dd |= m.destination;
        t.putBoolean("dd", dd);
        if (p.level().getBlockEntity(panel) instanceof DeviceBlockEntity d) t.putInt("here", d.floorY);
        CH.send(PacketDistributor.PLAYER.with(() -> p), new ScreenMsg(ScreenMsg.DESTINATION, panel, t, true));
    }

    // ------------------------------------------------------------------ 메시지
    public record CarActionMsg(BlockPos ctrl, int action, int arg) {
        public void encode(FriendlyByteBuf b) { b.writeBlockPos(ctrl); b.writeVarInt(action); b.writeVarInt(arg); }
        public static CarActionMsg decode(FriendlyByteBuf b) { return new CarActionMsg(b.readBlockPos(), b.readVarInt(), b.readVarInt()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !p.level().isLoaded(ctrl)) return;
            if (p.level().getBlockEntity(ctrl) instanceof ControllerBlockEntity c) {
                Vec3 cp = c.floors.isEmpty() ? Vec3.atCenterOf(ctrl) : c.carPos();
                if (p.position().distanceTo(cp) > 24) return;
                c.carAction(p, action, arg);
            }
        }
    }

    public record EditMsg(BlockPos ctrl, String key, int delta, String text) {
        public void encode(FriendlyByteBuf b) { b.writeBlockPos(ctrl); b.writeUtf(key, 64); b.writeVarInt(delta); b.writeUtf(text, 64); }
        public static EditMsg decode(FriendlyByteBuf b) { return new EditMsg(b.readBlockPos(), b.readUtf(64), b.readVarInt(), b.readUtf(64)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !p.level().isLoaded(ctrl)) return;
            if (p.position().distanceTo(Vec3.atCenterOf(ctrl)) > 10) return;
            if (p.level().getBlockEntity(ctrl) instanceof ControllerBlockEntity c) c.edit(p, key, delta, text);
        }
    }

    public record DestCallMsg(BlockPos panel, int toY) {
        public void encode(FriendlyByteBuf b) { b.writeBlockPos(panel); b.writeInt(toY); }
        public static DestCallMsg decode(FriendlyByteBuf b) { return new DestCallMsg(b.readBlockPos(), b.readInt()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !p.level().isLoaded(panel)) return;
            if (p.position().distanceTo(Vec3.atCenterOf(panel)) > 10) return;
            if (p.level().getBlockEntity(panel) instanceof DeviceBlockEntity d && d.ctrl != null
                    && p.level().getBlockEntity(d.ctrl) instanceof ControllerBlockEntity c) {
                c.destinationCall((ServerLevel) p.level(), d.floorY, toY, p);
            }
        }
    }

    /** 승강장 장치 위치 조정 (1/16 블록 단위) */
    public record OffsetMsg(BlockPos pos, int dx, int dy, boolean reset) {
        public void encode(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeVarInt(dx); b.writeVarInt(dy); b.writeBoolean(reset); }
        public static OffsetMsg decode(FriendlyByteBuf b) { return new OffsetMsg(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readBoolean()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !p.level().isLoaded(pos)) return;
            if (p.position().distanceTo(Vec3.atCenterOf(pos)) > 10) return;
            if (p.level().getBlockEntity(pos) instanceof DeviceBlockEntity d) {
                if (reset) d.setOffset(0, 0);
                else d.setOffset(d.offX + dx, d.offY + dy);
            }
        }
    }

    public record ScreenMsg(int kind, BlockPos pos, CompoundTag data, boolean open) {
        public static final int CONTROLLER = 0, DESTINATION = 1;
        public void encode(FriendlyByteBuf b) { b.writeVarInt(kind); b.writeBlockPos(pos); b.writeNbt(data); b.writeBoolean(open); }
        public static ScreenMsg decode(FriendlyByteBuf b) {
            int k = b.readVarInt();
            BlockPos p = b.readBlockPos();
            CompoundTag t = b.readNbt();
            return new ScreenMsg(k, p, t == null ? new CompoundTag() : t, b.readBoolean());
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mchoe.elevmod.client.ClientHooks.screen(kind, pos, data, open));
        }
    }
}

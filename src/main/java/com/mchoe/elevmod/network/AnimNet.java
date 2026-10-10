package com.mchoe.elevmod.network;

import com.google.gson.JsonParser;
import com.mchoe.elevmod.elevator.AnimStore;
import com.mchoe.elevmod.elevator.DotAnim;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 도트 애니메이션 라이브러리 동기화 */
public final class AnimNet {
    private AnimNet() {}

    /** 서버 → 클라이언트: 라이브러리 전체 (gzip JSON) */
    public record Sync(byte[] data) {
        public void encode(FriendlyByteBuf b) { b.writeByteArray(data); }
        public static Sync decode(FriendlyByteBuf b) { return new Sync(b.readByteArray(1_000_000)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            String json = unzip(data);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mchoe.elevmod.client.indicator.AnimLibrary.sync(json));
        }
    }

    /** 클라이언트 → 서버: 애니메이션 저장 */
    public record Save(String json) {
        // gzip 으로 줄여 보냄 (클라이언트→서버 패킷 크기 제한)
        public void encode(FriendlyByteBuf b) { b.writeByteArray(zip(json)); }
        public static Save decode(FriendlyByteBuf b) { return new Save(unzip(b.readByteArray(32000))); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            try {
                DotAnim a = DotAnim.fromJson(JsonParser.parseString(json).getAsJsonObject());
                if (a.name.length() > 32) a.name = a.name.substring(0, 32);
                if (AnimStore.put(a)) {
                    p.displayClientMessage(Component.translatable("elevmod.msg.anim_saved", a.name).withStyle(ChatFormatting.GREEN), true);
                    broadcast();
                }
            } catch (Exception e) {
                p.displayClientMessage(Component.translatable("elevmod.msg.design_bad").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    public record Delete(String id) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(id, 40); }
        public static Delete decode(FriendlyByteBuf b) { return new Delete(b.readUtf(40)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && AnimStore.delete(id)) broadcast();
        }
    }

    public static Sync sync() { return new Sync(zip(AnimStore.json())); }

    public static void broadcast() { Net.CH.send(PacketDistributor.ALL.noArg(), sync()); }

    public static void send(ServerPlayer p) { Net.CH.send(PacketDistributor.PLAYER.with(() -> p), sync()); }

    static byte[] zip(String s) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(o)) { g.write(s.getBytes(StandardCharsets.UTF_8)); }
            return o.toByteArray();
        } catch (Exception e) { return new byte[0]; }
    }

    static String unzip(byte[] b) {
        try (GZIPInputStream g = new GZIPInputStream(new ByteArrayInputStream(b))) { return new String(g.readAllBytes(), StandardCharsets.UTF_8); }
        catch (Exception e) { return "[]"; }
    }

    static void register(int[] id) {
        Net.CH.messageBuilder(Sync.class, id[0]++, NetworkDirection.PLAY_TO_CLIENT).encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        Net.CH.messageBuilder(Save.class, id[0]++, NetworkDirection.PLAY_TO_SERVER).encoder(Save::encode).decoder(Save::decode).consumerMainThread(Save::handle).add();
        Net.CH.messageBuilder(Delete.class, id[0]++, NetworkDirection.PLAY_TO_SERVER).encoder(Delete::encode).decoder(Delete::decode).consumerMainThread(Delete::handle).add();
    }
}

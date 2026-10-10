package com.mchoe.elevmod.network;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.elevator.DesignStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** 커스텀 디자인 업로드/다운로드 (조각 단위 전송) */
public final class DesignNet {
    private DesignNet() {}

    public static final int CHUNK = 28000;

    // ------------------------------------------------------------------ 서버 쪽 조립
    private static final Map<String, ByteArrayOutputStream> UPLOADS = new HashMap<>();

    /** 클라이언트 → 서버: 디자인 묶음(zip) 조각 */
    public record Upload(String id, String name, int idx, int total, byte[] data) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(id, 40); b.writeUtf(name, 64); b.writeVarInt(idx); b.writeVarInt(total); b.writeByteArray(data); }
        public static Upload decode(FriendlyByteBuf b) { return new Upload(b.readUtf(40), b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK + 16)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !DesignStore.validId(id) || total <= 0 || total * (long) CHUNK > DesignStore.MAX_ZIP) return;
            String key = p.getUUID() + "/" + id;
            ByteArrayOutputStream buf = UPLOADS.computeIfAbsent(key, k -> new ByteArrayOutputStream());
            if (idx == 0) buf.reset();
            buf.writeBytes(data);
            if (idx < total - 1) return;
            UPLOADS.remove(key);
            DesignStore.Entry old = DesignStore.get(id);
            if (old != null && !canEdit(p, old)) { p.displayClientMessage(Component.translatable("elevmod.msg.design_denied").withStyle(ChatFormatting.RED), false); return; }
            if (DesignStore.put(id, name, buf.toByteArray(), p.getUUID())) {
                p.displayClientMessage(Component.translatable("elevmod.msg.design_saved", name).withStyle(ChatFormatting.GREEN), false);
                broadcastIndex();
                // 이미 받아둔 클라이언트가 새 버전을 다시 받도록
                Net.CH.send(PacketDistributor.ALL.noArg(), new Invalidate(id));
            } else p.displayClientMessage(Component.translatable("elevmod.msg.design_bad").withStyle(ChatFormatting.RED), false);
        }
    }

    static boolean canEdit(ServerPlayer p, DesignStore.Entry e) {
        return e.owner.isEmpty() || e.owner.equals(p.getUUID().toString()) || p.hasPermissions(2) || p.server.isSingleplayerOwner(p.getGameProfile());
    }

    /** 클라이언트 → 서버: 디자인 내려받기 요청 */
    public record Request(String id) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(id, 40); }
        public static Request decode(FriendlyByteBuf b) { return new Request(b.readUtf(40)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            DesignStore.Entry e = DesignStore.get(id);
            if (e == null) return;
            int total = Math.max(1, (e.zip.length + CHUNK - 1) / CHUNK);
            for (int i = 0; i < total; i++) {
                byte[] part = Arrays.copyOfRange(e.zip, i * CHUNK, Math.min(e.zip.length, (i + 1) * CHUNK));
                Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Data(id, i, total, part));
            }
        }
    }

    /** 클라이언트 → 서버: 이름 바꾸기 / 삭제 */
    public record Edit(String id, String op, String arg) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(id, 40); b.writeUtf(op, 16); b.writeUtf(arg, 64); }
        public static Edit decode(FriendlyByteBuf b) { return new Edit(b.readUtf(40), b.readUtf(16), b.readUtf(64)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            DesignStore.Entry e = DesignStore.get(id);
            if (e == null) return;
            if (!canEdit(p, e)) { p.displayClientMessage(Component.translatable("elevmod.msg.design_denied").withStyle(ChatFormatting.RED), false); return; }
            boolean ok = switch (op) {
                case "rename" -> DesignStore.rename(id, arg);
                case "delete" -> DesignStore.delete(id);
                default -> false;
            };
            if (ok) broadcastIndex();
        }
    }

    /** 서버 → 클라이언트: 디자인 묶음 조각 */
    public record Data(String id, int idx, int total, byte[] data) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(id, 40); b.writeVarInt(idx); b.writeVarInt(total); b.writeByteArray(data); }
        public static Data decode(FriendlyByteBuf b) { return new Data(b.readUtf(40), b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK + 16)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mchoe.elevmod.client.design.RemoteDesigns.chunk(id, idx, total, data));
        }
    }

    /** 서버 → 클라이언트: 디자인 목록 (id, 이름, 부위들) */
    public record Index(List<String[]> rows) {
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(rows.size());
            for (String[] r : rows) { b.writeUtf(r[0], 40); b.writeUtf(r[1], 64); b.writeUtf(r[2], 200); }
        }
        public static Index decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < n; i++) rows.add(new String[]{b.readUtf(40), b.readUtf(64), b.readUtf(200)});
            return new Index(rows);
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mchoe.elevmod.client.design.RemoteDesigns.index(rows));
        }
    }

    /** 서버 → 클라이언트: 이 디자인을 다시 받아야 함 */
    public record Invalidate(String id) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(id, 40); }
        public static Invalidate decode(FriendlyByteBuf b) { return new Invalidate(b.readUtf(40)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mchoe.elevmod.client.design.RemoteDesigns.invalidate(id));
        }
    }

    public static Index index() {
        List<String[]> rows = new ArrayList<>();
        for (DesignStore.Entry e : DesignStore.all()) rows.add(new String[]{e.id, e.name, String.join(",", e.parts)});
        return new Index(rows);
    }

    public static void broadcastIndex() { Net.CH.send(PacketDistributor.ALL.noArg(), index()); }

    public static void sendIndex(ServerPlayer p) { Net.CH.send(PacketDistributor.PLAYER.with(() -> p), index()); }

    static void register(int[] id) {
        Net.CH.messageBuilder(Upload.class, id[0]++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(Upload::encode).decoder(Upload::decode).consumerMainThread(Upload::handle).add();
        Net.CH.messageBuilder(Request.class, id[0]++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
        Net.CH.messageBuilder(Edit.class, id[0]++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(Edit::encode).decoder(Edit::decode).consumerMainThread(Edit::handle).add();
        Net.CH.messageBuilder(Data.class, id[0]++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Data::encode).decoder(Data::decode).consumerMainThread(Data::handle).add();
        Net.CH.messageBuilder(Index.class, id[0]++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Index::encode).decoder(Index::decode).consumerMainThread(Index::handle).add();
        Net.CH.messageBuilder(Invalidate.class, id[0]++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Invalidate::encode).decoder(Invalidate::decode).consumerMainThread(Invalidate::handle).add();
        ElevMod.LOG.debug("[elevmod] design messages registered");
    }
}

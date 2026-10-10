package com.mchoe.elevmod.photo;

import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlock;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 개발용 촬영 세트 – -Delevmod.photo=true 일 때만 사용 (CI 스크린샷) */
public final class PhotoScenes {
    private PhotoScenes() {}

    public record Shot(String name, double x, double y, double z, float yaw, float pitch, @Nullable BlockPos ctrl, float door, double pathS) {}

    public static final List<Shot> SHOTS = Collections.synchronizedList(new ArrayList<>());
    public static volatile boolean ready;
    static final int Y0 = -60;

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) { l.setBlock(new BlockPos(x, y, z), s, 3); }

    private static BlockState facing(Block b, Direction d) { return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d); }

    /** 문 방향 SOUTH(+Z 가 승강장) 승강로 하나 */
    static ControllerBlockEntity elevator(ServerLevel l, ServerPlayer p, int x0, int z0, int floors, int floorH, int dw, int dh,
                                          boolean glass, Consumer<ControllerBlockEntity> cfg) {
        int top = Y0 + (floors - 1) * floorH + dh + 3;
        int xa = x0 - 2, xb = x0 + dw + 1, zb = z0 - 4, zf = z0;
        BlockState front = glass ? Blocks.GLASS.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState side = glass ? Blocks.GLASS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
        for (int y = Y0 - 3; y <= top; y++) for (int x = xa; x <= xb; x++) for (int z = zb; z <= zf; z++) {
            boolean edge = x == xa || x == xb || z == zb || z == zf;
            if (y == Y0 - 3 || y == top) set(l, x, y, z, Blocks.SMOOTH_STONE.defaultBlockState());
            else if (edge) set(l, x, y, z, z == zf ? front : side);
            else set(l, x, y, z, Blocks.AIR.defaultBlockState());
        }
        for (int i = 0; i < floors; i++) {
            int fy = Y0 + i * floorH;
            for (int dx = 0; dx < dw; dx++) for (int dy = 0; dy < dh; dy++) set(l, x0 + dx, fy + dy, zf, facing(ModRegistry.LANDING_DOOR.get(), Direction.SOUTH));
        }
        BlockPos cp = new BlockPos(x0, Y0, zb - 2);
        l.setBlock(cp, facing(ModRegistry.CONTROLLER.get(), Direction.NORTH), 3);
        ControllerBlockEntity c = (ControllerBlockEntity) l.getBlockEntity(cp);
        cfg.accept(c);
        for (int i = 0; i < floors; i++) c.linkDoor(l, new BlockPos(x0, Y0 + i * floorH, zf), p);
        cfg.accept(c);
        c.rebuild();
        // 기계실
        BlockPos m = new BlockPos(x0, top + 1, z0 - 2), g = new BlockPos(x0 + 1, top + 1, z0 - 2);
        l.setBlock(m, facing(ModRegistry.MACHINE_GEARLESS.get(), Direction.SOUTH), 3);
        l.setBlock(g, facing(ModRegistry.GOVERNOR.get(), Direction.SOUTH), 3);
        if (l.getBlockEntity(m) instanceof MachineBlockEntity me) c.linkMachine(me, p);
        if (l.getBlockEntity(g) instanceof MachineBlockEntity ge) c.linkMachine(ge, p);
        // 승강장 바닥
        for (int x = xa - 3; x <= xb + 3; x++) for (int z = zf + 1; z <= zf + 7; z++) set(l, x, Y0 - 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        return c;
    }

    static void device(ServerLevel l, ServerPlayer p, ControllerBlockEntity c, Block b, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        l.setBlock(pos, facing(b, Direction.SOUTH), 3);
        if (l.getBlockEntity(pos) instanceof DeviceBlockEntity d) c.linkDevice(d, p);
    }

    public static void build(MinecraftServer server) {
        ServerLevel l = server.overworld();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        l.setDayTime(6000);
        p.setGameMode(GameType.SPECTATOR);

        // ---- A: 일반 승객용 3개 층 + 승강장 장치
        ControllerBlockEntity a = elevator(l, p, 0, 0, 3, 4, 1, 2, false, c -> { c.usage = Spec.Usage.PASSENGER; c.doorType = Spec.DoorType.CO; c.capIdx = 5; });
        device(l, p, a, ModRegistry.HALL_BUTTON.get(), 1, Y0 + 1, 1);
        device(l, p, a, ModRegistry.INDICATOR_LCD.get(), 0, Y0 + 2, 1);
        device(l, p, a, ModRegistry.HALL_LANTERN.get(), -1, Y0 + 2, 1);
        double cx = 0.5, cd = a.cd;
        BlockPos ac = a.getBlockPos();
        SHOTS.add(new Shot("01_hall_closed", cx, Y0, 3.6, 180, 6, ac, 0f, 0));
        SHOTS.add(new Shot("02_hall_half", cx, Y0, 3.6, 180, 6, ac, 0.5f, 0));
        SHOTS.add(new Shot("03_hall_open", cx, Y0, 3.2, 180, 10, ac, 1f, 0));
        SHOTS.add(new Shot("04_car_front_closed", cx, Y0, -cd + 0.32, 0, 12, ac, 0f, 0));
        SHOTS.add(new Shot("05_car_front_open", cx, Y0, -cd + 0.32, 0, 12, ac, 1f, 0));
        SHOTS.add(new Shot("06_car_back", cx, Y0, -0.35, 180, 8, ac, 0f, 0));
        SHOTS.add(new Shot("07_car_side_left", cx, Y0, -cd / 2, 90, 10, ac, 0f, 0));
        SHOTS.add(new Shot("08_car_ceiling", cx, Y0, -cd / 2, 0, -70, ac, 0f, 0));
        float copX = (float) (0.5 + (-a.cw / 2 + a.frontOff - a.frontW / 2) / 2);
        SHOTS.add(new Shot("09_cop_close", copX, Y0 - 0.15, -0.72, 0, 22, ac, 0f, 0));

        // ---- B: 도어 방식별 (반쯤 열림)
        Object[][] types = {
                {Spec.DoorType.CO, 1, Spec.Usage.PASSENGER, 4, Spec.Jamb.NARROW, false},
                {Spec.DoorType.CO4, 2, Spec.Usage.PASSENGER_FREIGHT, 9, Spec.Jamb.WIDE, false},
                {Spec.DoorType.CO6, 3, Spec.Usage.FREIGHT, 12, Spec.Jamb.NARROW, false},
                {Spec.DoorType.S1, 1, Spec.Usage.PASSENGER, 4, Spec.Jamb.NARROW, false},
                {Spec.DoorType.S2, 1, Spec.Usage.HOSPITAL, 8, Spec.Jamb.NARROW, false},
                {Spec.DoorType.S2, 1, Spec.Usage.HOSPITAL, 8, Spec.Jamb.NARROW, true},
                {Spec.DoorType.S3, 2, Spec.Usage.FREIGHT, 10, Spec.Jamb.WIDE, false},
                {Spec.DoorType.S4, 2, Spec.Usage.FREIGHT, 10, Spec.Jamb.NARROW, false},
                {Spec.DoorType.UP, 2, Spec.Usage.FREIGHT, 10, Spec.Jamb.NONE, false},
                {Spec.DoorType.BIPART, 2, Spec.Usage.FREIGHT, 10, Spec.Jamb.NONE, false},
                {Spec.DoorType.SWING, 1, Spec.Usage.HOME, 1, Spec.Jamb.NARROW, false},
                {Spec.DoorType.GATE, 2, Spec.Usage.CONSTRUCTION, 9, Spec.Jamb.NONE, false}};
        int x = 14;
        for (Object[] t : types) {
            Spec.DoorType dt = (Spec.DoorType) t[0];
            int dw = (Integer) t[1];
            Spec.Usage u = (Spec.Usage) t[2];
            int cap = (Integer) t[3];
            Spec.Jamb jb = (Spec.Jamb) t[4];
            boolean rh = (Boolean) t[5];
            ControllerBlockEntity c = elevator(l, p, x, 0, 2, 4, dw, 2, false, cc -> {
                cc.usage = u; cc.doorType = dt; cc.capIdx = cap; cc.jamb = jb; cc.doorHand = rh ? Spec.Side.RIGHT : Spec.Side.LEFT;
                if (u == Spec.Usage.FREIGHT || u == Spec.Usage.CONSTRUCTION) cc.theme = Spec.Theme.CHECKER;
            });
            String n = dt.name().toLowerCase() + (rh ? "_right" : "");
            double mid = x + dw / 2.0;
            SHOTS.add(new Shot("10_door_" + n + "_hall", mid, Y0, 3.2 + dw * 0.5, 180, 8, c.getBlockPos(), 0.45f, 0));
            SHOTS.add(new Shot("11_door_" + n + "_car", mid, Y0, -c.cd + 0.3, 0, 10, c.getBlockPos(), 0.45f, 0));
            x += dw + 9;
        }

        // ---- E: 우드 + 다운라이트 승객용 (참고사진 비교용), 크기 직접 설정
        ControllerBlockEntity e = elevator(l, p, x, 0, 2, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.theme = Spec.Theme.WOOD; cc.ceiling = Spec.Ceiling.DOWNLIGHT; cc.capIdx = 6;
            cc.setW = 1.6f; cc.setD = 1.5f; cc.setH = 2.5f;
        });
        double ex = x + 0.5;
        SHOTS.add(new Shot("40_wood_front", ex, Y0, -e.cd + 0.3, 0, 8, e.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("41_wood_back", ex, Y0, -0.35, 180, 6, e.getBlockPos(), 0f, 0));
        x += 10;
        ControllerBlockEntity gd = elevator(l, p, x, 0, 2, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.theme = Spec.Theme.GOLD; cc.ceiling = Spec.Ceiling.GRID; cc.capIdx = 7; cc.jamb = Spec.Jamb.WIDE;
        });
        SHOTS.add(new Shot("42_gold_front", x + 0.5, Y0, -gd.cd + 0.3, 0, 8, gd.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("43_gold_hall", x + 0.5, Y0, 3.6, 180, 6, gd.getBlockPos(), 0f, 0));

        // ---- C: 승강장 장치 진열 벽
        for (int xx = -16; xx <= -2; xx++) for (int y = Y0; y <= Y0 + 3; y++) set(l, xx, y, 20, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int xx = -18; xx <= 0; xx++) for (int z = 21; z <= 26; z++) set(l, xx, Y0 - 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        Block[] devs = {ModRegistry.HALL_BUTTON.get(), ModRegistry.HALL_PANEL.get(), ModRegistry.INDICATOR_SEGMENT.get(), ModRegistry.INDICATOR_DOT.get(),
                ModRegistry.INDICATOR_LCD.get(), ModRegistry.INDICATOR_LAMP.get(), ModRegistry.HALL_LANTERN.get(), ModRegistry.FIRE_SWITCH.get(),
                ModRegistry.DESTINATION_PANEL.get(), ModRegistry.CALL_RECEIVER.get()};
        for (int i = 0; i < devs.length; i++) device(l, p, a, devs[i], -15 + i, Y0 + 1, 21);
        SHOTS.add(new Shot("20_hall_devices", -10.5, Y0, 25.5, 180, 4, ac, 0f, 0));

        // ---- D: 유리 승강로 (레일, 균형추, 로프, 기계실)
        ControllerBlockEntity g = elevator(l, p, -30, 0, 4, 4, 1, 2, true, c -> { c.usage = Spec.Usage.OBSERVATION; c.capIdx = 6; });
        SHOTS.add(new Shot("30_glass_shaft_side", -24.0, Y0 + 3, -2.0, 90, 4, g.getBlockPos(), 0f, 5.5));
        SHOTS.add(new Shot("31_glass_shaft_wide", -22.0, Y0 + 8, 8.0, 135, 20, g.getBlockPos(), 0f, 5.5));
        SHOTS.add(new Shot("32_machine_room", -29.5, Y0 + 20, 2.5, 180, 40, g.getBlockPos(), 0f, 5.5));
        ready = true;
    }

    public static void apply(MinecraftServer server, Shot s) {
        ServerLevel l = server.overworld();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        if (s.ctrl() != null && l.getBlockEntity(s.ctrl()) instanceof ControllerBlockEntity c) {
            c.photoFreeze(l, s.door(), s.pathS());
            if (s.name().startsWith("20")) c.dir = 1;
        }
        p.teleportTo(l, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
    }
}

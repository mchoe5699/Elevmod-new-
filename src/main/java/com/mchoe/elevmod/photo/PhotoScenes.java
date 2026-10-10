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
    /** 디자인 스튜디오로 만든 사진 디자인 id (클라이언트가 올림) */
    public static volatile String photoDesign = "";
    /** 표시기 설정 화면 촬영용 제어반 */
    public static volatile BlockPos indCtrl;
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
                if (dt.sideOpening()) { cc.window = true; cc.windowMask = 0b01; }   // 편개: 승강장/카 방범창 위치 비교
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

        // ---- F: 텐키 + 양쪽 운전반 + 양쪽 장애인 운전반, 터치식, 도트 표시기, 에칭 벽, 대리석 바닥
        x += 10;
        ControllerBlockEntity tk = elevator(l, p, x, 0, 3, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 9; cc.copKeys = Spec.CopKeys.TENKEY; cc.copSide = Spec.Side.BOTH;
            cc.accSide = Spec.Side.BOTH; cc.accessibleCop = true; cc.copStyle = Spec.ButtonStyle.TOUCH; cc.accStyle = Spec.ButtonStyle.TOUCH;
            cc.carDisplay = Spec.Display.DOT; cc.wallFinish = Spec.WallFinish.ETCHED; cc.floorFinish = Spec.FloorFinish.MARBLE;
            cc.ceiling = Spec.Ceiling.DOWNLIGHT;
        });
        double tx = x + 0.5;
        SHOTS.add(new Shot("50_tenkey_front", tx, Y0, -tk.cd + 0.25, 0, 10, tk.getBlockPos(), 0f, 0));
        float tkCop = (float) (tx + (-tk.cw / 2 + tk.frontOff - tk.frontW / 2) / 2);
        SHOTS.add(new Shot("51_tenkey_cop", tkCop, Y0 - 0.15, -0.72, 0, 22, tk.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("52_acc_left", tx, Y0 - 0.35, -tk.cd / 2 - 0.05, -90, 18, tk.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("53_acc_right", tx, Y0 - 0.35, -tk.cd / 2 - 0.05, 90, 18, tk.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("54_dot_transom", tx, Y0 + 0.2, -0.95, 0, -30, tk.getBlockPos(), 0f, 0));
        // ---- G: 7세그먼트 트랜섬 + 구형 버튼 + 고무바닥 + 대리석 벽, 행선층(DD) 카(층버튼 없음)
        x += 10;
        ControllerBlockEntity sg = elevator(l, p, x, 0, 3, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 7; cc.copStyle = Spec.ButtonStyle.CLASSIC; cc.carDisplay = Spec.Display.SEGMENT;
            cc.wallFinish = Spec.WallFinish.MARBLE; cc.floorFinish = Spec.FloorFinish.RUBBER;
        });
        SHOTS.add(new Shot("55_seg_transom", x + 0.5, Y0 + 0.2, -0.95, 0, -30, sg.getBlockPos(), 0f, 0));
        float sgCop = (float) (x + 0.5 + (-sg.cw / 2 + sg.frontOff - sg.frontW / 2) / 2);
        SHOTS.add(new Shot("56_classic_cop", sgCop, Y0 - 0.15, -0.72, 0, 22, sg.getBlockPos(), 0f, 0));
        x += 10;
        ControllerBlockEntity dd = elevator(l, p, x, 0, 3, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 7; cc.destination = true; cc.ddCopButtons = false; cc.copStyle = Spec.ButtonStyle.SQUARE;
        });
        float ddCop = (float) (x + 0.5 + (-dd.cw / 2 + dd.frontOff - dd.frontW / 2) / 2);
        SHOTS.add(new Shot("57_dd_cop_nobuttons", ddCop, Y0 - 0.15, -0.72, 0, 22, dd.getBlockPos(), 0f, 0));

        // ---- I: 방범창 문 (2칸 폭 중앙개폐) + 두 블록 사이 가운데로 옮긴 층표시기/홀랜턴
        x += 10;
        ControllerBlockEntity wn = elevator(l, p, x, 0, 2, 4, 2, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 8; cc.doorType = Spec.DoorType.CO; cc.window = true; cc.jamb = Spec.Jamb.WIDE;
        });
        device(l, p, wn, ModRegistry.INDICATOR_SEGMENT.get(), x, Y0 + 2, 1);
        device(l, p, wn, ModRegistry.HALL_BUTTON.get(), x + 2, Y0 + 1, 1);
        if (l.getBlockEntity(new BlockPos(x, Y0 + 2, 1)) instanceof DeviceBlockEntity de) de.setOffset(8, 2);   // 정면에서 오른쪽으로 ½ = 두 블록 사이
        SHOTS.add(new Shot("60_window_hall_closed", x + 1.0, Y0, 4.2, 180, 6, wn.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("61_window_car_closed", x + 1.0, Y0, -wn.cd + 0.3, 0, 6, wn.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("62_window_close", x + 0.75, Y0 - 0.1, 1.9, 180, 0, wn.getBlockPos(), 0f, 0));

        // ---- J: 방범창 선택 – 4짝 중앙개폐 중 바깥 두 짝(1번, 4번)만
        x += 10;
        ControllerBlockEntity wm = elevator(l, p, x, 0, 2, 4, 2, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER_FREIGHT; cc.capIdx = 9; cc.doorType = Spec.DoorType.CO4; cc.window = true; cc.windowMask = 0b1001;
        });
        SHOTS.add(new Shot("63_window_select_hall", x + 1.0, Y0, 4.2, 180, 6, wm.getBlockPos(), 0f, 0));
        // ---- K: 커스텀 디자인 예시 2종
        x += 10;
        ControllerBlockEntity cw1 = elevator(l, p, x, 0, 2, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 7; cc.designMode = Spec.DesignMode.CUSTOM; cc.customDesign = "classic_wood"; cc.window = true;
        });
        SHOTS.add(new Shot("70_classic_front", x + 0.5, Y0, -cw1.cd + 0.28, 0, 8, cw1.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("71_classic_side", x + 0.5, Y0, -cw1.cd / 2, -90, 10, cw1.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("72_classic_ceiling", x + 0.5, Y0, -cw1.cd / 2, 0, -75, cw1.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("73_classic_hall", x + 0.5, Y0, 3.4, 180, 6, cw1.getBlockPos(), 0f, 0));
        x += 10;
        ControllerBlockEntity cw2 = elevator(l, p, x, 0, 2, 4, 1, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 7; cc.designMode = Spec.DesignMode.CUSTOM; cc.customDesign = "modern_line";
        });
        SHOTS.add(new Shot("74_modern_front", x + 0.5, Y0, -cw2.cd + 0.28, 0, 8, cw2.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("75_modern_side", x + 0.5, Y0, -cw2.cd / 2, 90, 10, cw2.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("76_modern_ceiling", x + 0.5, Y0, -cw2.cd / 2, 0, -75, cw2.getBlockPos(), 0f, 0));

        // ---- L: 사진으로 만든 커스텀 디자인 (운전반·카 도어·승강장 문·층표시기·호출패널)
        if (!photoDesign.isEmpty()) {
            x += 10;
            final String pd = photoDesign;
            ControllerBlockEntity ph = elevator(l, p, x, 0, 3, 4, 1, 2, false, cc -> {
                cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 7; cc.designMode = Spec.DesignMode.CUSTOM; cc.customDesign = pd; cc.designUnified = true;
            });
            device(l, p, ph, ModRegistry.INDICATOR_SEGMENT.get(), x, Y0 + 2, 1);
            device(l, p, ph, ModRegistry.HALL_PANEL.get(), x + 1, Y0, 1);
            double hx = x + 0.5;
            SHOTS.add(new Shot("80_photo_hall", hx + 0.3, Y0, 3.3, 180, 8, ph.getBlockPos(), 0f, 0));
            SHOTS.add(new Shot("81_photo_car_front", hx, Y0, -ph.cd + 0.28, 0, 8, ph.getBlockPos(), 0f, 0));
            float phCop = (float) (hx + (-ph.cw / 2 + ph.frontOff - ph.frontW / 2) / 2);
            SHOTS.add(new Shot("82_photo_cop", phCop, Y0 - 0.15, -0.72, 0, 18, ph.getBlockPos(), 0f, 0));
            SHOTS.add(new Shot("83_photo_hall_close", hx + 0.5, Y0 + 0.3, 1.9, 180, 10, ph.getBlockPos(), 0f, 0));
        }

        // ---- M: 편개 2짝(2S) 방범창 – 승강장/카 안에서 같은 문짝에 창이 보여야 함, 트랜섬 도트 표시기 위치 이동
        x += 10;
        ControllerBlockEntity sw = elevator(l, p, x, 0, 2, 4, 2, 2, false, cc -> {
            cc.usage = Spec.Usage.PASSENGER; cc.capIdx = 8; cc.doorType = Spec.DoorType.S2; cc.window = true; cc.windowMask = 0b01;
            cc.carDisplay = Spec.Display.DOT; cc.indOffX = 25; cc.indOffY = 4;
            cc.ind.dotCols = 5; cc.ind.dotRows = 12; cc.ind.dotOn = 0xFF40FF60;
        });
        indCtrl = sw.getBlockPos();
        sw.ind.trans = 1; sw.ind.arrowMode = 1; sw.ind.arrowSec = 3; sw.doorDelay = 1.5f; sw.hallCancel = true;
        SHOTS.add(new Shot("64_s2_window_hall", x + 1.0, Y0, 4.2, 180, 6, sw.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("65_s2_window_car", x + 1.0, Y0, -sw.cd + 0.3, 0, 6, sw.getBlockPos(), 0f, 0));
        SHOTS.add(new Shot("66_s2_window_half_hall", x + 1.0, Y0, 4.2, 180, 6, sw.getBlockPos(), 0.4f, 0));
        SHOTS.add(new Shot("67_s2_window_half_car", x + 1.0, Y0, -sw.cd + 0.3, 0, 6, sw.getBlockPos(), 0.4f, 0));
        SHOTS.add(new Shot("68_s2_transom_offset", x + 1.0, Y0 + 0.2, -1.0, 0, -28, sw.getBlockPos(), 0f, 0));

        // ---- N: 표시기 사용자 설정 진열 (세그먼트 종류·모양·색, 도트 크기, 애니메이션, 회전)
        {
            for (int xx = -22; xx <= 0; xx++) for (int y = Y0; y <= Y0 + 3; y++) set(l, xx, y, 44, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            for (int xx = -24; xx <= 2; xx++) for (int z = 45; z <= 51; z++) set(l, xx, Y0 - 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            com.mchoe.elevmod.elevator.DotAnim an = new com.mchoe.elevmod.elevator.DotAnim();
            an.id = "a_phototest01"; an.name = "Photo Up"; an.cols = 5; an.rows = 7;
            for (int s2 = 0; s2 < 4; s2++) {
                com.mchoe.elevmod.elevator.DotAnim.Frame f = new com.mchoe.elevmod.elevator.DotAnim.Frame(35);
                f.sec = 0.3f;
                for (int yy = 0; yy < 7; yy++) for (int xx = 0; xx < 5; xx++) {
                    int r = Math.floorMod(yy + s2 * 2, 7);
                    boolean on = (r == 0 && xx == 2) || (r == 1 && xx >= 1 && xx <= 3) || (r == 2) || (r >= 3 && xx == 2);
                    if (on) f.dots[yy * 5 + xx] = (byte) (1 + (s2 % 2) * 2);
                }
                an.frames.add(f);
            }
            com.mchoe.elevmod.elevator.AnimStore.put(an);
            com.mchoe.elevmod.network.AnimNet.broadcast();
            Object[][] show = {
                    // 블록, 세그종류, 모양, 기울기, 앞자리1, 켜짐색, 잔상색, 도트 가로, 도트 세로, 애니메이션
                    {ModRegistry.INDICATOR_SEGMENT.get(), 0, 0, 8f, false, 0xFFFF2010, 0xFF3A0A06, 0, 0, ""},
                    {ModRegistry.INDICATOR_SEGMENT.get(), 0, 1, 4f, false, 0xFF40A8FF, 0xFF0A1A30, 0, 0, ""},
                    {ModRegistry.INDICATOR_SEGMENT.get(), 1, 0, 6f, true, 0xFFC81400, 0xFF2A0A06, 0, 0, ""},
                    {ModRegistry.INDICATOR_SEGMENT.get(), 1, 0, 6f, true, 0xFFFF8A18, 0xFF301800, 0, 0, ""},
                    {ModRegistry.INDICATOR_SEGMENT.get(), 3, 2, 0f, false, 0xFFFF2A10, 0xFF2A0A06, 0, 0, ""},
                    {ModRegistry.INDICATOR_DOT.get(), 0, 0, 0f, false, 0, 0, 5, 7, "a_phototest01"},
                    {ModRegistry.INDICATOR_DOT.get(), 0, 0, 0f, false, 0, 0, 5, 12, ""},
                    {ModRegistry.INDICATOR_DOT.get(), 0, 0, 0f, false, 0, 0, 7, 9, ""}};
            int sx = -20;
            for (Object[] v : show) {
                device(l, p, a, (Block) v[0], sx, Y0 + 2, 45);
                if (l.getBlockEntity(new BlockPos(sx, Y0 + 2, 45)) instanceof DeviceBlockEntity de) {
                    com.mchoe.elevmod.elevator.IndicatorProfile ip = new com.mchoe.elevmod.elevator.IndicatorProfile();
                    ip.segType = (Integer) v[1]; ip.segShape = (Integer) v[2]; ip.segSlant = (Float) v[3]; ip.segLead1 = (Boolean) v[4];
                    ip.segOn = (Integer) v[5]; ip.segOff = (Integer) v[6];
                    if ((Integer) v[7] > 0) { ip.dotCols = (Integer) v[7]; ip.dotRows = (Integer) v[8]; }
                    ip.animUp = (String) v[9]; ip.animIdle = (String) v[9];
                    if (sx == -13) ip.dotOn = 0xFF40FF60;
                    if (sx == -14) ip.dotRound = false;
                    de.forceIndTag(ip.save());
                }
                sx += 2;
            }
            // 회전한 호출 버튼 (Ctrl + / -)
            device(l, p, a, ModRegistry.HALL_BUTTON.get(), -3, Y0 + 1, 45);
            if (l.getBlockEntity(new BlockPos(-3, Y0 + 1, 45)) instanceof DeviceBlockEntity de) de.setYaw(25);
            for (int i = 0; i < show.length; i++)
                SHOTS.add(new Shot("29_ind_" + i, -20 + i * 2 + 0.5, Y0 + 0.95, 46.25, 180, 0, ac, 0f, 0));
            SHOTS.add(new Shot("26_ind_seg_left", -16.5, Y0 + 1.3, 47.6, 180, 6, ac, 0f, 0));
            SHOTS.add(new Shot("27_ind_dot_right", -8.0, Y0 + 1.3, 47.6, 180, 6, ac, 0f, 0));
            SHOTS.add(new Shot("28_ind_wide", -10.0, Y0 + 0.8, 50.0, 180, 4, ac, 0f, 0));
        }

        // ---- H: 승강장 장치 의장 진열 (디자인 변경기로 바꿀 수 있는 것들)
        for (int xx = -22; xx <= 0; xx++) for (int y = Y0; y <= Y0 + 3; y++) set(l, xx, y, 32, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int xx = -24; xx <= 2; xx++) for (int z = 33; z <= 39; z++) set(l, xx, Y0 - 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        Object[][] var = {
                {ModRegistry.HALL_BUTTON.get(), 4, 1}, {ModRegistry.INDICATOR_SEGMENT.get(), 4, 2}, {ModRegistry.INDICATOR_DOT.get(), 4, 1},
                {ModRegistry.INDICATOR_LCD.get(), 3, 2}, {ModRegistry.HALL_LANTERN.get(), 3, 1}};
        int vx = -21;
        for (Object[] v : var) {
            for (int k = 0; k < (Integer) v[1]; k++) {
                device(l, p, a, (Block) v[0], vx, Y0 + (Integer) v[2], 33);
                if (l.getBlockEntity(new BlockPos(vx, Y0 + (Integer) v[2], 33)) instanceof DeviceBlockEntity de) de.setDesign(k);
                vx++;
            }
            vx++;
        }
        SHOTS.add(new Shot("21_designs_left", -16.5, Y0, 36.2, 180, 8, ac, 0f, 0));
        SHOTS.add(new Shot("22_designs_right", -6.0, Y0, 36.2, 180, 8, ac, 0f, 0));
        SHOTS.add(new Shot("23_seg_close", -14.0, Y0 + 0.75, 35.2, 180, 2, ac, 0f, 0));
        SHOTS.add(new Shot("24_buttons_close", -19.0, Y0 - 0.25, 35.0, 180, 2, ac, 0f, 0));
        SHOTS.add(new Shot("25_lantern_close", -0.5, Y0 - 0.25, 35.0, 180, 2, ac, 0f, 0));

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
            if (s.name().startsWith("2")) c.dir = 1;
        }
        p.teleportTo(l, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
    }
}

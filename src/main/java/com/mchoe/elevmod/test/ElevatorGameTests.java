package com.mchoe.elevmod.test;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.block.LandingDoorBlock;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 자동 게임테스트 – 3개 층(4블록 간격) 승강로를 실제로 짓고 운행/안전장치를 검증한다.
 * gradle runGameTestServer 로 실행.
 */
@GameTestHolder(ElevMod.MODID)
@PrefixGameTestTemplate(false)
public class ElevatorGameTests {
    private static final int[] FLOORS = {1, 5, 9};

    /** 승강로 하나를 짓고 제어반에 연결 */
    private static ControllerBlockEntity build(GameTestHelper h, int doorX, int ctrlX) {
        ServerLevel lvl = h.getLevel();
        BlockPos ctrlRel = new BlockPos(ctrlX, 1, 0);
        h.setBlock(ctrlRel, ModRegistry.CONTROLLER.get().defaultBlockState());
        for (int y : FLOORS) {
            h.setBlock(new BlockPos(doorX, y, 5), ModRegistry.LANDING_DOOR.get().defaultBlockState().setValue(LandingDoorBlock.FACING, Direction.SOUTH));
            h.setBlock(new BlockPos(doorX, y + 1, 5), ModRegistry.LANDING_DOOR.get().defaultBlockState().setValue(LandingDoorBlock.FACING, Direction.SOUTH));
        }
        ControllerBlockEntity c = (ControllerBlockEntity) lvl.getBlockEntity(h.absolutePos(ctrlRel));
        if (c == null) throw new IllegalStateException("controller missing");
        Player p = h.makeMockPlayer();
        for (int y : FLOORS) c.linkDoor(lvl, h.absolutePos(new BlockPos(doorX, y, 5)), p);
        return c;
    }

    private static int absY(GameTestHelper h, int rel) { return h.absolutePos(new BlockPos(0, rel, 0)).getY(); }

    private static boolean doorsOpenAt(ControllerBlockEntity c, int idx) {
        return c.cur == idx && (c.state == ControllerBlockEntity.State.OPEN) && c.atFloorPos();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void hallCallBringsCar(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        h.assertTrue(c.floors.size() == 3, "3개 층이 등록되어야 함");
        h.assertTrue(c.floors.get(0).name.equals("1") && c.floors.get(2).name.equals("3"), "층 이름 1..3");
        ServerLevel lvl = h.getLevel();
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16);
                    h.assertTrue(!lvl.getEntitiesOfClass(ElevatorCarEntity.class, box).isEmpty(), "카 엔티티가 생성되어야 함");
                    c.hallCall(lvl, absY(h, 9), false, null);
                })
                .thenWaitUntil(() -> h.assertTrue(doorsOpenAt(c, 2), "카가 최상층에 도착해 문이 열려야 함"))
                .thenWaitUntil(() -> h.assertTrue(c.state == ControllerBlockEntity.State.IDLE && c.doorPos == 0, "문이 자동으로 닫혀야 함"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void ropeBreakTripsSafetyGear(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        ServerLevel lvl = h.getLevel();
        double[] startS = new double[1];
        h.startSequence()
                .thenExecuteAfter(2, () -> c.hallCall(lvl, absY(h, 9), false, null))
                .thenWaitUntil(() -> h.assertTrue(c.state == ControllerBlockEntity.State.IDLE && c.cur == 2 && c.doorPos == 0, "최상층 대기"))
                .thenExecute(() -> { startS[0] = c.s; h.assertTrue(c.simulateRopeBreak(lvl), "로프 절단 시험 시작"); })
                .thenWaitUntil(() -> h.assertTrue(c.fault == Spec.Fault.SAFETY_GEAR && c.v == 0, "비상정지장치로 정지해야 함"))
                .thenExecute(() -> h.assertTrue(startS[0] - c.s < 2.0 && c.s > c.floorS[1], "추락 거리는 2블록 미만이어야 함 (" + (startS[0] - c.s) + ")"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void brakeFailureRopeBrake(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        ServerLevel lvl = h.getLevel();
        h.startSequence()
                .thenExecuteAfter(2, () -> { c.simulateBrakeFailure(lvl); c.hallCall(lvl, absY(h, 1), true, null); })
                .thenWaitUntil(() -> h.assertTrue(c.fault == Spec.Fault.ROPE_BRAKE && c.v == 0, "로프브레이크가 작동해야 함"))
                .thenExecute(() -> h.assertTrue(Math.abs(c.s - c.floorS[0]) < 1.2, "1.2m 이내 정지 (" + Math.abs(c.s - c.floorS[0]) + ")"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void powerFailureArd(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        ServerLevel lvl = h.getLevel();
        h.startSequence()
                .thenExecuteAfter(2, () -> c.hallCall(lvl, absY(h, 9), false, null))
                .thenWaitUntil(() -> h.assertTrue(c.state == ControllerBlockEntity.State.MOVING && c.s > c.floorS[0] + 2.0, "운행 중"))
                .thenExecute(() -> c.setPower(lvl, true))
                .thenWaitUntil(() -> h.assertTrue(c.powerOff && c.atFloorPos() && c.doorPos >= 1f, "ARD 로 가까운 층에 착상 후 문 개방"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void fireRecall(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        ServerLevel lvl = h.getLevel();
        h.startSequence()
                .thenExecuteAfter(2, () -> c.hallCall(lvl, absY(h, 9), false, null))
                .thenWaitUntil(() -> h.assertTrue(doorsOpenAt(c, 2), "최상층 도착"))
                .thenExecute(() -> c.fireService(lvl, true, absY(h, 1)))
                .thenWaitUntil(() -> h.assertTrue(c.mode == Spec.Mode.FIRE1 && doorsOpenAt(c, 0), "소방 귀환층(1층)으로 귀환 후 문 열림 유지"))
                .thenIdle(120)
                .thenExecute(() -> h.assertTrue(c.state == ControllerBlockEntity.State.OPEN, "소방운전 중 문은 열린 채 유지"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void groupDispatchNearestCar(GameTestHelper h) {
        ControllerBlockEntity a = build(h, 2, 0);
        ControllerBlockEntity b = build(h, 6, 8);
        ServerLevel lvl = h.getLevel();
        a.linkGroup(lvl, b, h.makeMockPlayer());
        h.assertTrue(a.group.size() == 2 && b.group.size() == 2, "군관리 2대");
        // A호기를 최상층에 둔다
        a.s = a.floorS[2];
        a.cur = 2;
        h.runAfterDelay(3, () -> {
            a.hallCall(lvl, absY(h, 1), true, null);
            boolean bGot = b.hallUp[0], aGot = a.hallUp[0];
            h.assertTrue(!aGot, "먼 카(A)는 배정되지 않아야 함");
            h.assertTrue(bGot || b.state == ControllerBlockEntity.State.OPENING || b.state == ControllerBlockEntity.State.OPEN, "가까운 카(B)가 응답");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overloadKeepsDoorsOpen(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        ServerLevel lvl = h.getLevel();
        c.capIdx = 0; // 300kg (4인승)
        c.rebuild();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    // 카 안에 철 골렘 2마리(약 1000kg)
                    for (int i = 0; i < 2; i++) {
                        var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(lvl);
                        if (g != null) { var p = c.carPos(); g.moveTo(p.x, p.y + 0.05, p.z); g.setNoAi(true); lvl.addFreshEntity(g); }
                    }
                    c.hallCall(lvl, absY(h, 1), true, null);
                })
                .thenWaitUntil(() -> h.assertTrue(c.overload == 2 && c.state == ControllerBlockEntity.State.OPEN, "정원초과 감지 + 문 열림"))
                .thenIdle(200)
                .thenExecute(() -> h.assertTrue(c.state == ControllerBlockEntity.State.OPEN && c.cur == 0, "정원초과 동안 출발하지 않음"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void paternosterCycles(GameTestHelper h) {
        ControllerBlockEntity c = build(h, 4, 0);
        c.usage = Spec.Usage.PATERNOSTER;
        c.rebuild();
        double[] s0 = new double[1];
        h.startSequence()
                .thenExecuteAfter(5, () -> s0[0] = c.s)
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(c.carIds.size() >= 2, "카 여러 대가 순환");
                    h.assertTrue(c.s != s0[0], "계속 순환 이동");
                })
                .thenSucceed();
    }
}

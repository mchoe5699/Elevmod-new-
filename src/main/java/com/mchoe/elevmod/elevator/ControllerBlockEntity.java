package com.mchoe.elevmod.elevator;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlock;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 제어반(Control Panel). 승강기의 두뇌 – 운행 알고리즘(승합전자동 / 군관리), 도어 제어,
 * 각종 운전모드, 안전장치(조속기·비상정지장치·로프브레이크·파이널리밋·완충기), 정전 시 ARD.
 */
public class ControllerBlockEntity extends BlockEntity {
    public enum State { IDLE, MOVING, OPENING, OPEN, CLOSING, STOPPED }

    // ------------------------------------------------------------ 설정
    public Spec.Usage usage = Spec.Usage.PASSENGER;
    public Spec.Drive drive = Spec.Drive.GEARLESS;
    public Spec.DoorType doorType = Spec.DoorType.CO;
    public Spec.Theme theme = Spec.Theme.STAINLESS;
    public Spec.Voice voice = Spec.Voice.VOICE;
    public Spec.RedstoneFn rsFn = Spec.RedstoneFn.NONE;
    public int capIdx = 4;     // 750kg
    public int speedIdx = 6;   // 90 m/min
    public int dwellSec = 4;
    public int returnSec = 0;
    public boolean beep = true, fourAsF = false, skip4 = false, skip13 = false;
    public boolean ard = true, ropeBrake = true, preOpen = false, window = false, airClean = true, aircon = false;
    public boolean stopSwitch = false, inclined = false, accessibleCop = true, destination = false;
    public int lobbyY = Integer.MIN_VALUE, homeY = Integer.MIN_VALUE, fireY = Integer.MIN_VALUE;
    public String serial = "";

    // ------------------------------------------------------------ 구조
    public final List<Floor> floors = new ArrayList<>();
    public final Set<BlockPos> devices = new HashSet<>();
    public final Set<BlockPos> machines = new HashSet<>();
    public final List<BlockPos> group = new ArrayList<>();
    public final List<UUID> carIds = new ArrayList<>();
    public Direction carFacing = null;

    // ------------------------------------------------------------ 운행 상태
    public State state = State.IDLE;
    public Spec.Mode mode = Spec.Mode.NORMAL;
    public Spec.Fault fault = Spec.Fault.NONE;
    public boolean powerOff, simRope, simBrake, stopPressed;
    public double s, v;
    public int target = -1, cur = 0, dir = 0;
    public float doorPos;
    public boolean[] carCalls = new boolean[0], hallUp = new boolean[0], hallDown = new boolean[0];
    public long trips;
    public double travelled;
    public long lastInspect = -1;
    public final List<String> faultLog = new ArrayList<>();

    // ------------------------------------------------------------ 일시 상태(저장 안 함)
    private int timer, idleTicks, obstruct, holdOpen, holdClose, holdRun, holdRunDir, alarmArmed, ardTimer, doorLock;
    private boolean nudge, parkRun, crawl, ardRun, ardDone, manualRescue, rescueArrived, freeFall, govTripped;
    private boolean liftNeedsHold, quakeOpened, accessibleDwell, rsLast, ucmEngaged;
    private double levelErr;
    public int loadKg, overload, riders;
    private int tick, displayHash = 1, lastPush;
    private double ucmStart;

    // ------------------------------------------------------------ 계산값
    public float cw = 1.4f, cd = 1.35f, ch = 2.4f;
    public float frontW, frontH, frontOff, rearW, rearH, rearOff;
    public double[] floorS = new double[0];
    private double paterLoop;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CONTROLLER_BE.get(), pos, state);
    }

    // ================================================================= 계산 / 기하
    public int ratedKg() {
        if (usage == Spec.Usage.DUMBWAITER) return 100;
        if (usage == Spec.Usage.WHEELCHAIR_LIFT) return 250;
        if (usage == Spec.Usage.PATERNOSTER) return 150;
        return Spec.CAPACITY_KG[Math.max(0, Math.min(capIdx, Spec.CAPACITY_KG.length - 1))];
    }

    /** 실제 적용 정격속도 (m/min) – 방식별 상한 적용 */
    public int effectiveMpm() {
        int mpm = Spec.SPEED_MPM[Math.max(0, Math.min(speedIdx, Spec.SPEED_MPM.length - 1))];
        if (drive == Spec.Drive.HYDRAULIC) mpm = Math.min(mpm, 60);
        if (drive == Spec.Drive.DRUM) mpm = Math.min(mpm, 30);
        if (drive == Spec.Drive.RACK_PINION) mpm = Math.min(mpm, 90);
        switch (usage) {
            case WHEELCHAIR_LIFT -> mpm = Math.min(mpm, 9);
            case PATERNOSTER -> mpm = 18;
            case HOME, DUMBWAITER -> mpm = Math.min(mpm, 30);
            case CAR -> mpm = Math.min(mpm, 60);
            default -> {}
        }
        return mpm;
    }

    public double rated() { return Spec.bpt(effectiveMpm()); }

    public double accel() { return Spec.accel(effectiveMpm()); }

    private Vec3 vf() { Direction f = carFacing == null ? Direction.SOUTH : carFacing; return new Vec3(f.getStepX(), 0, f.getStepZ()); }

    private Vec3 vl() { Direction f = carFacing == null ? Direction.SOUTH : carFacing; Direction l = f.getCounterClockWise(); return new Vec3(l.getStepX(), 0, l.getStepZ()); }

    /** 카 치수 계산 + 층별 기준점(카 바닥 중심) + 경로 */
    public void rebuild() {
        floors.sort(Comparator.comparingInt(f -> f.y));
        int n = floors.size();
        // 카 치수
        int kg = ratedKg();
        float area = Spec.carArea(kg);
        if (usage == Spec.Usage.FREIGHT || usage == Spec.Usage.CAR) area *= 1.3f;
        float aspect = switch (usage) {
            case HOSPITAL -> 0.6f;
            case CAR -> 0.45f;
            case FREIGHT, PASSENGER_FREIGHT -> 0.85f;
            default -> 1.05f;
        };
        cw = (float) Math.sqrt(area * aspect);
        cd = area / cw;
        ch = 2.4f;
        switch (usage) {
            case DUMBWAITER -> { cw = 0.9f; cd = 0.9f; ch = 1.0f; }
            case WHEELCHAIR_LIFT -> { cw = 1.0f; cd = 1.4f; ch = 1.1f; }
            case PATERNOSTER -> { cw = 1.2f; cd = 1.0f; ch = 2.2f; }
            case CAR -> { cw = Math.max(cw, 2.6f); cd = Math.max(cd, 5.5f); ch = 2.4f; }
            case HOSPITAL -> { cw = Math.max(cw, 1.5f); cd = Math.max(cd, 2.5f); }
            case FREIGHT -> ch = 2.6f;
            case CONSTRUCTION -> ch = 2.3f;
            default -> {}
        }
        // 문 치수 / 관통형 깊이
        frontW = 0; rearW = 0;
        Floor.Door refFront = null, refRear = null;
        for (Floor f : floors) for (Floor.Door d : f.doors) {
            if (!d.rear && refFront == null) refFront = d;
            if (d.rear && refRear == null) refRear = d;
        }
        float maxDoorW = 0, maxDoorH = 0;
        for (Floor f : floors) for (Floor.Door d : f.doors) { maxDoorW = Math.max(maxDoorW, d.w); maxDoorH = Math.max(maxDoorH, d.h); }
        if (usage != Spec.Usage.DUMBWAITER) {
            cw = Math.max(cw, maxDoorW + 0.3f);
            ch = Math.max(ch, maxDoorH + 0.3f);
        } else {
            cw = Math.max(cw, maxDoorW);
            ch = Math.max(ch, maxDoorH);
        }
        Vec3 F = vf(), L = vl();
        if (refFront != null && refRear != null && !inclined) {
            double depth = Math.abs(refFront.plane().subtract(refRear.plane()).dot(F));
            if (depth >= 0.8 && depth <= 12) cd = (float) depth;
        }
        // 기준점
        Vec3 base = null;
        if (refFront != null) base = refFront.plane().subtract(F.scale(cd / 2.0));
        else if (refRear != null) base = refRear.plane().add(F.scale(cd / 2.0));
        for (Floor f : floors) {
            Vec3 a = base == null ? Vec3.atBottomCenterOf(worldPosition) : new Vec3(base.x, f.y, base.z);
            if (inclined && !f.doors.isEmpty()) {
                Floor.Door d = f.doors.get(0);
                a = d.rear ? d.plane().add(F.scale(cd / 2.0)) : d.plane().subtract(F.scale(cd / 2.0));
            }
            f.ax = a.x; f.ay = f.y; f.az = a.z;
            for (Floor.Door d : f.doors) d.off = (float) d.plane().subtract(a).dot(L);
        }
        if (refFront != null) { frontW = refFront.w; frontH = refFront.h; frontOff = refFront.off; }
        if (refRear != null) { rearW = refRear.w; rearH = refRear.h; rearOff = refRear.off; }
        // 경로
        floorS = new double[n];
        for (int i = 1; i < n; i++) floorS[i] = floorS[i - 1] + floors.get(i).anchor().distanceTo(floors.get(i - 1).anchor());
        // 호출 배열
        carCalls = Arrays.copyOf(carCalls, n);
        hallUp = Arrays.copyOf(hallUp, n);
        hallDown = Arrays.copyOf(hallDown, n);
        if (cur >= n) cur = Math.max(0, n - 1);
        if (target >= n) target = -1;
        computeNames();
        // 파터노스터 순환 경로 길이
        if (n > 0) {
            double hgt = floors.get(n - 1).y - floors.get(0).y + 2 * (ch + 0.5);
            paterLoop = 2 * hgt + 2 * (cw + 0.2);
        }
        displayHash++;
    }

    /** 층 이름 – 기준층 1층, 지하 B1.., 4층→F, 4층/13층 건너뛰기 */
    public void computeNames() {
        int n = floors.size();
        int lobby = 0;
        for (int i = 0; i < n; i++) if (floors.get(i).y == lobbyY) lobby = i;
        int num = 0;
        for (int i = 0; i < n; i++) {
            Floor f = floors.get(i);
            String name;
            if (i < lobby) {
                name = "B" + (lobby - i);
            } else {
                num++;
                while ((skip4 && num % 10 == 4 && num < 50) || (skip13 && num == 13)) num++;
                name = (num == 4 && fourAsF) ? "F" : String.valueOf(num);
            }
            f.name = f.customName.isEmpty() ? name : f.customName;
        }
    }

    public Vec3 posAt(double ss) {
        int n = floors.size();
        if (n == 0) return Vec3.atBottomCenterOf(worldPosition);
        if (n == 1) return floors.get(0).anchor().add(0, ss, 0);
        if (ss <= 0) {
            Vec3 a = floors.get(0).anchor(), b = floors.get(1).anchor();
            return a.add(b.subtract(a).normalize().scale(ss));
        }
        if (ss >= floorS[n - 1]) {
            Vec3 a = floors.get(n - 2).anchor(), b = floors.get(n - 1).anchor();
            return b.add(b.subtract(a).normalize().scale(ss - floorS[n - 1]));
        }
        for (int i = 0; i < n - 1; i++) {
            if (ss <= floorS[i + 1]) {
                double len = floorS[i + 1] - floorS[i];
                double t = len <= 0 ? 0 : (ss - floorS[i]) / len;
                Vec3 a = floors.get(i).anchor(), b = floors.get(i + 1).anchor();
                return a.add(b.subtract(a).scale(t));
            }
        }
        return floors.get(n - 1).anchor();
    }

    public Vec3 carPos() { return posAt(s).add(0, levelErr, 0); }

    public int nearestIdx(double ss) {
        int best = 0; double bd = Double.MAX_VALUE;
        for (int i = 0; i < floorS.length; i++) { double d = Math.abs(floorS[i] - ss); if (d < bd) { bd = d; best = i; } }
        return best;
    }

    public boolean atFloor() { return floorS.length > 0 && target < 0 && Math.abs(s - floorS[cur]) < 0.03 && Math.abs(v) < 1e-4; }

    public int idxOfY(int y) { for (int i = 0; i < floors.size(); i++) if (floors.get(i).y == y) return i; return -1; }

    private int lobbyIdx() { int i = idxOfY(lobbyY); return i < 0 ? 0 : i; }

    private int fireIdx() { int i = idxOfY(fireY); return i >= 0 ? i : lobbyIdx(); }

    private int homeIdx() { return idxOfY(homeY); }

    public AABB interior(Vec3 p, float extraDown) {
        Direction f = carFacing == null ? Direction.SOUTH : carFacing;
        double hx = f.getAxis() == Direction.Axis.Z ? cw / 2.0 : cd / 2.0;
        double hz = f.getAxis() == Direction.Axis.Z ? cd / 2.0 : cw / 2.0;
        return new AABB(p.x - hx, p.y - extraDown, p.z - hz, p.x + hx, p.y + ch, p.z + hz);
    }

    public char letter() {
        List<BlockPos> g = new ArrayList<>(group);
        if (!g.contains(worldPosition)) g.add(worldPosition);
        g.sort(Comparator.comparingLong(BlockPos::asLong));
        return (char) ('A' + Math.max(0, g.indexOf(worldPosition)));
    }

    // ================================================================= 틱
    public static void serverTick(Level level, BlockPos pos, BlockState st, ControllerBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tickServer(sl);
    }

    private void tickServer(ServerLevel lvl) {
        tick++;
        if (serial.isEmpty()) { serial = String.format("%04d-%03d", lvl.random.nextInt(10000), lvl.random.nextInt(1000)); setChanged(); }
        if (floors.isEmpty()) { removeCars(lvl); return; }
        if (floorS.length != floors.size()) rebuild();
        if (holdOpen > 0) holdOpen--;
        if (holdClose > 0) holdClose--;
        if (holdRun > 0) holdRun--;
        if (alarmArmed > 0) alarmArmed--;
        if (doorLock > 0) doorLock--;

        if (usage == Spec.Usage.PATERNOSTER) {
            tickPaternoster(lvl);
        } else {
            if (tick % 5 == 0) measureLoad(lvl);
            double before = s;
            if (freeFall || ucmActive()) tickSimulation(lvl);
            else if (manualRescue) tickPower(lvl);
            else if (fault != Spec.Fault.NONE) tickFault();
            else if (powerOff) tickPower(lvl);
            else tickNormal(lvl);
            checkLimits(lvl);
            if (levelErr != 0 && state == State.OPEN && timer < dwellTicks() - 20) levelErr = approach(levelErr, 0, 0.002); // 재착상
            travelled += Math.abs(s - before);
            updateCar(lvl);
        }
        if (tick % 2 == 0) pushDisplays(lvl);
        if (tick % 20 == 0) setChanged();
    }

    // ---------------------------------------------------------------- 정상 운전
    private void tickNormal(ServerLevel lvl) {
        switch (state) {
            case IDLE -> idle(lvl);
            case MOVING -> moving(lvl);
            case OPENING, OPEN, CLOSING -> doors(lvl);
            case STOPPED -> stopped(lvl);
        }
    }

    private boolean anyCall() {
        for (int i = 0; i < floors.size(); i++) if (carCalls[i] || hallUp[i] || hallDown[i]) return true;
        return false;
    }

    private void idle(ServerLevel lvl) {
        if (!atFloor()) { state = State.STOPPED; return; }
        if (mode == Spec.Mode.INSPECTION || stopPressed || doorLock > 0) { state = State.STOPPED; return; }
        int n = floors.size();
        switch (mode) {
            case PARKING -> {
                int p = homeIdx() >= 0 ? homeIdx() : lobbyIdx();
                if (cur != p) depart(p, true);
                return;
            }
            case FIRE1 -> {
                if (cur != fireIdx()) depart(fireIdx(), false);
                else if (doorPos <= 0) startOpening(lvl);
                return;
            }
            case EARTHQUAKE -> {
                if (!quakeOpened) { quakeOpened = true; startOpening(lvl); }
                return;
            }
            case SABBATH -> {
                if (++idleTicks < 20) return;
                int d = dir == 0 ? 1 : dir;
                int nx = cur + d;
                if (nx < 0 || nx >= n) { d = -d; nx = cur + d; }
                if (nx >= 0 && nx < n) { dir = d; depart(nx, false); }
                return;
            }
            case SHUTTLE -> {
                if (++idleTicks < 160) return;
                int lo = lobbyIdx(), hi = n - 1;
                depart(cur == hi ? lo : hi, false);
                return;
            }
            default -> {}
        }
        // 현재 층 호출
        if (carCalls[cur] || hallUp[cur] || hallDown[cur]) {
            carCalls[cur] = false;
            if (hallUp[cur] && (dir >= 0 || !hallDown[cur])) { hallUp[cur] = false; dir = 1; }
            else if (hallDown[cur]) { hallDown[cur] = false; dir = -1; }
            startOpening(lvl);
            announceDir(lvl);
            return;
        }
        int t = chooseNext();
        if (t >= 0 && t != cur) {
            liftNeedsHold = usage == Spec.Usage.WHEELCHAIR_LIFT && carCalls[t];
            depart(t, false);
            return;
        }
        dir = 0;
        idleTicks++;
        if (returnSec > 0 && idleTicks > returnSec * 20 && homeIdx() >= 0 && cur != homeIdx() && mode == Spec.Mode.NORMAL) {
            depart(homeIdx(), true);
        }
    }

    private int chooseNext() {
        boolean full = overload >= 1;
        int up = nextUp(cur, full), down = nextDown(cur, full);
        if (dir >= 0) return up >= 0 ? up : down;
        return down >= 0 ? down : up;
    }

    private int nextUp(int from, boolean full) {
        int n = floors.size();
        for (int i = from + 1; i < n; i++) if (carCalls[i] || (!full && hallUp[i])) return i;
        for (int i = n - 1; i > from; i--) if (!full && hallDown[i]) return i;
        return -1;
    }

    private int nextDown(int from, boolean full) {
        for (int i = from - 1; i >= 0; i--) if (carCalls[i] || (!full && hallDown[i])) return i;
        for (int i = 0; i < from; i++) if (!full && hallUp[i]) return i;
        return -1;
    }

    private void depart(int t, boolean park) {
        if (t < 0 || t >= floors.size()) return;
        target = t;
        parkRun = park;
        crawl = false;
        dir = floorS[t] > s ? 1 : -1;
        state = State.MOVING;
        idleTicks = 0;
        quakeOpened = false;
        trips++;
        displayHash++;
    }

    private double stopDist() { return v * v / (2 * accel()) + 0.02; }

    private void moving(ServerLevel lvl) {
        if (stopPressed || doorLock > 0) { emergencyStop(); return; }
        if (target < 0) { state = State.STOPPED; return; }
        // 운행 중 재목적지 결정(승합전자동)
        if (!parkRun && !crawl && mode == Spec.Mode.NORMAL) {
            boolean full = overload >= 1;
            if (dir > 0) {
                for (int i = 0; i < floors.size(); i++) {
                    if (floorS[i] <= s + stopDist() || floorS[i] >= floorS[target]) continue;
                    if (carCalls[i] || (!full && hallUp[i])) { target = i; break; }
                }
            } else if (dir < 0) {
                for (int i = floors.size() - 1; i >= 0; i--) {
                    if (floorS[i] >= s - stopDist() || floorS[i] <= floorS[target]) continue;
                    if (carCalls[i] || (!full && hallDown[i])) { target = i; break; }
                }
            }
        }
        // 휠체어 리프트: 버튼을 누르고 있는 동안만 운행
        if (liftNeedsHold && holdRun <= 0) { v = 0; return; }
        double vmax = crawl || ardRun ? Spec.CRAWL : rated();
        if (manualRescue) vmax = Spec.CRAWL * 0.5;
        if (drive == Spec.Drive.RACK_PINION) vmax *= 0.92 + 0.08 * Math.sin(tick * 0.9); // 거친 승차감
        if (move(vmax)) arrive(lvl);
        else if (preOpen && !parkRun && Math.abs(floorS[target] - s) < 0.125 && Math.abs(v) < 0.03 && canOpenAt(target)) {
            // 선행 개문 (착상 125mm 이내)
            if (doorPos == 0) landingTarget(lvl, target, true);
            doorPos = Math.min(1, doorPos + doorSpeed() * 0.5f);
        }
    }

    /** 다음에 정지 가능한 가장 가까운 층 */
    private int reachableAhead() {
        int n = floors.size();
        if (dir > 0) { for (int i = 0; i < n; i++) if (floorS[i] >= s + stopDist()) return i; return n - 1; }
        if (dir < 0) { for (int i = n - 1; i >= 0; i--) if (floorS[i] <= s - stopDist()) return i; return 0; }
        return nearestIdx(s);
    }

    /** 속도 프로파일로 이동. 도착하면 true */
    private boolean move(double vmax) {
        double a = accel();
        double dist = floorS[target] - s;
        double sign = Math.signum(dist);
        double desired = sign * Math.min(vmax, Math.sqrt(2 * a * Math.abs(dist)) * 0.97);
        if (Math.abs(desired) < 0.0015 && Math.abs(dist) > 0.0005) desired = sign * 0.0015;
        v = approach(v, desired, a);
        if (Math.abs(dist) <= Math.abs(v) + 0.0005) { s = floorS[target]; v = 0; return true; }
        s += v;
        return false;
    }

    private static double approach(double c, double t, double step) {
        if (c < t) return Math.min(t, c + step);
        return Math.max(t, c - step);
    }

    private void emergencyStop() {
        v = approach(v, 0, accel() * 4);
        s += v;
        if (v == 0) { state = State.STOPPED; target = -1; }
    }

    private void arrive(ServerLevel lvl) {
        cur = target;
        target = -1;
        v = 0;
        s = floorS[cur];
        levelErr = drive == Spec.Drive.HYDRAULIC ? (lvl.random.nextDouble() - 0.5) * 0.08 : 0; // 유압식 착상 오차
        playAt(lvl, carPos(), SoundEvents.IRON_TRAPDOOR_CLOSE, 0.25f, 0.6f); // 브레이크 폐쇄음
        boolean wasCrawl = crawl;
        crawl = false;
        ardRun = false;
        liftNeedsHold = false;
        if (manualRescue) { manualRescue = false; rescueArrived = true; state = State.IDLE; say(lvl, true, false, "elevmod.msg.rescue_arrived"); return; }
        if (parkRun) { parkRun = false; state = State.IDLE; if (mode == Spec.Mode.PARKING) say(lvl, true, false, "elevmod.msg.parked"); return; }
        if (mode == Spec.Mode.INSPECTION) { state = State.STOPPED; return; }
        carCalls[cur] = false;
        if (mode == Spec.Mode.NORMAL || mode == Spec.Mode.INDEPENDENT || mode == Spec.Mode.HOLD) {
            boolean moreUp = nextUp(cur, false) >= 0, moreDown = nextDown(cur, false) >= 0;
            if (dir > 0) {
                if (moreUp) hallUp[cur] = false;
                else if (hallDown[cur]) { hallDown[cur] = false; dir = -1; }
                else { hallUp[cur] = false; dir = moreDown ? -1 : 0; }
            } else if (dir < 0) {
                if (moreDown) hallDown[cur] = false;
                else if (hallUp[cur]) { hallUp[cur] = false; dir = 1; }
                else { hallDown[cur] = false; dir = moreUp ? 1 : 0; }
            }
        } else if (mode != Spec.Mode.SABBATH && mode != Spec.Mode.SHUTTLE) {
            dir = 0;
        }
        announceArrival(lvl);
        if (mode == Spec.Mode.FIRE2 || mode == Spec.Mode.FIRE2B) { state = State.IDLE; return; } // 열림 버튼을 눌러야 열림
        if (wasCrawl && !canOpenAt(cur)) { state = State.IDLE; return; }
        startOpening(lvl);
    }

    private void stopped(ServerLevel lvl) {
        v = 0;
        if (stopPressed || doorLock > 0) return;
        if (mode == Spec.Mode.INSPECTION) {
            // 점검운전: 버튼을 누르는 동안만 15m/min 으로 이동, 층 무시
            if (holdRun > 0 && doorPos <= 0) {
                double top = floorS[floorS.length - 1];
                double ns = s + holdRunDir * Spec.CRAWL;
                if (ns > -0.3 && ns < top + 0.3) { v = holdRunDir * Spec.CRAWL; s = ns; }
            }
            cur = nearestIdx(s);
            return;
        }
        if (doorPos > 0) { // 문부터 닫고 이동
            doorPos = Math.max(0f, doorPos - doorSpeed());
            if (doorPos == 0) landingSolid(lvl, cur, true);
            return;
        }
        // 재착상: 가장 가까운 층으로 저속 이동
        int t = nearestIdx(s);
        if (Math.abs(floorS[t] - s) < 0.03) { s = floorS[t]; cur = t; state = State.IDLE; return; }
        target = t;
        crawl = true;
        parkRun = false;
        dir = floorS[t] > s ? 1 : -1;
        state = State.MOVING;
    }

    // ---------------------------------------------------------------- 도어
    private float doorSpeed() { return 1f / (doorType.seconds * 20f); }

    private boolean canOpenAt(int idx) { return idx >= 0 && idx < floors.size() && !floors.get(idx).doors.isEmpty(); }

    private boolean manualClose() {
        return mode == Spec.Mode.INDEPENDENT || mode == Spec.Mode.HOLD || mode == Spec.Mode.FIRE2 || mode == Spec.Mode.FIRE2B;
    }

    private boolean keepOpen() {
        return (mode == Spec.Mode.FIRE1 && cur == fireIdx()) || mode == Spec.Mode.EARTHQUAKE;
    }

    private int dwellTicks() {
        int base = switch (mode) { case SABBATH -> 200; case SHUTTLE -> 160; default -> dwellSec * 20; };
        if (accessibleDwell) base = base * 2;
        if (usage == Spec.Usage.HOSPITAL || usage == Spec.Usage.CAR) base += 40;
        return base;
    }

    private void startOpening(ServerLevel lvl) {
        if (!canOpenAt(cur) || usage == Spec.Usage.PATERNOSTER) { state = State.IDLE; return; }
        state = State.OPENING;
        landingTarget(lvl, cur, true);
        landingSolid(lvl, cur, false);
    }

    private void doors(ServerLevel lvl) {
        boolean obstructed = tick % 2 == 0 && doorwayBlocked(lvl);
        switch (state) {
            case OPENING -> {
                if ((mode == Spec.Mode.FIRE2 || mode == Spec.Mode.FIRE2B) && holdOpen <= 0) { state = State.CLOSING; landingTarget(lvl, cur, false); return; }
                doorPos = Math.min(1f, doorPos + doorSpeed());
                if (doorPos >= 1f) { state = State.OPEN; timer = dwellTicks(); }
            }
            case OPEN -> {
                if (mode == Spec.Mode.FIRE2B && anyCall()) { // 2단: 개문발차
                    int t = chooseNext();
                    if (t >= 0) { landingTarget(lvl, cur, false); landingSolid(lvl, cur, true); depart(t, false); return; }
                }
                if (mode == Spec.Mode.FIRE1 && cur != fireIdx()) { state = State.CLOSING; nudge = true; landingTarget(lvl, cur, false); return; }
                if (mode == Spec.Mode.PARKING) { state = State.CLOSING; landingTarget(lvl, cur, false); return; }
                if (keepOpen()) return;
                if (manualClose()) {
                    if (holdClose > 0) { state = State.CLOSING; landingTarget(lvl, cur, false); }
                    return;
                }
                if (overload >= 2 || obstructed) timer = Math.max(timer, 20);
                if (overload >= 2 && tick % 20 == 0) {
                    playAt(lvl, carPos(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1f, 0.6f);
                    say(lvl, true, false, "elevmod.msg.overload");
                }
                if (--timer <= 0) { state = State.CLOSING; landingTarget(lvl, cur, false); sayVoice(lvl, true, false, "elevmod.msg.closing"); }
            }
            case CLOSING -> {
                if (manualClose() && holdClose <= 0) { reopen(lvl); return; }
                if (overload >= 2 && !nudge) { reopen(lvl); return; }
                if (obstructed && !nudge) {
                    obstruct += 20;
                    if (obstruct > 400) { nudge = true; say(lvl, true, true, "elevmod.msg.nudge"); }
                    else { reopen(lvl); if (beep) playAt(lvl, carPos(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, 1.6f); return; }
                }
                if (nudge && tick % 10 == 0) playAt(lvl, carPos(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.7f, 1.2f);
                doorPos = Math.max(0f, doorPos - doorSpeed() * (nudge ? 0.5f : 0.85f));
                if (doorPos <= 0) {
                    doorPos = 0;
                    landingSolid(lvl, cur, true);
                    state = State.IDLE;
                    obstruct = 0;
                    nudge = false;
                    accessibleDwell = false;
                    idleTicks = 0;
                }
            }
            default -> {}
        }
    }

    private void reopen(ServerLevel lvl) {
        state = State.OPENING;
        landingTarget(lvl, cur, true);
    }

    /** 세이프티슈 + 적외선 멀티빔: 문턱 영역에 무언가 있으면 반전 */
    private boolean doorwayBlocked(ServerLevel lvl) {
        if (cur < 0 || cur >= floors.size()) return false;
        Vec3 F = vf(), L = vl();
        Vec3 p = carPos();
        for (Floor.Door d : floors.get(cur).doors) {
            Vec3 c = d.plane();
            Vec3 a1 = c.add(L.scale(d.w / 2.0 - 0.1)).add(F.scale(d.rear ? -0.15 : 0.15));
            Vec3 a2 = c.subtract(L.scale(d.w / 2.0 - 0.1)).subtract(F.scale(d.rear ? -0.15 : 0.15));
            AABB box = new AABB(a1.x, p.y + 0.05, a1.z, a2.x, p.y + d.h - 0.1, a2.z);
            if (!lvl.getEntities((Entity) null, box, e -> !(e instanceof ElevatorCarEntity) && !e.isSpectator()).isEmpty()) return true;
        }
        return false;
    }

    private void landingTarget(ServerLevel lvl, int idx, boolean open) {
        if (idx < 0 || idx >= floors.size()) return;
        for (Floor.Door d : floors.get(idx).doors) {
            if (lvl.isLoaded(d.master) && lvl.getBlockEntity(d.master) instanceof LandingDoorBlockEntity be) be.command(open, doorSpeed());
        }
    }

    private void landingSolid(ServerLevel lvl, int idx, boolean solid) {
        if (idx < 0 || idx >= floors.size()) return;
        for (Floor.Door d : floors.get(idx).doors) for (BlockPos c : d.cells) LandingDoorBlock.setOpen(lvl, c, !solid);
    }

    // ---------------------------------------------------------------- 정전 / ARD
    private void tickPower(ServerLevel lvl) {
        if (state == State.MOVING && !ardRun && !manualRescue) {
            v = approach(v, 0, accel() * 4);
            s += v;
            if (v == 0) { target = -1; state = atFloorPos() ? State.IDLE : State.STOPPED; ardTimer = 0; }
            return;
        }
        if (ardRun || manualRescue) {
            if (target >= 0 && move(manualRescue ? Spec.CRAWL * 0.5 : Spec.CRAWL)) {
                cur = target; target = -1; v = 0; ardRun = false;
                if (manualRescue) { manualRescue = false; rescueArrived = true; state = State.IDLE; say(lvl, true, false, "elevmod.msg.rescue_arrived"); }
                else { ardDone = true; state = State.OPENING; landingTarget(lvl, cur, true); landingSolid(lvl, cur, false); say(lvl, true, true, "elevmod.msg.ard_arrived"); }
            }
            return;
        }
        if (state == State.OPENING) {
            doorPos = Math.min(1f, doorPos + doorSpeed());
            if (doorPos >= 1f) state = State.OPEN;
            return;
        }
        if (ard && !ardDone) {
            if (++ardTimer == 40) say(lvl, true, false, "elevmod.msg.ard_start");
            if (ardTimer > 60) {
                if (atFloorPos()) {
                    cur = nearestIdx(s); s = floorS[cur]; ardDone = true;
                    if (doorPos < 1 && canOpenAt(cur)) { state = State.OPENING; landingTarget(lvl, cur, true); landingSolid(lvl, cur, false); }
                    return;
                }
                // ALLS: 카와 균형추 중 가벼운 쪽으로 이동
                boolean carLight = loadKg < ratedKg() * 0.45;
                int t = carLight ? floorAbove() : floorBelow();
                if (t < 0) t = carLight ? floorBelow() : floorAbove();
                if (drive == Spec.Drive.HYDRAULIC) t = floorBelow() >= 0 ? floorBelow() : t; // 유압식은 하강 구출
                if (t >= 0) { target = t; dir = floorS[t] > s ? 1 : -1; ardRun = true; state = State.MOVING; }
            }
        } else if (!ard && tick % 200 == 0 && riders > 0 && !atFloorPos()) {
            say(lvl, true, false, "elevmod.msg.trapped");
        }
    }

    public boolean atFloorPos() { return Math.abs(floorS[nearestIdx(s)] - s) < 0.03; }

    private int floorAbove() { for (int i = 0; i < floorS.length; i++) if (floorS[i] > s + 0.01) return i; return -1; }

    private int floorBelow() { for (int i = floorS.length - 1; i >= 0; i--) if (floorS[i] < s - 0.01) return i; return -1; }

    // ---------------------------------------------------------------- 고장 시뮬레이션
    private boolean ucmActive() { return simBrake && (ucmEngaged || state == State.OPEN || state == State.OPENING); }

    private void tickSimulation(ServerLevel lvl) {
        double r = rated();
        if (freeFall) {
            if (fault == Spec.Fault.SAFETY_GEAR) {
                v = approach(v, 0, Spec.G * 0.6); // 비상정지장치 쐐기 감속
                s += v;
                if (v == 0) { freeFall = false; state = State.STOPPED; }
                return;
            }
            v -= Spec.G * 0.5; // 주로프 절단 → 낙하 가속(가이드 마찰 감안)
            s += v;
            if (!govTripped && -v > r * 1.15) {
                govTripped = true;
                log(lvl, "elevmod.log.governor_trip");
                say(lvl, true, false, "elevmod.msg.governor_trip");
                playAt(lvl, carPos(), SoundEvents.LEVER_CLICK, 1f, 0.5f);
            }
            if (-v > r * 1.3 + 0.004) {
                fault = Spec.Fault.SAFETY_GEAR;
                log(lvl, "elevmod.log.safety_gear");
                say(lvl, true, true, "elevmod.msg.safety_gear");
                playAt(lvl, carPos(), SoundEvents.ANVIL_LAND, 1f, 0.5f);
            }
            return;
        }
        // 브레이크 고장 → 개문 상태 이상 이동(UCM)
        if (!ucmEngaged) { ucmEngaged = true; ucmStart = s; }
        if (fault == Spec.Fault.ROPE_BRAKE) {
            v = approach(v, 0, Spec.G * 0.8);
            s += v;
            if (v == 0) { simBrake = false; ucmEngaged = false; state = State.STOPPED; }
            return;
        }
        boolean carLight = loadKg < ratedKg() * 0.45;
        if (drive != Spec.Drive.HYDRAULIC && drive != Spec.Drive.DRUM) v += (carLight ? 1 : -1) * 0.0012;
        else v -= 0.0008;
        s += v;
        landingTarget(lvl, cur, false);
        landingSolid(lvl, cur, true);
        if (ropeBrake && Math.abs(s - ucmStart) > 0.15) {
            fault = Spec.Fault.ROPE_BRAKE;
            log(lvl, "elevmod.log.rope_brake");
            say(lvl, true, true, "elevmod.msg.rope_brake");
            playAt(lvl, carPos(), SoundEvents.CHAIN_PLACE, 1f, 0.5f);
        }
    }

    private void checkLimits(ServerLevel lvl) {
        if (floorS.length == 0) return;
        double top = floorS[floorS.length - 1];
        if (s > top + 0.45 && fault != Spec.Fault.FINAL_LIMIT) {
            s = top + 0.45; v = 0; freeFall = false; simBrake = false; ucmEngaged = false;
            fault = Spec.Fault.FINAL_LIMIT; state = State.STOPPED; target = -1;
            log(lvl, "elevmod.log.final_limit");
            say(lvl, true, false, "elevmod.msg.final_limit");
            playAt(lvl, carPos(), SoundEvents.ANVIL_LAND, 0.7f, 0.8f);
        } else if (s < -0.45 && fault != Spec.Fault.BUFFER) {
            s = -0.45; v = 0; freeFall = false; simBrake = false; ucmEngaged = false;
            fault = Spec.Fault.BUFFER; state = State.STOPPED; target = -1;
            log(lvl, "elevmod.log.buffer");
            say(lvl, true, false, "elevmod.msg.buffer");
            playAt(lvl, carPos(), SoundEvents.ANVIL_LAND, 1f, 0.6f);
        }
    }

    private void tickFault() {
        if (v != 0) { v = approach(v, 0, accel() * 4); s += v; }
        state = State.STOPPED;
        target = -1;
    }

    public void resetFault(ServerLevel lvl) {
        if (fault == Spec.Fault.NONE && !freeFall && !simBrake) return;
        fault = Spec.Fault.NONE;
        freeFall = false; simBrake = false; simRope = false; govTripped = false; ucmEngaged = false;
        v = 0;
        state = State.STOPPED;
        log(lvl, "elevmod.log.reset");
        displayHash++;
    }

    // ---------------------------------------------------------------- 파터노스터
    private void tickPaternoster(ServerLevel lvl) {
        int n = floors.size();
        int cabins = Math.max(2, (int) (paterLoop / (ch + 0.5)));
        while (carIds.size() > cabins) {
            Entity e = lvl.getEntity(carIds.remove(carIds.size() - 1));
            if (e != null) e.discard();
        }
        while (carIds.size() < cabins) carIds.add(UUID.randomUUID());
        boolean run = !powerOff && fault == Spec.Fault.NONE && mode != Spec.Mode.EARTHQUAKE && mode != Spec.Mode.PARKING && !stopPressed;
        double prevS = s;
        if (run) s = (s + rated()) % paterLoop;
        v = run ? rated() : 0;
        travelled += Math.abs(s - prevS) < 1 ? Math.abs(s - prevS) : 0;
        // 승강장 문은 항상 개방
        if (tick % 40 == 0) for (int i = 0; i < n; i++) { landingTarget(lvl, i, true); landingSolid(lvl, i, false); }
        double spacing = paterLoop / cabins;
        for (int i = 0; i < cabins; i++) {
            double ps = (s + i * spacing) % paterLoop;
            Vec3 p = paterPos(ps);
            Vec3 p2 = paterPos((ps + (run ? rated() : 0)) % paterLoop);
            ElevatorCarEntity car = car(lvl, i, p);
            if (car != null) car.serverUpdate(this, p, run ? p2.subtract(p) : Vec3.ZERO, 0f, stateTag(i), i);
        }
    }

    private Vec3 paterPos(double ps) {
        Floor b = floors.get(0), t = floors.get(floors.size() - 1);
        double ext = ch + 0.5;
        double yb = b.y - ext, yt = t.y + ext;
        double hgt = yt - yb;
        double cross = cw + 0.2;
        Vec3 base = new Vec3(b.ax, 0, b.az);
        Vec3 L = vl();
        if (ps < hgt) return base.add(0, yb + ps, 0);
        ps -= hgt;
        if (ps < cross) return base.add(L.scale(ps)).add(0, yt, 0);
        ps -= cross;
        if (ps < hgt) return base.add(L.scale(cross)).add(0, yt - ps, 0);
        ps -= hgt;
        return base.add(L.scale(cross - Math.min(cross, ps))).add(0, yb, 0);
    }

    // ---------------------------------------------------------------- 부하(적재하중)
    private void measureLoad(ServerLevel lvl) {
        AABB box = interior(carPos(), 0.3f);
        int kg = 0, people = 0, players = 0;
        for (Entity e : lvl.getEntities((Entity) null, box, e -> !(e instanceof ElevatorCarEntity))) {
            if (e instanceof Player p) { if (p.isSpectator()) continue; kg += 75; people++; players++; }
            else if (e instanceof LivingEntity le) { kg += Math.max(5, (int) (le.getBbWidth() * le.getBbWidth() * le.getBbHeight() * 100)); people++; }
            else if (e instanceof ItemEntity ie) kg += (int) Math.ceil(ie.getItem().getCount() * (ie.getItem().getItem() instanceof BlockItem ? 2.0 : 0.5));
            else if (e instanceof AbstractMinecart) kg += 200;
            else if (e instanceof Boat) kg += 100;
            else kg += 20;
        }
        loadKg = kg;
        riders = people;
        int rated = ratedKg();
        int ov = kg > rated * 1.1 ? 2 : kg >= rated ? 1 : 0;
        if (ov != overload) { overload = ov; displayHash++; }
        if (usage == Spec.Usage.FREIGHT && players > 1 && tick % 200 == 0) say(lvl, true, false, "elevmod.msg.freight_one");
        if (usage == Spec.Usage.DUMBWAITER && players > 0 && tick % 100 == 0) say(lvl, true, false, "elevmod.msg.dumbwaiter");
    }

    // ---------------------------------------------------------------- 카 엔티티
    @Nullable
    private ElevatorCarEntity car(ServerLevel lvl, int i, Vec3 pos) {
        while (carIds.size() <= i) carIds.add(UUID.randomUUID());
        Entity e = lvl.getEntity(carIds.get(i));
        if (e instanceof ElevatorCarEntity c && c.isAlive()) return c;
        if (!lvl.isLoaded(BlockPos.containing(pos))) return null;
        ElevatorCarEntity c = new ElevatorCarEntity(ModRegistry.CAR.get(), lvl);
        c.setController(worldPosition);
        c.setPos(pos.x, pos.y, pos.z);
        lvl.addFreshEntity(c);
        carIds.set(i, c.getUUID());
        setChanged();
        return c;
    }

    private void updateCar(ServerLevel lvl) {
        while (carIds.size() > 1) {
            Entity e = lvl.getEntity(carIds.remove(carIds.size() - 1));
            if (e != null) e.discard();
        }
        Vec3 p = carPos();
        ElevatorCarEntity c = car(lvl, 0, p);
        if (c == null) return;
        Vec3 vel = v == 0 ? Vec3.ZERO : posAt(s + v).subtract(posAt(s));
        c.serverUpdate(this, p, vel, doorPos, stateTag(-1), -1);
    }

    public void removeCars(ServerLevel lvl) {
        for (UUID id : carIds) { Entity e = lvl.getEntity(id); if (e != null) e.discard(); }
        carIds.clear();
    }

    /** 카(엔티티)로 전달하는 표시/렌더 정보 */
    public CompoundTag stateTag(int cabin) {
        CompoundTag t = new CompoundTag();
        ListTag names = new ListTag();
        for (Floor f : floors) names.add(StringTag.valueOf(f.name));
        t.put("n", names);
        byte[] calls = new byte[floors.size()];
        for (int i = 0; i < calls.length; i++) calls[i] = (byte) ((carCalls[i] ? 1 : 0) | (hallUp[i] ? 2 : 0) | (hallDown[i] ? 4 : 0));
        t.putByteArray("c", calls);
        t.putInt("cur", floors.isEmpty() ? 0 : nearestIdx(s));
        t.putInt("dir", dir);
        t.putString("st", statusKey());
        t.putInt("mode", mode.ordinal());
        t.putInt("usage", usage.ordinal());
        t.putInt("drive", drive.ordinal());
        t.putInt("door", doorType.ordinal());
        t.putInt("theme", theme.ordinal());
        t.putFloat("fw", frontW); t.putFloat("fh", frontH); t.putFloat("fo", frontOff);
        t.putFloat("rw", rearW); t.putFloat("rh", rearH); t.putFloat("ro", rearOff);
        boolean atF = !floors.isEmpty() && Math.abs(floorS[nearestIdx(s)] - s) < 0.2;
        Floor here = floors.isEmpty() ? null : floors.get(nearestIdx(s));
        t.putBoolean("fo2", atF && here != null && here.hasFront());
        t.putBoolean("ro2", atF && here != null && here.hasRear());
        if (!floors.isEmpty()) {
            t.putFloat("bot", (float) floors.get(0).y);
            t.putFloat("top", (float) floors.get(floors.size() - 1).y);
        }
        int my = Integer.MIN_VALUE;
        for (BlockPos m : machines) my = Math.max(my, m.getY());
        t.putInt("my", my);
        t.putString("ser", serial);
        t.putString("let", String.valueOf(letter()));
        t.putInt("kg", ratedKg());
        t.putInt("load", loadKg);
        t.putInt("spd", effectiveMpm());
        t.putInt("ov", overload);
        boolean lights = !powerOff && fault == Spec.Fault.NONE && !(mode == Spec.Mode.PARKING && state == State.IDLE && doorPos == 0)
                && !(riders == 0 && state == State.IDLE && idleTicks > 1200);
        t.putBoolean("light", lights);
        t.putBoolean("emg", powerOff || fault != Spec.Fault.NONE);
        t.putBoolean("fan", riders > 0 || state != State.IDLE);
        t.putBoolean("air", airClean);
        t.putBoolean("ac", aircon);
        t.putBoolean("win", window);
        t.putBoolean("acc", accessibleCop && usage.passengerCar());
        t.putBoolean("stop", stopSwitch);
        t.putBoolean("dd", destination);
        t.putBoolean("sp", stopPressed);
        t.putBoolean("open", state == State.OPEN || state == State.OPENING);
        t.putBoolean("glass", usage == Spec.Usage.OBSERVATION);
        t.putInt("cab", cabin);
        return t;
    }

    public String statusKey() {
        if (fault != Spec.Fault.NONE || freeFall) return "fault";
        if (powerOff) return ard ? "power_ard" : "power";
        return switch (mode) {
            case EARTHQUAKE -> "quake";
            case FIRE1, FIRE2, FIRE2B -> "fire";
            case INSPECTION -> "insp";
            case PARKING -> "park";
            case HOLD -> "hold";
            case INDEPENDENT -> "indep";
            case SABBATH -> "sabbath";
            case SHUTTLE -> "shuttle";
            default -> stopPressed ? "stop" : overload >= 2 ? "over" : overload == 1 ? "full" : "";
        };
    }

    // ---------------------------------------------------------------- 승강장 표시 장치 / 기계실
    private void pushDisplays(ServerLevel lvl) {
        if (floors.isEmpty()) return;
        int idx = nearestIdx(s);
        String text = (powerOff && !ard) || (fault != Spec.Fault.NONE && drive == Spec.Drive.RACK_PINION) ? "--" : floors.get(idx).name;
        boolean doorsOpen = state == State.OPEN || state == State.OPENING;
        int h = java.util.Objects.hash(text, dir, statusKey(), state, cur, Arrays.hashCode(hallUp), Arrays.hashCode(hallDown), displayHash, v == 0, mode, (int) (v * 200));
        if (h == lastPush && tick % 40 != 0) return;
        lastPush = h;
        List<ControllerBlockEntity> grp = members(lvl);
        for (BlockPos p : devices) {
            if (!lvl.isLoaded(p)) continue;
            if (!(lvl.getBlockEntity(p) instanceof DeviceBlockEntity d)) continue;
            int fi = idxOfY(d.floorY);
            boolean up = false, down = false, hasUp = false, hasDown = false;
            for (ControllerBlockEntity c : grp) {
                int ci = c.idxOfY(d.floorY);
                if (ci < 0) continue;
                if (ci < c.floors.size() - 1) hasUp = true;
                if (ci > 0) hasDown = true;
                up |= c.hallUp.length > ci && c.hallUp[ci];
                down |= c.hallDown.length > ci && c.hallDown[ci];
            }
            boolean here = fi >= 0 && fi == cur && doorsOpen;
            d.applyDisplay(text, dir, statusKey(), v != 0, here && dir > 0, here && dir < 0, here && dir == 0,
                    up, down, hasUp, hasDown, String.valueOf(letter()), usage == Spec.Usage.DUMBWAITER, floors.get(Math.max(0, fi)).name, namesList());
        }
        float speed = (float) v;
        for (BlockPos m : machines) {
            if (lvl.isLoaded(m) && lvl.getBlockEntity(m) instanceof MachineBlockEntity me) me.setSpeed(speed, drive.ordinal());
        }
    }

    private List<String> namesList() { List<String> l = new ArrayList<>(); for (Floor f : floors) l.add(f.name); return l; }

    public List<ControllerBlockEntity> members(Level lvl) {
        List<ControllerBlockEntity> out = new ArrayList<>();
        if (group.isEmpty()) { out.add(this); return out; }
        for (BlockPos p : group) {
            if (p.equals(worldPosition)) { out.add(this); continue; }
            if (lvl.isLoaded(p) && lvl.getBlockEntity(p) instanceof ControllerBlockEntity c) out.add(c);
        }
        if (!out.contains(this)) out.add(this);
        return out;
    }

    // ================================================================= 호출
    public boolean acceptsHall() {
        return mode == Spec.Mode.NORMAL && !powerOff && fault == Spec.Fault.NONE && !stopPressed
                && usage != Spec.Usage.PATERNOSTER;
    }

    /** 군관리 할당 비용 */
    private double cost(int idx, boolean up) {
        double pos = floorS.length == 0 ? 0 : nearestIdx(s);
        int n = floors.size();
        double c;
        if (dir == 0) c = Math.abs(pos - idx);
        else {
            boolean ahead = dir > 0 ? idx >= pos : idx <= pos;
            boolean same = (dir > 0) == up;
            if (ahead && same) c = Math.abs(pos - idx);
            else { int end = dir > 0 ? n - 1 : 0; c = Math.abs(end - pos) + Math.abs(end - idx); }
        }
        int pending = 0;
        for (int i = 0; i < n; i++) if (carCalls[i] || hallUp[i] || hallDown[i]) pending++;
        c += pending * 1.5;
        if (overload >= 1) c += 100;
        if (state == State.OPEN || state == State.OPENING) c += 1;
        return c;
    }

    /** 승강장 호출 버튼 (군관리: 가장 빨리 올 수 있는 카 1대만 응답) */
    public void hallCall(ServerLevel lvl, int floorY, boolean up, @Nullable Player p) {
        List<ControllerBlockEntity> grp = members(lvl);
        for (ControllerBlockEntity c : grp) {
            int i = c.idxOfY(floorY);
            if (i >= 0 && (up ? c.hallUp[i] : c.hallDown[i])) return;
        }
        ControllerBlockEntity best = null;
        double bc = Double.MAX_VALUE;
        for (ControllerBlockEntity c : grp) {
            int i = c.idxOfY(floorY);
            if (i < 0 || !c.acceptsHall()) continue;
            double cc = c.cost(i, up);
            if (cc < bc) { bc = cc; best = c; }
        }
        if (best == null) {
            if (p != null) p.displayClientMessage(Component.translatable("elevmod.msg.no_service").withStyle(ChatFormatting.RED), true);
            return;
        }
        int i = best.idxOfY(floorY);
        if (beep && p != null) lvl.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.4f, 2f);
        boolean doorsOpenHere = best.cur == i && (best.state == State.OPEN || best.state == State.OPENING) && (best.dir == 0 || (best.dir > 0) == up);
        if (doorsOpenHere) { best.timer = Math.max(best.timer, best.dwellTicks()); if (best.dir == 0) best.dir = up ? 1 : -1; return; }
        if (best.cur == i && best.state == State.CLOSING && (best.dir == 0 || (best.dir > 0) == up)) { best.reopen(lvl); if (best.dir == 0) best.dir = up ? 1 : -1; return; }
        if (up) best.hallUp[i] = true; else best.hallDown[i] = true;
        best.displayHash++;
    }

    public void hallCallDefault(ServerLevel lvl, int floorY) {
        int i = idxOfY(floorY);
        if (i < 0) return;
        hallCall(lvl, floorY, i == 0, null);
    }

    private void registerCarCall(ServerLevel lvl, int idx, Player p) {
        if (idx < 0 || idx >= floors.size()) return;
        boolean ok = switch (mode) {
            case NORMAL, INDEPENDENT, HOLD, FIRE2, FIRE2B, INSPECTION -> true;
            default -> false;
        };
        if (!ok || powerOff || fault != Spec.Fault.NONE) {
            p.displayClientMessage(Component.translatable("elevmod.msg.button_disabled").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (destination && mode == Spec.Mode.NORMAL) {
            p.displayClientMessage(Component.translatable("elevmod.msg.dd_mode"), true);
            return;
        }
        if (mode == Spec.Mode.INSPECTION) return;
        if (mode == Spec.Mode.FIRE2 || mode == Spec.Mode.FIRE2B) Arrays.fill(carCalls, false); // 1회 1층
        if (idx == cur && (state != State.MOVING)) {
            if (state == State.CLOSING || state == State.IDLE) { if (canOpenAt(cur)) { state = State.OPENING; landingTarget(lvl, cur, true); landingSolid(lvl, cur, false); } }
            return;
        }
        carCalls[idx] = true;
        if (beep) lvl.playSound(null, BlockPos.containing(carPos()), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.4f, 2f);
        if (voice == Spec.Voice.VOICE) p.displayClientMessage(Component.translatable("elevmod.voice.floor_button", floors.get(idx).name), true);
        displayHash++;
    }

    /** 운전반 조작 처리 (서버) */
    public void carAction(ServerPlayer p, int action, int arg) {
        if (!(level instanceof ServerLevel lvl)) return;
        boolean opKey = p.getInventory().contains(new net.minecraft.world.item.ItemStack(ModRegistry.OPERATOR_KEY.get())) || p.isCreative();
        boolean fireKey = p.getInventory().contains(new net.minecraft.world.item.ItemStack(ModRegistry.FIRE_KEY.get())) || p.isCreative();
        switch (action) {
            case CarAction.FLOOR -> registerCarCall(lvl, arg, p);
            case CarAction.ACCESSIBLE_FLOOR -> { accessibleDwell = true; registerCarCall(lvl, arg, p); }
            case CarAction.CANCEL -> {
                if (arg >= 0 && arg < carCalls.length && carCalls[arg]) {
                    carCalls[arg] = false;
                    if (target == arg && state == State.MOVING) { int t = chooseNext(); target = t >= 0 ? t : reachableAhead(); }
                    p.displayClientMessage(Component.translatable("elevmod.msg.cancel", floors.get(arg).name), true);
                }
            }
            case CarAction.OPEN, CarAction.OPEN_HOLD -> {
                holdOpen = 8;
                if (powerOff || fault != Spec.Fault.NONE) return;
                if (state == State.CLOSING) reopen(lvl);
                else if (state == State.OPEN) timer = Math.max(timer, dwellTicks());
                else if (state == State.IDLE && atFloor() && canOpenAt(cur)) { state = State.OPENING; landingTarget(lvl, cur, true); landingSolid(lvl, cur, false); }
            }
            case CarAction.CLOSE, CarAction.CLOSE_HOLD -> {
                holdClose = 8;
                if (state == State.OPEN && !keepOpen() && overload < 2 && mode != Spec.Mode.FIRE1) {
                    state = State.CLOSING; landingTarget(lvl, cur, false);
                }
            }
            case CarAction.ALARM -> {
                if (alarmArmed <= 0) {
                    alarmArmed = 100;
                    p.displayClientMessage(Component.translatable("elevmod.msg.alarm_confirm").withStyle(ChatFormatting.YELLOW), true);
                } else {
                    alarmArmed = 0;
                    alarm(lvl, p);
                }
            }
            case CarAction.KEY_INDEPENDENT -> {
                if (!opKey) { p.displayClientMessage(Component.translatable("elevmod.msg.need_operator_key").withStyle(ChatFormatting.RED), true); return; }
                setMode(lvl, mode == Spec.Mode.INDEPENDENT ? Spec.Mode.NORMAL : Spec.Mode.INDEPENDENT);
            }
            case CarAction.HOLD -> {
                if (usage != Spec.Usage.FREIGHT && usage != Spec.Usage.PASSENGER_FREIGHT && usage != Spec.Usage.CAR) return;
                setMode(lvl, mode == Spec.Mode.HOLD ? Spec.Mode.NORMAL : Spec.Mode.HOLD);
            }
            case CarAction.KEY_INSPECTION -> {
                if (!opKey) { p.displayClientMessage(Component.translatable("elevmod.msg.need_operator_key").withStyle(ChatFormatting.RED), true); return; }
                setMode(lvl, mode == Spec.Mode.INSPECTION ? Spec.Mode.NORMAL : Spec.Mode.INSPECTION);
            }
            case CarAction.RUN_UP, CarAction.RUN_DOWN -> {
                holdRun = 6;
                holdRunDir = action == CarAction.RUN_UP ? 1 : -1;
                if (usage == Spec.Usage.WHEELCHAIR_LIFT && mode == Spec.Mode.NORMAL && state == State.IDLE) {
                    int t = holdRunDir > 0 ? Math.min(cur + 1, floors.size() - 1) : Math.max(cur - 1, 0);
                    if (t != cur) { carCalls[t] = true; }
                }
            }
            case CarAction.KEY_FIRE1 -> {
                if (!fireKey) { p.displayClientMessage(Component.translatable("elevmod.msg.need_fire_key").withStyle(ChatFormatting.RED), true); return; }
                if (mode == Spec.Mode.FIRE1) setMode(lvl, Spec.Mode.FIRE2);
                else if (mode == Spec.Mode.FIRE2 || mode == Spec.Mode.FIRE2B) setMode(lvl, Spec.Mode.FIRE1);
                else p.displayClientMessage(Component.translatable("elevmod.msg.fire_need_phase1"), true);
            }
            case CarAction.KEY_FIRE2 -> {
                if (!fireKey) { p.displayClientMessage(Component.translatable("elevmod.msg.need_fire_key").withStyle(ChatFormatting.RED), true); return; }
                if (mode == Spec.Mode.FIRE2) setMode(lvl, Spec.Mode.FIRE2B);
                else if (mode == Spec.Mode.FIRE2B) setMode(lvl, Spec.Mode.FIRE2);
                else p.displayClientMessage(Component.translatable("elevmod.msg.fire_need_phase1"), true);
            }
            case CarAction.STOP_SWITCH -> {
                if (!stopSwitch) return;
                stopPressed = !stopPressed;
                if (stopPressed) { log(lvl, "elevmod.log.stop_switch"); say(lvl, true, false, "elevmod.msg.stop_switch"); }
                displayHash++;
            }
            default -> {}
        }
    }

    private void alarm(ServerLevel lvl, ServerPlayer p) {
        Component msg = Component.translatable("elevmod.msg.alarm_broadcast", serial, String.valueOf(letter()),
                floors.isEmpty() ? "?" : floors.get(nearestIdx(s)).name, p.getName()).withStyle(ChatFormatting.RED);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        for (ServerPlayer sp : lvl.players()) {
            if (sp.position().distanceTo(c) < 160 || sp.hasPermissions(2)) sp.sendSystemMessage(msg);
        }
        lvl.playSound(null, BlockPos.containing(carPos()), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1f);
        lvl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1f);
        p.displayClientMessage(Component.translatable("elevmod.msg.alarm_sent").withStyle(ChatFormatting.GREEN), false);
        log(lvl, "elevmod.log.alarm");
    }

    public void setMode(ServerLevel lvl, Spec.Mode m) {
        if (mode == m) return;
        Spec.Mode old = mode;
        mode = m;
        displayHash++;
        quakeOpened = false;
        if (m != Spec.Mode.NORMAL && m != Spec.Mode.FIRE2 && m != Spec.Mode.FIRE2B) redistributeHall(lvl);
        switch (m) {
            case FIRE1 -> {
                Arrays.fill(carCalls, false);
                if (old != Spec.Mode.FIRE2 && old != Spec.Mode.FIRE2B) say(lvl, true, true, "elevmod.msg.fire");
                if (state == State.MOVING && target >= 0 && !floors.isEmpty()) {
                    int f = fireIdx();
                    boolean toward = (floorS[f] - s) * dir > stopDist();
                    if (toward) { target = f; parkRun = false; } else { target = reachableAhead(); parkRun = true; }
                }
            }
            case EARTHQUAKE -> {
                Arrays.fill(carCalls, false);
                say(lvl, true, true, "elevmod.msg.quake");
                if (state == State.MOVING && target >= 0) { target = reachableAhead(); parkRun = false; }
            }
            case PARKING, SABBATH, SHUTTLE -> Arrays.fill(carCalls, false);
            case INSPECTION -> { Arrays.fill(carCalls, false); if (state == State.MOVING) { target = reachableAhead(); parkRun = true; } }
            case NORMAL -> { if (old == Spec.Mode.INSPECTION) state = State.STOPPED; }
            default -> {}
        }
        say(lvl, true, false, m.key());
    }

    private void redistributeHall(ServerLevel lvl) {
        for (int i = 0; i < floors.size(); i++) {
            int y = floors.get(i).y;
            if (hallUp[i]) { hallUp[i] = false; hallCall(lvl, y, true, null); }
            if (hallDown[i]) { hallDown[i] = false; hallCall(lvl, y, false, null); }
        }
    }

    /** 소방운전 스위치 / 화재 감지 */
    public void fireService(ServerLevel lvl, boolean on, int floorY) {
        for (ControllerBlockEntity c : members(lvl)) {
            if (on) {
                if (c.idxOfY(floorY) >= 0) c.fireY = floorY;
                if (!c.mode.fire()) c.setMode(lvl, Spec.Mode.FIRE1);
            } else if (c.mode.fire()) c.setMode(lvl, Spec.Mode.NORMAL);
        }
    }

    public void setPower(ServerLevel lvl, boolean off) {
        if (powerOff == off) return;
        powerOff = off;
        displayHash++;
        if (off) {
            ardTimer = 0; ardDone = false; ardRun = false; rescueArrived = false;
            log(lvl, "elevmod.log.power_off");
            say(lvl, true, false, "elevmod.msg.power_off");
        } else {
            log(lvl, "elevmod.log.power_on");
            ardRun = false; manualRescue = false;
            if (state == State.OPEN) timer = 40;
            else if (state != State.OPENING) state = atFloorPos() ? State.IDLE : State.STOPPED;
            if (state == State.IDLE) { cur = nearestIdx(s); s = floorS[cur]; target = -1; if (doorPos > 0) { state = State.CLOSING; landingTarget(lvl, cur, false); } }
        }
    }

    /** 기계실 수동 구출 – 브레이크 개방, 무거운 쪽으로 저속 이동 */
    public void manualRescue(ServerLevel lvl, Player p) {
        if (!powerOff && fault == Spec.Fault.NONE) { p.displayClientMessage(Component.translatable("elevmod.msg.rescue_not_needed"), true); return; }
        if (atFloorPos()) { p.displayClientMessage(Component.translatable("elevmod.msg.rescue_at_floor"), true); rescueArrived = true; return; }
        if (drive == Spec.Drive.HYDRAULIC || drive == Spec.Drive.DRUM) {
            target = floorBelow();
        } else {
            boolean carHeavy = loadKg >= ratedKg() * 0.45;
            target = carHeavy ? floorBelow() : floorAbove();
            if (target < 0) target = carHeavy ? floorAbove() : floorBelow();
        }
        if (target < 0) return;
        freeFall = false; simBrake = false; ucmEngaged = false;
        manualRescue = true;
        state = State.MOVING;
        dir = floorS[target] > s ? 1 : -1;
        p.displayClientMessage(Component.translatable("elevmod.msg.rescue_start"), true);
        log(lvl, "elevmod.log.manual_rescue");
    }

    public boolean rescueDoorAllowed() { return rescueArrived || (powerOff && atFloorPos()) || fault != Spec.Fault.NONE && atFloorPos(); }

    /** 삼각키로 승강장 문을 연 경우 – 안전회로 개방 */
    public void doorLockOpened(ServerLevel lvl, int floorY, int ticks) {
        int i = idxOfY(floorY);
        boolean carHere = i >= 0 && Math.abs(floorS[i] - s) < 0.6;
        if (!carHere) {
            doorLock = ticks;
            if (state == State.MOVING) log(lvl, "elevmod.log.door_lock");
        } else if (i >= 0 && (powerOff || fault != Spec.Fault.NONE || rescueArrived)) {
            // 구출: 카 문도 개방
            doorPos = 1f;
            state = State.OPEN;
            cur = i;
            say(lvl, true, false, "elevmod.msg.rescued");
        }
    }

    public void redstone(ServerLevel lvl, boolean on) {
        if (on == rsLast) return;
        rsLast = on;
        switch (rsFn) {
            case FIRE -> fireService(lvl, on, fireY == Integer.MIN_VALUE ? (floors.isEmpty() ? 0 : floors.get(lobbyIdx()).y) : fireY);
            case EARTHQUAKE -> { if (on) setMode(lvl, Spec.Mode.EARTHQUAKE); else if (mode == Spec.Mode.EARTHQUAKE) setMode(lvl, Spec.Mode.NORMAL); }
            case POWER -> setPower(lvl, on);
            case PARKING -> { if (on) setMode(lvl, Spec.Mode.PARKING); else if (mode == Spec.Mode.PARKING) setMode(lvl, Spec.Mode.NORMAL); }
            default -> {}
        }
    }

    public void destinationCall(ServerLevel lvl, int fromY, int toY, Player p) {
        List<ControllerBlockEntity> grp = members(lvl);
        boolean dd = false;
        for (ControllerBlockEntity c : grp) dd |= c.destination;
        if (!dd) {
            // 외부 운전반(덤웨이터 등): 해당 층으로 보내기
            int i = idxOfY(toY);
            if (i < 0) return;
            if (i == cur && state != State.MOVING) { if (state == State.IDLE) startOpening(lvl); return; }
            carCalls[i] = true;
            p.displayClientMessage(Component.translatable("elevmod.msg.send_to", floors.get(i).name), true);
            if (beep) lvl.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.4f, 2f);
            return;
        }
        if (fromY == toY) return;
        ControllerBlockEntity best = null;
        double bc = Double.MAX_VALUE;
        boolean up = toY > fromY;
        for (ControllerBlockEntity c : grp) {
            int a = c.idxOfY(fromY), b = c.idxOfY(toY);
            if (a < 0 || b < 0 || !c.acceptsHall()) continue;
            double cc = c.cost(a, up);
            if (cc < bc) { bc = cc; best = c; }
        }
        if (best == null) { p.displayClientMessage(Component.translatable("elevmod.msg.no_service").withStyle(ChatFormatting.RED), true); return; }
        int a = best.idxOfY(fromY), b = best.idxOfY(toY);
        if (up) best.hallUp[a] = true; else best.hallDown[a] = true;
        best.carCalls[b] = true;
        best.displayHash++;
        p.displayClientMessage(Component.translatable("elevmod.msg.dd_assign", String.valueOf(best.letter()), best.floors.get(b).name).withStyle(ChatFormatting.AQUA), false);
        if (beep) lvl.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.5f, 1.8f);
    }

    // ================================================================= 안내방송
    private List<ServerPlayer> ridersList(ServerLevel lvl) {
        AABB box = interior(carPos(), 0.4f).inflate(0.2);
        return lvl.getEntitiesOfClass(ServerPlayer.class, box, e -> !e.isSpectator());
    }

    private void say(ServerLevel lvl, boolean riders, boolean landing, String key, Object... args) {
        Component c = Component.translatable(key, args);
        List<ServerPlayer> in = ridersList(lvl);
        if (riders) for (ServerPlayer p : in) p.displayClientMessage(c, true);
        if (landing && cur < floors.size()) {
            Vec3 a = floors.get(cur).anchor();
            for (ServerPlayer p : lvl.players()) if (p.position().distanceTo(a) < 6 && !in.contains(p)) p.displayClientMessage(c, true);
        }
    }

    private void sayVoice(ServerLevel lvl, boolean riders, boolean landing, String key, Object... args) {
        if (voice == Spec.Voice.VOICE) say(lvl, riders, landing, key, args);
    }

    private void announceArrival(ServerLevel lvl) {
        Vec3 p = carPos();
        if (voice == Spec.Voice.SILENT) return;
        if (voice == Spec.Voice.CHIME) {
            playAt(lvl, p, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.2f); // 구형 '땡'
            return;
        }
        // 딩동 + "n층입니다"
        playAt(lvl, p, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9f, 1.4f);
        playAt(lvl, p.add(0, 0.5, 0), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9f, 1.05f);
        String name = floors.get(cur).name;
        String key = dir > 0 ? "elevmod.voice.arrive_up" : dir < 0 ? "elevmod.voice.arrive_down" : "elevmod.voice.arrive";
        say(lvl, true, true, key, name);
    }

    private void announceDir(ServerLevel lvl) {
        if (voice != Spec.Voice.VOICE) return;
        if (dir > 0) say(lvl, true, true, "elevmod.voice.going_up");
        else if (dir < 0) say(lvl, true, true, "elevmod.voice.going_down");
    }

    private void playAt(ServerLevel lvl, Vec3 p, SoundEvent ev, float vol, float pitch) {
        lvl.playSound(null, p.x, p.y + 1.5, p.z, ev, SoundSource.BLOCKS, vol, pitch);
    }

    private void log(ServerLevel lvl, String key) {
        long day = lvl.getDayTime() / 24000L;
        faultLog.add(day + "|" + key);
        while (faultLog.size() > 8) faultLog.remove(0);
        displayHash++;
        setChanged();
    }

    // ================================================================= 링크
    public boolean linkDoor(ServerLevel lvl, BlockPos clicked, Player p) {
        BlockState st = lvl.getBlockState(clicked);
        if (!(st.getBlock() instanceof LandingDoorBlock)) return false;
        Direction f = st.getValue(LandingDoorBlock.FACING);
        if (carFacing == null) carFacing = f;
        boolean rear;
        if (f == carFacing) rear = false;
        else if (f == carFacing.getOpposite()) rear = true;
        else { p.displayClientMessage(Component.translatable("elevmod.msg.door_side").withStyle(ChatFormatting.RED), false); return true; }
        // 같은 평면의 연속된 문 칸 찾기
        Direction a = f.getCounterClockWise();
        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> queue = new ArrayList<>();
        queue.add(clicked);
        while (!queue.isEmpty() && cells.size() < 16) {
            BlockPos c = queue.remove(0);
            if (cells.contains(c)) continue;
            BlockState cs = lvl.getBlockState(c);
            if (!(cs.getBlock() instanceof LandingDoorBlock) || cs.getValue(LandingDoorBlock.FACING) != f) continue;
            cells.add(c);
            queue.add(c.relative(a)); queue.add(c.relative(a.getOpposite())); queue.add(c.above()); queue.add(c.below());
        }
        // 이미 등록된 문이면 제거 후 재등록
        for (BlockPos c : cells) removeDoorCell(c, false);
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos c : cells) {
            int col = c.getX() * a.getStepX() + c.getZ() * a.getStepZ();
            minCol = Math.min(minCol, col); maxCol = Math.max(maxCol, col);
            minY = Math.min(minY, c.getY()); maxY = Math.max(maxY, c.getY());
        }
        Floor.Door d = new Floor.Door();
        d.facing = f;
        d.rear = rear;
        d.w = maxCol - minCol + 1;
        d.h = maxY - minY + 1;
        d.cells.addAll(cells);
        for (BlockPos c : cells) {
            int col = c.getX() * a.getStepX() + c.getZ() * a.getStepZ();
            if (col == minCol && c.getY() == minY) d.master = c;
        }
        if (d.master == null) d.master = cells.get(0);
        Floor fl = null;
        for (Floor x : floors) if (x.y == minY) fl = x;
        if (fl == null) { fl = new Floor(minY); floors.add(fl); }
        fl.doors.add(d);
        for (BlockPos c : cells) {
            if (lvl.getBlockEntity(c) instanceof LandingDoorBlockEntity be) {
                int col = c.getX() * a.getStepX() + c.getZ() * a.getStepZ();
                be.link(worldPosition, c.equals(d.master), col - minCol, c.getY() - minY, d.w, d.h, doorType, theme, window);
            }
        }
        if (lobbyY == Integer.MIN_VALUE) lobbyY = minY;
        rebuild();
        setChanged();
        int idx = idxOfY(minY);
        p.displayClientMessage(Component.translatable("elevmod.msg.door_linked", floors.get(idx).name, d.w, d.h,
                Component.translatable(rear ? "elevmod.side.rear" : "elevmod.side.front")).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public void unlinkDoor(ServerLevel lvl, BlockPos pos) {
        for (Floor f : floors) for (Floor.Door d : f.doors) if (d.cells.contains(pos)) {
            for (BlockPos c : d.cells) {
                if (lvl.getBlockEntity(c) instanceof LandingDoorBlockEntity be) be.unlink();
                LandingDoorBlock.setOpen(lvl, c, false);
            }
        }
        removeDoorCell(pos, true);
    }

    public void removeDoorCell(BlockPos c, boolean notify) {
        boolean changed = false;
        for (int i = floors.size() - 1; i >= 0; i--) {
            Floor f = floors.get(i);
            if (f.doors.removeIf(d -> d.cells.contains(c))) changed = true;
            if (f.doors.isEmpty()) { floors.remove(i); changed = true; }
        }
        if (changed) {
            if (floors.isEmpty()) carFacing = null;
            Arrays.fill(carCalls, false); Arrays.fill(hallUp, false); Arrays.fill(hallDown, false);
            carCalls = new boolean[0]; hallUp = new boolean[0]; hallDown = new boolean[0];
            rebuild();
            if (state == State.MOVING) { target = -1; state = State.STOPPED; }
            setChanged();
        }
    }

    public int nearestFloorY(int y) {
        int best = Integer.MIN_VALUE, bd = Integer.MAX_VALUE;
        for (Floor f : floors) { int d = y - f.y; if (d >= -2 && d <= 5 && Math.abs(d) < bd) { bd = Math.abs(d); best = f.y; } }
        return best;
    }

    public void linkDevice(DeviceBlockEntity d, Player p) {
        int fy = nearestFloorY(d.getBlockPos().getY());
        if (fy == Integer.MIN_VALUE) { p.displayClientMessage(Component.translatable("elevmod.msg.need_floor").withStyle(ChatFormatting.RED), false); return; }
        d.link(worldPosition, fy);
        devices.add(d.getBlockPos());
        displayHash++;
        setChanged();
        p.displayClientMessage(Component.translatable("elevmod.msg.device_linked", floors.get(idxOfY(fy)).name).withStyle(ChatFormatting.GREEN), false);
    }

    public void linkMachine(MachineBlockEntity m, Player p) {
        machines.add(m.getBlockPos());
        m.link(worldPosition);
        setChanged();
        p.displayClientMessage(Component.translatable("elevmod.msg.machine_linked").withStyle(ChatFormatting.GREEN), false);
    }

    public void linkGroup(ServerLevel lvl, ControllerBlockEntity other, Player p) {
        Set<BlockPos> all = new HashSet<>(group);
        all.addAll(other.group);
        all.add(worldPosition);
        all.add(other.worldPosition);
        if (all.size() > 8) { p.displayClientMessage(Component.translatable("elevmod.msg.group_max").withStyle(ChatFormatting.RED), false); return; }
        List<BlockPos> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingLong(BlockPos::asLong));
        for (BlockPos bp : sorted) {
            if (lvl.getBlockEntity(bp) instanceof ControllerBlockEntity c) {
                c.group.clear(); c.group.addAll(sorted); c.displayHash++; c.setChanged();
            }
        }
        p.displayClientMessage(Component.translatable("elevmod.msg.group_linked", sorted.size()).withStyle(ChatFormatting.GREEN), false);
    }

    public void onBroken(ServerLevel lvl) {
        removeCars(lvl);
        for (Floor f : floors) for (Floor.Door d : f.doors) for (BlockPos c : d.cells) {
            if (lvl.getBlockEntity(c) instanceof LandingDoorBlockEntity be) be.unlink();
            LandingDoorBlock.setOpen(lvl, c, false);
        }
        for (BlockPos p : devices) if (lvl.getBlockEntity(p) instanceof DeviceBlockEntity d) d.unlink();
        for (BlockPos g : group) {
            if (!g.equals(worldPosition) && lvl.getBlockEntity(g) instanceof ControllerBlockEntity c) { c.group.remove(worldPosition); if (c.group.size() <= 1) c.group.clear(); c.setChanged(); }
        }
    }

    /** 설정 변경 후 문 모양 갱신 */
    private void refreshDoors(ServerLevel lvl) {
        for (Floor f : floors) for (Floor.Door d : f.doors) for (BlockPos c : d.cells) {
            if (lvl.getBlockEntity(c) instanceof LandingDoorBlockEntity be) be.style(doorType, theme, window);
        }
    }

    // ================================================================= 제어반 화면 설정
    public void edit(ServerPlayer p, String key, int delta, String text) {
        if (!(level instanceof ServerLevel lvl)) return;
        switch (key) {
            case "usage" -> {
                Spec.Usage old = usage;
                usage = cycle(Spec.Usage.values(), usage, delta);
                applyUsageDefaults();
                if (old == Spec.Usage.PATERNOSTER || usage == Spec.Usage.PATERNOSTER) {
                    removeCars(lvl);
                    s = 0; v = 0; cur = 0; target = -1; state = State.IDLE; doorPos = 0;
                }
            }
            case "drive" -> drive = cycle(Spec.Drive.values(), drive, delta);
            case "door" -> doorType = cycle(Spec.DoorType.values(), doorType, delta);
            case "theme" -> theme = cycle(Spec.Theme.values(), theme, delta);
            case "voice" -> voice = cycle(Spec.Voice.values(), voice, delta);
            case "rs" -> rsFn = cycle(Spec.RedstoneFn.values(), rsFn, delta);
            case "cap" -> capIdx = Math.floorMod(capIdx + delta, Spec.CAPACITY_KG.length);
            case "speed" -> speedIdx = Math.floorMod(speedIdx + delta, Spec.SPEED_MPM.length);
            case "dwell" -> dwellSec = Math.max(1, Math.min(30, dwellSec + delta));
            case "return" -> returnSec = Math.floorMod(returnSec + delta * 15, 315);
            case "beep" -> beep = !beep;
            case "fourf" -> fourAsF = !fourAsF;
            case "skip4" -> skip4 = !skip4;
            case "skip13" -> skip13 = !skip13;
            case "ard" -> ard = !ard;
            case "ropebrake" -> ropeBrake = !ropeBrake;
            case "preopen" -> preOpen = !preOpen;
            case "window" -> window = !window;
            case "airclean" -> airClean = !airClean;
            case "aircon" -> aircon = !aircon;
            case "stopswitch" -> { stopSwitch = !stopSwitch; if (!stopSwitch) stopPressed = false; }
            case "inclined" -> inclined = !inclined;
            case "acccop" -> accessibleCop = !accessibleCop;
            case "dd" -> destination = !destination;
            case "lobby" -> lobbyY = cycleFloorY(lobbyY, delta, false);
            case "home" -> homeY = cycleFloorY(homeY, delta, true);
            case "fire" -> fireY = cycleFloorY(fireY, delta, true);
            case "rename" -> { if (delta >= 0 && delta < floors.size()) floors.get(delta).customName = text.length() > 6 ? text.substring(0, 6) : text; }
            case "mode_normal" -> { if (mode != Spec.Mode.NORMAL) setMode(lvl, Spec.Mode.NORMAL); }
            case "mode_parking" -> setMode(lvl, mode == Spec.Mode.PARKING ? Spec.Mode.NORMAL : Spec.Mode.PARKING);
            case "mode_sabbath" -> setMode(lvl, mode == Spec.Mode.SABBATH ? Spec.Mode.NORMAL : Spec.Mode.SABBATH);
            case "mode_shuttle" -> setMode(lvl, mode == Spec.Mode.SHUTTLE ? Spec.Mode.NORMAL : Spec.Mode.SHUTTLE);
            case "mode_quake" -> setMode(lvl, mode == Spec.Mode.EARTHQUAKE ? Spec.Mode.NORMAL : Spec.Mode.EARTHQUAKE);
            case "mode_fire" -> fireService(lvl, !mode.fire(), fireY == Integer.MIN_VALUE ? (floors.isEmpty() ? 0 : floors.get(lobbyIdx()).y) : fireY);
            case "power" -> setPower(lvl, !powerOff);
            case "sim_rope" -> simulateRopeBreak(lvl);
            case "sim_brake" -> { if (simulateBrakeFailure(lvl)) p.displayClientMessage(Component.translatable("elevmod.msg.sim_brake"), true); }
            case "reset" -> resetFault(lvl);
            case "rescue" -> manualRescue(lvl, p);
            case "inspect" -> { lastInspect = lvl.getDayTime() / 24000L; log(lvl, "elevmod.log.inspection"); }
            case "unlink_group" -> {
                for (BlockPos g : new ArrayList<>(group)) if (!g.equals(worldPosition) && lvl.getBlockEntity(g) instanceof ControllerBlockEntity c) { c.group.remove(worldPosition); if (c.group.size() <= 1) c.group.clear(); c.setChanged(); }
                group.clear();
            }
            case "respawn" -> removeCars(lvl);
            default -> {}
        }
        rebuild();
        refreshDoors(lvl);
        setChanged();
        displayHash++;
        com.mchoe.elevmod.network.Net.sendController(p, this, false);
    }

    /** 시험: 주로프 절단 → 낙하 → 조속기 → 비상정지장치 */
    public boolean simulateRopeBreak(ServerLevel lvl) {
        if (fault != Spec.Fault.NONE || powerOff || floors.isEmpty() || usage == Spec.Usage.PATERNOSTER) return false;
        landingTarget(lvl, cur, false); landingSolid(lvl, cur, true); doorPos = 0;
        simRope = true; freeFall = true; govTripped = false; state = State.MOVING; target = -1;
        log(lvl, "elevmod.log.rope_break");
        playAt(lvl, carPos(), SoundEvents.CHAIN_BREAK, 1f, 0.6f);
        return true;
    }

    /** 시험: 브레이크 고장 → 다음 개문 정차 중 이상 이동(UCM) → 로프브레이크 */
    public boolean simulateBrakeFailure(ServerLevel lvl) {
        if (fault != Spec.Fault.NONE || powerOff || usage == Spec.Usage.PATERNOSTER) return false;
        simBrake = true;
        log(lvl, "elevmod.log.brake_fail");
        return true;
    }

    private void applyUsageDefaults() {
        switch (usage) {
            case HOSPITAL -> doorType = Spec.DoorType.S2;
            case FREIGHT -> { doorType = Spec.DoorType.S2; theme = Spec.Theme.CHECKER; if (capIdx < 7) capIdx = 10; }
            case CAR -> { doorType = Spec.DoorType.UP; drive = Spec.Drive.HYDRAULIC; theme = Spec.Theme.CHECKER; if (capIdx < 10) capIdx = 11; }
            case OBSERVATION -> theme = Spec.Theme.GLASS;
            case DUMBWAITER -> { doorType = Spec.DoorType.UP; drive = Spec.Drive.DRUM; }
            case HOME -> { doorType = Spec.DoorType.SWING; drive = Spec.Drive.MRL; capIdx = 1; }
            case WHEELCHAIR_LIFT -> { doorType = Spec.DoorType.SWING; drive = Spec.Drive.RACK_PINION; }
            case CONSTRUCTION -> { doorType = Spec.DoorType.GATE; drive = Spec.Drive.RACK_PINION; theme = Spec.Theme.CHECKER; }
            case PATERNOSTER -> { theme = Spec.Theme.WOOD; drive = Spec.Drive.GEARED; }
            case PASSENGER_FREIGHT -> { if (capIdx < 8) capIdx = 9; }
            default -> {}
        }
    }

    private int cycleFloorY(int curY, int delta, boolean allowNone) {
        List<Integer> ys = new ArrayList<>();
        if (allowNone) ys.add(Integer.MIN_VALUE);
        for (Floor f : floors) ys.add(f.y);
        if (ys.isEmpty()) return curY;
        int i = ys.indexOf(curY);
        if (i < 0) i = 0;
        return ys.get(Math.floorMod(i + delta, ys.size()));
    }

    private static <E extends Enum<E>> E cycle(E[] vals, E cur, int d) { return vals[Math.floorMod(cur.ordinal() + d, vals.length)]; }

    public CompoundTag screenTag() {
        CompoundTag t = new CompoundTag();
        saveSettings(t);
        ListTag names = new ListTag();
        for (Floor f : floors) names.add(StringTag.valueOf(f.name));
        t.put("names", names);
        ListTag ys = new ListTag();
        for (Floor f : floors) ys.add(IntTag.valueOf(f.y));
        t.put("ys", ys);
        t.putString("state", state.name());
        t.putString("modeK", mode.key());
        t.putString("faultK", fault.key());
        t.putBoolean("powerOff", powerOff);
        t.putLong("trips", trips);
        t.putDouble("dist", travelled);
        t.putInt("load", loadKg);
        t.putInt("rated", ratedKg());
        t.putInt("mpm", effectiveMpm());
        t.putFloat("cw", cw); t.putFloat("cd", cd); t.putFloat("ch", ch);
        t.putString("letter", String.valueOf(letter()));
        t.putInt("groupSize", Math.max(1, group.size()));
        t.putInt("devices", devices.size());
        t.putInt("machines", machines.size());
        t.putString("cur", floors.isEmpty() ? "-" : floors.get(nearestIdx(s)).name);
        ListTag log = new ListTag();
        for (String l : faultLog) log.add(StringTag.valueOf(l));
        t.put("log", log);
        double h = floors.isEmpty() ? 0 : floors.get(floors.size() - 1).y - floors.get(0).y;
        t.putDouble("rise", h);
        return t;
    }

    // ================================================================= 저장
    private void saveSettings(CompoundTag t) {
        t.putInt("usage", usage.ordinal());
        t.putInt("drive", drive.ordinal());
        t.putInt("doorType", doorType.ordinal());
        t.putInt("theme", theme.ordinal());
        t.putInt("voice", voice.ordinal());
        t.putInt("rs", rsFn.ordinal());
        t.putInt("cap", capIdx);
        t.putInt("speed", speedIdx);
        t.putInt("dwell", dwellSec);
        t.putInt("return", returnSec);
        t.putBoolean("beep", beep);
        t.putBoolean("fourf", fourAsF);
        t.putBoolean("skip4", skip4);
        t.putBoolean("skip13", skip13);
        t.putBoolean("ard", ard);
        t.putBoolean("ropebrake", ropeBrake);
        t.putBoolean("preopen", preOpen);
        t.putBoolean("window", window);
        t.putBoolean("airclean", airClean);
        t.putBoolean("aircon", aircon);
        t.putBoolean("stopswitch", stopSwitch);
        t.putBoolean("inclined", inclined);
        t.putBoolean("acccop", accessibleCop);
        t.putBoolean("dd", destination);
        t.putInt("lobby", lobbyY);
        t.putInt("home", homeY);
        t.putInt("fire", fireY);
        t.putString("serial", serial);
        t.putLong("lastInspect", lastInspect);
    }

    private void loadSettings(CompoundTag t) {
        usage = en(Spec.Usage.values(), t.getInt("usage"));
        drive = en(Spec.Drive.values(), t.getInt("drive"));
        doorType = en(Spec.DoorType.values(), t.getInt("doorType"));
        theme = en(Spec.Theme.values(), t.getInt("theme"));
        voice = en(Spec.Voice.values(), t.getInt("voice"));
        rsFn = en(Spec.RedstoneFn.values(), t.getInt("rs"));
        capIdx = t.getInt("cap");
        speedIdx = t.getInt("speed");
        dwellSec = Math.max(1, t.getInt("dwell"));
        returnSec = t.getInt("return");
        beep = t.getBoolean("beep");
        fourAsF = t.getBoolean("fourf");
        skip4 = t.getBoolean("skip4");
        skip13 = t.getBoolean("skip13");
        ard = t.getBoolean("ard");
        ropeBrake = t.getBoolean("ropebrake");
        preOpen = t.getBoolean("preopen");
        window = t.getBoolean("window");
        airClean = t.getBoolean("airclean");
        aircon = t.getBoolean("aircon");
        stopSwitch = t.getBoolean("stopswitch");
        inclined = t.getBoolean("inclined");
        accessibleCop = t.getBoolean("acccop");
        destination = t.getBoolean("dd");
        lobbyY = t.getInt("lobby");
        homeY = t.getInt("home");
        fireY = t.getInt("fire");
        serial = t.getString("serial");
        lastInspect = t.getLong("lastInspect");
    }

    private static <E extends Enum<E>> E en(E[] vals, int i) { return vals[Math.max(0, Math.min(i, vals.length - 1))]; }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        CompoundTag set = new CompoundTag();
        saveSettings(set);
        t.put("settings", set);
        ListTag fl = new ListTag();
        for (Floor f : floors) fl.add(f.save());
        t.put("floors", fl);
        t.put("devices", longs(devices));
        t.put("machines", longs(machines));
        t.put("group", longs(group));
        ListTag ids = new ListTag();
        for (UUID id : carIds) ids.add(StringTag.valueOf(id.toString()));
        t.put("cars", ids);
        if (carFacing != null) t.putInt("facing", carFacing.get2DDataValue());
        t.putDouble("s", s);
        t.putInt("cur", cur);
        t.putInt("mode", mode.ordinal());
        t.putInt("fault", fault.ordinal());
        t.putBoolean("powerOff", powerOff);
        t.putLong("trips", trips);
        t.putDouble("dist", travelled);
        ListTag log = new ListTag();
        for (String l : faultLog) log.add(StringTag.valueOf(l));
        t.put("log", log);
    }

    private static ListTag longs(java.util.Collection<BlockPos> c) {
        ListTag l = new ListTag();
        for (BlockPos p : c) l.add(LongTag.valueOf(p.asLong()));
        return l;
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        if (t.contains("settings")) loadSettings(t.getCompound("settings"));
        floors.clear();
        ListTag fl = t.getList("floors", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) floors.add(Floor.load(fl.getCompound(i)));
        devices.clear(); machines.clear(); group.clear();
        for (Tag x : t.getList("devices", Tag.TAG_LONG)) devices.add(BlockPos.of(((LongTag) x).getAsLong()));
        for (Tag x : t.getList("machines", Tag.TAG_LONG)) machines.add(BlockPos.of(((LongTag) x).getAsLong()));
        for (Tag x : t.getList("group", Tag.TAG_LONG)) group.add(BlockPos.of(((LongTag) x).getAsLong()));
        carIds.clear();
        for (Tag x : t.getList("cars", Tag.TAG_STRING)) {
            try { carIds.add(UUID.fromString(x.getAsString())); } catch (IllegalArgumentException ignored) {}
        }
        carFacing = t.contains("facing") ? Direction.from2DDataValue(t.getInt("facing")) : null;
        s = t.getDouble("s");
        cur = t.getInt("cur");
        mode = en(Spec.Mode.values(), t.getInt("mode"));
        fault = en(Spec.Fault.values(), t.getInt("fault"));
        powerOff = t.getBoolean("powerOff");
        trips = t.getLong("trips");
        travelled = t.getDouble("dist");
        faultLog.clear();
        for (Tag x : t.getList("log", Tag.TAG_STRING)) faultLog.add(x.getAsString());
        rebuild();
        if (!floors.isEmpty()) {
            cur = Math.min(cur, floors.size() - 1);
            if (Math.abs(floorS[nearestIdx(s)] - s) < 0.05) { cur = nearestIdx(s); s = floorS[cur]; state = State.IDLE; }
            else state = State.STOPPED;
        }
        doorPos = 0;
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public static void debug(String s) { ElevMod.LOG.debug(s); }
}

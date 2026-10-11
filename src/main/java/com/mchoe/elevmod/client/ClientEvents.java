package com.mchoe.elevmod.client;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.client.render.CarGeometry;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ElevMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static final List<ElevatorCarEntity> CARS = new ArrayList<>();

    /** 현재 클라이언트 월드의 카 목록 (렌더러에서 승강장 문 연동에 사용) */
    public static List<ElevatorCarEntity> cars() { return CARS; }

    /** 틱 시작: 모든 카 위치 예측 갱신 */
    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        CARS.clear();
        if (mc.level == null || mc.isPaused()) return;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof ElevatorCarEntity car) { car.clientAdvance(); CARS.add(car); }
        }
    }

    /** 로컬 플레이어 이동 직전에 카 이동량만큼 함께 이동 (부드러운 탑승) */
    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer p) || !p.level().isClientSide) return;
        if (p.isSpectator() || p.isPassenger()) return;
        for (ElevatorCarEntity car : CARS) {
            if (car.cPrev == null || car.cCur == null || car.isRemoved()) continue;
            if (car.isDumbwaiter()) {
                if (car.contains(p, car.cCur)) {
                    Vec3 F = new Vec3(car.facing().getStepX(), 0, car.facing().getStepZ());
                    double depth = car.facing().getAxis() == net.minecraft.core.Direction.Axis.Z ? car.halfZ() : car.halfX();
                    Vec3 out = car.cCur.add(F.scale(depth + 0.45));
                    p.setPos(F.x != 0 ? out.x : p.getX(), p.getY(), F.z != 0 ? out.z : p.getZ());
                }
                continue;
            }
            if (!car.contains(p, car.cPrev)) continue;
            Vec3 d = car.cCur.subtract(car.cPrev);
            double nx = p.getX() + d.x, ny = p.getY() + d.y, nz = p.getZ() + d.z;
            double floor = car.cCur.y;
            boolean inside = Math.abs(nx - car.cCur.x) <= car.halfX() && Math.abs(nz - car.cCur.z) <= car.halfZ();
            if (inside && ny < floor) ny = floor;
            if (d.lengthSqr() > 0 || ny != p.getY()) p.setPos(nx, ny, nz);
            if (inside && p.getY() <= floor + 0.001) {
                Vec3 dm = p.getDeltaMovement();
                if (dm.y < 0) p.setDeltaMovement(dm.x, 0, dm.z);
                p.setOnGround(true);
            }
            p.fallDistance = 0;
            break;
        }
    }

    /** 카 안에서 운전반을 우클릭(또는 웅크리고 빈손 우클릭)하면 운전반 화면 */
    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        // 디자인 변경기 위치 조정 모드: 카 안 표시기(문 위)·운전반 표시창의 위치·크기 화면
        if (p.getMainHandItem().getItem() instanceof com.mchoe.elevmod.item.DesignToolItem && com.mchoe.elevmod.item.DesignToolItem.positionMode(p.getMainHandItem())) {
            for (ElevatorCarEntity car : CARS) {
                if (car.cCur == null || !car.contains(p, car.cCur)) continue;
                int target = -1;
                if (CarGeometry.lookingAtCop(car, p.getEyePosition(1f), p.getViewVector(1f), car.cCur) != 0) target = 1;
                else if (CarGeometry.pick(car, p.getEyePosition(1f), p.getViewVector(1f), car.cCur) == com.mchoe.elevmod.elevator.CarAction.PART_INDICATOR) target = 0;
                if (target < 0 && p.isShiftKeyDown()) target = 0;   // 웅크리고 우클릭: 어디를 보든 배치 화면
                if (target < 0) continue;
                mc.setScreen(new com.mchoe.elevmod.client.screen.CarLayoutScreen(car, target));
                e.setCanceled(true);
                e.setSwingHand(true);
                return;
            }
        }
        // 디자인 변경기: 카 안에서 보고 있는 부위의 의장 변경
        if (p.getMainHandItem().getItem() instanceof com.mchoe.elevmod.item.DesignToolItem && !com.mchoe.elevmod.item.DesignToolItem.positionMode(p.getMainHandItem())) {
            for (ElevatorCarEntity car : CARS) {
                if (car.cCur == null || !car.contains(p, car.cCur)) continue;
                int part = CarGeometry.pick(car, p.getEyePosition(1f), p.getViewVector(1f), car.cCur);
                if (part < 0) continue;
                com.mchoe.elevmod.network.Net.CH.sendToServer(new com.mchoe.elevmod.network.Net.CarActionMsg(car.controller(),
                        com.mchoe.elevmod.elevator.CarAction.DESIGN, part * 2 + (p.isShiftKeyDown() ? 1 : 0)));
                e.setCanceled(true);
                e.setSwingHand(true);
                return;
            }
        }
        for (ElevatorCarEntity car : CARS) {
            if (car.cCur == null || !car.contains(p, car.cCur) || !car.usage().hasCop()) continue;
            int hit = CarGeometry.lookingAtCop(car, p.getEyePosition(1f), p.getViewVector(1f), car.cCur);
            if (hit == 0 && p.isShiftKeyDown() && p.getMainHandItem().isEmpty()) hit = 1;
            if (hit != 0) {
                ClientHooks.openCop(car, hit == 2);
                e.setCanceled(true);
                e.setSwingHand(false);
                return;
            }
        }
    }

    /** 서버를 나가면 받은 사용자 디자인 정리 */
    @SubscribeEvent
    public static void loggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        com.mchoe.elevmod.client.design.RemoteDesigns.clear();
    }

    /**
     * Ctrl + '+' / Ctrl + '-' : 보고 있는 엘리베이터 부품(승강장 장치, 승강장 문, 제어반, 기계)을 1°씩 회전.
     * Ctrl + Shift 는 15°씩, Ctrl + 0 은 원래 각도로.
     */
    @SubscribeEvent
    public static void key(InputEvent.Key e) {
        if (e.getAction() == org.lwjgl.glfw.GLFW.GLFW_RELEASE) return;
        if ((e.getModifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) return;
        int k = e.getKey();
        boolean plus = k == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL || k == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD;
        boolean minus = k == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || k == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;
        boolean zero = k == org.lwjgl.glfw.GLFW.GLFW_KEY_0 || k == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0;
        if (!plus && !minus && !zero) return;
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh) || bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        var be = mc.level.getBlockEntity(bh.getBlockPos());
        if (!(be instanceof com.mchoe.elevmod.block.DeviceBlockEntity || be instanceof com.mchoe.elevmod.block.LandingDoorBlockEntity
                || be instanceof com.mchoe.elevmod.block.MachineBlockEntity || be instanceof com.mchoe.elevmod.elevator.ControllerBlockEntity)) return;
        float step = (e.getModifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0 ? 15f : 1f;
        com.mchoe.elevmod.network.Net.CH.sendToServer(new com.mchoe.elevmod.network.RotateMsg(bh.getBlockPos(), plus ? step : -step, zero));
    }
}

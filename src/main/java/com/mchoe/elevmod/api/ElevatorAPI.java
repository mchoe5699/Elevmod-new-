package com.mchoe.elevmod.api;

import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 다른 모드(예: 홈네트워크 월패드)에서 엘리베이터를 호출/조회하기 위한 공개 API.
 * 월패드 쪽에서는 제어반 좌표와 세대 층의 승강장 바닥 Y 만 알면 된다.
 */
public final class ElevatorAPI {
    private ElevatorAPI() {}

    @Nullable
    public static ControllerBlockEntity get(Level level, BlockPos controller) {
        if (!level.isLoaded(controller)) return null;
        return level.getBlockEntity(controller) instanceof ControllerBlockEntity c ? c : null;
    }

    /** 승강장 호출 (군관리 포함). floorY = 승강장 문 바닥 Y. 성공하면 true */
    public static boolean call(ServerLevel level, BlockPos controller, int floorY, boolean up) {
        ControllerBlockEntity c = get(level, controller);
        if (c == null || c.idxOfY(floorY) < 0) return false;
        c.hallCall(level, floorY, up, null);
        return true;
    }

    /** 현재 카 위치의 층 이름 ("1", "B2", "F" 등) */
    public static String currentFloor(Level level, BlockPos controller) {
        ControllerBlockEntity c = get(level, controller);
        if (c == null || c.floors.isEmpty()) return "-";
        return c.floors.get(c.nearestIdx(c.s)).name;
    }

    /** 운행 방향: 1 상승, -1 하강, 0 정지 */
    public static int direction(Level level, BlockPos controller) {
        ControllerBlockEntity c = get(level, controller);
        return c == null ? 0 : (c.v > 0 ? 1 : c.v < 0 ? -1 : 0);
    }

    /** 상태 키 (fire, park, insp, power, fault, full, over ... 빈 문자열이면 정상) */
    public static String status(Level level, BlockPos controller) {
        ControllerBlockEntity c = get(level, controller);
        return c == null ? "offline" : c.statusKey();
    }
}

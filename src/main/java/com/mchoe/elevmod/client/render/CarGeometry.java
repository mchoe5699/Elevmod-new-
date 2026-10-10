package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.entity.ElevatorCarEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 카 로컬 좌표계(원점=바닥 중심, +Z=정면(승강장쪽), +X=정면의 반시계 방향) 상의 부품 위치 */
public final class CarGeometry {
    private CarGeometry() {}

    public static final float T = 0.06f; // 벽 두께

    /** 주 운전반(COP): {x1, y1, z1, x2, y2, z2} – 정면 벽 안쪽, 문 옆 */
    public static float[] cop(ElevatorCarEntity car) {
        CompoundTag st = car.state();
        float w = car.carW(), d = car.carD();
        float fw = st.getFloat("fw"), fo = st.getFloat("fo");
        float pw = 0.26f;
        float z2 = d / 2 - T, z1 = z2 - 0.04f;
        if (fw <= 0) return new float[]{-w / 2 + 0.1f, 0.85f, z1, -w / 2 + 0.1f + pw, 1.75f, z2};
        float doorL = fo - fw / 2, doorR = fo + fw / 2;
        if (doorL - 0.06f - pw >= -w / 2 + T) return new float[]{doorL - 0.06f - pw, 0.85f, z1, doorL - 0.06f, 1.75f, z2};
        if (doorR + 0.06f + pw <= w / 2 - T) return new float[]{doorR + 0.06f, 0.85f, z1, doorR + 0.06f + pw, 1.75f, z2};
        // 공간 부족: 측면 벽
        float x1 = w / 2 - T - 0.04f;
        return new float[]{x1, 0.85f, d / 2 - 0.45f, w / 2 - T, 1.75f, d / 2 - 0.15f};
    }

    /** 장애인용 운전반: 좌측 벽, 휠체어 높이의 가로형 */
    public static float[] accessibleCop(ElevatorCarEntity car) {
        float w = car.carW(), d = car.carD();
        float half = Math.min(0.35f, d / 2 - 0.2f);
        return new float[]{-w / 2 + T, 0.85f, -half, -w / 2 + T + 0.04f, 1.1f, half};
    }

    /** 로컬 상자를 월드 AABB 로 변환 */
    public static AABB toWorld(ElevatorCarEntity car, Vec3 origin, float[] b) {
        Direction f = car.facing();
        Direction l = f.getCounterClockWise();
        Vec3 F = new Vec3(f.getStepX(), 0, f.getStepZ()), L = new Vec3(l.getStepX(), 0, l.getStepZ());
        Vec3 a = origin.add(L.scale(b[0])).add(0, b[1], 0).add(F.scale(b[2]));
        Vec3 c = origin.add(L.scale(b[3])).add(0, b[4], 0).add(F.scale(b[5]));
        return new AABB(a, c).inflate(0.03);
    }

    /** 시선이 운전반을 향하는지: 0 = 아니오, 1 = 주 운전반, 2 = 장애인용 */
    public static int lookingAtCop(ElevatorCarEntity car, Vec3 eye, Vec3 look, @Nullable Vec3 origin) {
        if (origin == null) return 0;
        Vec3 end = eye.add(look.scale(3));
        if (toWorld(car, origin, cop(car)).clip(eye, end).isPresent()) return 1;
        if (car.state().getBoolean("acc") && toWorld(car, origin, accessibleCop(car)).clip(eye, end).isPresent()) return 2;
        return 0;
    }
}

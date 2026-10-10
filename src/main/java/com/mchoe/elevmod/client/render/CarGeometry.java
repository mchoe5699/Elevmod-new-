package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 카 로컬 좌표계: 원점 = 바닥 중심, +Z = 정면(승강장 쪽), +X = 정면의 반시계 방향.
 * 카 안에서 정면을 보면 오른쪽이 -X 이다.
 * 내부 치수 w × d × h, 정면 벽(프런트 리턴/트랜섬)은 z ∈ [d/2 - T, d/2] 안쪽에 있고 카 도어는 그 바깥에 매달린다.
 */
public final class CarGeometry {
    private CarGeometry() {}

    public static final float T = 0.06f;   // 벽 두께
    public static final float COP_Y1 = 0.80f, COP_Y2 = 1.85f;

    /** 운전반 위치: box = {x1,y1,z1,x2,y2,z2}, wall = 0 정면 리턴, +1 = +X 측벽, -1 = -X 측벽 */
    public record Cop(float[] box, int wall) {}

    public static Cop cop(ElevatorCarEntity car) {
        CompoundTag st = car.state();
        float w = car.carW(), d = car.carD();
        float fw = st.getFloat("fw"), fo = st.getFloat("fo");
        int sideSetting = st.getInt("cops");
        Spec.DoorType dt = Spec.DoorType.values()[Math.max(0, Math.min(st.getInt("door"), Spec.DoorType.values().length - 1))];
        boolean rh = st.getBoolean("rh");
        float zFace = d / 2 - T;
        float doorL = fw > 0 ? fo - fw / 2 : 0, doorR = fw > 0 ? fo + fw / 2 : 0;
        float retNeg = fw > 0 ? doorL - (-w / 2) : w / 2;   // -X 쪽(안에서 오른쪽) 리턴 폭
        float retPos = fw > 0 ? w / 2 - doorR : w / 2;      // +X 쪽(안에서 왼쪽) 리턴 폭
        // 기본: 안에서 볼 때 오른쪽(-X). 편개는 문짝 수납부 쪽(넓은 리턴)
        boolean neg = true;
        if (sideSetting == Spec.Side.LEFT.ordinal()) neg = false;
        else if (sideSetting == Spec.Side.AUTO.ordinal() && dt.sideOpening()) neg = !rh;
        float need = 0.22f;
        if ((neg ? retNeg : retPos) < need && (neg ? retPos : retNeg) >= need) neg = !neg;
        float ret = neg ? retNeg : retPos;
        if (fw > 0 && ret < need) {
            // 리턴이 좁으면 정면에 가까운 측벽에 설치
            int wall = neg ? -1 : 1;
            float x = wall * (w / 2);
            float z2 = d / 2 - T - 0.08f, z1 = z2 - 0.24f;
            return new Cop(new float[]{wall < 0 ? x : x - 0.012f, COP_Y1, z1, wall < 0 ? x + 0.012f : x, COP_Y2, z2}, wall);
        }
        float pw = Math.min(0.24f, ret - 0.06f);
        float cx = neg ? (-w / 2 + doorL) / 2 : (doorR + w / 2) / 2;
        if (fw <= 0) cx = -w / 4;
        return new Cop(new float[]{cx - pw / 2, COP_Y1, zFace - 0.012f, cx + pw / 2, COP_Y2, zFace}, 0);
    }

    /** 장애인용 운전반: 카 안에서 볼 때 왼쪽 측벽(+X), 휠체어 높이의 가로형 */
    public static float[] accessibleCop(ElevatorCarEntity car) {
        float w = car.carW(), d = car.carD();
        float half = Math.min(0.32f, d / 2 - 0.25f);
        float cz = Math.max(-d / 2 + half + 0.1f, -0.05f);
        return new float[]{w / 2 - 0.014f, 0.86f, cz - half, w / 2, 1.06f, cz + half};
    }

    public static AABB toWorld(ElevatorCarEntity car, Vec3 origin, float[] b) {
        Direction f = car.facing();
        Direction l = f.getCounterClockWise();
        Vec3 F = new Vec3(f.getStepX(), 0, f.getStepZ()), L = new Vec3(l.getStepX(), 0, l.getStepZ());
        Vec3 a = origin.add(L.scale(b[0])).add(0, b[1], 0).add(F.scale(b[2]));
        Vec3 c = origin.add(L.scale(b[3])).add(0, b[4], 0).add(F.scale(b[5]));
        return new AABB(a, c).inflate(0.04);
    }

    /** 시선이 운전반을 향하는지: 0 = 아니오, 1 = 주 운전반, 2 = 장애인용 */
    public static int lookingAtCop(ElevatorCarEntity car, Vec3 eye, Vec3 look, @Nullable Vec3 origin) {
        if (origin == null) return 0;
        Vec3 end = eye.add(look.scale(3));
        if (toWorld(car, origin, cop(car).box()).clip(eye, end).isPresent()) return 1;
        if (car.state().getBoolean("acc") && toWorld(car, origin, accessibleCop(car)).clip(eye, end).isPresent()) return 2;
        return 0;
    }
}

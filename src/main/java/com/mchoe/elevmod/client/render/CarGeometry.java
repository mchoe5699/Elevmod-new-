package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.elevator.CarAction;
import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 카 로컬 좌표계: 원점 = 바닥 중심, +Z = 정면(승강장 쪽), +X = 정면의 반시계 방향.
 * 카 안에서 정면(문)을 보면 오른쪽이 -X, 왼쪽이 +X 이다.
 * 내부 치수 w × d × h, 정면 벽(프런트 리턴/트랜섬)은 z ∈ [d/2 - T, d/2] 안쪽에 있고 카 도어는 그 바깥에 매달린다.
 */
public final class CarGeometry {
    private CarGeometry() {}

    public static final float T = 0.06f;   // 벽 두께
    public static final float COP_Y1 = 0.80f, COP_Y2 = 1.85f;
    public static final float ACC_Y1 = 0.95f, ACC_Y2 = 1.17f;

    /** 운전반 위치: box = {x1,y1,z1,x2,y2,z2}, wall = 0 정면 리턴, +1 = +X 측벽, -1 = -X 측벽 */
    public record Cop(float[] box, int wall) {}

    private static Spec.Side side(CompoundTag st, String key) {
        Spec.Side[] v = Spec.Side.values();
        return v[Math.max(0, Math.min(st.getInt(key), v.length - 1))];
    }

    /** 주 운전반 목록 (설정에 따라 한쪽 또는 양쪽) */
    public static List<Cop> cops(ElevatorCarEntity car) {
        CompoundTag st = car.state();
        float w = car.carW(), d = car.carD();
        float fw = st.getFloat("fw"), fo = st.getFloat("fo");
        Spec.Side setting = side(st, "cops");
        Spec.DoorType dt = Spec.DoorType.values()[Math.max(0, Math.min(st.getInt("door"), Spec.DoorType.values().length - 1))];
        boolean rh = st.getBoolean("hand");
        float doorL = fw > 0 ? fo - fw / 2 : 0, doorR = fw > 0 ? fo + fw / 2 : 0;
        float retNeg = fw > 0 ? doorL - (-w / 2) : w / 2;   // -X 쪽(안에서 오른쪽) 리턴 폭
        float retPos = fw > 0 ? w / 2 - doorR : w / 2;      // +X 쪽(안에서 왼쪽) 리턴 폭
        List<Cop> out = new ArrayList<>();
        if (setting == Spec.Side.BOTH) {
            out.add(place(true, w, d, fw, doorL, doorR, retNeg));
            out.add(place(false, w, d, fw, doorL, doorR, retPos));
            return out;
        }
        // 기본: 안에서 볼 때 오른쪽(-X). 편개는 문짝 수납부 쪽(넓은 리턴)
        boolean neg = true;
        if (setting == Spec.Side.LEFT) neg = false;
        else if (setting == Spec.Side.AUTO && dt.sideOpening()) neg = !rh;
        float need = 0.22f;
        if (setting == Spec.Side.AUTO && (neg ? retNeg : retPos) < need && (neg ? retPos : retNeg) >= need) neg = !neg;
        out.add(place(neg, w, d, fw, doorL, doorR, neg ? retNeg : retPos));
        return out;
    }

    private static Cop place(boolean neg, float w, float d, float fw, float doorL, float doorR, float ret) {
        float zFace = d / 2 - T;
        if (fw > 0 && ret < 0.22f) {
            // 리턴이 좁으면 정면에 가까운 측벽에 설치
            int wall = neg ? -1 : 1;
            float x = wall * (w / 2);
            float z2 = d / 2 - T - 0.08f, z1 = z2 - 0.24f;
            return new Cop(new float[]{wall < 0 ? x : x - 0.012f, COP_Y1, z1, wall < 0 ? x + 0.012f : x, COP_Y2, z2}, wall);
        }
        float pw = Math.min(0.24f, ret - 0.06f);
        float cx = neg ? (-w / 2 + doorL) / 2 : (doorR + w / 2) / 2;
        if (fw <= 0) cx = neg ? -w / 4 : w / 4;
        return new Cop(new float[]{cx - pw / 2, COP_Y1, zFace - 0.012f, cx + pw / 2, COP_Y2, zFace}, 0);
    }

    /** 첫 번째 주 운전반 (하위 호환) */
    public static Cop cop(ElevatorCarEntity car) { return cops(car).get(0); }

    /** 장애인용 운전반: 측벽의 휠체어 높이 가로형. AUTO/LEFT = 안에서 왼쪽(+X), RIGHT = 오른쪽(-X), BOTH = 양쪽 */
    public static List<float[]> accessibleCops(ElevatorCarEntity car) {
        CompoundTag st = car.state();
        float w = car.carW(), d = car.carD();
        float half = Math.min(0.32f, d / 2 - 0.25f);
        float cz = Math.max(-d / 2 + half + 0.1f, -0.05f);
        Spec.Side s = side(st, "accs");
        List<float[]> out = new ArrayList<>();
        boolean left = s == Spec.Side.AUTO || s.left(), right = s.right();
        if (left) out.add(new float[]{w / 2 - 0.014f, ACC_Y1, cz - half, w / 2, ACC_Y2, cz + half});
        if (right) out.add(new float[]{-w / 2, ACC_Y1, cz - half, -w / 2 + 0.014f, ACC_Y2, cz + half});
        return out;
    }

    public static float[] accessibleCop(ElevatorCarEntity car) { return accessibleCops(car).get(0); }

    /** 트랜섬 위치표시기 상자 (없으면 null) */
    @Nullable
    public static float[] transom(ElevatorCarEntity car) {
        CompoundTag st = car.state();
        float fw = st.getFloat("fw"), fh = st.getFloat("fh"), fo = st.getFloat("fo");
        float h = car.carH(), d = car.carD();
        if (fw <= 0 || h - fh < 0.24f) return null;
        // 제어반에서 정한 위치 조정 (cm, 실내에서 볼 때 오른쪽·위가 +)
        float ox = st.getFloat("indx") / 100f, oy = st.getFloat("indy") / 100f;
        float y1 = fh + 0.05f + oy, y2 = Math.min(h - 0.06f, fh + 0.19f) + oy;
        if (y2 > h - 0.01f) { y1 -= y2 - (h - 0.01f); y2 = h - 0.01f; }
        if (y1 < 0.05f) { y2 += 0.05f - y1; y1 = 0.05f; }
        float cx = Math.max(-car.carW() / 2 + 0.18f, Math.min(car.carW() / 2 - 0.18f, fo - ox));
        return new float[]{cx - 0.16f, y1, d / 2 - T - 0.012f, cx + 0.16f, y2, d / 2 - T};
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
        double best = Double.MAX_VALUE;
        int hit = 0;
        for (Cop c : cops(car)) {
            Optional<Vec3> h = toWorld(car, origin, c.box()).clip(eye, end);
            if (h.isPresent() && h.get().distanceToSqr(eye) < best) { best = h.get().distanceToSqr(eye); hit = 1; }
        }
        if (car.state().getBoolean("acc")) for (float[] b : accessibleCops(car)) {
            Optional<Vec3> h = toWorld(car, origin, b).clip(eye, end);
            if (h.isPresent() && h.get().distanceToSqr(eye) < best) { best = h.get().distanceToSqr(eye); hit = 2; }
        }
        return hit;
    }

    /** 디자인 변경기: 시선이 가리키는 카 부위 (CarAction.PART_*), 없으면 -1 */
    public static int pick(ElevatorCarEntity car, Vec3 eye, Vec3 look, Vec3 origin) {
        Direction f = car.facing();
        Direction l = f.getCounterClockWise();
        Vec3 F = new Vec3(f.getStepX(), 0, f.getStepZ()), L = new Vec3(l.getStepX(), 0, l.getStepZ());
        Vec3 o = eye.subtract(origin);
        Vec3 p = new Vec3(o.dot(L), o.y, o.dot(F));           // 로컬 시점
        Vec3 dv = new Vec3(look.dot(L), look.y, look.dot(F));  // 로컬 방향
        Vec3 end = p.add(dv.scale(4));
        CompoundTag st = car.state();
        float w = car.carW(), h = car.carH(), d = car.carD();
        double best = Double.MAX_VALUE;
        int part = -1;
        // 1) 작은 부품(상자) 우선
        List<float[]> boxes = new ArrayList<>();
        List<Integer> parts = new ArrayList<>();
        for (Cop c : cops(car)) { boxes.add(c.box()); parts.add(CarAction.PART_COP); }
        if (st.getBoolean("acc")) for (float[] b : accessibleCops(car)) { boxes.add(b); parts.add(CarAction.PART_ACC); }
        float[] tr = transom(car);
        if (tr != null) { boxes.add(tr); parts.add(CarAction.PART_INDICATOR); }
        for (int i = 0; i < boxes.size(); i++) {
            float[] b = boxes.get(i);
            AABB box = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]).inflate(0.03);
            Optional<Vec3> hit = box.clip(p, end);
            if (hit.isPresent()) {
                double dd = hit.get().distanceToSqr(p);
                if (dd < best) { best = dd; part = parts.get(i); }
            }
        }
        if (part >= 0) return part;
        // 2) 면: 바닥, 천장, 측벽, 후벽, 정면(문 / 리턴)
        float fw = st.getFloat("fw"), fh = st.getFloat("fh"), fo = st.getFloat("fo");
        double bt = Double.MAX_VALUE;
        double[] tt = {
                dv.y < 0 ? (0 - p.y) / dv.y : -1,
                dv.y > 0 ? (h - p.y) / dv.y : -1,
                dv.x > 0 ? (w / 2 - p.x) / dv.x : dv.x < 0 ? (-w / 2 - p.x) / dv.x : -1,
                dv.z < 0 ? (-d / 2 - p.z) / dv.z : -1,
                dv.z > 0 ? (d / 2 - T - p.z) / dv.z : -1};
        int[] pp = {CarAction.PART_FLOOR, CarAction.PART_CEILING, CarAction.PART_WALL, CarAction.PART_WALL, -2};
        for (int i = 0; i < tt.length; i++) {
            double t = tt[i];
            if (t <= 0 || t > 4) continue;
            Vec3 q = p.add(dv.scale(t));
            if (q.y < -0.01 || q.y > h + 0.01 || Math.abs(q.x) > w / 2 + 0.01 || q.z < -d / 2 - 0.01 || q.z > d / 2 + 0.01) continue;
            if (t < bt) {
                bt = t;
                part = pp[i];
                if (part == -2) part = fw > 0 && Math.abs(q.x - fo) < fw / 2 && q.y < fh ? CarAction.PART_DOOR : CarAction.PART_WALL;
            }
        }
        return part;
    }
}

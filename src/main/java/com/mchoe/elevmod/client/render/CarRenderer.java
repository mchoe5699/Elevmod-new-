package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.mchoe.elevmod.client.design.CustomDesign;
import com.mchoe.elevmod.client.design.CustomDesigns;
import com.mchoe.elevmod.client.design.Relief;

import java.util.List;

import static com.mchoe.elevmod.client.render.CarGeometry.T;

/**
 * 엘리베이터 카 렌더러 – 실제 카 구성:
 * 바닥(석재/체커) · 카 실(문턱) · 측면/후면 판넬(+킥플레이트, 상부 프리즈) · 프런트 리턴 + 트랜섬 ·
 * 리턴에 매립된 운전반(COP) · 카 도어(망원식 레이어, 앞날 세이프티 빔) · 핸드레일 · 거울 · 매립형 천장 조명 ·
 * 카 외부: 카 프레임(크로스헤드/스타일/플랭크), 도어 오퍼레이터, 토가드, 가이드레일, 균형추, 로프, 유압잭
 */
public class CarRenderer extends EntityRenderer<ElevatorCarEntity> {
    private static final int WHITE = 0xFFFFFFFF, STEEL = 0xFFB4BAC0, DARK = 0xFF26282C, BLACK = 0xFF060608;

    public CarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ElevatorCarEntity e) { return RenderUtil.tex("panel_stainless"); }

    @Override
    public boolean shouldRender(ElevatorCarEntity e, Frustum f, double x, double y, double z) { return e.cCur != null; }

    private static <E extends Enum<E>> E en(E[] v, int i) { return v[Math.max(0, Math.min(i, v.length - 1))]; }

    @Override
    public void render(ElevatorCarEntity car, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (car.cCur == null || car.cPrev == null) return;
        CompoundTag st = car.state();
        if (st.isEmpty()) return;
        Vec3 rp = car.cPrev.lerp(car.cCur, pt);
        double vx = Mth.lerp(pt, car.xOld, car.getX()), vy = Mth.lerp(pt, car.yOld, car.getY()), vz = Mth.lerp(pt, car.zOld, car.getZ());
        ps.pushPose();
        ps.translate(rp.x - vx, rp.y - vy, rp.z - vz);
        ps.mulPose(Axis.YP.rotationDegrees(-car.facing().toYRot() - car.groupYaw()));   // 회전기로 묶어 돌린 각도 포함

        Ctx c = new Ctx();
        c.ps = ps; c.buf = buf; c.st = st; c.light = light;
        c.w = car.carW(); c.h = car.carH(); c.d = car.carD();
        c.usage = en(Spec.Usage.values(), st.getInt("usage"));
        c.drive = en(Spec.Drive.values(), st.getInt("drive"));
        c.dt = en(Spec.DoorType.values(), st.getInt("door"));
        c.theme = en(Spec.Theme.values(), st.getInt("theme"));
        c.ceiling = en(Spec.Ceiling.values(), st.getInt("ceil"));
        c.lights = st.getBoolean("light");
        c.emg = st.getBoolean("emg");
        c.il = c.lights ? RenderUtil.FULL : c.emg ? LightTexture.pack(5, 0) : light;
        c.door = car.doorPrev + (car.doorCur - car.doorPrev) * pt;
        c.carAbs = (float) rp.y;
        c.fw = st.getFloat("fw"); c.fh = st.getFloat("fh"); c.fo = st.getFloat("fo");
        c.rw = st.getFloat("rw"); c.rh = st.getFloat("rh"); c.ro = st.getFloat("ro");
        c.glass = st.getBoolean("glass") || c.theme == Spec.Theme.GLASS;
        c.right = st.getBoolean("hand");
        c.wallF = en(Spec.WallFinish.values(), st.getInt("wallf"));
        c.floorF = en(Spec.FloorFinish.values(), st.getInt("floorf"));
        c.copSt = en(Spec.ButtonStyle.values(), st.getInt("copst"));
        c.accSt = en(Spec.ButtonStyle.values(), st.getInt("accst"));
        c.disp = en(Spec.Display.values(), st.getInt("disp"));
        c.tenkey = st.getBoolean("tenkey");
        c.cbtn = !st.contains("cbtn") || st.getBoolean("cbtn");
        c.cd = CustomDesigns.get(st.getString("dsg_car"));
        c.cdDoor = CustomDesigns.get(st.getString("dsg_carDoor"));
        c.cdCop = CustomDesigns.get(st.getString("dsg_cop"));
        c.cdAcc = CustomDesigns.get(st.getString("dsg_copAcc"));
        c.cdInd = CustomDesigns.get(st.getString("dsg_indicator"));
        c.car = car;
        c.ind = profile(car.getId() + ":", st.getCompound("ind"));
        // 개별 디자인 (없으면 제어반 설정)
        c.indT = st.contains("indT") ? profile(car.getId() + ":T", st.getCompound("indT")) : c.ind;
        c.indC = st.contains("indC") ? profile(car.getId() + ":C", st.getCompound("indC")) : c.ind;
        c.moving = st.getBoolean("mv");
        c.open = st.getBoolean("dop");

        if (c.usage == Spec.Usage.WHEELCHAIR_LIFT) renderLift(c);
        else cab(c);
        if (st.contains("bot")) shaft(c);
        ps.popPose();
    }

    /** 렌더 공용 상태 */
    private static final class Ctx {
        PoseStack ps; MultiBufferSource buf; CompoundTag st; ElevatorCarEntity car;
        int light, il; float w, h, d, door, carAbs, fw, fh, fo, rw, rh, ro;
        Spec.Usage usage; Spec.Drive drive; Spec.DoorType dt; Spec.Theme theme; Spec.Ceiling ceiling;
        Spec.WallFinish wallF; Spec.FloorFinish floorF; Spec.ButtonStyle copSt, accSt; Spec.Display disp;
        boolean lights, emg, glass, right, tenkey, cbtn;
        CustomDesign cd, cdDoor, cdCop, cdAcc, cdInd;
        com.mchoe.elevmod.elevator.IndicatorProfile ind, indT, indC; boolean moving, open;
    }

    private static final java.util.Map<String, Object[]> PROFILES = new java.util.HashMap<>();

    /** 카 상태의 표시기 설정 (바뀔 때만 다시 읽음) */
    private static com.mchoe.elevmod.elevator.IndicatorProfile profile(String id, CompoundTag t) {
        Object[] e = PROFILES.get(id);
        if (e == null || !e[0].equals(t)) {
            if (PROFILES.size() > 256) PROFILES.clear();
            e = new Object[]{t.copy(), com.mchoe.elevmod.elevator.IndicatorProfile.load(t)};
            PROFILES.put(id, e);
        }
        return (com.mchoe.elevmod.elevator.IndicatorProfile) e[1];
    }

    private String wallTex(Ctx c) {
        if (c.wallF != Spec.WallFinish.AUTO && c.usage != Spec.Usage.CONSTRUCTION) {
            return switch (c.wallF) {
                case HAIRLINE -> "panel_stainless";
                case ETCHED -> "panel_etched";
                case MIRROR -> "panel_mirror";
                case GOLD -> "panel_gold";
                case WOOD -> "panel_wood";
                case WHITE -> "panel_white";
                case MARBLE -> "panel_marble";
                default -> "panel_stainless";
            };
        }
        if (c.usage == Spec.Usage.FREIGHT || c.usage == Spec.Usage.CAR || c.usage == Spec.Usage.CONSTRUCTION)
            return c.theme == Spec.Theme.STAINLESS || c.theme == Spec.Theme.CHECKER ? "panel_checker" : "panel_" + c.theme.tex();
        if (c.usage == Spec.Usage.PATERNOSTER) return "panel_wood";
        if (c.theme == Spec.Theme.CHECKER || c.theme == Spec.Theme.GLASS) return "panel_stainless";
        return "panel_" + c.theme.tex();
    }

    private String doorTex(Ctx c) {
        return switch (c.theme) {
            case GLASS -> "door_glass";
            case GOLD -> "door_gold";
            case WHITE -> "door_white";
            case CHECKER -> "door_checker";
            default -> "door_stainless";
        };
    }

    // ================================================================== 카 실내/외피
    private void cab(Ctx c) {
        PoseStack ps = c.ps;
        MultiBufferSource buf = c.buf;
        float w = c.w, h = c.h, d = c.d;
        boolean freight = c.usage == Spec.Usage.FREIGHT || c.usage == Spec.Usage.CAR || c.usage == Spec.Usage.CONSTRUCTION;
        boolean openFront = c.usage == Spec.Usage.PATERNOSTER;
        boolean mesh = c.usage == Spec.Usage.CONSTRUCTION;
        String wt = wallTex(c);
        boolean tiled = wt.equals("panel_checker");
        VertexConsumer wall = tiled ? RenderUtil.solid(buf, "floor_checker") : RenderUtil.solid(buf, wt);
        VertexConsumer steel = RenderUtil.solid(buf, "steel");
        VertexConsumer kick = RenderUtil.solid(buf, "kick");

        // ---- 바닥 + 플랫폼
        String floorTex = c.cd != null && c.cd.floorTex != null ? c.cd.floorTex : switch (c.floorF) {
            case GRANITE -> "floor_granite";
            case MARBLE -> "floor_marble";
            case CHECKER -> "floor_checker";
            case RUBBER -> "floor_rubber";
            case WOOD -> "floor_wood";
            default -> freight ? "floor_checker" : c.usage == Spec.Usage.PATERNOSTER ? "panel_wood" : "floor_granite";
        };
        if (floorTex.startsWith("@")) RenderUtil.panel(ps, RenderUtil.solid(buf, floorTex), -w / 2, -0.1f, -d / 2, w / 2, 0, d / 2 - T, c.il, WHITE); // 사진 바닥: 한 장으로
        else RenderUtil.box(ps, RenderUtil.solid(buf, floorTex), -w / 2, -0.1f, -d / 2, w / 2, 0, d / 2 - T, c.il, WHITE);
        RenderUtil.box(ps, steel, -w / 2 - T, -0.22f, -d / 2 - T, w / 2 + T, -0.1f, d / 2, c.light, WHITE);

        // ---- 측면 벽 (외피 + 내부 판넬)
        float zA = -d / 2, zB = d / 2 - T;            // 측벽 내부 길이 구간
        for (int sx = -1; sx <= 1; sx += 2) {
            float xo = sx * (w / 2 + T), xi = sx * w / 2;
            float x1 = Math.min(xo, xi), x2 = Math.max(xo, xi);
            if (c.glass) {
                RenderUtil.panel(ps, RenderUtil.glass(buf, "panel_glass"), x1, 0, zA, x2, h, zB, c.il, WHITE);
            } else if (mesh) {
                RenderUtil.box(ps, RenderUtil.cut(buf, "wall_mesh"), x1, 0, zA, x2, h, zB, c.il, WHITE);
            } else {
                RenderUtil.box(ps, steel, x1, 0, zA - T, x2, h, d / 2, c.light, WHITE);
                wallPanels(c, wall, tiled, true, sx, zA, zB);
            }
        }
        // ---- 후면 벽
        if (c.rw <= 0) {
            if (c.glass) RenderUtil.panel(ps, RenderUtil.glass(buf, "panel_glass"), -w / 2, 0, -d / 2 - T, w / 2, h, -d / 2, c.il, WHITE);
            else if (mesh) RenderUtil.box(ps, RenderUtil.cut(buf, "wall_mesh"), -w / 2, 0, -d / 2 - T, w / 2, h, -d / 2, c.il, WHITE);
            else {
                RenderUtil.box(ps, steel, -w / 2 - T, 0, -d / 2 - T, w / 2 + T, h, -d / 2, c.light, WHITE);
                wallPanels(c, wall, tiled, false, 0, -w / 2, w / 2);
            }
        } else {
            frontWall(c, wall, -1);
        }
        if (c.glass) {
            for (float sx : new float[]{-1, 1}) for (float sz : new float[]{-1, 1})
                RenderUtil.fill(ps, buf, sx * (w / 2 + T / 2) - 0.035f, 0, sz * (d / 2) - 0.035f, sx * (w / 2 + T / 2) + 0.035f, h, sz * (d / 2) + 0.035f, c.il, STEEL);
        }
        // ---- 정면 (프런트 리턴 + 트랜섬)
        if (!openFront) frontWall(c, wall, 1);

        // ---- 킥플레이트 (측/후면 하단)
        if (!c.glass && !mesh) {
            for (int sx = -1; sx <= 1; sx += 2) {
                float xi = sx * w / 2, xk = xi - sx * 0.014f;
                RenderUtil.panel(ps, kick, Math.min(xi, xk), 0, zA, Math.max(xi, xk), 0.13f, zB, c.il, WHITE);
            }
            if (c.rw <= 0) RenderUtil.panel(ps, kick, -w / 2, 0, -d / 2, w / 2, 0.13f, -d / 2 + 0.014f, c.il, WHITE);
        }

        // ---- 거울(후면 상부) + 핸드레일
        boolean passenger = c.usage.passengerCar() && !c.glass;
        if (passenger && c.rw <= 0 && c.theme != Spec.Theme.MIRROR && c.wallF != Spec.WallFinish.MIRROR && (c.cd == null || c.cd.mirror))
            RenderUtil.panel(ps, RenderUtil.solid(buf, "mirror"), -w / 2 + 0.12f, 1.0f, -d / 2 + 0.014f, w / 2 - 0.12f, h - 0.14f, -d / 2 + 0.02f, c.il, WHITE);
        if (c.usage.passengerCar() || c.glass) handrails(c);
        if (freight && !mesh) bumpers(c);

        // ---- 천장
        ceiling(c, mesh);

        // ---- 카 실(문턱)
        VertexConsumer sill = RenderUtil.solid(buf, c.cd != null && c.cd.sillTex != null ? c.cd.sillTex : "sill");
        if (c.fw > 0 && !openFront) RenderUtil.box(ps, sill, c.fo - c.fw / 2 - 0.03f, -0.006f, d / 2 - T, c.fo + c.fw / 2 + 0.03f, 0.004f, d / 2 + 0.032f, c.il, WHITE);
        if (c.rw > 0) RenderUtil.box(ps, sill, c.ro - c.rw / 2 - 0.03f, -0.006f, -d / 2 - 0.032f, c.ro + c.rw / 2 + 0.03f, 0.004f, -d / 2 + T, c.il, WHITE);

        // ---- 카 도어
        if (!openFront) {
            Spec.DoorType carType = c.dt == Spec.DoorType.SWING ? Spec.DoorType.GATE : c.dt;
            if (c.fw > 0) carDoor(c, carType, c.fw, c.fh, c.fo, false, c.st.getBoolean("fo2") ? c.door : 0);
            if (c.rw > 0) carDoor(c, carType, c.rw, c.rh, c.ro, true, c.st.getBoolean("ro2") ? c.door : 0);
        }
        // ---- 운전반 / 표시기
        if (c.usage.hasCop()) {
            cop(c);
            if (c.fw > 0) transomIndicator(c);   // 문 위 공간이 좁으면 CarGeometry.transom 이 null
            if (c.st.getBoolean("acc")) for (float[] b : CarGeometry.accessibleCops(c.car)) accessibleCop(c, b);
        }
    }

    /** 측벽(side=true, sx=±1) 또는 후면 벽의 판넬 분할 */
    private void wallPanels(Ctx c, VertexConsumer wall, boolean tiled, boolean side, int sx, float a, float b) {
        float len = b - a;
        CustomDesign.Surface cw = c.cd == null ? null : !side && c.cd.backWall != null ? c.cd.backWall : c.cd.wall;
        float pwid = c.cd != null && c.cd.panelWidth > 0 ? c.cd.panelWidth : 0.72f;
        int n = Math.max(1, Math.round(len / pwid));
        float gap = 0.008f, seg = (len - gap * (n - 1)) / n;
        float y1 = 0.13f, y2 = c.h - 0.05f;
        for (int i = 0; i < n; i++) {
            float s1 = a + i * (seg + gap), s2 = s1 + seg;
            if (cw != null) {
                // 커스텀 디자인 벽 판넬: 안쪽에서 볼 때 왼쪽→오른쪽 좌표계
                float inset = Math.max(0.012f, cw.maxRecess() + 0.002f);
                Relief.Frame f;
                if (side && sx > 0) f = new Relief.Frame(c.w / 2 - inset, y1, s1, 0, 0, 1, 0, 1, 0, -1, 0, 0);
                else if (side) f = new Relief.Frame(-c.w / 2 + inset, y1, s2, 0, 0, -1, 0, 1, 0, 1, 0, 0);
                else f = new Relief.Frame(s1, y1, -c.d / 2 + inset, 1, 0, 0, 0, 1, 0, 0, 0, 1);
                Relief.draw(c.ps, c.buf, cw, f, s2 - s1, y2 - y1, c.il, null);
                continue;
            }
            if (side) {
                float xi = sx * c.w / 2, xp = xi - sx * 0.012f;
                if (tiled) RenderUtil.box(c.ps, wall, Math.min(xi, xp), y1, s1, Math.max(xi, xp), y2, s2, c.il, WHITE);
                else RenderUtil.panel(c.ps, wall, Math.min(xi, xp), y1, s1, Math.max(xi, xp), y2, s2, c.il, WHITE);
            } else {
                float zi = -c.d / 2;
                if (tiled) RenderUtil.box(c.ps, wall, s1, y1, zi, s2, y2, zi + 0.012f, c.il, WHITE);
                else RenderUtil.panel(c.ps, wall, s1, y1, zi, s2, y2, zi + 0.012f, c.il, WHITE);
            }
        }
        // 상부 프리즈(스테인리스 띠)
        VertexConsumer fr = RenderUtil.solid(c.buf, "kick");
        if (side) {
            float xi = sx * c.w / 2, xp = xi - sx * 0.01f;
            RenderUtil.panel(c.ps, fr, Math.min(xi, xp), y2, a, Math.max(xi, xp), c.h, b, c.il, WHITE);
        } else RenderUtil.panel(c.ps, fr, a, y2, -c.d / 2, b, c.h, -c.d / 2 + 0.01f, c.il, WHITE);
    }

    /** 정면(dir=1) 또는 관통형 후면(dir=-1) 벽: 리턴 판넬 + 트랜섬 */
    private void frontWall(Ctx c, VertexConsumer wall, int dir) {
        float dw = dir > 0 ? c.fw : c.rw, dh = dir > 0 ? c.fh : c.rh, off = dir > 0 ? c.fo : c.ro;
        float zo = dir * c.d / 2, zi = zo - dir * T;
        float z1 = Math.min(zo, zi), z2 = Math.max(zo, zi);
        float w = c.w, h = c.h;
        VertexConsumer ret = RenderUtil.solid(c.buf, c.theme == Spec.Theme.WOOD || c.theme == Spec.Theme.WHITE || c.theme == Spec.Theme.MIRROR
                || c.theme == Spec.Theme.GLASS || c.theme == Spec.Theme.CHECKER ? "panel_stainless" : "panel_" + c.theme.tex());
        if (dw <= 0) { RenderUtil.panel(c.ps, ret, -w / 2 - T, 0, z1, w / 2 + T, h, z2, c.il, WHITE); return; }
        float l = off - dw / 2, r = off + dw / 2;
        CustomDesign.Surface fwS = c.cd != null ? (c.cd.frontWall != null ? c.cd.frontWall : null) : null;
        if (fwS != null) {
            // 리턴/트랜섬의 실내 쪽 면 (실내에서 정면을 볼 때 왼쪽→오른쪽 = -X 방향(정면) / +X 방향(후면))
            float zin = dir > 0 ? z1 - fwS.maxRecess() - 0.0015f : z2 + fwS.maxRecess() + 0.0015f;
            float[][] pieces = {{r, 0, w / 2 + T, h}, {-w / 2 - T, 0, l, h}, {l, dh, r, h}};
            for (float[] q : pieces) {
                if (q[2] - q[0] <= 0.01f || q[3] - q[1] <= 0.01f) continue;
                CustomDesign.Surface srf = q[1] > 0 && c.cd.transom != null ? c.cd.transom : fwS;
                Relief.Frame f = dir > 0 ? new Relief.Frame(q[2], q[1], zin, -1, 0, 0, 0, 1, 0, 0, 0, -1)
                        : new Relief.Frame(q[0], q[1], zin, 1, 0, 0, 0, 1, 0, 0, 0, 1);
                float[] uvw = null;
                if (c.cd.frontSpan && srf == fwS) {
                    // 정면 벽 전체 사진: 조각마다 자기 자리 (안에서 볼 때 왼쪽 = +X)
                    float tw = w + 2 * T, xl = w / 2 + T;
                    uvw = dir > 0 ? new float[]{(xl - q[2]) / tw, 1 - q[3] / h, (xl - q[0]) / tw, 1 - q[1] / h}
                            : new float[]{(q[0] + xl) / tw, 1 - q[3] / h, (q[2] + xl) / tw, 1 - q[1] / h};
                }
                Relief.draw(c.ps, c.buf, srf, f, q[2] - q[0], q[3] - q[1], c.il, null, uvw);
            }
        }
        if (l > -w / 2 - T) RenderUtil.panel(c.ps, ret, -w / 2 - T, 0, z1, l, h, z2, c.il, WHITE);
        if (r < w / 2 + T) RenderUtil.panel(c.ps, ret, r, 0, z1, w / 2 + T, h, z2, c.il, WHITE);
        if (dh < h) RenderUtil.panel(c.ps, ret, l, dh, z1, r, h, z2, c.il, WHITE);
        // 출입구 안쪽 테두리(리턴 모서리 그림자)
        float zs = dir > 0 ? z1 - 0.001f : z2 + 0.001f;
        RenderUtil.fill(c.ps, c.buf, l - 0.008f, 0, Math.min(zs, zs + dir * 0.001f), l, dh, Math.max(zs, zs + dir * 0.001f), c.il, 0xFF5A5E62);
        RenderUtil.fill(c.ps, c.buf, r, 0, Math.min(zs, zs + dir * 0.001f), r + 0.008f, dh, Math.max(zs, zs + dir * 0.001f), c.il, 0xFF5A5E62);
    }

    /** 카 도어: 정면 벽 바깥, 빠른 문짝이 가장 바깥(승강장 쪽) */
    private void carDoor(Ctx c, Spec.DoorType type, float W, float H, float off, boolean rear, float p) {
        boolean glass = c.theme == Spec.Theme.GLASS;
        String tex = doorTex(c);
        VertexConsumer vc = glass ? RenderUtil.glass(c.buf, tex) : RenderUtil.solid(c.buf, tex);
        List<float[]> rects = DoorLayout.panels(type, W, H, p);
        int layers = 1;
        for (float[] r : rects) layers = Math.max(layers, (int) r[4] + 1);
        boolean mirror = c.right && type.sideOpening();
        float plane = rear ? -c.d / 2 : c.d / 2;
        int[] rank = DoorLayout.rank(type, W, H, mirror);
        int wm = c.st.getBoolean("win") ? (c.st.contains("winm") ? c.st.getInt("winm") : -1) : 0;
        for (int ri = 0; ri < rects.size(); ri++) {
            float[] r = rects.get(ri);
            float a1 = mirror ? W - r[1] : r[0], a2 = mirror ? W - r[0] : r[1];
            float lead = mirror ? -r[6] : r[6];
            // 문 좌표 → 카 x (후면 문은 축이 반대)
            float x1 = rear ? off + W / 2 - a2 : off - W / 2 + a1;
            float x2 = rear ? off + W / 2 - a1 : off - W / 2 + a2;
            if (rear) lead = -lead;
            float k = 0.004f + (layers - 1 - r[4]) * 0.008f; // 빠른 문짝(layer 0)이 가장 바깥
            float z1 = rear ? plane - k - 0.006f : plane + k;
            float z2 = rear ? plane - k : plane + k + 0.006f;
            if (r[5] == 1) { RenderUtil.fill(c.ps, c.buf, x1, r[2], z1, x2, r[3], z2, c.il, 0xFF6E7378); continue; }
            float[] win = glass ? null : RenderUtil.windowRect(DoorLayout.hasWindow(wm, rank[ri]), x1, x2, r[2], r[3]);
            CustomDesign.Surface ds = c.cdDoor != null ? c.cdDoor.door : null;
            RenderUtil.doorPanel(c.ps, c.buf, vc, x1, r[2], z1, x2, r[3], z2, win, ds == null || !rear, ds == null || rear, c.il, WHITE);
            if (ds != null) {
                // 카 안쪽 면: 실내에서 볼 때 왼쪽→오른쪽 좌표계. 오목한 부분이 보이도록 깊이만큼 실내 쪽으로 띄움
                float lift = ds.maxRecess() + 0.0005f;
                float pw = x2 - x1, ph = r[3] - r[2];
                float[] lw = null;
                Relief.Frame f;
                if (!rear) {
                    f = new Relief.Frame(x2, r[2], z1 - lift, -1, 0, 0, 0, 1, 0, 0, 0, -1);
                    if (win != null) lw = new float[]{x2 - win[2], win[1] - r[2], x2 - win[0], win[3] - r[2]};
                } else {
                    f = new Relief.Frame(x1, r[2], z2 + lift, 1, 0, 0, 0, 1, 0, 0, 0, 1);
                    if (win != null) lw = new float[]{win[0] - x1, win[1] - r[2], win[2] - x1, win[3] - r[2]};
                }
                float[] uvw = null;
                if (c.cdDoor.doorSpan) {
                    // 카 안에서 찍은 출입구 전체 사진: 이 문짝이 닫혔을 때 차지하는 부분
                    float[] kk = DoorLayout.panels(type, W, H, 0).get(ri);
                    float c1 = mirror ? W - kk[1] : kk[0], c2 = mirror ? W - kk[0] : kk[1];
                    uvw = new float[]{(W - c2) / W, 1 - kk[3] / H, (W - c1) / W, 1 - kk[2] / H};
                }
                Relief.draw(c.ps, c.buf, ds, f, pw, ph, c.il, lw, uvw);
                // 띄운 만큼 문짝 옆면을 막음
                if (lift > 0.002f) {
                    float za = rear ? z2 : z1 - lift, zb = rear ? z2 + lift : z1;
                    RenderUtil.fill(c.ps, c.buf, x1, r[2], za, x1 + 0.001f, r[3], zb, c.il, 0xFF8A8E92);
                    RenderUtil.fill(c.ps, c.buf, x2 - 0.001f, r[2], za, x2, r[3], zb, c.il, 0xFF8A8E92);
                }
            }
            // 세이프티 슈 / 멀티빔(검은 띠) – 앞날 안쪽 면
            if (lead != 0 && H > 1.2f) {
                float ex = lead > 0 ? x2 - 0.018f : x1, ex2 = lead > 0 ? x2 : x1 + 0.018f;
                float zi = rear ? z2 : z1 - 0.003f, zj = rear ? z2 + 0.003f : z1;
                RenderUtil.fill(c.ps, c.buf, ex, 0.04f, zi, ex2, H - 0.06f, zj, c.il, 0xFF141414);
            }
        }
    }

    private void handrails(Ctx c) {
        float y = 0.9f, r = 0.018f;
        int col = 0xFFD8DCE0;
        if (c.cd != null && c.cd.handrail != null) {
            CustomDesign.Rail hr = c.cd.handrail;
            if (hr.shape.equals("none")) return;
            y = hr.y; r = hr.r; col = hr.color;
            if (hr.shape.equals("flat")) {
                float t = r * 0.5f;
                float z1 = -c.d / 2 + 0.18f, z2 = c.d / 2 - T - 0.28f;
                if (hr.sides) for (int sx = -1; sx <= 1; sx += 2) {
                    float x = sx * (c.w / 2 - 0.05f);
                    if (z2 > z1) RenderUtil.fill(c.ps, c.buf, x - t, y - r, z1, x + t, y + r, z2, c.il, col);
                }
                if (hr.back && c.rw <= 0) RenderUtil.fill(c.ps, c.buf, -c.w / 2 + 0.18f, y - r, -c.d / 2 + 0.05f - t, c.w / 2 - 0.18f, y + r, -c.d / 2 + 0.05f + t, c.il, col);
                return;
            }
        }
        float z1 = -c.d / 2 + 0.18f, z2 = c.d / 2 - T - 0.28f;
        for (int sx = -1; sx <= 1; sx += 2) {
            float x = sx * (c.w / 2 - 0.055f);
            if (z2 > z1) {
                RenderUtil.rod(c.ps, c.buf, 'z', z1, z2, x, y, r, c.il, col);
                for (float bz : new float[]{z1 + 0.05f, z2 - 0.05f})
                    RenderUtil.fill(c.ps, c.buf, Math.min(x, sx * c.w / 2), y - 0.012f, bz - 0.012f, Math.max(x, sx * c.w / 2), y + 0.012f, bz + 0.012f, c.il, col);
            }
        }
        if (c.rw <= 0) {
            float z = -c.d / 2 + 0.055f;
            RenderUtil.rod(c.ps, c.buf, 'x', -c.w / 2 + 0.18f, c.w / 2 - 0.18f, y, z, r, c.il, col);
            for (float bx : new float[]{-c.w / 2 + 0.25f, c.w / 2 - 0.25f})
                RenderUtil.fill(c.ps, c.buf, bx - 0.012f, y - 0.012f, -c.d / 2, bx + 0.012f, y + 0.012f, z, c.il, col);
        }
    }

    private void bumpers(Ctx c) {
        int col = 0xFF7A5A36;
        for (float y : new float[]{0.32f, 0.9f}) {
            for (int sx = -1; sx <= 1; sx += 2) {
                float xi = sx * c.w / 2, xb = xi - sx * 0.05f;
                RenderUtil.fill(c.ps, c.buf, Math.min(xi, xb), y - 0.06f, -c.d / 2 + 0.05f, Math.max(xi, xb), y + 0.06f, c.d / 2 - T - 0.1f, c.il, col);
            }
            if (c.rw <= 0) RenderUtil.fill(c.ps, c.buf, -c.w / 2 + 0.05f, y - 0.06f, -c.d / 2, c.w / 2 - 0.05f, y + 0.06f, -c.d / 2 + 0.05f, c.il, col);
        }
    }

    private void ceiling(Ctx c, boolean mesh) {
        PoseStack ps = c.ps;
        MultiBufferSource buf = c.buf;
        float w = c.w, h = c.h, d = c.d;
        int glow = c.lights ? RenderUtil.FULL : c.il;
        int lightCol = c.lights ? 0xFFFFFFFF : 0xFF707070;
        // 카 상부 외피
        CustomDesign.Surface cs = c.cd != null && !mesh ? c.cd.ceiling : null;
        float topY = h + (cs != null ? cs.maxRecess() + 0.01f : 0);
        RenderUtil.box(ps, RenderUtil.solid(buf, "steel"), -w / 2 - T, topY, -d / 2 - T, w / 2 + T, topY + 0.05f, d / 2, c.light, WHITE);
        if (cs != null) {
            // 커스텀 천장: 아래를 향하는 면, 안쪽에서 올려다볼 때 뒤→앞이 화면 위쪽
            Relief.draw(ps, buf, cs, new Relief.Frame(-w / 2, h - 0.004f, -d / 2, 1, 0, 0, 0, 0, 1, 0, -1, 0), w, d - T, c.lights ? c.il : c.il, null);
            RenderUtil.fill(ps, buf, w / 2 - 0.22f, h - 0.016f, -d / 2 + 0.05f, w / 2 - 0.06f, h - 0.004f, -d / 2 + 0.11f, c.emg ? RenderUtil.FULL : c.il, c.emg ? 0xFFFFE070 : 0xFFE8E8E0);
            return;
        }
        if (mesh || c.usage == Spec.Usage.DUMBWAITER) {
            RenderUtil.quadY(ps, RenderUtil.solid(buf, "ceiling_panel"), -w / 2, -d / 2, w / 2, d / 2 - T, h - 0.001f, true, c.il, 0xFFC0C0C0);
            return;
        }
        float cy = h - 0.004f;
        VertexConsumer panel = RenderUtil.solid(buf, "ceiling_panel");
        switch (c.ceiling) {
            case PANEL -> {
                RenderUtil.quadY(ps, panel, -w / 2, -d / 2, w / 2, d / 2 - T, cy, true, c.il, 0xFFD6D8DA);
                RenderUtil.quadY(ps, RenderUtil.solid(buf, "ceiling_light"), -w / 2 + 0.14f, -d / 2 + 0.14f, w / 2 - 0.14f, d / 2 - T - 0.14f, cy - 0.003f, true, glow, lightCol);
                // 조명 테두리
                RenderUtil.fill(ps, buf, -w / 2 + 0.12f, cy - 0.02f, -d / 2 + 0.12f, w / 2 - 0.12f, cy - 0.004f, -d / 2 + 0.14f, c.il, STEEL);
                RenderUtil.fill(ps, buf, -w / 2 + 0.12f, cy - 0.02f, d / 2 - T - 0.14f, w / 2 - 0.12f, cy - 0.004f, d / 2 - T - 0.12f, c.il, STEEL);
                RenderUtil.fill(ps, buf, -w / 2 + 0.12f, cy - 0.02f, -d / 2 + 0.12f, -w / 2 + 0.14f, cy - 0.004f, d / 2 - T - 0.12f, c.il, STEEL);
                RenderUtil.fill(ps, buf, w / 2 - 0.14f, cy - 0.02f, -d / 2 + 0.12f, w / 2 - 0.12f, cy - 0.004f, d / 2 - T - 0.12f, c.il, STEEL);
            }
            case GRID -> {
                RenderUtil.quadY(ps, panel, -w / 2, -d / 2, w / 2, d / 2 - T, cy, true, c.il, 0xFFB8BCC0);
                RenderUtil.box(ps, RenderUtil.solid(buf, "ceiling_grid"), -w / 2 + 0.1f, cy - 0.006f, -d / 2 + 0.1f, w / 2 - 0.1f, cy - 0.003f, d / 2 - T - 0.1f, glow, lightCol);
                RenderUtil.fill(ps, buf, -0.012f, cy - 0.03f, -d / 2 + 0.1f, 0.012f, cy - 0.006f, d / 2 - T - 0.1f, c.il, STEEL);
                RenderUtil.fill(ps, buf, -w / 2 + 0.1f, cy - 0.03f, -0.012f, w / 2 - 0.1f, cy - 0.006f, 0.012f, c.il, STEEL);
            }
            case DOWNLIGHT -> {
                RenderUtil.quadY(ps, RenderUtil.solid(buf, "panel_stainless"), -w / 2, -d / 2, w / 2, d / 2 - T, cy, true, c.il, WHITE);
                VertexConsumer dl = RenderUtil.cut(buf, "downlight");
                int nx = Math.max(1, Math.round(w / 0.6f)), nz = Math.max(1, Math.round((d - T) / 0.7f));
                for (int i = 0; i < nx; i++) for (int j = 0; j < nz; j++) {
                    float x = -w / 2 + w * (i + 0.5f) / nx, z = -d / 2 + (d - T) * (j + 0.5f) / nz;
                    RenderUtil.quadY(ps, dl, x - 0.07f, z - 0.07f, x + 0.07f, z + 0.07f, cy - 0.002f, true, glow, lightCol);
                }
            }
            case COVE -> {
                RenderUtil.quadY(ps, RenderUtil.solid(buf, "panel_stainless"), -w / 2 + 0.16f, -d / 2 + 0.16f, w / 2 - 0.16f, d / 2 - T - 0.16f, cy - 0.06f, true, c.il, WHITE);
                RenderUtil.quadY(ps, RenderUtil.solid(buf, "ceiling_light"), -w / 2, -d / 2, w / 2, d / 2 - T, cy, true, glow, lightCol);
            }
        }
        // 비상등 / 환기구 / CCTV
        RenderUtil.fill(ps, buf, w / 2 - 0.22f, cy - 0.012f, -d / 2 + 0.05f, w / 2 - 0.06f, cy, -d / 2 + 0.11f, c.emg ? RenderUtil.FULL : c.il, c.emg ? 0xFFFFE070 : 0xFFE8E8E0);
        RenderUtil.fill(ps, buf, -0.18f, cy - 0.006f, d / 2 - T - 0.1f, 0.18f, cy, d / 2 - T - 0.04f, c.il, c.st.getBoolean("fan") ? 0xFF2A2E32 : 0xFF3A3E42);
        RenderUtil.fill(ps, buf, -w / 2 + 0.06f, cy - 0.05f, d / 2 - T - 0.12f, -w / 2 + 0.12f, cy, d / 2 - T - 0.06f, c.il, 0xFF101010);
    }

    // ================================================================== 운전반
    private static String faceTex(Spec.ButtonStyle st) {
        return switch (st) {
            case TOUCH -> "cop_face_black";
            case CLASSIC -> "cop_face_classic";
            default -> "cop_face";
        };
    }

    private void cop(Ctx c) {
        for (CarGeometry.Cop cop : CarGeometry.cops(c.car)) {
            float[] b = cop.box();
            PoseStack ps = c.ps;
            ps.pushPose();
            if (cop.wall() == 0) ps.translate(0, 0, -frontLift(c, false)); // 리턴 장식 위로 띄움
            RenderUtil.panel(ps, RenderUtil.solid(c.buf, faceTex(c.copSt)), b[0], b[1], b[2], b[3], b[4], b[5], c.il, WHITE);
            ps.pushPose();
            // 판넬 앞면을 원점, 카 안쪽을 +Z, 오른쪽을 +X 로 하는 좌표계로 변환
            float pw;
            if (cop.wall() == 0) {
                ps.translate((b[0] + b[3]) / 2, 0, b[2]);
                ps.mulPose(Axis.YP.rotationDegrees(180));
                pw = b[3] - b[0];
            } else {
                float face = cop.wall() > 0 ? b[0] : b[3];
                ps.translate(face, 0, (b[2] + b[5]) / 2);
                ps.mulPose(Axis.YP.rotationDegrees(cop.wall() > 0 ? -90 : 90));
                pw = b[5] - b[2];
            }
            CustomDesign.PanelPart pp = c.cdCop != null ? c.cdCop.copPart : null;
            CustomDesign.Surface face = pp != null ? pp.face : c.cdCop != null ? c.cdCop.cop : null;
            if (face != null) {
                float off = face.maxRecess() + 0.0015f;
                Relief.draw(ps, c.buf, face, new Relief.Frame(-pw / 2, b[1], off, 1, 0, 0, 0, 1, 0, 0, 0, 1), pw, b[4] - b[1], c.il, null);
                ps.translate(0, 0, off + face.maxRaise() + 0.0005f);
            }
            if (pp == null || !customCopFace(c, pp, pw, b[1], b[4], c.copSt)) copFace(c, pw, b[1], b[4]);
            ps.popPose();
            ps.popPose();
        }
    }

    /** 버튼 하나 (로컬 z 평면, +Z 가 사람 쪽) */
    private void button(Ctx c, Spec.ButtonStyle st, float x, float y, float size, boolean lit, String label, float z, boolean alarm) {
        String tex = switch (st) {
            case SQUARE -> lit ? "btn_sq_lit" : "btn_sq";
            case TOUCH -> lit ? "btn_touch_lit" : "btn_touch";
            case CLASSIC -> lit ? "btn_classic_lit" : "btn_classic";
            default -> lit ? "btn_lit" : "btn";
        };
        if (alarm && st != Spec.ButtonStyle.TOUCH) tex = "btn_alarm";
        float s = size / 2;
        RenderUtil.quadZ(c.ps, RenderUtil.cut(c.buf, tex), x - s, y - s, x + s, y + s, z + 0.004f, true, lit ? RenderUtil.FULL : c.il, WHITE);
        int col = switch (st) {
            case TOUCH -> lit ? 0xFF7FE8FF : 0xFFD8DCE0;
            case CLASSIC -> lit ? 0xFFFF5A20 : 0xFF141414;
            default -> lit ? 0xFFFFB020 : 0xFF1A1A1A;
        };
        if (alarm) col = st == Spec.ButtonStyle.TOUCH ? 0xFFFFC040 : 0xFF1A1A1A;
        float ts = label.length() > 2 ? size * 0.28f : size * 0.42f;
        RenderUtil.text(c.ps, c.buf, label, x, y + size * 0.04f, z + 0.005f, ts, col);
    }

    private static String pad2(String s) { return s.length() >= 2 ? s : " " + s; }

    /** 위치표시 창: 소자 종류에 따라 7세그먼트 / 도트매트릭스 / LCD */
    private void indicator(Ctx c, float x1, float y1, float x2, float y2, float z, String cur, int dir) {
        indicator(c, x1, y1, x2, y2, z, cur, dir, null);
    }

    private void indicator(Ctx c, float x1, float y1, float x2, float y2, float z, String cur, int dir, String key) {
        String type = switch (c.disp) { case DOT -> "dot"; case LCD -> "lcd"; default -> "seg"; };
        int col = switch (c.disp) { case DOT -> 0xFFFF8A18; case LCD -> 0xFF7FE3FF; default -> 0xFFFF2A10; };
        com.mchoe.elevmod.elevator.IndicatorProfile pr = key != null && key.startsWith(":tr") ? c.indT : key != null && key.startsWith(":cop") ? c.indC : c.ind;
        PanelDraw.display(c.ps, c.buf, pr, key == null ? null : c.car.getId() + key, type, col, 0xFF0E2E66, x1, y1, x2, y2, z, c.il, cur, dir, c.moving, c.open);
    }

    /**
     * 사진으로 만든 운전반: 사진에서 찾은 표시창 자리에 실제 층 표시, 찾은 버튼 자리에 층 배정·불빛.
     * 버튼이 층 수보다 적게 찾아졌으면 false (기본 버튼 배열을 사진 위에 그림).
     */
    private boolean customCopFace(Ctx c, CustomDesign.PanelPart pp, float pw, float y1, float y2, Spec.ButtonStyle st) {
        ListTag names = c.st.getList("n", Tag.TAG_STRING);
        byte[] calls = c.st.getByteArray("c");
        int n = names.size();
        float H = y2 - y1, x0 = -pw / 2;
        String cur = n > 0 ? names.getString(Math.max(0, Math.min(c.st.getInt("cur"), n - 1))) : "";
        if (pp.display != null) {
            float[] d = pp.display;
            float ddx = c.st.getFloat("cdx") / 100f / pw, ddy = c.st.getFloat("cdy") / 100f / H, ddw = c.st.getFloat("cdw") / 100f / pw, ddh = c.st.getFloat("cdh") / 100f / H;
            d = new float[]{d[0] + ddx - ddw / 2, d[1] + ddy, d[2] + ddx + ddw / 2, d[3] + ddy + ddh};
            PanelDraw.display(c.ps, c.buf, c.indC, c.car.getId() + ":cc" + pw + y1, pp.dispType, pp.digit, pp.lcdBg, x0 + d[0] * pw, y1 + d[1] * H, x0 + d[2] * pw, y1 + d[3] * H, 0.001f, c.il, cur, c.st.getInt("dir"), c.moving, c.open);
        }
        if (!c.cbtn || c.tenkey || pp.buttons.size() < n) return false;
        int[] fl = PanelDraw.assignFloors(pp.buttons, n);
        for (int i = 0; i < pp.buttons.size(); i++) {
            if (fl[i] < 0) continue;
            float[] b = pp.buttons.get(i);
            float bx = x0 + b[0] * pw, by = y1 + b[1] * H, br = b[2] * pw;
            boolean lit = fl[i] < calls.length && (calls[fl[i]] & 1) != 0;
            if (lit) PanelDraw.ring(c.ps, c.buf, bx, by, br, 0.002f, pp.lit);
            if (pp.labels) RenderUtil.text(c.ps, c.buf, names.getString(fl[i]), bx, by, 0.003f, br * 0.9f, lit ? pp.lit : 0xFF202020);
        }
        return true;
    }

    /** 운전반 면 그리기 (로컬: x ∈ [-pw/2, pw/2], z=0 이 면, +Z 가 사람 쪽) */
    private void copFace(Ctx c, float pw, float y1, float y2) {
        PoseStack ps = c.ps;
        MultiBufferSource buf = c.buf;
        CompoundTag st = c.st;
        ListTag names = st.getList("n", Tag.TAG_STRING);
        byte[] calls = st.getByteArray("c");
        int n = names.size();
        float z = 0.001f;
        Spec.ButtonStyle bs0 = c.copSt;
        boolean touch = bs0 == Spec.ButtonStyle.TOUCH;
        int engrave = touch ? 0xFFB8BCC0 : 0xFF303030;
        // 표시창
        // 제어반에서 정한 표시창 위치·크기 조정 (cm)
        float ddx = st.getFloat("cdx") / 100f, ddy = st.getFloat("cdy") / 100f, ddw = st.getFloat("cdw") / 100f, ddh = st.getFloat("cdh") / 100f;
        float dy1 = y2 - 0.17f + ddy, dy2 = y2 - 0.05f + ddy + ddh;
        float dx1 = -pw / 2 + 0.03f + ddx - ddw / 2, dx2 = pw / 2 - 0.03f + ddx + ddw / 2;
        if (dx2 - dx1 < 0.03f) dx2 = dx1 + 0.03f;
        if (dy2 - dy1 < 0.03f) dy2 = dy1 + 0.03f;
        String cur = n > 0 ? names.getString(Math.max(0, Math.min(st.getInt("cur"), n - 1))) : "";
        indicator(c, dx1, dy1, dx2, dy2, z + 0.002f, cur, st.getInt("dir"), ":cop" + pw + y1);
        String sk = st.getString("st");
        if (!sk.isEmpty()) RenderUtil.text(ps, buf, I18n.get("elevmod.status." + sk), 0, dy1 - 0.012f, z + 0.003f, 0.016f, touch ? 0xFFFFD040 : 0xFFB06000);
        // 스피커 그릴
        for (int i = 0; i < 5; i++) {
            float sx = -0.04f + i * 0.02f;
            RenderUtil.fill(ps, buf, sx - 0.004f, dy1 - 0.05f, 0, sx + 0.004f, dy1 - 0.03f, z + 0.001f, c.il, touch ? 0xFF3A3E44 : 0xFF2A2C30);
        }
        float top = dy1 - 0.07f, bottom = y1 + 0.2f;
        if (!c.cbtn) {
            // 행선층 예약(DD) 카: 층 버튼 없음 – 등록된 행선층만 표시
            float sy = top - 0.09f;
            Glyphs.rect(ps, buf, -pw / 2 + 0.03f, sy, pw / 2 - 0.03f, top, z + 0.002f, RenderUtil.FULL, 0xFF0A1E3C);
            RenderUtil.text(ps, buf, I18n.get("elevmod.cop.dest_list"), 0, top - 0.016f, z + 0.003f, 0.014f, 0xFF9FD8FF);
            RenderUtil.text(ps, buf, litList(names, calls, 5), 0, sy + 0.03f, z + 0.003f, 0.024f, WHITE);
        } else if (c.tenkey) {
            tenkeyPad(c, bs0, -pw / 2 + 0.02f, pw / 2 - 0.02f, bottom, top, z, 3, names, calls);
        } else {
            // 층 버튼
            int cols = n <= 12 ? 2 : n <= 27 ? 3 : 4;
            int rows = Math.max(1, (n + cols - 1) / cols);
            float cell = Math.min(0.075f, (top - bottom) / rows);
            float colW = Math.min(0.075f, (pw - 0.04f) / cols);
            float bs = Math.min(cell, colW) * 0.78f;
            for (int i = 0; i < n; i++) {
                int r = (n - 1 - i) / cols, col = i % cols;
                float x = (col - (cols - 1) / 2f) * colW;
                float y = top - r * cell - cell / 2;
                boolean lit = i < calls.length && (calls[i] & 1) != 0;
                button(c, bs0, x, y, bs, lit, names.getString(i), z, false);
            }
        }
        // 열림 / 닫힘 / 비상벨
        float by = y1 + 0.11f;
        float gap = Math.min(0.065f, (pw - 0.04f) / 3);
        float b2 = Math.min(0.058f, gap * 0.8f);
        String[] lab = {"◀▶", "▶◀", "!"};
        for (int i = 0; i < 3; i++) button(c, bs0, (i - 1) * gap, by, b2, false, lab[i], z, i == 2);
        // 키 스위치 + 정격 표기
        RenderUtil.fill(ps, buf, -0.012f, y1 + 0.03f, 0, 0.012f, y1 + 0.055f, z + 0.002f, c.il, 0xFF202020);
        int kg = st.getInt("kg");
        RenderUtil.text(ps, buf, Spec.persons(kg) + "인승 " + kg + "kg", 0, y1 + 0.175f, z + 0.002f, 0.013f, engrave);
        if (st.getBoolean("stop"))
            RenderUtil.quadZ(ps, RenderUtil.cut(buf, "btn"), pw / 2 - 0.05f, y1 + 0.02f, pw / 2 - 0.015f, y1 + 0.055f, z + 0.004f, true, st.getBoolean("sp") ? RenderUtil.FULL : c.il, 0xFFFF4040);
    }

    /** 등록된 층 목록 문자열 (최대 max 개) */
    private static String litList(ListTag names, byte[] calls, int max) {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (int i = 0; i < names.size() && k < max; i++) {
            if (i < calls.length && (calls[i] & 1) != 0) { if (sb.length() > 0) sb.append(' '); sb.append(names.getString(i)); k++; }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /**
     * 텐키(10-KEY) 패드: 숫자 0~9, B(지하), C(정정/취소), 등록. 위에 예약층(등록된 행선층) 표시창.
     * cols = 3 이면 세로형(주 운전반), 7 이면 가로형(장애인 운전반).
     */
    private void tenkeyPad(Ctx c, Spec.ButtonStyle bst, float x1, float x2, float yb, float yt, float z, int cols, ListTag names, byte[] calls) {
        String[] keys = cols == 3
                ? new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "B", "0", "C"}
                : new String[]{"1", "2", "3", "4", "5", "6", "B", "7", "8", "9", "0", "C"};
        float stripH = cols == 3 ? 0.06f : 0f;
        if (stripH > 0) {
            Glyphs.rect(c.ps, c.buf, x1 + 0.01f, yt - stripH, x2 - 0.01f, yt, z + 0.002f, RenderUtil.FULL, 0xFF0A1E3C);
            RenderUtil.text(c.ps, c.buf, litList(names, calls, 4), (x1 + x2) / 2, yt - stripH / 2, z + 0.003f, 0.02f, WHITE);
        }
        int rows = cols == 3 ? 5 : 2;
        int perRow = cols == 3 ? 3 : 7;
        float cw = (x2 - x1) / perRow, rh = (yt - stripH - 0.01f - yb) / rows;
        float bs = Math.min(cw, rh) * 0.8f;
        for (int i = 0; i < keys.length; i++) {
            int r = i / perRow, col = i % perRow;
            float x = x1 + cw * (col + 0.5f), y = yt - stripH - 0.01f - rh * (r + 0.5f);
            button(c, bst, x, y, bs, false, keys[i], z, false);
        }
        // 등록 키
        if (cols == 3) {
            float y = yt - stripH - 0.01f - rh * 4.5f;
            float kx1 = x1 + cw * 0.12f, kx2 = x2 - cw * 0.12f;
            String tex = bst == Spec.ButtonStyle.TOUCH ? "btn_touch" : "btn_sq";
            RenderUtil.quadZ(c.ps, RenderUtil.cut(c.buf, tex), kx1, y - bs / 2, kx2, y + bs / 2, z + 0.004f, true, c.il, WHITE);
            RenderUtil.text(c.ps, c.buf, I18n.get("elevmod.tenkey.enter"), (x1 + x2) / 2, y, z + 0.006f, bs * 0.34f, bst == Spec.ButtonStyle.TOUCH ? 0xFFD8DCE0 : 0xFF1A1A1A);
        } else {
            int i = keys.length;
            float x = x1 + cw * (i % perRow + 0.5f), y = yt - rh * (i / perRow + 0.5f);
            button(c, bst, x, y, bs, false, I18n.get("elevmod.tenkey.enter"), z, false);
        }
    }

    private void accessibleCop(Ctx c, float[] b) {
        PoseStack ps = c.ps;
        boolean leftWall = b[0] > 0; // +X 벽
        RenderUtil.panel(ps, RenderUtil.solid(c.buf, faceTex(c.accSt)), b[0], b[1], b[2], b[3], b[4], b[5], c.il, WHITE);
        ps.pushPose();
        ps.translate(leftWall ? b[0] : b[3], 0, (b[2] + b[5]) / 2);
        ps.mulPose(Axis.YP.rotationDegrees(leftWall ? -90 : 90)); // +Z 가 카 안쪽
        float len = b[5] - b[2];
        CustomDesign.PanelPart ap = c.cdAcc != null ? c.cdAcc.accPart : null;
        if (ap != null && ap.face != null) {
            float off = ap.face.maxRecess() + 0.0015f;
            Relief.draw(ps, c.buf, ap.face, new Relief.Frame(-len / 2, b[1], off, 1, 0, 0, 0, 1, 0, 0, 0, 1), len, b[4] - b[1], c.il, null);
            ps.translate(0, 0, off + ap.face.maxRaise() + 0.0005f);
            if (customCopFace(c, ap, len, b[1], b[4], c.accSt)) { ps.popPose(); return; }
        }
        ListTag names = c.st.getList("n", Tag.TAG_STRING);
        byte[] calls = c.st.getByteArray("c");
        int n = names.size();
        float y1 = b[1], y2 = b[4];
        // 오른쪽 끝: 열림/닫힘/비상
        float side = 0.075f;
        float colX = len / 2 - 0.02f - side / 2;
        float rh3 = (y2 - y1 - 0.02f) / 3;
        float sb = Math.min(side * 0.8f, rh3 * 0.82f);
        String[] lab = {"◀▶", "▶◀", "!"};
        for (int i = 0; i < 3; i++) button(c, c.accSt, colX, y2 - 0.01f - rh3 * (i + 0.5f), sb, false, lab[i], 0, i == 2);
        float ax1 = -len / 2 + 0.02f, ax2 = len / 2 - 0.03f - side;
        if (!c.cbtn) {
            Glyphs.rect(ps, c.buf, ax1, y1 + 0.03f, ax2, y2 - 0.03f, 0.003f, RenderUtil.FULL, 0xFF0A1E3C);
            RenderUtil.text(ps, c.buf, litList(names, calls, 5), (ax1 + ax2) / 2, (y1 + y2) / 2, 0.004f, 0.026f, WHITE);
        } else if (c.tenkey) {
            tenkeyPad(c, c.accSt, ax1, ax2, y1 + 0.01f, y2 - 0.01f, 0, 7, names, calls);
        } else {
            float al = ax2 - ax1;
            int perRow = Math.max(1, Math.min(n, (int) (al / 0.055f)));
            int rows = Math.max(1, (n + perRow - 1) / perRow);
            float cell = al / perRow;
            float rowH = (y2 - y1 - 0.03f) / rows;
            float bs = Math.min(cell, rowH) * 0.75f;
            for (int i = 0; i < n; i++) {
                int r = i / perRow, col = i % perRow;
                float x = ax1 + cell * (col + 0.5f);
                float y = y2 - 0.015f - rowH * (r + 0.5f);
                boolean lit = i < calls.length && (calls[i] & 1) != 0;
                button(c, c.accSt, x, y, bs, lit, names.getString(i), 0, false);
            }
        }
        ps.popPose();
    }

    /** 커스텀 정면 벽(리턴/트랜섬)이 실내로 튀어나온 두께 – 그 위에 붙는 운전반·표시기를 그만큼 띄운다 */
    private static float frontLift(Ctx c, boolean transom) {
        if (c.cd == null || c.cd.frontWall == null) return 0;
        CustomDesign.Surface s = transom && c.cd.transom != null ? c.cd.transom : c.cd.frontWall;
        return s.maxRecess() + s.maxRaise() + 0.003f;
    }

    /** 트랜섬(문 위) 위치표시기 */
    private void transomIndicator(Ctx c) {
        float[] b = CarGeometry.transom(c.car);
        if (b == null) return;
        float lift = frontLift(c, true);
        c.ps.pushPose();
        c.ps.translate(0, 0, -lift);
        ListTag names = c.st.getList("n", Tag.TAG_STRING);
        int n = names.size();
        float z = c.d / 2 - T;
        // 테두리(스테인리스 프레임)
        if (c.cdInd == null || c.cdInd.indicator == null)
            RenderUtil.fill(c.ps, c.buf, b[0] - 0.012f, b[1] - 0.012f, z - 0.004f, b[3] + 0.012f, b[4] + 0.012f, z, c.il, 0xFF9AA0A6);
        c.ps.pushPose();
        c.ps.translate((b[0] + b[3]) / 2, 0, z - 0.005f);
        c.ps.mulPose(Axis.YP.rotationDegrees(180));
        String cur = n == 0 ? "" : names.getString(Math.max(0, Math.min(c.st.getInt("cur"), n - 1)));
        CustomDesign.PanelPart ip = c.cdInd != null ? c.cdInd.indicator : null;
        if (ip != null) {
            // 사진 표시기: 판 크기 = 사진 비율(높이는 트랜섬에 맞춤)
            float hh = b[4] - b[1], ww = ip.w > 0 && ip.h > 0 ? Math.min(0.9f, hh * ip.w / ip.h) : 0.32f;
            PanelDraw.face(c.ps, c.buf, ip, -ww / 2, b[1], ww, hh, 0.0005f, c.il);
            float zf = (ip.face != null ? ip.face.maxRaise() : 0) + 0.0015f;
            float[] d = ip.display != null ? ip.display : new float[]{0.1f, 0.15f, 0.9f, 0.85f};
            PanelDraw.display(c.ps, c.buf, c.indT, c.car.getId() + ":tr", ip.dispType, ip.digit, ip.lcdBg, -ww / 2 + d[0] * ww, b[1] + d[1] * hh, -ww / 2 + d[2] * ww, b[1] + d[3] * hh, zf, c.il, cur, c.st.getInt("dir"), c.moving, c.open);
        } else {
            float hw = (b[3] - b[0]) / 2;
            indicator(c, -hw, b[1], hw, b[4], 0, cur, c.st.getInt("dir"), ":tr");
        }
        c.ps.popPose();
        c.ps.popPose();
    }

    // ================================================================== 휠체어 리프트
    private void renderLift(Ctx c) {
        float w = c.w, d = c.d, rh = 1.05f;
        int il = c.il;
        RenderUtil.box(c.ps, RenderUtil.solid(c.buf, "floor_checker"), -w / 2, -0.08f, -d / 2, w / 2, 0, d / 2, il, WHITE);
        for (int sx = -1; sx <= 1; sx += 2) {
            float x = sx * w / 2;
            RenderUtil.rod(c.ps, c.buf, 'y', 0, rh, x, -d / 2 + 0.03f, 0.02f, il, STEEL);
            RenderUtil.rod(c.ps, c.buf, 'y', 0, rh, x, d / 2 - 0.03f, 0.02f, il, STEEL);
            RenderUtil.rod(c.ps, c.buf, 'z', -d / 2, d / 2, x, rh, 0.022f, il, 0xFFE8B020);
            RenderUtil.rod(c.ps, c.buf, 'z', -d / 2, d / 2, x, 0.5f, 0.015f, il, STEEL);
        }
        RenderUtil.rod(c.ps, c.buf, 'x', -w / 2, w / 2, rh, -d / 2 + 0.03f, 0.022f, il, 0xFFE8B020);
        if (c.fw > 0) carDoor(c, Spec.DoorType.GATE, Math.min(c.fw, w), rh, 0, false, c.st.getBoolean("fo2") ? c.door : 0);
        RenderUtil.fill(c.ps, c.buf, w / 2 - 0.22f, 0.85f, -d / 2 + 0.1f, w / 2 - 0.05f, 1.15f, -d / 2 + 0.25f, il, 0xFF20406A);
    }

    // ================================================================== 승강로 설비
    private void shaft(Ctx c) {
        if (c.usage == Spec.Usage.PATERNOSTER) return;
        PoseStack ps = c.ps;
        MultiBufferSource buf = c.buf;
        float w = c.w, h = c.h, d = c.d;
        int light = c.light;
        float bot = c.st.getFloat("bot"), top = c.st.getFloat("top");
        float yb = bot - c.carAbs - 1.6f, yt = top - c.carAbs + h + 1.6f;
        VertexConsumer rail = RenderUtil.solid(buf, "rail");
        VertexConsumer steel = RenderUtil.solid(buf, "steel");
        if (c.drive == Spec.Drive.RACK_PINION) {
            RenderUtil.box(ps, rail, w / 2 + T + 0.05f, yb, -0.15f, w / 2 + T + 0.35f, yt, 0.15f, light, WHITE);
            RenderUtil.fill(ps, buf, w / 2 + T, 0, -0.2f, w / 2 + T + 0.06f, 0.6f, 0.2f, light, 0xFFE0B020);
            return;
        }
        // 가이드레일 (T형)
        for (int sx = -1; sx <= 1; sx += 2) {
            float xr = sx * (w / 2 + T + 0.1f);
            RenderUtil.box(ps, rail, xr - 0.008f, yb, -0.05f, xr + 0.008f, yt, 0.05f, light, WHITE);
            float xf = sx * (w / 2 + T + 0.16f);
            RenderUtil.box(ps, rail, Math.min(xr, xf), yb, -0.006f, Math.max(xr, xf), yt, 0.006f, light, WHITE);
        }
        // 카 프레임: 스타일(좌우 기둥), 크로스헤드, 세이프티 플랭크, 가이드슈
        for (int sx = -1; sx <= 1; sx += 2) {
            float x = sx * (w / 2 + T + 0.04f);
            RenderUtil.box(ps, steel, x - 0.03f, -0.25f, -0.06f, x + 0.03f, h + 0.25f, 0.06f, light, WHITE);
            for (float gy : new float[]{-0.2f, h + 0.18f})
                RenderUtil.fill(ps, buf, Math.min(x, x + sx * 0.06f), gy, -0.04f, Math.max(x, x + sx * 0.06f), gy + 0.1f, 0.04f, light, 0xFF303438);
        }
        RenderUtil.box(ps, steel, -w / 2 - T - 0.07f, h + 0.18f, -0.08f, w / 2 + T + 0.07f, h + 0.3f, 0.08f, light, WHITE);
        RenderUtil.box(ps, steel, -w / 2 - T - 0.07f, -0.34f, -0.08f, w / 2 + T + 0.07f, -0.22f, 0.08f, light, WHITE);
        // 도어 오퍼레이터(카 상부 정면) + 토가드
        if (c.fw > 0) {
            RenderUtil.fill(ps, buf, c.fo - c.fw * 0.9f, h + 0.05f, d / 2 - 0.25f, c.fo + c.fw * 0.9f, h + 0.22f, d / 2 + 0.02f, light, 0xFF3C4046);
            RenderUtil.fill(ps, buf, c.fo - c.fw / 2, -0.85f, d / 2 - 0.01f, c.fo + c.fw / 2, -0.12f, d / 2, light, 0xFFD0B020);
        }
        if (c.drive == Spec.Drive.HYDRAULIC) {
            float pit = bot - c.carAbs - 1.4f;
            RenderUtil.rod(ps, buf, 'y', pit, -0.34f, 0, 0, 0.07f, light, 0xFFDDE2E6);
            RenderUtil.rod(ps, buf, 'y', pit, pit + 1.0f, 0, 0, 0.12f, light, 0xFF3A4048);
            return;
        }
        int my = c.st.getInt("my");
        float ropeTop = my != Integer.MIN_VALUE ? my + 0.5f - c.carAbs : top - c.carAbs + h + 2.5f;
        if (c.drive == Spec.Drive.MRL) ropeTop = top - c.carAbs + h + 0.8f;
        for (float rx : new float[]{-0.045f, -0.015f, 0.015f, 0.045f}) RenderUtil.rod(ps, buf, 'y', h + 0.3f, ropeTop, rx, 0, 0.006f, light, 0xFF55595E);
        if (!c.drive.hasCounterweight()) return;
        float cwH = 1.4f;
        float cwBottom = (top + h - cwH) - (c.carAbs - bot) - c.carAbs;
        float cx1, cx2, cz1, cz2;
        if (c.rw > 0) { cx1 = w / 2 + T + 0.24f; cx2 = w / 2 + T + 0.5f; cz1 = -0.32f; cz2 = 0.32f; }
        else { cx1 = -0.42f; cx2 = 0.42f; cz1 = -d / 2 - T - 0.36f; cz2 = -d / 2 - T - 0.12f; }
        RenderUtil.box(ps, RenderUtil.solid(buf, "counterweight"), cx1 + 0.04f, cwBottom + 0.08f, cz1, cx2 - 0.04f, cwBottom + cwH - 0.08f, cz2, light, WHITE);
        RenderUtil.box(ps, steel, cx1, cwBottom, cz1 - 0.01f, cx1 + 0.04f, cwBottom + cwH, cz2 + 0.01f, light, WHITE);
        RenderUtil.box(ps, steel, cx2 - 0.04f, cwBottom, cz1 - 0.01f, cx2, cwBottom + cwH, cz2 + 0.01f, light, WHITE);
        RenderUtil.box(ps, steel, cx1, cwBottom + cwH - 0.08f, cz1 - 0.01f, cx2, cwBottom + cwH, cz2 + 0.01f, light, WHITE);
        float ccx = (cx1 + cx2) / 2, ccz = (cz1 + cz2) / 2;
        for (float rx : new float[]{-0.03f, 0f, 0.03f}) RenderUtil.rod(ps, buf, 'y', cwBottom + cwH, ropeTop, ccx + rx, ccz, 0.006f, light, 0xFF55595E);
    }
}

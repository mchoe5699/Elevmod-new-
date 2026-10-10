package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.elevator.Spec;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.mchoe.elevmod.client.render.CarGeometry.T;

public class CarRenderer extends EntityRenderer<ElevatorCarEntity> {
    private static final int STEEL = 0xFF9AA0A6, DARK = 0xFF2A2C30, WHITE = 0xFFFFFFFF, AMBER = 0xFFFFB020, BLACK = 0xFF0A0A0A;

    public CarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ElevatorCarEntity e) { return RenderUtil.tex("wall_stainless"); }

    @Override
    public boolean shouldRender(ElevatorCarEntity e, Frustum f, double x, double y, double z) { return e.cCur != null; }

    @Override
    public void render(ElevatorCarEntity car, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (car.cCur == null || car.cPrev == null) return;
        Vec3 rp = car.cPrev.lerp(car.cCur, pt);
        double vx = Mth.lerp(pt, car.xOld, car.getX()), vy = Mth.lerp(pt, car.yOld, car.getY()), vz = Mth.lerp(pt, car.zOld, car.getZ());
        CompoundTag st = car.state();
        if (st.isEmpty()) return;
        ps.pushPose();
        ps.translate(rp.x - vx, rp.y - vy, rp.z - vz);
        ps.mulPose(Axis.YP.rotationDegrees(-car.facing().toYRot()));

        float w = car.carW(), h = car.carH(), d = car.carD();
        Spec.Usage usage = en(Spec.Usage.values(), st.getInt("usage"));
        Spec.Drive drive = en(Spec.Drive.values(), st.getInt("drive"));
        Spec.DoorType dt = en(Spec.DoorType.values(), st.getInt("door"));
        Spec.Theme theme = en(Spec.Theme.values(), st.getInt("theme"));
        boolean lights = st.getBoolean("light"), emg = st.getBoolean("emg");
        int il = lights ? RenderUtil.FULL : emg ? LightTexture.pack(5, 0) : light;
        float door = car.doorPrev + (car.doorCur - car.doorPrev) * pt;
        float carAbs = (float) rp.y;

        String wallTex = switch (usage) {
            case PATERNOSTER -> "wall_wood";
            case CONSTRUCTION -> "wall_mesh";
            case FREIGHT, CAR -> theme == Spec.Theme.STAINLESS ? "wall_checker" : "wall_" + theme.tex();
            default -> "wall_" + theme.tex();
        };
        boolean glassWalls = st.getBoolean("glass") || theme == Spec.Theme.GLASS;
        String floorTex = usage == Spec.Usage.FREIGHT || usage == Spec.Usage.CAR || usage == Spec.Usage.CONSTRUCTION ? "floor_checker"
                : usage == Spec.Usage.PATERNOSTER ? "wall_wood" : "floor_tile";

        // ---------------- 바닥
        RenderUtil.box(ps, RenderUtil.solid(buf, floorTex), -w / 2, -0.12f, -d / 2, w / 2, 0, d / 2, il, WHITE);

        float fw = st.getFloat("fw"), fh = st.getFloat("fh"), fo = st.getFloat("fo");
        float rw = st.getFloat("rw"), rh = st.getFloat("rh"), ro = st.getFloat("ro");

        if (usage == Spec.Usage.WHEELCHAIR_LIFT) {
            renderLift(ps, buf, w, d, il, dt, door, fw, fo, st);
        } else {
            boolean openFront = usage == Spec.Usage.PATERNOSTER;
            VertexConsumer wall = RenderUtil.solid(buf, wallTex);
            // 측면/후면 벽
            if (glassWalls) {
                VertexConsumer g = RenderUtil.glass(buf, "wall_glass");
                RenderUtil.box(ps, g, -w / 2, 0, -d / 2 + T, -w / 2 + T, h, d / 2 - T, il, WHITE);
                RenderUtil.box(ps, g, w / 2 - T, 0, -d / 2 + T, w / 2, h, d / 2 - T, il, WHITE);
                if (rw <= 0) RenderUtil.box(ps, g, -w / 2, 0, -d / 2, w / 2, h, -d / 2 + T, il, WHITE);
                for (float sx : new float[]{-1, 1}) for (float sz : new float[]{-1, 1})
                    RenderUtil.fill(ps, buf, sx * w / 2 - 0.05f, 0, sz * d / 2 - 0.05f, sx * w / 2 + 0.05f, h, sz * d / 2 + 0.05f, il, STEEL);
                RenderUtil.fill(ps, buf, -w / 2, 0.9f, -d / 2 + T, -w / 2 + T + 0.02f, 0.95f, d / 2 - T, il, STEEL);
                RenderUtil.fill(ps, buf, w / 2 - T - 0.02f, 0.9f, -d / 2 + T, w / 2, 0.95f, d / 2 - T, il, STEEL);
            } else {
                RenderUtil.box(ps, wall, -w / 2, 0, -d / 2, -w / 2 + T, h, d / 2, il, WHITE);
                RenderUtil.box(ps, wall, w / 2 - T, 0, -d / 2, w / 2, h, d / 2, il, WHITE);
            }
            if (!glassWalls || rw > 0) frontWall(ps, wall, w, h, -d / 2, -d / 2 + T, rw, rh, ro, il);
            if (!openFront) frontWall(ps, wall, w, h, d / 2 - T, d / 2, fw, fh, fo, il);
            // 천장 + 조명
            RenderUtil.box(ps, wall, -w / 2, h, -d / 2, w / 2, h + 0.06f, d / 2, il, WHITE);
            if (usage != Spec.Usage.CONSTRUCTION && usage != Spec.Usage.DUMBWAITER) {
                int lc = lights ? 0xFFFFF4DC : 0xFF505050;
                RenderUtil.fill(ps, buf, -w / 3, h - 0.015f, -d / 3, w / 3, h, d / 3, lights ? RenderUtil.FULL : il, lc);
                if (emg) RenderUtil.fill(ps, buf, w / 2 - T - 0.2f, h - 0.03f, -d / 2 + T, w / 2 - T, h, -d / 2 + T + 0.08f, RenderUtil.FULL, 0xFFFFE070);
                if (st.getBoolean("fan") && lights) RenderUtil.fill(ps, buf, -0.12f, h - 0.02f, d / 2 - T - 0.25f, 0.12f, h - 0.005f, d / 2 - T - 0.05f, il, 0xFF303438);
            }
            // 거울 + 핸드레일 (승객용)
            if (usage.passengerCar() && !glassWalls && rw <= 0) {
                RenderUtil.box(ps, RenderUtil.solid(buf, "mirror"), -w / 2 + 0.15f, 0.95f, -d / 2 + T, w / 2 - 0.15f, h - 0.25f, -d / 2 + T + 0.008f, il, WHITE);
                RenderUtil.fill(ps, buf, -w / 2 + 0.2f, 0.88f, -d / 2 + T + 0.05f, w / 2 - 0.2f, 0.92f, -d / 2 + T + 0.08f, il, STEEL);
            }
            // 카 도어
            Spec.DoorType carType = dt == Spec.DoorType.SWING ? Spec.DoorType.GATE : dt;
            String doorTex = theme == Spec.Theme.GLASS ? "door_glass" : (usage == Spec.Usage.CONSTRUCTION ? "wall_mesh" : "door_" + theme.tex());
            boolean glassDoor = theme == Spec.Theme.GLASS;
            if (fw > 0 && !openFront) doorPanels(ps, buf, carType, doorTex, glassDoor, fw, fh, fo - fw / 2, d / 2, false, st.getBoolean("fo2") ? door : 0, il, st.getBoolean("win"));
            if (rw > 0) doorPanels(ps, buf, carType, doorTex, glassDoor, rw, rh, ro + rw / 2, -d / 2, true, st.getBoolean("ro2") ? door : 0, il, st.getBoolean("win"));
            // 운전반 / 표시기
            if (usage.hasCop()) {
                renderCop(ps, buf, car, st, il);
                if (fw > 0 && fh + 0.32f < h) indicator(ps, buf, st, fo, fh, d, il);
                if (st.getBoolean("acc")) renderAccessible(ps, buf, car, st, il);
            }
        }

        // ---------------- 승강로 설비 (카 외부)
        if (st.contains("bot")) shaft(ps, buf, st, usage, drive, w, h, d, carAbs, light, rw > 0);
        ps.popPose();
    }

    private static <E extends Enum<E>> E en(E[] v, int i) { return v[Math.max(0, Math.min(i, v.length - 1))]; }

    /** 문 개구부가 있는 벽 */
    private void frontWall(PoseStack ps, VertexConsumer wall, float w, float h, float z1, float z2, float dw, float dh, float off, int il) {
        if (dw <= 0) { RenderUtil.box(ps, wall, -w / 2, 0, z1, w / 2, h, z2, il, WHITE); return; }
        float l = off - dw / 2, r = off + dw / 2;
        if (l > -w / 2) RenderUtil.box(ps, wall, -w / 2, 0, z1, l, h, z2, il, WHITE);
        if (r < w / 2) RenderUtil.box(ps, wall, r, 0, z1, w / 2, h, z2, il, WHITE);
        if (dh < h) RenderUtil.box(ps, wall, l, dh, z1, r, h, z2, il, WHITE);
    }

    /** 카 도어 문짝 – 카 정면(또는 후면) 바깥쪽 평면 */
    private void doorPanels(PoseStack ps, MultiBufferSource buf, Spec.DoorType type, String tex, boolean glass, float W, float H,
                            float origin, float plane, boolean mirror, float p, int il, boolean window) {
        VertexConsumer vc = glass ? RenderUtil.glass(buf, tex) : RenderUtil.solid(buf, tex);
        List<float[]> rects = DoorLayout.panels(type, W, H, p);
        for (float[] r : rects) {
            float x1 = mirror ? origin - r[1] : origin + r[0];
            float x2 = mirror ? origin - r[0] : origin + r[1];
            float off = 0.004f + r[4] * 0.006f;
            float z1 = mirror ? plane - off - 0.005f : plane + off;
            float z2 = mirror ? plane - off : plane + off + 0.005f;
            if (r[5] == 1) RenderUtil.fill(ps, buf, x1, r[2], z1, x2, r[3], z2, il, 0xFF6E7378);
            else {
                RenderUtil.box(ps, vc, x1, r[2], z1, x2, r[3], z2, il, WHITE);
                if (window && r[3] - r[2] > 1.5f) {
                    float cx = (x1 + x2) / 2;
                    float wz1 = mirror ? z2 : z1 - 0.002f, wz2 = mirror ? z2 + 0.002f : z1;
                    RenderUtil.fill(ps, buf, cx - 0.06f, 1.2f, wz1, cx + 0.06f, 1.55f, wz2, il, 0xFF1C2833);
                }
            }
        }
    }

    private void renderLift(PoseStack ps, MultiBufferSource buf, float w, float d, int il, Spec.DoorType dt, float door, float fw, float fo, CompoundTag st) {
        // 휠체어 리프트: 난간 + 앞 게이트
        float rh = 1.05f;
        for (float sx : new float[]{-1, 1}) {
            RenderUtil.fill(ps, buf, sx * w / 2 - 0.03f, 0, -d / 2, sx * w / 2 + 0.03f, rh, -d / 2 + 0.06f, il, STEEL);
            RenderUtil.fill(ps, buf, sx * w / 2 - 0.03f, 0, d / 2 - 0.06f, sx * w / 2 + 0.03f, rh, d / 2, il, STEEL);
            RenderUtil.fill(ps, buf, sx * w / 2 - 0.03f, rh - 0.05f, -d / 2, sx * w / 2 + 0.03f, rh, d / 2, il, AMBER);
            RenderUtil.fill(ps, buf, sx * w / 2 - 0.03f, 0.5f, -d / 2, sx * w / 2 + 0.03f, 0.54f, d / 2, il, STEEL);
        }
        RenderUtil.fill(ps, buf, -w / 2, rh - 0.05f, -d / 2, w / 2, rh, -d / 2 + 0.05f, il, AMBER);
        if (fw > 0) doorPanels(ps, buf, Spec.DoorType.GATE, "white", false, Math.min(fw, w), rh, -Math.min(fw, w) / 2, d / 2, false, door, il, false);
        // 조작 박스
        RenderUtil.fill(ps, buf, w / 2 - 0.2f, 0.9f, -d / 2 + 0.1f, w / 2 - 0.05f, 1.15f, -d / 2 + 0.25f, il, 0xFF20406A);
    }

    private void renderCop(PoseStack ps, MultiBufferSource buf, ElevatorCarEntity car, CompoundTag st, int il) {
        float[] b = CarGeometry.cop(car);
        boolean side = b[3] - b[0] < 0.1f; // 측면 벽 설치
        RenderUtil.box(ps, RenderUtil.solid(buf, "cop_panel"), b[0], b[1], b[2], b[3], b[4], b[5], il, WHITE);
        ListTag names = st.getList("n", Tag.TAG_STRING);
        byte[] calls = st.getByteArray("c");
        int n = names.size();
        int cols = n <= 10 ? 2 : n <= 21 ? 3 : 4;
        int rows = Math.max(1, (n + cols - 1) / cols);
        float top = b[4] - 0.2f, bottom = b[1] + 0.14f;
        float cell = Math.min(0.075f, (top - bottom) / rows);
        float bs = cell * 0.75f;
        String cur = n > 0 ? names.getString(Math.max(0, Math.min(st.getInt("cur"), n - 1))) : "";
        String disp = cur + RenderUtil.arrow(st.getInt("dir"));
        ps.pushPose();
        if (side) {
            // 측면 벽(+X 방향 벽, -X 를 바라봄)에 붙은 경우: 좌표계를 돌려서 정면과 동일하게 그림
            float cz = (b[2] + b[5]) / 2;
            ps.translate(b[0], 0, cz);
            ps.mulPose(Axis.YP.rotationDegrees(90));
            b = new float[]{-0.15f, b[1], 0f, 0.15f, b[4], 0.04f};
        }
        float face = b[2] - 0.001f;
        float cx = (b[0] + b[3]) / 2;
        // 상단 표시창
        RenderUtil.fill(ps, buf, b[0] + 0.02f, b[4] - 0.17f, face - 0.002f, b[3] - 0.02f, b[4] - 0.03f, face, RenderUtil.FULL, BLACK);
        RenderUtil.textBack(ps, buf, disp, cx, b[4] - 0.1f, face - 0.003f, 0.08f, 0xFFFF6A1A);
        String stKey = st.getString("st");
        if (!stKey.isEmpty()) RenderUtil.textBack(ps, buf, net.minecraft.client.resources.language.I18n.get("elevmod.status." + stKey), cx, b[4] - 0.155f, face - 0.003f, 0.025f, 0xFFFFD040);
        // 층 버튼
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            float x = b[0] + (b[3] - b[0]) * (c + 0.5f) / cols;
            float y = bottom + (rows - 1 - r) * cell + cell / 2;
            boolean lit = i < calls.length && (calls[i] & 1) != 0;
            RenderUtil.fill(ps, buf, x - bs / 2, y - bs / 2, face - 0.012f, x + bs / 2, y + bs / 2, face, lit ? RenderUtil.FULL : il, lit ? AMBER : 0xFFB8BCC2);
            RenderUtil.textBack(ps, buf, names.getString(i), x, y, face - 0.013f, bs * 0.55f, lit ? 0xFF3A1E00 : 0xFF202020);
        }
        // 열림 / 닫힘 / 비상
        float by = b[1] + 0.07f;
        float w3 = (b[3] - b[0]) / 3;
        String[] lab = {"◀▶", "▶◀", "!"};
        int[] col = {0xFFD0D4D8, 0xFFD0D4D8, 0xFFF0C020};
        for (int i = 0; i < 3; i++) {
            float x = b[0] + w3 * (i + 0.5f);
            RenderUtil.fill(ps, buf, x - 0.03f, by - 0.03f, face - 0.012f, x + 0.03f, by + 0.03f, face, il, col[i]);
            RenderUtil.textBack(ps, buf, lab[i], x, by, face - 0.013f, 0.03f, 0xFF202020);
        }
        if (st.getBoolean("stop")) {
            RenderUtil.fill(ps, buf, cx - 0.04f, b[1] + 0.005f, face - 0.012f, cx + 0.04f, b[1] + 0.03f, face, st.getBoolean("sp") ? RenderUtil.FULL : il, 0xFFD02020);
        }
        ps.popPose();
    }

    private void renderAccessible(PoseStack ps, MultiBufferSource buf, ElevatorCarEntity car, CompoundTag st, int il) {
        float[] b = CarGeometry.accessibleCop(car);
        RenderUtil.box(ps, RenderUtil.solid(buf, "cop_panel"), b[0], b[1], b[2], b[3], b[4], b[5], il, WHITE);
        ListTag names = st.getList("n", Tag.TAG_STRING);
        byte[] calls = st.getByteArray("c");
        int n = names.size();
        ps.pushPose();
        ps.translate(b[3] + 0.001f, 0, 0);
        ps.mulPose(Axis.YP.rotationDegrees(90)); // 이제 +Z 가 카 안쪽(+X)
        float len = b[5] - b[2];
        int cols = Math.max(1, Math.min(n, (int) (len / 0.07f)));
        int rows = Math.max(1, (n + cols - 1) / cols);
        float cell = Math.min(0.07f, len / cols);
        float rowH = (b[4] - b[1] - 0.04f) / Math.max(rows, 1);
        float bs = Math.min(cell, rowH) * 0.75f;
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            // 회전 후 로컬 x = -원래 z
            float x = -(b[2] + cell * (c + 0.5f));
            float y = b[4] - 0.02f - rowH * (r + 0.5f);
            boolean lit = i < calls.length && (calls[i] & 1) != 0;
            RenderUtil.fill(ps, buf, x - bs / 2, y - bs / 2, 0, x + bs / 2, y + bs / 2, 0.012f, lit ? RenderUtil.FULL : il, lit ? AMBER : 0xFFB8BCC2);
            RenderUtil.text(ps, buf, names.getString(i), x, y, 0.013f, bs * 0.55f, 0xFF202020);
        }
        ps.popPose();
    }

    private void indicator(PoseStack ps, MultiBufferSource buf, CompoundTag st, float fo, float fh, float d, int il) {
        float z2 = d / 2 - T, z1 = z2 - 0.03f;
        RenderUtil.fill(ps, buf, fo - 0.22f, fh + 0.06f, z1, fo + 0.22f, fh + 0.28f, z2, RenderUtil.FULL, BLACK);
        ListTag names = st.getList("n", Tag.TAG_STRING);
        int n = names.size();
        if (n == 0) return;
        String cur = names.getString(Math.max(0, Math.min(st.getInt("cur"), n - 1)));
        RenderUtil.textBack(ps, buf, RenderUtil.arrow(st.getInt("dir")) + " " + cur, fo, fh + 0.17f, z1 - 0.002f, 0.13f, 0xFFFF3020);
        if (st.getBoolean("air")) RenderUtil.textBack(ps, buf, "Air-Clean", fo, fh + 0.085f, z1 - 0.002f, 0.025f, 0xFF60D0FF);
    }

    /** 가이드레일, 균형추, 로프, 유압잭, 랙 마스트 */
    private void shaft(PoseStack ps, MultiBufferSource buf, CompoundTag st, Spec.Usage usage, Spec.Drive drive, float w, float h, float d,
                       float carAbs, int light, boolean hasRear) {
        if (usage == Spec.Usage.PATERNOSTER) return;
        float bot = st.getFloat("bot"), top = st.getFloat("top");
        float yb = bot - carAbs - 1.5f, yt = top - carAbs + h + 1.5f;
        VertexConsumer rail = RenderUtil.solid(buf, "rail");
        if (drive == Spec.Drive.RACK_PINION) {
            RenderUtil.box(ps, rail, w / 2 + 0.05f, yb, -0.15f, w / 2 + 0.35f, yt, 0.15f, light, WHITE);
            RenderUtil.fill(ps, buf, w / 2, 0, -0.2f, w / 2 + 0.06f, 0.6f, 0.2f, light, 0xFFE0B020);
            return;
        }
        for (float sx : new float[]{-1, 1}) {
            float x1 = sx > 0 ? w / 2 + 0.08f : -w / 2 - 0.16f;
            RenderUtil.box(ps, rail, x1, yb, -0.05f, x1 + 0.08f, yt, 0.05f, light, WHITE);
        }
        if (drive == Spec.Drive.HYDRAULIC) {
            float pit = bot - carAbs - 1.2f;
            RenderUtil.fill(ps, buf, -0.09f, pit, -0.09f, 0.09f, -0.12f, 0.09f, light, 0xFFD8DCE0);
            RenderUtil.fill(ps, buf, -0.15f, pit, -0.15f, 0.15f, pit + 1.0f, 0.15f, light, 0xFF3A4048);
            return;
        }
        // 카 상부 크로스헤드
        RenderUtil.fill(ps, buf, -w / 2 - 0.1f, h + 0.06f, -0.08f, w / 2 + 0.1f, h + 0.2f, 0.08f, light, 0xFF404448);
        int my = st.getInt("my");
        float ropeTop = my != Integer.MIN_VALUE ? my + 0.5f - carAbs : top - carAbs + h + 2.5f;
        if (drive == Spec.Drive.MRL) ropeTop = top - carAbs + h + 0.8f;
        for (float rx : new float[]{-0.06f, 0f, 0.06f}) RenderUtil.fill(ps, buf, rx - 0.01f, h + 0.2f, -0.01f, rx + 0.01f, ropeTop, 0.01f, light, 0xFF505458);
        if (!drive.hasCounterweight()) return;
        float cwH = 1.4f;
        float cwBottom = (top + h - cwH) - (carAbs - bot) - carAbs;
        float cx1, cx2, cz1, cz2;
        if (hasRear) { cx1 = w / 2 + 0.22f; cx2 = w / 2 + 0.5f; cz1 = -0.35f; cz2 = 0.35f; }
        else { cx1 = -0.4f; cx2 = 0.4f; cz1 = -d / 2 - 0.42f; cz2 = -d / 2 - 0.14f; }
        RenderUtil.box(ps, RenderUtil.solid(buf, "counterweight"), cx1, cwBottom, cz1, cx2, cwBottom + cwH, cz2, light, WHITE);
        float ccx = (cx1 + cx2) / 2, ccz = (cz1 + cz2) / 2;
        for (float rx : new float[]{-0.05f, 0.05f}) RenderUtil.fill(ps, buf, ccx + rx - 0.01f, cwBottom + cwH, ccz - 0.01f, ccx + rx + 0.01f, ropeTop, ccz + 0.01f, light, 0xFF505458);
    }
}

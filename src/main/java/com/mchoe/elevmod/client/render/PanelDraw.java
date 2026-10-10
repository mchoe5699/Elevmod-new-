package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.client.design.CustomDesign;
import com.mchoe.elevmod.client.design.Relief;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 판(운전반·표시기·호출버튼 등) 공용 그리기 – 기본 디자인과 사진으로 만든 커스텀 디자인 모두 */
public final class PanelDraw {
    private PanelDraw() {}

    /** 위치표시 창. type: "seg" 7세그먼트, "dot" 도트매트릭스, "lcd" LCD (프로필·전환 효과 없이) */
    public static void display(PoseStack ps, MultiBufferSource buf, String type, int col, int lcdBg, float x1, float y1, float x2, float y2,
                               float z, int light, String cur, int dir, boolean moving) {
        display(ps, buf, null, null, type, col, lcdBg, x1, y1, x2, y2, z, light, cur, dir, moving);
    }

    /** 프로필(동작·모양·색)과 전환 효과 상태 key 를 쓰는 위치표시 창 */
    public static void display(PoseStack ps, MultiBufferSource buf, @org.jetbrains.annotations.Nullable com.mchoe.elevmod.elevator.IndicatorProfile prof,
                               @org.jetbrains.annotations.Nullable Object key, String type, int col, int bg, float x1, float y1, float x2, float y2,
                               float z, int light, String cur, int dir, boolean moving) {
        int base = type.equals("lcd") ? 0xFF7FE3FF : col;
        int b = type.equals("lcd") ? bg : type.equals("dot") ? 0xFF0C0806 : 0xFF140404;
        com.mchoe.elevmod.client.indicator.IndicatorDraw.draw(ps, buf, prof, type, base, b, x1, y1, x2, y2, z, light, cur, dir, moving, key);
    }

    /** 사진 앞면 (x0,y0 = 왼쪽 아래, +Z 가 보는 사람 쪽) */
    public static void face(PoseStack ps, MultiBufferSource buf, CustomDesign.PanelPart pp, float x0, float y0, float w, float h, float z, int light) {
        if (pp.face == null) return;
        Relief.draw(ps, buf, pp.face, new Relief.Frame(x0, y0, z, 1, 0, 0, 0, 1, 0, 0, 0, 1), w, h, light, null);
    }

    /** 눌린 버튼의 불빛 (사진 위에 겹쳐 그리는 빛나는 링) */
    public static void ring(PoseStack ps, MultiBufferSource buf, float cx, float cy, float r, float z, int color) {
        RenderUtil.quadZ(ps, RenderUtil.glass(buf, "btn_ring"), cx - r * 1.15f, cy - r * 1.15f, cx + r * 1.15f, cy + r * 1.15f, z, true, RenderUtil.FULL, color);
    }

    /**
     * 사진에서 찾은 버튼을 층에 배정: 아래 줄부터 낮은 층, 한 줄 안에서는 왼→오른 오름차순 (국내 운전반 배열).
     * 층보다 버튼이 많으면 위쪽 남는 버튼은 배정하지 않음. 반환: 버튼마다 층 인덱스(-1 = 배정 없음)
     */
    public static int[] assignFloors(List<float[]> buttons, int floors) {
        int n = buttons.size();
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        if (n < floors || floors == 0) return out;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        idx.sort(Comparator.comparingDouble(i -> buttons.get(i)[1]));
        List<List<Integer>> rows = new ArrayList<>();
        for (int i : idx) {
            float[] b = buttons.get(i);
            List<Integer> row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (row != null && Math.abs(buttons.get(row.get(0))[1] - b[1]) < Math.max(b[2], 0.01f) * 0.9f) row.add(i);
            else { row = new ArrayList<>(); row.add(i); rows.add(row); }
        }
        int next = 0;
        for (List<Integer> row : rows) {
            row.sort(Comparator.comparingDouble(i -> buttons.get(i)[0]));
            for (int i : row) if (next < floors) out[i] = next++;
        }
        return out;
    }
}

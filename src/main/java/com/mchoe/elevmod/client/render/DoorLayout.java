package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.elevator.Spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 문짝 배치. 문 좌표계: x 0..W(유효 개구 폭), y 0..H, p = 0(닫힘)..1(열림). 편개는 x=0 쪽(수납부)으로 열린다.
 *
 * <p>실제 망원식(telescoping) 도어 구조를 따른다:
 * <ul>
 *   <li>문짝 n 장이 개구 폭을 n 등분해 닫고, 열리면 모두 개구 바깥 수납부(폭 W/n)에 겹쳐 들어간다.</li>
 *   <li>가장 빠른 문짝이 닫히는 쪽(스트라이크 측)에 있고 이동거리가 가장 길다 (속도비 n : n-1 : … : 1).</li>
 *   <li>빠른 문짝일수록 카와 승강장 사이 틈(running clearance)에 가깝다 → layer 0 = 틈에 가장 가까운 면.</li>
 *   <li>중앙개폐 2-speed(4짝)·3-speed(6짝)는 양쪽에 같은 구조가 대칭으로 있고, 가장 빠른 문짝끼리 가운데서 만난다.</li>
 * </ul>
 * 결과: {x1, x2, y1, y2, layer, kind(0 문짝 / 1 게이트 살), lead(+1: x2 가 앞날, -1: x1 이 앞날, 0: 없음)}
 */
public final class DoorLayout {
    private DoorLayout() {}

    public static List<float[]> panels(Spec.DoorType type, float W, float H, float p) {
        List<float[]> out = new ArrayList<>();
        switch (type) {
            case CO, CO4, CO6 -> {
                int k = type.panels;
                float q = W / (2f * k);
                for (int i = 0; i < k; i++) {
                    float travel = (W / 2f - i * q) * p;
                    // 왼쪽 반
                    out.add(new float[]{W / 2f - (i + 1) * q - travel, W / 2f - i * q - travel, 0, H, i, 0, i == 0 ? 1 : 0});
                    // 오른쪽 반 (대칭)
                    out.add(new float[]{W / 2f + i * q + travel, W / 2f + (i + 1) * q + travel, 0, H, i, 0, i == 0 ? -1 : 0});
                }
            }
            case S1, S2, S3, S4 -> {
                int n = type.panels;
                float q = W / n;
                for (int i = 0; i < n; i++) {
                    float travel = (W - i * q) * p;
                    out.add(new float[]{W - (i + 1) * q - travel, W - i * q - travel, 0, H, i, 0, i == 0 ? 1 : 0});
                }
            }
            case UP -> {
                // 2짝 상승개폐: 아래짝(빠름)이 위짝 뒤로 겹쳐 올라감
                float half = H / 2f;
                out.add(new float[]{0, W, H * p, half + H * p, 0, 0, 0});
                out.add(new float[]{0, W, half + half * p, H + half * p, 1, 0, 0});
            }
            case BIPART -> {
                // 상하개폐: 위짝은 위로, 아래짝은 바닥 아래로
                float half = H / 2f;
                out.add(new float[]{0, W, half + half * p, H + half * p, 0, 0, 0});
                out.add(new float[]{0, W, -half * p, half - half * p, 0, 0, 0});
            }
            case GATE -> {
                float width = W * (1f - 0.85f * p);
                int k = Math.max(4, Math.round(W * 8));
                for (int i = 0; i <= k; i++) {
                    float x = width * i / k;
                    out.add(new float[]{x, x + 0.025f, 0, H, 0, 1, 0});
                }
                out.add(new float[]{0, width + 0.025f, H - 0.05f, H, 0, 1, 0});
                out.add(new float[]{0, width + 0.025f, 0, 0.05f, 0, 1, 0});
                out.add(new float[]{0, width + 0.025f, H / 2f - 0.02f, H / 2f + 0.02f, 0, 1, 0});
            }
            case SWING -> out.add(new float[]{0, W, 0, H, 0, 0, 0}); // 회전은 렌더러에서
        }
        return out;
    }

    /** 문짝이 겹쳐 수납되는 공간 폭(한쪽) */
    public static float pocket(Spec.DoorType type, float W) {
        return switch (type) {
            case CO, CO4, CO6 -> W / (2f * type.panels);
            case S1, S2, S3, S4 -> W / type.panels;
            default -> 0f;
        };
    }
}

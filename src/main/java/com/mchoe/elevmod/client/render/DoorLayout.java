package com.mchoe.elevmod.client.render;

import com.mchoe.elevmod.elevator.Spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 문짝 배치 계산. 문 좌표계: x 0..W (닫힌 상태에서 문이 덮는 폭), y 0..H, p = 0(닫힘)..1(열림).
 * 결과: {x1, x2, y1, y2, layer, kind}  kind 0 = 문짝, 1 = 게이트 살
 * 문짝은 열리면서 개구부 밖(벽 속 수납공간)으로 미끄러져 들어간다.
 */
public final class DoorLayout {
    private DoorLayout() {}

    public static List<float[]> panels(Spec.DoorType type, float W, float H, float p) {
        List<float[]> out = new ArrayList<>();
        switch (type) {
            case CO -> {
                float half = W / 2f, m = half * p;
                out.add(new float[]{-m, half - m, 0, H, 0, 0});
                out.add(new float[]{half + m, W + m, 0, H, 0, 0});
            }
            case CO4 -> {
                float q = W / 4f;
                out.add(new float[]{-q * p, q - q * p, 0, H, 0, 0});
                out.add(new float[]{q - 2 * q * p, 2 * q - 2 * q * p, 0, H, 1, 0});
                out.add(new float[]{3 * q + q * p, 4 * q + q * p, 0, H, 0, 0});
                out.add(new float[]{2 * q + 2 * q * p, 3 * q + 2 * q * p, 0, H, 1, 0});
            }
            case S1 -> out.add(new float[]{-W * p, W - W * p, 0, H, 0, 0});
            case S2 -> {
                float half = W / 2f;
                out.add(new float[]{-half * p, half - half * p, 0, H, 0, 0});
                out.add(new float[]{half - 2 * half * p, W - 2 * half * p, 0, H, 1, 0});
            }
            case S3 -> {
                float t = W / 3f;
                out.add(new float[]{-t * p, t - t * p, 0, H, 0, 0});
                out.add(new float[]{t - 2 * t * p, 2 * t - 2 * t * p, 0, H, 1, 0});
                out.add(new float[]{2 * t - 3 * t * p, 3 * t - 3 * t * p, 0, H, 2, 0});
            }
            case UP -> {
                // 2짝 상승개폐: 아래짝이 위짝보다 2배 빨리 올라가 겹쳐 수납
                float half = H / 2f;
                out.add(new float[]{0, W, half + half * p, H + half * p, 1, 0});
                out.add(new float[]{0, W, H * p, half + H * p, 0, 0});
            }
            case GATE -> {
                float width = W * (1f - 0.85f * p);
                int k = Math.max(4, Math.round(W * 8));
                for (int i = 0; i <= k; i++) {
                    float x = width * i / k;
                    out.add(new float[]{x, x + 0.03f, 0, H, 0, 1});
                }
                out.add(new float[]{0, width + 0.03f, H - 0.05f, H, 0, 1});
                out.add(new float[]{0, width + 0.03f, 0, 0.05f, 0, 1});
                out.add(new float[]{0, width + 0.03f, H / 2f - 0.02f, H / 2f + 0.02f, 0, 1});
            }
            case SWING -> out.add(new float[]{0, W, 0, H, 0, 0}); // 회전은 렌더러에서
        }
        return out;
    }
}

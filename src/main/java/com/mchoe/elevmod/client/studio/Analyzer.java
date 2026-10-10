package com.mchoe.elevmod.client.studio;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 사진 분석 – 원근 보정한 사진에서 디자인 요소를 찾는다.
 * <ul>
 *   <li>판넬 이음새(가로/세로 홈): 밝기 경계가 한 줄로 길게 이어지는 곳</li>
 *   <li>볼록/오목 판넬: 위 조명 아래에서 볼록하면 윗변이 밝고 아랫변이 어둡다 (오목은 반대)</li>
 *   <li>곡면(기둥·천장 홈): 가로(세로) 밝기 분포가 가운데로 매끈하게 솟거나 꺼짐</li>
 *   <li>버튼: 주변과 대비가 큰 둥글거나 네모난 덩어리 (크기가 비슷한 것끼리)</li>
 *   <li>표시창: 큰 어두운 직사각형(LED/세그먼트) 또는 파랗게 밝은 직사각형(LCD)</li>
 *   <li>홀랜턴 램프: 색이 진하고 밝은 덩어리 두 개</li>
 * </ul>
 * 좌표는 모두 비율(0~1), y 는 아래 → 위.
 */
public final class Analyzer {
    private Analyzer() {}

    public static final class Result {
        public final List<Float> seamX = new ArrayList<>(), seamY = new ArrayList<>();
        /** {x1,y1,x2,y2, 종류(+1 볼록, -1 오목), 모따기 비율} */
        public final List<float[]> panels = new ArrayList<>();
        /** {x1,y1,x2,y2, 축(0=x,1=y), 세기(+볼록/-오목)} */
        public final List<float[]> curves = new ArrayList<>();
        /** {cx, cy, r(폭 비율)} */
        public final List<float[]> buttons = new ArrayList<>();
        public float[] display;
        public final List<float[]> lamps = new ArrayList<>();
        public int digit = 0, lit = 0, lcdBg = 0;
    }

    // ------------------------------------------------------------------ 원근 보정
    /** 네 모서리(왼위, 오른위, 오른아래, 왼아래; 원본 픽셀)로 사진을 펴서 outW×outH 로 */
    public static BufferedImage warp(BufferedImage src, float[] c, int outW, int outH) {
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        double[] hm = homography(c);
        int sw = src.getWidth(), sh = src.getHeight();
        for (int y = 0; y < outH; y++) {
            double v = (y + 0.5) / outH;
            for (int x = 0; x < outW; x++) {
                double u = (x + 0.5) / outW;
                double den = hm[6] * u + hm[7] * v + 1;
                double sx = (hm[0] * u + hm[1] * v + hm[2]) / den, sy = (hm[3] * u + hm[4] * v + hm[5]) / den;
                out.setRGB(x, y, bilinear(src, sx - 0.5, sy - 0.5, sw, sh));
            }
        }
        return out;
    }

    private static int bilinear(BufferedImage s, double x, double y, int w, int h) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        int c00 = px(s, x0, y0, w, h), c10 = px(s, x0 + 1, y0, w, h), c01 = px(s, x0, y0 + 1, w, h), c11 = px(s, x0 + 1, y0 + 1, w, h);
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            double a = ((c00 >> sh) & 255) * (1 - fx) + ((c10 >> sh) & 255) * fx;
            double b = ((c01 >> sh) & 255) * (1 - fx) + ((c11 >> sh) & 255) * fx;
            r |= ((int) Math.round(a * (1 - fy) + b * fy) & 255) << sh;
        }
        return r | 0xFF000000;
    }

    private static int px(BufferedImage s, int x, int y, int w, int h) {
        return s.getRGB(Math.max(0, Math.min(w - 1, x)), Math.max(0, Math.min(h - 1, y)));
    }

    /** 단위 정사각형 (u,v) → 사각형 네 점 투영 변환 계수 */
    static double[] homography(float[] c) {
        double x0 = c[0], y0 = c[1], x1 = c[2], y1 = c[3], x2 = c[4], y2 = c[5], x3 = c[6], y3 = c[7];
        double dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3;
        double dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3;
        double g, h;
        double den = dx1 * dy2 - dx2 * dy1;
        if (Math.abs(den) < 1e-9) { g = 0; h = 0; }
        else { g = (dx3 * dy2 - dx2 * dy3) / den; h = (dx1 * dy3 - dx3 * dy1) / den; }
        double a = x1 - x0 + g * x1, b = x3 - x0 + h * x3, cc = x0;
        double d = y1 - y0 + g * y1, e = y3 - y0 + h * y3, f = y0;
        return new double[]{a, b, cc, d, e, f, g, h};
    }

    // ------------------------------------------------------------------ 공용
    static float[][] lum(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        float[][] L = new float[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = img.getRGB(x, y);
            L[y][x] = 0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255);
        }
        return L;
    }

    static float[][] blur(float[][] L, int r) {
        int h = L.length, w = L[0].length;
        float[][] t = new float[h][w], o = new float[h][w];
        for (int y = 0; y < h; y++) {
            float s = 0; int n = 0;
            for (int x = -r; x < w + r; x++) {
                if (x + r < w) { s += L[y][x + r]; n++; }
                if (x - r - 1 >= 0) { s -= L[y][x - r - 1]; n--; }
                if (x >= 0 && x < w) t[y][x] = s / n;
            }
        }
        for (int x = 0; x < w; x++) {
            float s = 0; int n = 0;
            for (int y = -r; y < h + r; y++) {
                if (y + r < h) { s += t[y + r][x]; n++; }
                if (y - r - 1 >= 0) { s -= t[y - r - 1][x]; n--; }
                if (y >= 0 && y < h) o[y][x] = s / n;
            }
        }
        return o;
    }

    // ------------------------------------------------------------------ 면(벽·문·천장)
    /** strength 0 = 사진만, 1 약하게, 2 보통, 3 강하게 */
    public static Result surface(BufferedImage img, int strength) {
        Result r = new Result();
        if (strength <= 0) return r;
        BufferedImage s = ImageLoader.scale(img, Math.min(1.0, 220.0 / Math.max(img.getWidth(), img.getHeight())));
        float[][] L = lum(s);
        int h = L.length, w = L[0].length;
        float k = strength == 1 ? 3.2f : strength == 2 ? 2.5f : 1.9f;
        // 이음새: 열/행별 수평·수직 기울기의 '연속성' (한 줄 전체에 걸친 경계만)
        float[] col = new float[w], row = new float[h];
        for (int x = 1; x < w - 1; x++) {
            int run = 0, best = 0;
            for (int y = 0; y < h; y++) {
                float g = Math.abs(L[y][x + 1] - L[y][x - 1]);
                if (g > 14) { run++; best = Math.max(best, run); } else run = 0;
                col[x] += g;
            }
            col[x] = col[x] / h * (0.4f + 0.6f * best / (float) h);
        }
        for (int y = 1; y < h - 1; y++) {
            int run = 0, best = 0;
            for (int x = 0; x < w; x++) {
                float g = Math.abs(L[y + 1][x] - L[y - 1][x]);
                if (g > 14) { run++; best = Math.max(best, run); } else run = 0;
                row[y] += g;
            }
            row[y] = row[y] / w * (0.4f + 0.6f * best / (float) w);
        }
        for (int i : peaks(col, k, 0.06f)) r.seamX.add(i / (float) (w - 1));
        for (int i : peaks(row, k, 0.06f)) r.seamY.add(1 - i / (float) (h - 1));
        // 이음새로 나뉜 칸마다 볼록/오목/곡면 판단
        List<Float> xs = new ArrayList<>(), ys = new ArrayList<>();
        xs.add(0f); xs.addAll(r.seamX); xs.add(1f);
        ys.add(0f); ys.addAll(r.seamY); ys.add(1f);
        xs.sort(null); ys.sort(null);
        float thr = strength == 1 ? 0.12f : strength == 2 ? 0.08f : 0.05f;
        for (int i = 0; i < xs.size() - 1; i++) for (int j = 0; j < ys.size() - 1; j++) {
            float fx1 = xs.get(i), fx2 = xs.get(i + 1), fy1 = ys.get(j), fy2 = ys.get(j + 1);
            if (fx2 - fx1 < 0.08f || fy2 - fy1 < 0.08f) continue;
            int px1 = (int) (fx1 * (w - 1)), px2 = (int) (fx2 * (w - 1));
            int py1 = (int) ((1 - fy2) * (h - 1)), py2 = (int) ((1 - fy1) * (h - 1)); // 이미지 y 는 위가 0
            int bw = Math.max(2, (px2 - px1) / 12), bh = Math.max(2, (py2 - py1) / 25);
            float mid = mean(L, px1 + bw, py1 + 3 * bh, px2 - bw, py2 - 3 * bh);
            float m = Math.max(20, mid);
            // 모서리 하이라이트/그림자: 맨 바깥 띠와 바로 안쪽 띠의 차이(완만한 조명 기울기는 거의 0)
            float t0 = mean(L, px1 + bw, py1 + 1, px2 - bw, py1 + 1 + bh), t1 = mean(L, px1 + bw, py1 + 1 + bh, px2 - bw, py1 + 1 + 2 * bh);
            float b0 = mean(L, px1 + bw, py2 - 1 - bh, px2 - bw, py2 - 1), b1 = mean(L, px1 + bw, py2 - 1 - 2 * bh, px2 - bw, py2 - 1 - bh);
            float st = (t0 - t1) / m, sb = (b0 - b1) / m;
            float ix = 0.035f, iy = 0.035f;
            int kind = st > thr && sb < -thr ? 1 : st < -thr && sb > thr ? -1 : 0;
            if (kind != 0) {
                r.panels.add(new float[]{fx1 + ix * (fx2 - fx1), fy1 + iy * (fy2 - fy1), fx2 - ix * (fx2 - fx1), fy2 - iy * (fy2 - fy1), kind, 0.08f});
                continue;
            }
            // 곡면: 가로 밝기 분포
            float[] prof = new float[Math.max(3, px2 - px1)];
            for (int x = 0; x < prof.length; x++) prof[x] = mean(L, px1 + x, py1 + bh, px1 + x + 1, py2 - bh);
            float curv = curvature(prof, m);
            if (Math.abs(curv) > thr * 1.3f) r.curves.add(new float[]{fx1, fy1, fx2, fy2, 0, curv});
        }
        return r;
    }

    private static float curvature(float[] p, float m) {
        int n = p.length;
        int e = Math.max(1, n / 6);
        float edge = 0, mid = 0;
        for (int i = 0; i < e; i++) edge += p[i] + p[n - 1 - i];
        edge /= 2 * e;
        for (int i = n / 2 - e / 2; i <= n / 2 + e / 2; i++) mid += p[Math.max(0, Math.min(n - 1, i))];
        mid /= (e / 2) * 2 + 1;
        // 매끈한지: 부호 바뀜이 적어야 함
        int flips = 0;
        float prev = 0;
        for (int i = 1; i < n; i++) {
            float d = p[i] - p[i - 1];
            if (Math.abs(d) < 1.5f) continue;
            if (prev != 0 && Math.signum(d) != Math.signum(prev)) flips++;
            prev = d;
        }
        if (flips > 3) return 0;
        return (mid - edge) / m;
    }

    private static float mean(float[][] L, int x1, int y1, int x2, int y2) {
        int h = L.length, w = L[0].length;
        x1 = Math.max(0, x1); y1 = Math.max(0, y1); x2 = Math.min(w, Math.max(x1 + 1, x2)); y2 = Math.min(h, Math.max(y1 + 1, y2));
        float s = 0; int n = 0;
        for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) { s += L[y][x]; n++; }
        return n == 0 ? 0 : s / n;
    }

    private static List<Integer> peaks(float[] v, float k, float minSep) {
        int n = v.length;
        float mean = 0, sd = 0;
        int lo = (int) (n * 0.05f), hi = (int) (n * 0.95f);
        for (int i = lo; i < hi; i++) mean += v[i];
        mean /= Math.max(1, hi - lo);
        for (int i = lo; i < hi; i++) sd += (v[i] - mean) * (v[i] - mean);
        sd = (float) Math.sqrt(sd / Math.max(1, hi - lo));
        List<Integer> out = new ArrayList<>();
        int sep = Math.max(2, (int) (n * minSep));
        for (int i = lo; i < hi; i++) {
            if (v[i] < mean + k * sd || v[i] < 3) continue;
            boolean max = true;
            for (int j = Math.max(0, i - sep); j <= Math.min(n - 1, i + sep); j++) if (v[j] > v[i]) { max = false; break; }
            if (max && (out.isEmpty() || i - out.get(out.size() - 1) >= sep)) out.add(i);
            if (out.size() >= 8) break;
        }
        return out;
    }

    // ------------------------------------------------------------------ 판(운전반·표시기·버튼·랜턴)
    /** dispType: "seg", "dot", "lcd" – 표시창 찾는 방식이 다름 */
    public static Result panel(BufferedImage img, String dispType, boolean wantButtons, boolean wantDisplay, boolean wantLamps) {
        Result r = new Result();
        double sc = Math.min(1.0, 260.0 / Math.max(img.getWidth(), img.getHeight()));
        BufferedImage s = ImageLoader.scale(img, sc);
        int w = s.getWidth(), h = s.getHeight();
        float[][] L = lum(s);
        int[] rgb = new int[w * h];
        s.getRGB(0, 0, w, h, rgb, 0, w);
        if (wantDisplay) r.display = findDisplay(L, rgb, w, h, dispType);
        if (wantButtons) findButtons(L, w, h, r);
        if (r.display != null) {
            float[] d = r.display;
            r.buttons.removeIf(b -> b[0] > d[0] && b[0] < d[2] && b[1] > d[1] && b[1] < d[3]);
            int[] c = colors(rgb, w, h, d);
            r.digit = c[0];
            r.lcdBg = c[1];
        }
        if (wantLamps) findLamps(rgb, w, h, r);
        r.lit = litColor(rgb, w, h, r.buttons);
        return r;
    }

    private static float[] findDisplay(float[][] L, int[] rgb, int w, int h, String type) {
        boolean[] m = new boolean[w * h];
        float mean = 0;
        for (float[] row : L) for (float v : row) mean += v;
        mean /= w * h;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = rgb[y * w + x];
            int R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
            m[y * w + x] = type.equals("lcd")
                    ? (B > R + 25 && B > G + 5 && B > 70) || (L[y][x] > 200 && B > R)
                    : L[y][x] < Math.min(55, mean * 0.45f);
        }
        // 닫기(구멍 메우기): 세그먼트 숫자 때문에 생긴 틈 채움
        m = close(m, w, h, Math.max(1, w / 60));
        int[] best = null;
        float bestScore = 0;
        for (int[] cc : components(m, w, h)) {
            int bw = cc[2] - cc[0] + 1, bh = cc[3] - cc[1] + 1;
            float fill = cc[4] / (float) (bw * bh);
            float area = bw * bh / (float) (w * h);
            if (area < 0.015f || area > 0.75f || fill < 0.82f) continue;
            float sc = area * fill;
            if (sc > bestScore) { bestScore = sc; best = cc; }
        }
        if (best == null) return null;
        return new float[]{best[0] / (float) w, 1 - (best[3] + 1) / (float) h, (best[2] + 1) / (float) w, 1 - best[1] / (float) h};
    }

    private static void findButtons(float[][] L, int w, int h, Result r) {
        float[][] b = blur(L, Math.max(2, Math.min(w, h) / 10));
        float[] d = new float[w * h];
        float mean = 0, sd = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            // 주변과의 밝기 차 + 경계 세기(가는 테두리 버튼도 잡히도록)
            float gx = x > 0 && x < w - 1 ? L[y][x + 1] - L[y][x - 1] : 0, gy = y > 0 && y < h - 1 ? L[y + 1][x] - L[y - 1][x] : 0;
            d[y * w + x] = Math.max(Math.abs(L[y][x] - b[y][x]), 0.6f * (float) Math.hypot(gx, gy));
            mean += d[y * w + x];
        }
        mean /= w * h;
        for (float v : d) sd += (v - mean) * (v - mean);
        sd = (float) Math.sqrt(sd / (w * h));
        boolean[] m = new boolean[w * h];
        for (int i = 0; i < m.length; i++) m[i] = d[i] > mean + 1.2f * sd;
        m = close(m, w, h, Math.max(1, Math.min(w, h) / 90));
        int ref = Math.min(w, h);
        List<float[]> cand = new ArrayList<>();
        for (int[] cc : components(m, w, h)) {
            int bw = cc[2] - cc[0] + 1, bh = cc[3] - cc[1] + 1;
            float asp = bw / (float) bh, fill = cc[4] / (float) (bw * bh);
            if (bw < ref * 0.05f || bw > ref * 0.45f || bh < ref * 0.05f || bh > ref * 0.45f) continue;
            if (asp < 0.7f || asp > 1.43f || fill < 0.12f) continue;
            float cx = (cc[0] + cc[2] + 1) / 2f / w, cy = 1 - (cc[1] + cc[3] + 1) / 2f / h;
            float rad = (bw + bh) / 4f / w;
            cand.add(new float[]{cx, cy, rad});
        }
        if (cand.isEmpty()) return;
        // 크기가 비슷한 것끼리 (중앙값 기준)
        List<Float> rs = new ArrayList<>();
        for (float[] c : cand) rs.add(c[2]);
        rs.sort(null);
        float med = rs.get(rs.size() / 2);
        for (float[] c : cand) {
            if (c[2] < med * 0.6f || c[2] > med * 1.6f) continue;
            boolean dup = false;
            for (float[] e : r.buttons) if (Math.hypot(e[0] - c[0], (e[1] - c[1]) * h / w) < med) { dup = true; break; }
            if (!dup) r.buttons.add(c);
        }
        r.buttons.sort((a, c) -> Float.compare(c[1], a[1]));
    }

    private static void findLamps(int[] rgb, int w, int h, Result r) {
        boolean[] m = new boolean[w * h];
        for (int i = 0; i < m.length; i++) {
            int c = rgb[i];
            int R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
            int mx = Math.max(R, Math.max(G, B)), mn = Math.min(R, Math.min(G, B));
            m[i] = (mx > 150 && mx - mn > 70) || mx > 235;
        }
        m = close(m, w, h, 1);
        List<int[]> cs = components(m, w, h);
        cs.sort((a, b) -> b[4] - a[4]);
        for (int i = 0; i < Math.min(2, cs.size()); i++) {
            int[] c = cs.get(i);
            if (c[4] < w * h * 0.004f) break;
            r.lamps.add(new float[]{c[0] / (float) w, 1 - (c[3] + 1) / (float) h, (c[2] + 1) / (float) w, 1 - c[1] / (float) h});
        }
        // 0 = 상승(위 또는 왼쪽)
        if (r.lamps.size() == 2) {
            float[] a = r.lamps.get(0), b = r.lamps.get(1);
            boolean vertical = Math.abs((a[1] + a[3]) - (b[1] + b[3])) > Math.abs((a[0] + a[2]) - (b[0] + b[2]));
            boolean swap = vertical ? (a[1] + a[3]) < (b[1] + b[3]) : (a[0] + a[2]) > (b[0] + b[2]);
            if (swap) { r.lamps.set(0, b); r.lamps.set(1, a); }
        }
    }

    /** 표시창 안의 글자 색(밝고 진한 픽셀 평균)과 바탕색 */
    private static int[] colors(int[] rgb, int w, int h, float[] d) {
        int x1 = (int) (d[0] * w), x2 = (int) (d[2] * w), y1 = (int) ((1 - d[3]) * h), y2 = (int) ((1 - d[1]) * h);
        long sr = 0, sg = 0, sb = 0, n = 0, br = 0, bg = 0, bb = 0, bn = 0;
        for (int y = Math.max(0, y1); y < Math.min(h, y2); y++) for (int x = Math.max(0, x1); x < Math.min(w, x2); x++) {
            int c = rgb[y * w + x];
            int R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
            int mx = Math.max(R, Math.max(G, B)), mn = Math.min(R, Math.min(G, B));
            if (mx > 140 && (mx - mn > 60 || mx > 220)) { sr += R; sg += G; sb += B; n++; }
            else { br += R; bg += G; bb += B; bn++; }
        }
        int digit = n > 5 ? 0xFF000000 | (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n) : 0;
        if (digit != 0) digit = boost(digit);
        int bgc = bn > 0 ? 0xFF000000 | (int) (br / bn) << 16 | (int) (bg / bn) << 8 | (int) (bb / bn) : 0;
        return new int[]{digit, bgc};
    }

    /** 눌린 버튼 불빛 색: 버튼 안의 진한 색 픽셀 (없으면 0 = 기본 호박색) */
    private static int litColor(int[] rgb, int w, int h, List<float[]> buttons) {
        long sr = 0, sg = 0, sb = 0, n = 0;
        for (float[] b : buttons) {
            int cx = (int) (b[0] * w), cy = (int) ((1 - b[1]) * h), rr = (int) (b[2] * w);
            for (int y = cy - rr; y <= cy + rr; y++) for (int x = cx - rr; x <= cx + rr; x++) {
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                int c = rgb[y * w + x];
                int R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
                int mx = Math.max(R, Math.max(G, B)), mn = Math.min(R, Math.min(G, B));
                if (mx > 150 && mx - mn > 80) { sr += R; sg += G; sb += B; n++; }
            }
        }
        if (n < 10) return 0;
        return boost(0xFF000000 | (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n));
    }

    /** 표시용으로 밝게 */
    private static int boost(int c) {
        int R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
        int mx = Math.max(1, Math.max(R, Math.max(G, B)));
        float k = 255f / mx;
        return 0xFF000000 | Math.min(255, (int) (R * k)) << 16 | Math.min(255, (int) (G * k)) << 8 | Math.min(255, (int) (B * k));
    }

    // ------------------------------------------------------------------ 이진 영상 도구
    private static boolean[] close(boolean[] m, int w, int h, int r) {
        return erode(dilate(m, w, h, r), w, h, r);
    }

    private static boolean[] dilate(boolean[] m, int w, int h, int r) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!m[y * w + x]) continue;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < w && yy < h) o[yy * w + xx] = true;
            }
        }
        return o;
    }

    private static boolean[] erode(boolean[] m, int w, int h, int r) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean all = true;
            for (int dy = -r; dy <= r && all; dy++) for (int dx = -r; dx <= r; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= w || yy >= h || !m[yy * w + xx]) { all = false; break; }
            }
            o[y * w + x] = all;
        }
        return o;
    }

    /** 연결 덩어리: {x1, y1, x2, y2, 픽셀 수} */
    private static List<int[]> components(boolean[] m, int w, int h) {
        int[] lab = new int[m.length];
        List<int[]> out = new ArrayList<>();
        int[] stack = new int[m.length];
        int id = 0;
        for (int i = 0; i < m.length; i++) {
            if (!m[i] || lab[i] != 0) continue;
            id++;
            int sp = 0;
            stack[sp++] = i;
            lab[i] = id;
            int x1 = w, y1 = h, x2 = 0, y2 = 0, n = 0;
            while (sp > 0) {
                int p = stack[--sp];
                int x = p % w, y = p / w;
                n++;
                x1 = Math.min(x1, x); x2 = Math.max(x2, x); y1 = Math.min(y1, y); y2 = Math.max(y2, y);
                int[] nb = {p - 1, p + 1, p - w, p + w};
                for (int q : nb) {
                    if (q < 0 || q >= m.length) continue;
                    if ((q == p - 1 && x == 0) || (q == p + 1 && x == w - 1)) continue;
                    if (m[q] && lab[q] == 0) { lab[q] = id; stack[sp++] = q; }
                }
            }
            out.add(new int[]{x1, y1, x2, y2, n});
        }
        return out;
    }
}

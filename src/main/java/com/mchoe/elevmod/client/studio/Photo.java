package com.mchoe.elevmod.client.studio;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;

/** 스튜디오에 올린 사진 한 장 */
public final class Photo {
    private static int counter;

    public final String file;
    public BufferedImage img;
    /** 왼위, 오른위, 오른아래, 왼아래 (원본 픽셀) */
    public float[] corners;
    public Target target = Target.CAR_WALL;
    public String dispType = "seg";
    public int strength = 2;
    public float height;
    public Analyzer.Result preview;
    /** 층 버튼이 아닌 것으로 표시한 버튼(열림·닫힘·비상 등) – 분석 결과의 버튼 번호 */
    public final java.util.Set<Integer> excluded = new java.util.HashSet<>();
    /** 사진의 숫자 위에 모드의 층 이름을 겹쳐 쓸지 */
    public boolean labels = false;
    private ResourceLocation tex;

    public Photo(String file, BufferedImage img) {
        this.file = file;
        this.img = img;
        resetCorners();
        guessTarget();
        height = target.defHeight;
    }

    public void resetCorners() {
        int w = img.getWidth(), h = img.getHeight();
        corners = new float[]{0, 0, w, 0, w, h, 0, h};
        invalidate();
    }

    /** 파일 이름으로 부위 추측 (예: cop, 운전반, door, 문 …) */
    private void guessTarget() {
        String f = file.toLowerCase(java.util.Locale.ROOT);
        if (f.contains("acc") || f.contains("장애")) target = Target.COP_ACC;
        else if (f.contains("cop") || f.contains("운전반")) target = Target.COP;
        else if (f.contains("lantern") || f.contains("랜턴")) target = Target.LANTERN;
        else if (f.contains("panel") || f.contains("패널")) target = Target.HALL_PANEL;
        else if (f.contains("call") || f.contains("호출") || f.contains("콜")) target = Target.HALL_BUTTON;
        else if (f.contains("indicator") || f.contains("표시") || f.contains("인디")) target = Target.INDICATOR;
        else if (f.contains("jamb") || f.contains("쟘") || f.contains("틀")) target = Target.JAMB;
        else if (f.contains("hall") || f.contains("landing") || f.contains("승강장")) target = Target.LANDING_DOOR;
        else if (f.contains("door") || f.contains("문")) target = Target.CAR_DOOR;
        else if (f.contains("ceil") || f.contains("천장")) target = Target.CAR_CEILING;
        else if (f.contains("floor") || f.contains("바닥")) target = Target.CAR_FLOOR;
        else if (f.contains("back") || f.contains("뒷") || f.contains("뒤")) target = Target.CAR_BACK;
        else if (f.contains("front") || f.contains("정면")) target = Target.CAR_FRONT;
    }

    public void invalidate() { preview = null; excluded.clear(); }

    /** 분석 (미리보기와 만들기가 같은 결과를 쓰도록 한 번만) */
    public Analyzer.Result analysis() {
        if (preview == null) {
            BufferedImage w = warped(320);
            preview = target.panel ? Analyzer.panel(w, dispType, target.hasButtons(), target.hasDisplay(), target == Target.LANTERN)
                    : target == Target.CAR_FLOOR ? new Analyzer.Result() : Analyzer.surface(w, strength);
        }
        return preview;
    }

    /** 펴진 사진의 가로/세로 비율 (모서리 기준) */
    public float aspect() {
        float[] c = corners;
        double top = Math.hypot(c[2] - c[0], c[3] - c[1]), bot = Math.hypot(c[4] - c[6], c[5] - c[7]);
        double left = Math.hypot(c[6] - c[0], c[7] - c[1]), right = Math.hypot(c[4] - c[2], c[5] - c[3]);
        return (float) ((top + bot) / Math.max(1, left + right));
    }

    /** 원근 보정한 사진 (긴 변 max px) */
    public BufferedImage warped(int max) {
        float a = aspect();
        int w = a >= 1 ? max : Math.max(8, Math.round(max * a)), h = a >= 1 ? Math.max(8, Math.round(max / a)) : max;
        return Analyzer.warp(img, corners, w, h);
    }

    public ResourceLocation texture() {
        if (tex == null) {
            NativeImage n = toNative(img);
            tex = Minecraft.getInstance().getTextureManager().register("elevmod_studio_" + (counter++), new DynamicTexture(n));
        }
        return tex;
    }

    public void rotate() {
        img = ImageLoader.rotate90(img);
        release();
        resetCorners();
    }

    public void release() {
        if (tex != null) { Minecraft.getInstance().getTextureManager().release(tex); tex = null; }
    }

    public static NativeImage toNative(BufferedImage b) {
        NativeImage n = new NativeImage(b.getWidth(), b.getHeight(), false);
        for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
            int c = b.getRGB(x, y);
            int a = (c >>> 24) & 255, r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
            n.setPixelRGBA(x, y, (a << 24) | (bl << 16) | (g << 8) | r);
        }
        return n;
    }
}

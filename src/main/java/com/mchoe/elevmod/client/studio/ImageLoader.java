package com.mchoe.elevmod.client.studio;

import com.mchoe.elevmod.ElevMod;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 사진 파일 읽기. 자바 기본(PNG, JPG/JPEG, GIF, BMP, WBMP, TIFF) + 함께 들어 있는 추가 디코더(WebP, PSD, TGA, ICO 등).
 * 휴대폰 사진의 EXIF 회전 정보도 반영한다.
 */
public final class ImageLoader {
    private ImageLoader() {}

    private static boolean extraRegistered;

    /** 추가 형식(TwelveMonkeys ImageIO) 등록 – 없으면 조용히 건너뜀 */
    public static synchronized void registerExtra() {
        if (extraRegistered) return;
        extraRegistered = true;
        String[] spis = {
                "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.psd.PSDImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.tga.TGAImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.bmp.ICOImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.bmp.CURImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.pnm.PNMImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.pcx.PCXImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.sgi.SGIImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.iff.IFFImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.pict.PICTImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.icns.ICNSImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.hdr.HDRImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi",
                "com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi"};
        int ok = 0;
        for (String c : spis) {
            try {
                Object spi = Class.forName(c, true, ImageLoader.class.getClassLoader()).getDeclaredConstructor().newInstance();
                IIORegistry.getDefaultInstance().registerServiceProvider((ImageReaderSpi) spi);
                ok++;
            } catch (Throwable t) {
                ElevMod.LOG.debug("[elevmod] image reader not available: {}", c);
            }
        }
        ElevMod.LOG.info("[elevmod] extra image formats registered: {}", ok);
    }

    /** 지원 형식 목록(사용자 안내용) */
    public static String formats() {
        registerExtra();
        java.util.TreeSet<String> s = new java.util.TreeSet<>();
        for (String f : ImageIO.getReaderFileSuffixes()) s.add(f.toLowerCase(Locale.ROOT));
        return String.join(", ", s);
    }

    public static BufferedImage load(Path p) throws Exception {
        registerExtra();
        byte[] data = Files.readAllBytes(p);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".avif"))
                throw new UnsupportedOperationException("heic");
            throw new UnsupportedOperationException("format");
        }
        img = toRgb(img);
        int orient = exifOrientation(data);
        if (orient > 1) img = orient(img, orient);
        // 너무 큰 사진은 줄임 (분석·미리보기용 최대 1600px)
        int max = Math.max(img.getWidth(), img.getHeight());
        if (max > 1600) img = scale(img, 1600.0 / max);
        return img;
    }

    static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage o = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return o;
    }

    public static BufferedImage scale(BufferedImage src, double k) {
        int w = Math.max(1, (int) Math.round(src.getWidth() * k)), h = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return o;
    }

    /** 90° 단위 회전 (사용자가 직접 돌릴 때) */
    public static BufferedImage rotate90(BufferedImage src) {
        BufferedImage o = new BufferedImage(src.getHeight(), src.getWidth(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++)
            o.setRGB(src.getHeight() - 1 - y, x, src.getRGB(x, y));
        return o;
    }

    private static BufferedImage orient(BufferedImage src, int o) {
        int w = src.getWidth(), h = src.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap = o >= 5;
        switch (o) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return src; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /** JPEG EXIF 방향 태그(0x0112) – 없으면 1 */
    static int exifOrientation(byte[] d) {
        try {
            if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) return 1;
            int i = 2;
            while (i + 4 < d.length) {
                if ((d[i] & 0xFF) != 0xFF) return 1;
                int marker = d[i + 1] & 0xFF;
                int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
                if (marker == 0xE1 && i + 10 < d.length && d[i + 4] == 'E' && d[i + 5] == 'x' && d[i + 6] == 'i' && d[i + 7] == 'f') {
                    int t = i + 10;
                    boolean le = d[t] == 'I';
                    int ifd = t + u32(d, t + 4, le);
                    int n = u16(d, ifd, le);
                    for (int k = 0; k < n; k++) {
                        int e = ifd + 2 + k * 12;
                        if (u16(d, e, le) == 0x0112) return u16(d, e + 8, le);
                    }
                    return 1;
                }
                if (marker == 0xDA) return 1;
                i += 2 + len;
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private static int u16(byte[] d, int i, boolean le) {
        return le ? (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8) : ((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF);
    }

    private static int u32(byte[] d, int i, boolean le) {
        return le ? (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8) | ((d[i + 2] & 0xFF) << 16) | ((d[i + 3] & 0xFF) << 24)
                : ((d[i] & 0xFF) << 24) | ((d[i + 1] & 0xFF) << 16) | ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
    }
}

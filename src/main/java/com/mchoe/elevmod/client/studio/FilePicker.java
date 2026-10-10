package com.mchoe.elevmod.client.studio;

import com.mchoe.elevmod.ElevMod;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 컴퓨터의 파일 선택 창 (여러 장 선택). LWJGL tinyfiledialogs 를 사용하고,
 * 없거나 실패하면 null 을 돌려준다 (그 경우 창으로 끌어다 놓기 사용).
 */
public final class FilePicker {
    private FilePicker() {}

    public static boolean available() {
        try { Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs"); return true; } catch (Throwable t) { return false; }
    }

    /** 별도 스레드에서 열고, 결과를 받으면 done 호출 (렌더 스레드에서 실행되도록 호출 측에서 처리) */
    public static void open(String title, Consumer<List<Path>> done) {
        Thread t = new Thread(() -> {
            List<Path> out = new ArrayList<>();
            try {
                Class<?> tf = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                Method m = tf.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
                        Class.forName("org.lwjgl.PointerBuffer"), CharSequence.class, boolean.class);
                // 필터 없이 열어 모든 형식을 고를 수 있게 함 (읽을 수 없는 형식은 나중에 안내)
                Object r = m.invoke(null, title, "", null, null, true);
                if (r instanceof String s && !s.isEmpty()) for (String part : s.split("\\|")) if (!part.isBlank()) out.add(Path.of(part));
            } catch (Throwable e) {
                ElevMod.LOG.warn("[elevmod] file dialog failed: {}", e.toString());
                out = null;
            }
            done.accept(out);
        }, "elevmod-file-dialog");
        t.setDaemon(true);
        t.start();
    }
}

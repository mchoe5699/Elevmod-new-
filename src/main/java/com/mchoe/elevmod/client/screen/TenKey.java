package com.mchoe.elevmod.client.screen;

import net.minecraft.nbt.ListTag;

import java.util.Locale;

/**
 * 텐키(10-KEY) 입력 → 층 찾기.
 * 숫자를 차례로 눌러 층 번호를 만들고(15층 = 1, 5), 지하층은 B 를 먼저 누른다(지하 3층 = B, 3).
 * 층 이름이 "1F", "B1", "F"(4층 F 표기) 등이어도 맞춰 준다.
 */
public final class TenKey {
    private TenKey() {}

    private static String norm(String s) {
        s = s.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (s.length() > 1 && s.endsWith("F") && Character.isDigit(s.charAt(s.length() - 2))) s = s.substring(0, s.length() - 1);
        if (s.startsWith("B")) {
            String n = s.substring(1).replaceFirst("^0+(?=\\d)", "");
            return "B" + n;
        }
        return s.replaceFirst("^0+(?=\\d)", "");
    }

    /** 입력과 일치하는 층 인덱스, 없으면 -1 */
    public static int match(String input, ListTag names) {
        String in = norm(input);
        for (int i = 0; i < names.size(); i++) if (norm(names.getString(i)).equals(in)) return i;
        // 4층을 F 로 표기한 건물: 4 입력 → F 층
        if (in.equals("4")) for (int i = 0; i < names.size(); i++) if (norm(names.getString(i)).equals("F")) return i;
        if (in.equals("B4")) for (int i = 0; i < names.size(); i++) if (norm(names.getString(i)).equals("BF")) return i;
        return -1;
    }
}

package com.mchoe.elevmod.elevator;

import net.minecraft.nbt.CompoundTag;

/**
 * 층표시기(인디케이터) 동작·모양 설정 – 제어반마다 하나, 카 표시기와 승강장 표시기에 모두 적용.
 * 색 값 0 = 장치/의장 기본색 사용.
 */
public final class IndicatorProfile {
    // ---------------------------------------------------------------- 층 전환 효과
    public static final String[] TRANS = {"none", "scroll", "fade", "blink", "wipe"};
    public static final String[] TRANS_DIR = {"auto", "up", "down", "left", "right"};
    public int trans = 0, transDir = 0;
    public float transSec = 0.4f;
    // ---------------------------------------------------------------- 화살표 전환 시점 (0 도착 시, 1 도착 전, 2 도착 후)
    public int arrowMode = 0;
    public float arrowSec = 2f;
    // ---------------------------------------------------------------- 목적층이 없을 때 화살표 꺼지는 시점 (0 도착 시, 1 도착 전, 2 도착 후)
    public int offMode = 0;
    public float offSec = 2f;
    /** 목적층이 없어도 문이 열린 채 정차해 있는 동안 (마지막 운행 방향) 화살표 표시 */
    public boolean openArrow = false;
    /** 도트 글꼴 (라이브러리 id, "" = 기본 글꼴) */
    public String dotFont = "";
    /** 문열림 애니메이션 (문이 열리는 중·열려 있을 때, "" = 화살표 정지 장면) */
    public String animOpen = "";
    // ---------------------------------------------------------------- 표시 칸 배치 (%) : 화살표 칸 폭, 숫자 위치·크기, 화살표 크기
    public int arrowW = 30, numX = 0, numY = 0, numScale = 100, arrowScale = 100;
    // ---------------------------------------------------------------- 도트매트릭스
    public int dotCols = 5, dotRows = 7;
    public boolean dotRound = true;
    public int dotOn, dotOff, dotBg;
    // ---------------------------------------------------------------- 세그먼트 (0 = 7세그, 1 = 9세그(위 V), 2 = 14세그, 3 = 16세그)
    public int segType = 0, segShape = 0;      // 모양 0 각진(육각) 1 둥근 2 평평
    public float segSlant = 6f, segThick = 0.12f;
    public boolean segLead1 = false;
    public int segOn, segOff, segBg;
    // ---------------------------------------------------------------- LCD
    public int lcdFg, lcdArrow, lcdBg;
    // ---------------------------------------------------------------- 애니메이션 (애니메이션 라이브러리 id, "" = 기본 화살표)
    public String animUp = "", animDown = "", animIdle = "";

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("tr", trans); t.putInt("td", transDir); t.putFloat("ts", transSec);
        t.putInt("am", arrowMode); t.putFloat("as", arrowSec);
        t.putInt("om", offMode); t.putFloat("os", offSec); t.putBoolean("oa", openArrow); t.putString("df", dotFont);
        t.putString("ao", animOpen); t.putInt("aw", arrowW); t.putInt("nx", numX); t.putInt("ny", numY); t.putInt("nsc", numScale); t.putInt("asc", arrowScale);
        t.putInt("dc", dotCols); t.putInt("dr", dotRows); t.putBoolean("dro", dotRound);
        t.putInt("don", dotOn); t.putInt("doff", dotOff); t.putInt("dbg", dotBg);
        t.putInt("st", segType); t.putInt("ss", segShape); t.putFloat("sl", segSlant); t.putFloat("sth", segThick); t.putBoolean("s1", segLead1);
        t.putInt("son", segOn); t.putInt("soff", segOff); t.putInt("sbg", segBg);
        t.putInt("lf", lcdFg); t.putInt("la", lcdArrow); t.putInt("lb", lcdBg);
        t.putString("au", animUp); t.putString("ad", animDown); t.putString("ai", animIdle);
        return t;
    }

    public static IndicatorProfile load(CompoundTag t) {
        IndicatorProfile p = new IndicatorProfile();
        if (t == null || t.isEmpty()) return p;
        p.trans = t.getInt("tr"); p.transDir = t.getInt("td"); p.transSec = t.contains("ts") ? t.getFloat("ts") : 0.4f;
        p.arrowMode = t.getInt("am"); p.arrowSec = t.contains("as") ? t.getFloat("as") : 2f;
        p.offMode = t.getInt("om"); p.offSec = t.contains("os") ? t.getFloat("os") : 2f; p.openArrow = t.getBoolean("oa"); p.dotFont = t.getString("df");
        p.animOpen = t.getString("ao");
        p.arrowW = t.contains("aw") ? t.getInt("aw") : 30; p.numX = t.getInt("nx"); p.numY = t.getInt("ny");
        p.numScale = t.contains("nsc") ? t.getInt("nsc") : 100; p.arrowScale = t.contains("asc") ? t.getInt("asc") : 100;
        p.dotCols = t.contains("dc") ? clamp(t.getInt("dc"), 3, 16) : 5; p.dotRows = t.contains("dr") ? clamp(t.getInt("dr"), 5, 24) : 7;
        p.dotRound = !t.contains("dro") || t.getBoolean("dro");
        p.dotOn = t.getInt("don"); p.dotOff = t.getInt("doff"); p.dotBg = t.getInt("dbg");
        p.segType = t.getInt("st"); p.segShape = t.getInt("ss");
        p.segSlant = t.contains("sl") ? t.getFloat("sl") : 6f; p.segThick = t.contains("sth") ? t.getFloat("sth") : 0.12f;
        p.segLead1 = t.getBoolean("s1");
        p.segOn = t.getInt("son"); p.segOff = t.getInt("soff"); p.segBg = t.getInt("sbg");
        p.lcdFg = t.getInt("lf"); p.lcdArrow = t.getInt("la"); p.lcdBg = t.getInt("lb");
        p.animUp = t.getString("au"); p.animDown = t.getString("ad"); p.animIdle = t.getString("ai");
        return p;
    }

    static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    /** 제어반 화면에서 바꾸기: key = 필드 이름, delta = 순환 방향, text = 직접 입력 값 */
    public void edit(String key, int delta, String text) {
        switch (key) {
            case "trans" -> trans = Math.floorMod(trans + delta, TRANS.length);
            case "transdir" -> transDir = Math.floorMod(transDir + delta, TRANS_DIR.length);
            case "transsec" -> transSec = num(text, transSec, 0f, 5f);
            case "arrowmode" -> arrowMode = Math.floorMod(arrowMode + delta, 3);
            case "arrowsec" -> arrowSec = num(text, arrowSec, 0f, 30f);
            case "offmode" -> offMode = Math.floorMod(offMode + delta, 3);
            case "offsec" -> offSec = num(text, offSec, 0f, 30f);
            case "openarrow" -> openArrow = !openArrow;
            case "dotfont" -> dotFont = text;
            case "animOpen" -> animOpen = text;
            case "arroww" -> arrowW = (int) num(text, arrowW, 0, 60);
            case "numx" -> numX = (int) num(text, numX, -50, 50);
            case "numy" -> numY = (int) num(text, numY, -50, 50);
            case "numscale" -> numScale = (int) num(text, numScale, 30, 200);
            case "arrowscale" -> arrowScale = (int) num(text, arrowScale, 30, 200);
            case "dotsize" -> {
                String[] s = text.toLowerCase().replace("×", "x").replace("*", "x").split("x");
                if (s.length == 2) {
                    try { dotCols = clamp(Integer.parseInt(s[0].trim()), 3, 16); dotRows = clamp(Integer.parseInt(s[1].trim()), 5, 24); } catch (NumberFormatException ignored) {}
                }
            }
            case "dotround" -> dotRound = !dotRound;
            case "dotOn" -> dotOn = color(text);
            case "dotOff" -> dotOff = color(text);
            case "dotBg" -> dotBg = color(text);
            case "segtype" -> segType = Math.floorMod(segType + delta, 4);
            case "segshape" -> segShape = Math.floorMod(segShape + delta, 3);
            case "segslant" -> segSlant = num(text, segSlant, -20f, 20f);
            case "segthick" -> segThick = num(text, segThick, 0.05f, 0.25f);
            case "seglead" -> segLead1 = !segLead1;
            case "segOn" -> segOn = color(text);
            case "segOff" -> segOff = color(text);
            case "segBg" -> segBg = color(text);
            case "lcdFg" -> lcdFg = color(text);
            case "lcdArrow" -> lcdArrow = color(text);
            case "lcdBg" -> lcdBg = color(text);
            case "animUp" -> animUp = text;
            case "animDown" -> animDown = text;
            case "animIdle" -> animIdle = text;
            default -> {}
        }
    }

    static float num(String t, float def, float lo, float hi) {
        try { return Math.max(lo, Math.min(hi, Float.parseFloat(t.trim().replace(",", ".")))); } catch (Exception e) { return def; }
    }

    /** "#RRGGBB" / "RRGGBB" → ARGB, 빈 문자열 = 0 (기본색) */
    public static int color(String t) {
        t = t.trim().replace("#", "");
        if (t.isEmpty()) return 0;
        try { return 0xFF000000 | (int) (Long.parseLong(t, 16) & 0xFFFFFF); } catch (Exception e) { return 0; }
    }

    public static int or(int c, int def) { return c == 0 ? def : c; }
}

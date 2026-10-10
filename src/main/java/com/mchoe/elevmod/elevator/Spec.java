package com.mchoe.elevmod.elevator;

import java.util.Locale;

/**
 * 나무위키 '엘리베이터', '엘리베이터/구성', '엘리베이터/종류' 문서를 기준으로 한 사양표.
 * 1블록 = 1m, 1틱 = 1/20초 로 환산한다.
 */
public final class Spec {
    private Spec() {}

    /** 용도별 분류 (엘리베이터/종류 문서) */
    public enum Usage {
        PASSENGER,          // 승객용
        ACCESSIBLE,         // 장애인용
        PASSENGER_FREIGHT,  // 승객화물용
        FREIGHT,            // 화물용
        HOSPITAL,           // 병원용(구 침대용)
        OBSERVATION,        // 전망용
        FIREFIGHTER,        // 소방구조용(구 비상용)
        EVACUATION,         // 피난용
        CAR,                // 자동차용
        DUMBWAITER,         // 덤웨이터
        HOME,               // 가정용/주택용
        WHEELCHAIR_LIFT,    // 휠체어 리프트
        CONSTRUCTION,       // 공사용 리프트(랙/피니언)
        PATERNOSTER;        // 순환식(파터노스터)

        public String key() { return "elevmod.usage." + name().toLowerCase(Locale.ROOT); }

        public boolean passengerCar() {
            return this != FREIGHT && this != CAR && this != DUMBWAITER && this != CONSTRUCTION
                    && this != PATERNOSTER && this != WHEELCHAIR_LIFT;
        }

        public boolean hasCop() { return this != DUMBWAITER && this != PATERNOSTER; }
    }

    /** 구동 방식 (작동 방식 문단) */
    public enum Drive {
        GEARED,      // 권상식 기어드(웜/헬리컬) - 기계실
        GEARLESS,    // 권상식 기어리스 - 기계실
        MRL,         // 무기계실
        MMR,         // 미니기계실
        HYDRAULIC,   // 유압식 (간접식)
        DRUM,        // 권동식 (포지티브식)
        RACK_PINION; // 랙/피니언

        public String key() { return "elevmod.drive." + name().toLowerCase(Locale.ROOT); }

        public boolean hasCounterweight() { return this == GEARED || this == GEARLESS || this == MRL || this == MMR; }
    }

    /**
     * 도어 방식. panels = 한쪽(또는 전체) 문짝 수, center = 중앙개폐 여부.
     * 망원식(2-speed/3-speed) 문짝은 빠른 문짝이 카-승강장 사이 틈에 가깝게, 느린 문짝이 바깥쪽에 겹쳐 수납된다.
     */
    public enum DoorType {
        CO(2.0f, 1, true),      // 중앙개폐 2짝
        CO4(2.4f, 2, true),     // 2-speed 중앙개폐 4짝
        CO6(2.8f, 3, true),     // 3-speed 중앙개폐 6짝 (대형 화물)
        S1(2.2f, 1, false),     // 편개 1짝
        S2(2.6f, 2, false),     // 2-speed 편개 2짝 (2S)
        S3(3.0f, 3, false),     // 3-speed 편개 3짝 (3S)
        S4(3.4f, 4, false),     // 4-speed 편개 4짝
        UP(4.0f, 2, false),     // 상승개폐 (2짝 위로 겹침)
        BIPART(3.6f, 2, true),  // 상하개폐 (위짝 위로, 아래짝 아래로 – 대형 화물)
        SWING(1.6f, 1, false),  // 여닫이
        GATE(2.8f, 1, false);   // 접이식 게이트

        public final float seconds;
        public final int panels;
        public final boolean center;
        DoorType(float seconds, int panels, boolean center) { this.seconds = seconds; this.panels = panels; this.center = center; }
        public String key() { return "elevmod.door." + name().toLowerCase(Locale.ROOT); }
        public boolean sideOpening() { return !center && this != UP && this != SWING && this != GATE; }
    }

    /** 승강장 출입구 틀(쟘) */
    public enum Jamb {
        NARROW, WIDE, NONE;
        public String key() { return "elevmod.jamb." + name().toLowerCase(Locale.ROOT); }
        public float face() { return this == WIDE ? 0.32f : this == NARROW ? 0.1f : 0f; }
    }

    /** 카 천장 */
    public enum Ceiling {
        PANEL, DOWNLIGHT, GRID, COVE;
        public String key() { return "elevmod.ceiling." + name().toLowerCase(Locale.ROOT); }
    }

    /** 운전반 위치(카 안에서 문을 바라볼 때 기준) / 편개 문 열림 방향. BOTH = 양쪽 모두 설치 */
    public enum Side {
        AUTO, LEFT, RIGHT, BOTH;
        public String key() { return "elevmod.side2." + name().toLowerCase(Locale.ROOT); }
        public boolean left() { return this == LEFT || this == BOTH; }
        public boolean right() { return this == RIGHT || this == BOTH; }
    }

    /** 버튼 의장 (승강장 호출버튼 · 카 운전반 · 장애인 운전반 공용) */
    public enum ButtonStyle {
        ROUND,    // 원형 스테인리스 + 링 조명 (현재 국내 표준형)
        SQUARE,   // 사각 버튼
        TOUCH,    // 터치식(흑색 강화유리, 정전식)
        CLASSIC;  // 구형 대형 원형 버튼(황동 테)
        public String key() { return "elevmod.bstyle." + name().toLowerCase(Locale.ROOT); }
    }

    /** 위치표시기 소자 */
    public enum Display {
        SEGMENT, DOT, LCD;
        public String key() { return "elevmod.display." + name().toLowerCase(Locale.ROOT); }
    }

    /** 카 벽 마감 (AUTO = 테마를 따름) */
    public enum WallFinish {
        AUTO, HAIRLINE, ETCHED, MIRROR, GOLD, WOOD, WHITE, MARBLE;
        public String key() { return "elevmod.wall." + name().toLowerCase(Locale.ROOT); }
    }

    /** 카 바닥 마감 (AUTO = 용도를 따름) */
    public enum FloorFinish {
        AUTO, GRANITE, MARBLE, CHECKER, RUBBER, WOOD;
        public String key() { return "elevmod.floor." + name().toLowerCase(Locale.ROOT); }
    }

    /** 운전반 층 입력 방식: 층별 버튼 / 텐키(10-KEY, 숫자 조합 입력) */
    public enum CopKeys {
        BUTTONS, TENKEY;
        public String key() { return "elevmod.copkeys." + name().toLowerCase(Locale.ROOT); }
    }

    public enum Theme {
        STAINLESS, GOLD, WOOD, WHITE, MIRROR, CHECKER, GLASS;
        public String key() { return "elevmod.theme." + name().toLowerCase(Locale.ROOT); }
        public String tex() { return name().toLowerCase(Locale.ROOT); }
    }

    /** 안내 방식: 음성안내 / 구형 '땡' 타종 / 무음 */
    public enum Voice {
        VOICE, CHIME, SILENT;
        public String key() { return "elevmod.voice." + name().toLowerCase(Locale.ROOT); }
    }

    /** 제어반 레드스톤 입력 기능 */
    public enum RedstoneFn {
        NONE, FIRE, EARTHQUAKE, POWER, PARKING;
        public String key() { return "elevmod.rs." + name().toLowerCase(Locale.ROOT); }
    }

    /** 운전 모드 */
    public enum Mode {
        NORMAL,      // 자동(군관리 포함)
        INDEPENDENT, // 독립운전
        HOLD,        // 화물용 홀드(전용)
        FIRE1,       // 소방운전 1차(호출귀환)
        FIRE2,       // 소방운전 카 내부 1단
        FIRE2B,      // 소방운전 카 내부 2단(개문발차)
        INSPECTION,  // 점검운전
        PARKING,     // 파킹(운행정지)
        SABBATH,     // 안식일 운전(각층 자동정지)
        SHUTTLE,     // 전망대 셔틀(직원 운행)
        EARTHQUAKE;  // 지진관제

        public String key() { return "elevmod.mode." + name().toLowerCase(Locale.ROOT); }
        public boolean fire() { return this == FIRE1 || this == FIRE2 || this == FIRE2B; }
    }

    public enum Fault {
        NONE, SAFETY_GEAR, ROPE_BRAKE, FINAL_LIMIT, BUFFER, DOOR_LOCK, STOP_SWITCH;
        public String key() { return "elevmod.fault." + name().toLowerCase(Locale.ROOT); }
    }

    // ---------------------------------------------------------------- 정격하중 / 정원
    /** 적재하중(kg). 국내 최대 승객화물용 10250kg(157인승, 개정 136인승), 화물용 30000kg */
    public static final int[] CAPACITY_KG = {300, 450, 550, 600, 750, 900, 1000, 1150, 1350, 1600, 2000, 2500,
            3000, 4000, 5000, 7500, 10250, 15000, 20000, 30000};

    /** 개정 기준 1인 75kg */
    public static int persons(int kg) { return Math.max(1, kg / 75); }
    /** 구 기준 1인 65kg */
    public static int personsOld(int kg) { return Math.max(1, kg / 65); }

    private static final float[][] AREA = {
            {100, 0.37f}, {300, 0.9f}, {450, 1.3f}, {600, 1.6f}, {750, 1.9f}, {900, 2.2f}, {1000, 2.4f},
            {1150, 2.7f}, {1350, 3.1f}, {1600, 3.56f}, {2000, 4.2f}, {2500, 5.0f}};

    /** 카 바닥 유효면적(m²) – EN81/KS 승강기 기준표 근사 */
    public static float carArea(int kg) {
        if (kg <= AREA[0][0]) return AREA[0][1];
        for (int i = 1; i < AREA.length; i++) {
            if (kg <= AREA[i][0]) {
                float t = (kg - AREA[i - 1][0]) / (AREA[i][0] - AREA[i - 1][0]);
                return AREA[i - 1][1] + t * (AREA[i][1] - AREA[i - 1][1]);
            }
        }
        return 5.0f + (kg - 2500) / 100f * 0.16f;
    }

    // ---------------------------------------------------------------- 정격속도
    /** 정격속도 (m/min). 유압식 최대 61, 권동식 30, 초고속 1,010m/min 이상 */
    public static final int[] SPEED_MPM = {9, 15, 18, 30, 45, 60, 90, 105, 120, 150, 180, 210, 240, 300, 360, 420,
            480, 540, 600, 750, 900, 1010, 1260};

    /** m/min -> 블록/틱 */
    public static double bpt(double mpm) { return mpm / 1200.0; }

    /** 가속도 (블록/틱²) */
    public static double accel(double mpm) {
        double a = mpm <= 60 ? 0.5 : mpm <= 150 ? 0.8 : mpm <= 360 ? 1.0 : 1.2; // m/s²
        return a / 400.0;
    }

    public static final double G = 9.8 / 400.0;
    /** 저속 재착상/구출 운전 속도 15m/min */
    public static final double CRAWL = bpt(15);

    public static String speedClass(int mpm) {
        if (mpm <= 45) return "elevmod.speedclass.low";
        if (mpm <= 105) return "elevmod.speedclass.mid";
        if (mpm <= 240) return "elevmod.speedclass.high";
        return "elevmod.speedclass.ultra";
    }
}

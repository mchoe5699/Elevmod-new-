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

    /** 도어 방식 (엘리베이터/구성 - 도어 문단) */
    public enum DoorType {
        CO(2.0f),      // 중앙개폐 2짝 (single slide center)
        CO4(2.4f),     // 2/4 중앙개폐 4짝
        S1(2.2f),      // 편개 1짝 (single side)
        S2(2.6f),      // 2짝 편개 (2-speed)
        S3(3.0f),      // 3짝 편개 (3-speed)
        UP(4.0f),      // 상승개폐 (화물/자동차용)
        SWING(1.6f),   // 여닫이문
        GATE(2.8f);    // 접이식 게이트(팬터그래프)

        public final float seconds;
        DoorType(float seconds) { this.seconds = seconds; }
        public String key() { return "elevmod.door." + name().toLowerCase(Locale.ROOT); }
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

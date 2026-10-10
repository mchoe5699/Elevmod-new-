package com.mchoe.elevmod.elevator;

/** 카 운전반(COP) 조작 코드 */
public final class CarAction {
    private CarAction() {}

    public static final int FLOOR = 1;          // arg = floor index
    public static final int CANCEL = 2;         // arg = floor index (행선층 취소)
    public static final int OPEN = 3;           // 열림 버튼 (누름)
    public static final int OPEN_HOLD = 4;      // 열림 버튼 누르고 있음
    public static final int CLOSE = 5;          // 닫힘 버튼
    public static final int CLOSE_HOLD = 6;     // 닫힘 버튼 누르고 있음
    public static final int ALARM = 7;          // 비상호출
    public static final int KEY_INDEPENDENT = 8;// 운전원 키: 독립운전
    public static final int KEY_INSPECTION = 9; // 운전원 키: 점검운전
    public static final int RUN_UP = 10;        // 점검/휠체어리프트 상승 (누르고 있음)
    public static final int RUN_DOWN = 11;      // 하강
    public static final int KEY_FIRE1 = 12;     // 소방 키 1단
    public static final int KEY_FIRE2 = 13;     // 소방 키 2단(개문발차)
    public static final int HOLD = 14;          // 화물용 홀드
    public static final int STOP_SWITCH = 15;   // 구형 비상정지 버튼
    public static final int ACCESSIBLE_FLOOR = 16; // 장애인용 운전반 층 버튼 (문 열림 시간 연장)
    public static final int DESIGN = 17;        // 디자인 변경기: arg = 부위 * 2 + (뒤로 1)

    /** 디자인 변경 부위 */
    public static final int PART_WALL = 0, PART_FLOOR = 1, PART_CEILING = 2, PART_COP = 3, PART_ACC = 4, PART_INDICATOR = 5, PART_DOOR = 6, PART_HANDRAIL = 7;
}

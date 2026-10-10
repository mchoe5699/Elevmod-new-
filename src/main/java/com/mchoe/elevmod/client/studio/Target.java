package com.mchoe.elevmod.client.studio;

/** 사진이 어떤 부위인지 – 사진마다 따로 정한다 */
public enum Target {
    CAR_WALL("wall", false, 0),        // 카 옆벽 (안쪽에서)
    CAR_BACK("backWall", false, 0),    // 카 뒷벽
    CAR_FRONT("frontWall", false, 0),  // 카 정면(리턴·문 위) – 안쪽에서 본 문 쪽 벽 전체
    CAR_CEILING("ceiling", false, 0),  // 카 천장 (올려다본 모습)
    CAR_FLOOR("floor", false, 0),      // 카 바닥
    CAR_DOOR("door", false, 0),        // 카 도어 (안쪽에서 본 닫힌 문 전체)
    LANDING_DOOR("landingDoor", false, 0), // 승강장 문 (홀에서 본 닫힌 문 전체)
    JAMB("jambFace", false, 0),        // 승강장 문틀(쟘) 정면 – 틀 바깥선까지
    COP("copPanel", true, 1.05f),      // 주 운전반
    COP_ACC("copAcc", true, 0.22f),    // 장애인 운전반 (가로형)
    INDICATOR("indicator", true, 0.16f), // 층표시기 (LED·LCD·세그먼트)
    HALL_BUTTON("hallButton", true, 0.3f), // 승강장 호출버튼
    HALL_PANEL("hallPanel", true, 0.45f),  // 호출버튼 패널(표시기 포함)
    LANTERN("lantern", true, 0.14f);   // 홀랜턴

    public final String key;
    public final boolean panel;
    public final float defHeight;

    Target(String key, boolean panel, float defHeight) { this.key = key; this.panel = panel; this.defHeight = defHeight; }

    public String langKey() { return "elevmod.target." + name().toLowerCase(java.util.Locale.ROOT); }

    public boolean hasDisplay() { return this == COP || this == COP_ACC || this == INDICATOR || this == HALL_PANEL; }

    public boolean hasButtons() { return this == COP || this == COP_ACC || this == HALL_BUTTON || this == HALL_PANEL; }
}

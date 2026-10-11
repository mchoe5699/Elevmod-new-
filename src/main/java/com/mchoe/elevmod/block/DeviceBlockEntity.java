package com.mchoe.elevmod.block;

import com.mchoe.elevmod.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 승강장 장치(호출버튼, 층표시기, 홀랜턴, 소방스위치, 행선층 패널, 원격호출 수신기) 공용 블록엔티티 */
public class DeviceBlockEntity extends BlockEntity {
    @Nullable public BlockPos ctrl;
    public int floorY;
    // 제어반이 밀어주는 표시 정보
    public String text = "", status = "", letter = "", floorName = "";
    public int dir;
    /** 카 문이 열리는 중/열려 있음 (문열림 화살표용) */
    public boolean doorOpen;
    /** 장치 크기 (%, 가로·세로) */
    public int sizeW = 100, sizeH = 100;

    public void setSize(int w, int h) {
        sizeW = Math.max(50, Math.min(250, w)); sizeH = Math.max(50, Math.min(250, h));
        sync();
    }

    public void setDoorOpen(boolean o) { if (o != doorOpen) { doorOpen = o; hash = 0; } }

    public boolean moving, lanUp, lanDown, lanAny, upLit, downLit, hasUp = true, hasDown = true, dumbwaiter;
    public final List<String> names = new ArrayList<>();
    public boolean redstoneLast;
    /** 의장 번호 (종류별 의미는 designKey 참고) */
    public int design;
    /** 제어반이 지정한 커스텀 디자인 id, 이 장치만의 선택("" = 제어반 따름, "-" = 기본 디자인 고정, 그 외 = 디자인 id) */
    public String ctrlDesign = "", customSel = "";

    /** 제어반의 표시기 설정(동작·모양·색) */
    public CompoundTag indTag = new CompoundTag();
    private com.mchoe.elevmod.elevator.IndicatorProfile profCache;
    private CompoundTag profCacheTag;

    /** 촬영(진열)용: 제어반 설정 대신 이 장치만의 표시기 설정을 유지 (저장 안 함) */
    public boolean lockInd;

    public void forceIndTag(CompoundTag t) { lockInd = false; setIndTag(t); lockInd = true; }

    public void setIndTag(CompoundTag t) {
        if (lockInd || t.equals(indTag)) return;
        indTag = t;
        hash = 0;
        sync();
    }

    /** 이 표시기만의 개별 디자인 (비어 있으면 제어반 설정을 따름) */
    public CompoundTag ownInd = new CompoundTag();

    public com.mchoe.elevmod.elevator.IndicatorProfile profile() {
        CompoundTag t = ownInd.isEmpty() ? indTag : ownInd;
        if (profCache == null || profCacheTag != t) { profCache = com.mchoe.elevmod.elevator.IndicatorProfile.load(t); profCacheTag = t; }
        return profCache;
    }

    /** 개별 디자인 바꾸기 (key "follow" = 제어반 설정 따르기) */
    public void editInd(String key, int delta, String text) {
        if (key.equals("follow")) ownInd = new CompoundTag();
        else {
            com.mchoe.elevmod.elevator.IndicatorProfile pr = com.mchoe.elevmod.elevator.IndicatorProfile.load(ownInd.isEmpty() ? indTag : ownInd);
            pr.edit(key, delta, text);
            ownInd = pr.save();
        }
        hash = 0;
        sync();
    }

    /** 실제로 쓸 커스텀 디자인 id ("" = 기본 디자인) */
    public String effectiveDesign() { return customSel.equals("-") ? "" : !customSel.isEmpty() ? customSel : ctrlDesign; }

    public void setCtrlDesign(String id) {
        if (id.equals(ctrlDesign)) return;
        ctrlDesign = id;
        hash = 0;
        sync();
    }

    public void setCustomSel(String sel, int basic) {
        customSel = sel;
        design = basic;
        hash = 0;
        sync();
    }

    /** 설치 위치 미세 조정 (1/16 블록 단위). offX + = 정면에서 볼 때 오른쪽, offY + = 위 */
    public int offX, offY;
    public static final int OFF_LIMIT = 16;

    public void setOffset(int x, int y) {
        offX = Math.max(-OFF_LIMIT, Math.min(OFF_LIMIT, x));
        offY = Math.max(-OFF_LIMIT, Math.min(OFF_LIMIT, y));
        hash = 0;
        sync();
    }
    private int hash;

    /** 종류별 의장 개수 */
    public static int designCount(DeviceBlock.Kind k) {
        return switch (k) {
            case HALL_BUTTON, HALL_PANEL -> 4;               // 원형 / 사각 / 터치 / 구형
            case IND_SEGMENT, IND_DOT -> 4;                  // 색상 4종
            case IND_LCD -> 3;                               // 청색 / 흑색 / 백색
            case IND_LAMP -> 2;                              // 호박색 / 백색 유리
            case LANTERN -> 3;                               // 삼각 렌즈 / 원형 / 세로 바
            case DESTINATION -> 2;                           // 짙은 / 밝은
            default -> 1;
        };
    }

    public static String designKey(DeviceBlock.Kind k, int d) {
        String g = switch (k) {
            case HALL_BUTTON, HALL_PANEL -> "bstyle." + com.mchoe.elevmod.elevator.Spec.ButtonStyle.values()[Math.min(d, 3)].name().toLowerCase(java.util.Locale.ROOT);
            case IND_SEGMENT -> "dcolor.seg" + d;
            case IND_DOT -> "dcolor.dot" + d;
            case IND_LCD -> "dcolor.lcd" + d;
            case IND_LAMP -> "dcolor.lamp" + d;
            case LANTERN -> "dcolor.lantern" + d;
            case DESTINATION -> "dcolor.dest" + d;
            default -> "design.default";
        };
        return "elevmod." + g;
    }

    public void setDesign(int d) {
        design = d;
        hash = 0;
        sync();
    }

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DEVICE_BE.get(), pos, state);
    }

    public void link(BlockPos c, int fy) {
        ctrl = c;
        floorY = fy;
        hash = 0;
        sync();
    }

    public void unlink() {
        ctrl = null;
        text = ""; status = ""; letter = ""; floorName = "";
        upLit = downLit = lanUp = lanDown = lanAny = false;
        names.clear();
        sync();
    }

    public void applyDisplay(String text, int dir, String status, boolean moving, boolean lanUp, boolean lanDown, boolean lanAny,
                             boolean upLit, boolean downLit, boolean hasUp, boolean hasDown, String letter, boolean dumbwaiter,
                             String floorName, List<String> names) {
        int h = Objects.hash(text, dir, status, moving, lanUp, lanDown, lanAny, upLit, downLit, hasUp, hasDown, letter, dumbwaiter, floorName, names);
        if (h == hash) return;
        hash = h;
        this.text = text; this.dir = dir; this.status = status; this.moving = moving;
        this.lanUp = lanUp; this.lanDown = lanDown; this.lanAny = lanAny;
        this.upLit = upLit; this.downLit = downLit; this.hasUp = hasUp; this.hasDown = hasDown;
        this.letter = letter; this.dumbwaiter = dumbwaiter; this.floorName = floorName;
        this.names.clear();
        this.names.addAll(names);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (ctrl != null) t.putLong("ctrl", ctrl.asLong());
        t.putInt("fy", floorY);
        t.putString("text", text);
        t.putString("st", status);
        t.putString("let", letter);
        t.putString("fn", floorName);
        t.putInt("dir", dir);
        int flags = (moving ? 1 : 0) | (lanUp ? 2 : 0) | (lanDown ? 4 : 0) | (lanAny ? 8 : 0) | (upLit ? 16 : 0) | (downLit ? 32 : 0)
                | (hasUp ? 64 : 0) | (hasDown ? 128 : 0) | (dumbwaiter ? 256 : 0) | (redstoneLast ? 512 : 0) | (doorOpen ? 1024 : 0);
        t.putInt("sw", sizeW); t.putInt("sh", sizeH);
        t.putInt("fl", flags);
        t.putInt("design", design);
        t.putString("cdes", ctrlDesign); t.putString("csel", customSel);
        t.put("ind", indTag);
        if (!ownInd.isEmpty()) t.put("oind", ownInd);
        t.putInt("offX", offX); t.putInt("offY", offY);
        t.putLong("origin", worldPosition.asLong());
        t.putFloat("yaw", yaw);
        if (!Double.isNaN(pivX)) { t.putDouble("pvx", pivX); t.putDouble("pvz", pivZ); }
        ListTag l = new ListTag();
        for (String n : names) l.add(StringTag.valueOf(n));
        t.put("names", l);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        yaw = t.getFloat("yaw");
        pivX = Double.NaN; pivZ = Double.NaN;
        if (t.contains("pvx")) yaw = 0;   // 회전기(삭제됨)로 돌린 것은 원래대로
        ctrl = t.contains("ctrl") ? BlockPos.of(t.getLong("ctrl")) : null;
        floorY = t.getInt("fy");
        text = t.getString("text");
        status = t.getString("st");
        letter = t.getString("let");
        floorName = t.getString("fn");
        dir = t.getInt("dir");
        int f = t.getInt("fl");
        moving = (f & 1) != 0; lanUp = (f & 2) != 0; lanDown = (f & 4) != 0; lanAny = (f & 8) != 0;
        upLit = (f & 16) != 0; downLit = (f & 32) != 0; hasUp = (f & 64) != 0; hasDown = (f & 128) != 0;
        dumbwaiter = (f & 256) != 0; redstoneLast = (f & 512) != 0; doorOpen = (f & 1024) != 0;
        sizeW = t.contains("sw") ? t.getInt("sw") : 100; sizeH = t.contains("sh") ? t.getInt("sh") : 100;
        design = t.getInt("design");
        ctrlDesign = t.getString("cdes"); customSel = t.getString("csel");
        indTag = t.getCompound("ind");
        ownInd = t.getCompound("oind");
        offX = t.getInt("offX"); offY = t.getInt("offY");
        if (t.contains("origin")) {
            BlockPos o = BlockPos.of(t.getLong("origin"));
            if (!o.equals(worldPosition)) {
                BlockPos d = worldPosition.subtract(o);
                if (ctrl != null) ctrl = ctrl.offset(d);
                floorY += d.getY(); moved = true;
            }
        }
        names.clear();
        ListTag l = t.getList("names", Tag.TAG_STRING);
        for (Tag x : l) names.add(x.getAsString());
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        net.minecraft.world.phys.AABB b = new net.minecraft.world.phys.AABB(worldPosition).inflate(1.2);
        if (yaw == 0 || Double.isNaN(pivX)) return b;
        // 회전기로 돌리면 회전축에서 떨어진 만큼 그려지는 곳이 옮겨짐
        double r = Math.hypot(worldPosition.getX() + 0.5 - pivX, worldPosition.getZ() + 0.5 - pivZ);
        return b.inflate(r + 1);
    }

    /** Ctrl + / - 로 돌린 각도(도, 세로축) */
    public float yaw;
    /** 회전기로 묶어 돌릴 때의 회전축 (월드 x, z). NaN = 블록 자체 가운데 */
    public double pivX = Double.NaN, pivZ = Double.NaN;

    /** 회전기: 묶음 회전 (yaw 0 + 축 NaN = 해제) */
    public void setGroupRot(float y, double px, double pz) { pivX = px; pivZ = pz; setYaw(y); }


    public void setYaw(float y) {
        yaw = y;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** 복사로 좌표가 옮겨졌으면 새 좌표가 저장되도록 표시 */
    private boolean moved;

    @Override
    public void onLoad() {
        super.onLoad();
        if (moved && level != null && !level.isClientSide) { moved = false; setChanged(); }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

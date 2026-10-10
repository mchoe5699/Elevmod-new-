package com.mchoe.elevmod.entity;

import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.elevator.Spec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 엘리베이터 카. 위치는 제어반이 계산해 서버에서 갱신하고, 클라이언트는 위치/속도를 받아 예측 보간한다.
 * 클라이언트에서는 바닥판만 충돌체로 동작해 플레이어가 그 위에 서고, 이동량만큼 탑승자를 함께 옮긴다.
 */
public class ElevatorCarEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> CTRL = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> OFFSET = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> VEL = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DIM = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DOOR = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> STATE = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // 클라이언트 예측 상태
    public Vec3 cPrev, cCur;
    public float doorPrev, doorCur;
    private int sinceSync;
    private int checkTimer;

    public ElevatorCarEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CTRL, BlockPos.ZERO);
        entityData.define(OFFSET, new Vector3f());
        entityData.define(VEL, new Vector3f());
        entityData.define(DIM, new Vector3f(1.4f, 2.4f, 1.35f));
        entityData.define(FACING, Direction.SOUTH.get2DDataValue());
        entityData.define(DOOR, 0f);
        entityData.define(STATE, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (OFFSET.equals(key)) sinceSync = 0;
        if (DIM.equals(key) || FACING.equals(key)) setBoundingBox(makeBoundingBox());
    }

    // ------------------------------------------------------------ 접근자
    public BlockPos controller() { return entityData.get(CTRL); }
    public void setController(BlockPos p) { entityData.set(CTRL, p); }
    public float carW() { return entityData.get(DIM).x; }
    public float carH() { return entityData.get(DIM).y; }
    public float carD() { return entityData.get(DIM).z; }
    public Direction facing() { return Direction.from2DDataValue(entityData.get(FACING)); }
    public float doorTarget() { return entityData.get(DOOR); }
    public CompoundTag state() { return entityData.get(STATE); }

    public Spec.Usage usage() {
        int i = state().getInt("usage");
        Spec.Usage[] v = Spec.Usage.values();
        return v[Math.max(0, Math.min(i, v.length - 1))];
    }

    public boolean isDumbwaiter() { return usage() == Spec.Usage.DUMBWAITER; }

    public double halfX() { return facing().getAxis() == Direction.Axis.Z ? carW() / 2.0 : carD() / 2.0; }
    public double halfZ() { return facing().getAxis() == Direction.Axis.Z ? carD() / 2.0 : carW() / 2.0; }

    /** 바닥판 충돌 박스 (덤웨이터는 사람이 탈 수 없도록 전체가 충돌체) */
    @Override
    public AABB makeBoundingBox() {
        Vec3 p = position();
        double hx = halfX(), hz = halfZ();
        double top = isDumbwaiter() ? carH() : 0;
        return new AABB(p.x - hx, p.y - 0.15, p.z - hz, p.x + hx, p.y + top, p.z + hz);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 p = position();
        double hx = halfX() + 1.5, hz = halfZ() + 1.5;
        return new AABB(p.x - hx, p.y - 64, p.z - hz, p.x + hx, p.y + 64, p.z + hz);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    /** 위치 p 기준으로 엔티티가 카 안에 있는지 */
    public boolean contains(Entity e, Vec3 p) {
        double dx = Math.abs(e.getX() - p.x), dz = Math.abs(e.getZ() - p.z);
        double dy = e.getY() - p.y;
        return dx <= halfX() + 0.2 && dz <= halfZ() + 0.2 && dy >= -0.45 && dy <= carH();
    }

    // ------------------------------------------------------------ 동작
    @Override
    public boolean canBeCollidedWith() { return level().isClientSide; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void push(Entity e) {}

    @Override
    public boolean hurt(DamageSource src, float amount) { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // 위치는 clientAdvance 에서 직접 관리
    }

    @Override
    public void tick() {
        if (level().isClientSide) return;
        if (++checkTimer >= 40) {
            checkTimer = 0;
            if (!level().isLoaded(controller()) || !(level().getBlockEntity(controller()) instanceof ControllerBlockEntity c) || !c.carIds.contains(getUUID())) discard();
        }
    }

    /** 제어반에서 매 틱 호출 – 위치 갱신과 탑승자 이동 */
    public void serverUpdate(ControllerBlockEntity c, Vec3 pos, Vec3 vel, float door, CompoundTag st, int cabin) {
        Vec3 old = position();
        Vec3 delta = pos.subtract(old);
        Direction f = c.carFacing == null ? Direction.SOUTH : c.carFacing;
        entityData.set(DIM, new Vector3f(c.cw, c.ch, c.cd));
        entityData.set(FACING, f.get2DDataValue());
        BlockPos cp = c.getBlockPos();
        entityData.set(OFFSET, new Vector3f((float) (pos.x - cp.getX()), (float) (pos.y - cp.getY()), (float) (pos.z - cp.getZ())));
        entityData.set(VEL, new Vector3f((float) vel.x, (float) vel.y, (float) vel.z));
        entityData.set(DOOR, door);
        if (!st.equals(entityData.get(STATE))) entityData.set(STATE, st);

        double hx = halfX(), hz = halfZ();
        AABB box = new AABB(old.x - hx - 0.2, old.y - 0.45, old.z - hz - 0.2, old.x + hx + 0.2, old.y + c.ch, old.z + hz + 0.2);
        List<Entity> list = level().getEntities(this, box, e -> !(e instanceof ElevatorCarEntity) && !e.isPassenger() && !e.isSpectator());
        for (Entity e : list) {
            e.fallDistance = 0;
            if (e instanceof ServerPlayer sp) { markRider(sp); continue; }
            if (e instanceof Player) continue;
            boolean inside = Math.abs(e.getX() - old.x) <= hx && Math.abs(e.getZ() - old.z) <= hz;
            if (!inside) continue;
            double ny = e.getY() + delta.y;
            if (ny < pos.y) ny = pos.y;
            e.setPos(e.getX() + delta.x, ny, e.getZ() + delta.z);
            if (e.getY() <= pos.y + 0.01) {
                Vec3 dm = e.getDeltaMovement();
                if (dm.y < 0) e.setDeltaMovement(dm.x, 0, dm.z);
                e.setOnGround(true);
            }
            e.hasImpulse = true;
        }
        setPos(pos.x, pos.y, pos.z);
    }

    /** 승강로 안에 떠 있는 플레이어가 '비행' 으로 킥 당하지 않도록 서버측 표시 */
    private void markRider(ServerPlayer sp) {
        CompoundTag pd = sp.getPersistentData();
        pd.putLong("elevmod_ride", level().getGameTime());
        if (!sp.getAbilities().mayfly) {
            sp.getAbilities().mayfly = true;
            pd.putBoolean("elevmod_mf", true);
        }
    }

    /** 클라이언트: 매 틱 시작에 예측 이동 */
    public void clientAdvance() {
        BlockPos c = controller();
        Vector3f off = entityData.get(OFFSET);
        Vector3f vel = entityData.get(VEL);
        Vec3 v = new Vec3(vel.x, vel.y, vel.z);
        Vec3 target = new Vec3(c.getX() + off.x, c.getY() + off.y, c.getZ() + off.z).add(v.scale(sinceSync));
        sinceSync++;
        if (cCur == null) { cCur = target; cPrev = target; doorCur = doorTarget(); doorPrev = doorCur; }
        cPrev = cCur;
        Vec3 pred = cCur.add(v);
        Vec3 err = target.subtract(pred);
        Vec3 next;
        if (err.lengthSqr() > 9) next = target;
        else if (v.lengthSqr() == 0 && err.lengthSqr() < 0.00001) next = target;
        else next = pred.add(err.scale(0.4));
        cCur = next;
        setPos(next.x, next.y, next.z);
        doorPrev = doorCur;
        float dt = doorTarget();
        float step = 0.08f;
        doorCur = Math.abs(dt - doorCur) < step ? dt : doorCur + Math.signum(dt - doorCur) * step;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }
}

package com.mchoe.elevmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * 카의 벽·천장·문 같은 충돌 조각. 클라이언트에서만 충돌체로 동작해 플레이어가 카 벽을 통과하지 못하게 한다.
 * (바닥은 원래 카 본체가 맡던 역할도 이 조각이 맡는다)
 */
public class CarPart extends PartEntity<ElevatorCarEntity> {
    boolean active;

    public CarPart(ElevatorCarEntity parent) {
        super(parent);
        this.noPhysics = true;
        this.refreshDimensions();
    }

    void set(AABB box) {
        active = true;
        setPosRaw((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
        setBoundingBox(box);
    }

    void off() {
        active = false;
        Entity p = getParent();
        setPosRaw(p.getX(), p.getY() - 512, p.getZ());
        setBoundingBox(new AABB(p.getX(), p.getY() - 512, p.getZ(), p.getX(), p.getY() - 512, p.getZ()));
    }

    @Override
    public boolean canBeCollidedWith() { return active && level().isClientSide; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void push(Entity e) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.fixed(0.01f, 0.01f); }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}
}

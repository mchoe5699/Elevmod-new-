package com.mchoe.elevmod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 벽에 붙는 장치용 모양. 좌표는 '앞면이 북쪽(-Z)을 향하고 뒤가 z=16 에 붙은' 기준으로 준다. */
public final class Shapes2 {
    private Shapes2() {}

    public static VoxelShape wall(Direction facing, double x1, double y1, double x2, double y2, double depth) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 0, 16 - x1, y2, depth);
            case EAST -> Block.box(0, y1, x1, depth, y2, x2);
            case WEST -> Block.box(16 - depth, y1, 16 - x2, 16, y2, 16 - x1);
            default -> Block.box(x1, y1, 16 - depth, x2, y2, 16);
        };
    }
}

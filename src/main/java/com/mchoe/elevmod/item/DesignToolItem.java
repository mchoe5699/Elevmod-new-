package com.mchoe.elevmod.item;

import com.mchoe.elevmod.block.DeviceBlock;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 디자인 변경기(의장 샘플북).
 * <ul>
 *   <li>승강장 장치(호출버튼·층표시기·홀랜턴 등) 우클릭 → 그 장치의 의장 변경</li>
 *   <li>승강장 문 우클릭 → 그 층 승강장 문 마감 변경, 웅크리고 우클릭 → 쟘(문틀) 변경</li>
 *   <li>카 안에서 벽/바닥/천장/운전반/장애인 운전반/표시기/카 도어를 보고 우클릭 → 해당 부위 의장 변경 (클라이언트에서 처리)</li>
 * </ul>
 * 웅크리면 장치/카 부위는 역순으로 바뀐다.
 */
public class DesignToolItem extends Item {
    public DesignToolItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof DeviceBlockEntity) && !(be instanceof LandingDoorBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        int d = p.isShiftKeyDown() ? -1 : 1;
        Component msg = null;
        if (be instanceof DeviceBlockEntity dev && dev.getBlockState().getBlock() instanceof DeviceBlock db) {
            int n = DeviceBlockEntity.designCount(db.kind);
            dev.setDesign(Math.floorMod(dev.design + d, n));
            msg = Component.translatable("elevmod.msg.design", Component.translatable(dev.getBlockState().getBlock().getDescriptionId()),
                    Component.translatable(DeviceBlockEntity.designKey(db.kind, dev.design)));
        } else if (be instanceof LandingDoorBlockEntity door) {
            LandingDoorBlockEntity m = door.master ? door : level.getBlockEntity(door.masterPos()) instanceof LandingDoorBlockEntity mm ? mm : door;
            if (p.isShiftKeyDown()) {
                m.cycleJambOverride();
                msg = Component.translatable("elevmod.msg.design", Component.translatable("elevmod.part.jamb"),
                        m.jambOverride < 0 ? Component.translatable("elevmod.design.follow") : Component.translatable(com.mchoe.elevmod.elevator.Spec.Jamb.values()[m.jambOverride].key()));
            } else {
                m.cycleFinish();
                msg = Component.translatable("elevmod.msg.design", Component.translatable("elevmod.part.landing_door"),
                        m.finish < 0 ? Component.translatable("elevmod.design.follow") : Component.translatable(com.mchoe.elevmod.elevator.Spec.Theme.values()[m.finish].key()));
            }
        }
        if (msg != null) {
            p.displayClientMessage(msg.copy().withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, ctx.getClickedPos(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.elevmod.design_tool.tip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.elevmod.design_tool.tip2").withStyle(ChatFormatting.GRAY));
    }
}

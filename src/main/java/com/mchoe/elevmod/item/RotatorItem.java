package com.mchoe.elevmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 회전기: 첫 번째 지점(블록 우클릭)과 두 번째 지점(웅크리고 블록 우클릭)으로 영역을 잡고,
 * 허공에 우클릭해 그 안의 엘리베이터 부품을 하나로 묶어 0~360° 돌린다 (영역 가운데가 회전축, 배치는 그대로 고정).
 * 허공에 웅크리고 우클릭 = 선택 해제.
 */
public class RotatorItem extends Item {
    public RotatorItem(Properties p) { super(p); }

    @Nullable
    public static BlockPos point(ItemStack st, int i) {
        CompoundTag t = st.getTag();
        String k = i == 1 ? "p1" : "p2";
        return t != null && t.contains(k) ? BlockPos.of(t.getLong(k)) : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide) {
            int i = p.isShiftKeyDown() ? 2 : 1;
            BlockPos pos = ctx.getClickedPos();
            ctx.getItemInHand().getOrCreateTag().putLong(i == 1 ? "p1" : "p2", pos.asLong());
            p.displayClientMessage(Component.translatable("elevmod.rotator.point", i, pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.isShiftKeyDown()) {
            if (!level.isClientSide && st.getTag() != null) {
                st.getTag().remove("p1");
                st.getTag().remove("p2");
                p.displayClientMessage(Component.translatable("elevmod.rotator.cleared").withStyle(ChatFormatting.GRAY), true);
            }
        } else if (level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.mchoe.elevmod.client.ClientHooks.openRotator(hand));
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack st) { return point(st, 1) != null && point(st, 2) != null; }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.elevmod.rotator.tip").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.elevmod.rotator.tip2").withStyle(ChatFormatting.GRAY));
        BlockPos a = point(st, 1), b = point(st, 2);
        if (a != null) tip.add(Component.literal("① " + a.toShortString()).withStyle(ChatFormatting.AQUA));
        if (b != null) tip.add(Component.literal("② " + b.toShortString()).withStyle(ChatFormatting.AQUA));
    }
}

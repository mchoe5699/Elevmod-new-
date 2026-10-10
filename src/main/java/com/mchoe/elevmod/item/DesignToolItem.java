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
        if (positionMode(ctx.getItemInHand())) {
            // 위치 조정 모드: 장치를 우클릭하면 위치 조정 화면
            if (be instanceof DeviceBlockEntity && level.isClientSide)
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.mchoe.elevmod.client.ClientHooks.openOffset(ctx.getClickedPos()));
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        int d = p.isShiftKeyDown() ? -1 : 1;
        Component msg = null;
        if (be instanceof DeviceBlockEntity dev && dev.getBlockState().getBlock() instanceof DeviceBlock db) {
            // 순서: 제어반 따름 → 기본 의장 0..n-1 → 이 부위를 정의한 커스텀 디자인들
            String part = switch (db.kind) {
                case HALL_BUTTON, CALL_RECEIVER, FIRE_SWITCH -> "hallButton";
                case HALL_PANEL, DESTINATION -> "hallPanel";
                case LANTERN -> "lantern";
                default -> "indicator";
            };
            List<String> states = new java.util.ArrayList<>();
            states.add("");
            int n = DeviceBlockEntity.designCount(db.kind);
            for (int k = 0; k < n; k++) states.add("-" + k);
            states.addAll(com.mchoe.elevmod.elevator.CustomDesignIndex.ids(part));
            String cur = dev.customSel.isEmpty() ? "" : dev.customSel.equals("-") ? "-" + dev.design : dev.customSel;
            int i = states.indexOf(cur);
            String nx = states.get(Math.floorMod(i + d, states.size()));
            Component what;
            if (nx.isEmpty()) { dev.setCustomSel("", dev.design); what = Component.translatable("elevmod.design.follow"); }
            else if (nx.startsWith("-")) {
                int k = Integer.parseInt(nx.substring(1));
                dev.setCustomSel("-", k);
                what = Component.translatable(DeviceBlockEntity.designKey(db.kind, k));
            } else { dev.setCustomSel(nx, dev.design); what = Component.literal(com.mchoe.elevmod.elevator.CustomDesignIndex.name(nx)); }
            msg = Component.translatable("elevmod.msg.design", Component.translatable(dev.getBlockState().getBlock().getDescriptionId()), what);
        } else if (be instanceof LandingDoorBlockEntity door) {
            LandingDoorBlockEntity m = door.master ? door : level.getBlockEntity(door.masterPos()) instanceof LandingDoorBlockEntity mm ? mm : door;
            if (p.isShiftKeyDown()) {
                m.cycleJambOverride();
                msg = Component.translatable("elevmod.msg.design", Component.translatable("elevmod.part.jamb"),
                        m.jambOverride < 0 ? Component.translatable("elevmod.design.follow") : Component.translatable(com.mchoe.elevmod.elevator.Spec.Jamb.values()[m.jambOverride].key()));
            } else {
                // 순서: 제어반 따름 → 기본 마감(테마) → 승강장 문 커스텀 디자인들
                List<String> states = new java.util.ArrayList<>();
                states.add("");
                for (int k = 0; k < com.mchoe.elevmod.elevator.Spec.Theme.values().length; k++) states.add("-" + k);
                states.addAll(com.mchoe.elevmod.elevator.CustomDesignIndex.ids("landing"));
                String cur = m.customSel.isEmpty() ? "" : m.customSel.equals("-") ? "-" + Math.max(0, m.finish) : m.customSel;
                int i = states.indexOf(cur);
                String nx = states.get(Math.floorMod(i + 1, states.size()));
                Component what;
                if (nx.isEmpty()) { m.setSel("", -1); what = Component.translatable("elevmod.design.follow"); }
                else if (nx.startsWith("-")) {
                    int k = Integer.parseInt(nx.substring(1));
                    m.setSel("-", k);
                    what = Component.translatable(com.mchoe.elevmod.elevator.Spec.Theme.values()[k].key());
                } else { m.setSel(nx, -1); what = Component.literal(com.mchoe.elevmod.elevator.CustomDesignIndex.name(nx)); }
                msg = Component.translatable("elevmod.msg.design", Component.translatable("elevmod.part.landing_door"), what);
            }
        }
        if (msg != null) {
            p.displayClientMessage(msg.copy().withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, ctx.getClickedPos(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean positionMode(ItemStack st) { return st.getTag() != null && st.getTag().getBoolean("pos"); }

    /** 허공에 웅크리고 우클릭: 디자인 모드 ↔ 위치 조정 모드 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player p, net.minecraft.world.InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!p.isShiftKeyDown()) {
            // 허공에 우클릭: 디자인 스튜디오 (사진으로 커스텀 디자인 만들기)
            if (level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.mchoe.elevmod.client.ClientHooks.openStudio());
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        if (!level.isClientSide) {
            boolean pos = !positionMode(st);
            st.getOrCreateTag().putBoolean("pos", pos);
            p.displayClientMessage(Component.translatable(pos ? "elevmod.design_tool.mode_pos" : "elevmod.design_tool.mode_design").withStyle(ChatFormatting.AQUA), true);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack st) { return positionMode(st); }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(positionMode(st) ? "elevmod.design_tool.mode_pos" : "elevmod.design_tool.mode_design").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.elevmod.design_tool.tip3").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.elevmod.design_tool.tip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.elevmod.design_tool.tip2").withStyle(ChatFormatting.GRAY));
    }
}

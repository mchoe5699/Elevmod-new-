package com.mchoe.elevmod.item;

import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** 링커 / 삼각키(도어키) / 소방 키 / 운전원 키 / 승강기 점검기 */
public class ElevItem extends Item {
    public enum Type { LINKER, DOOR_KEY, FIRE_KEY, OPERATOR_KEY, TESTER }

    public final Type type;

    public ElevItem(Properties p, Type type) {
        super(p);
        this.type = type;
    }

    @Nullable
    public static BlockPos stored(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.contains("ctrl") ? BlockPos.of(t.getLong("ctrl")) : null;
    }

    @Override
    public boolean isFoil(ItemStack st) { return type == Type.LINKER && stored(st) != null; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        if (level.isClientSide) return type == Type.FIRE_KEY || type == Type.OPERATOR_KEY ? InteractionResult.PASS : InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack st = ctx.getItemInHand();
        switch (type) {
            case LINKER -> {
                BlockPos sp = stored(st);
                if (be instanceof ControllerBlockEntity c) {
                    if (p.isShiftKeyDown() && sp != null && !sp.equals(pos) && level.getBlockEntity(sp) instanceof ControllerBlockEntity other) {
                        other.linkGroup(sl, c, p);
                    } else {
                        st.getOrCreateTag().putLong("ctrl", pos.asLong());
                        p.displayClientMessage(Component.translatable("elevmod.msg.ctrl_selected", c.serial).withStyle(ChatFormatting.AQUA), true);
                    }
                    return InteractionResult.SUCCESS;
                }
                if (be == null) return InteractionResult.PASS;
                if (sp == null || !(level.getBlockEntity(sp) instanceof ControllerBlockEntity c)) {
                    p.displayClientMessage(Component.translatable("elevmod.msg.select_ctrl_first").withStyle(ChatFormatting.RED), true);
                    return InteractionResult.SUCCESS;
                }
                if (be instanceof LandingDoorBlockEntity) {
                    if (p.isShiftKeyDown()) { c.unlinkDoor(sl, pos); p.displayClientMessage(Component.translatable("elevmod.msg.unlinked"), true); }
                    else c.linkDoor(sl, pos, p);
                } else if (be instanceof DeviceBlockEntity d) {
                    if (p.isShiftKeyDown()) { c.devices.remove(pos); c.setChanged(); d.unlink(); p.displayClientMessage(Component.translatable("elevmod.msg.unlinked"), true); }
                    else c.linkDevice(d, p);
                } else if (be instanceof MachineBlockEntity m) {
                    c.linkMachine(m, p);
                } else return InteractionResult.PASS;
                return InteractionResult.SUCCESS;
            }
            case DOOR_KEY -> {
                if (be instanceof LandingDoorBlockEntity d) { d.manualOpen(p); return InteractionResult.SUCCESS; }
            }
            case TESTER -> {
                if (be instanceof ControllerBlockEntity c) {
                    if (p.isShiftKeyDown() && p instanceof ServerPlayer spl) {
                        c.edit(spl, "inspect", 0, "");
                        p.displayClientMessage(Component.translatable("elevmod.msg.inspected", c.serial).withStyle(ChatFormatting.GREEN), false);
                    } else diagnostics(c, p, sl);
                    return InteractionResult.SUCCESS;
                }
            }
            default -> {}
        }
        return InteractionResult.PASS;
    }

    private static void diagnostics(ControllerBlockEntity c, Player p, ServerLevel sl) {
        p.sendSystemMessage(Component.translatable("elevmod.diag.title", c.serial, String.valueOf(c.letter())).withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.translatable("elevmod.diag.spec", Component.translatable(c.usage.key()), Component.translatable(c.drive.key()),
                c.ratedKg(), com.mchoe.elevmod.elevator.Spec.persons(c.ratedKg()), com.mchoe.elevmod.elevator.Spec.personsOld(c.ratedKg()), c.effectiveMpm()));
        p.sendSystemMessage(Component.translatable("elevmod.diag.state", Component.translatable(c.mode.key()), Component.translatable(c.fault.key()),
                c.state.name(), c.loadKg, c.floors.size()));
        p.sendSystemMessage(Component.translatable("elevmod.diag.stats", c.trips, String.format("%.1f", c.travelled)));
        long day = sl.getDayTime() / 24000L;
        if (c.lastInspect < 0) p.sendSystemMessage(Component.translatable("elevmod.diag.never_inspected").withStyle(ChatFormatting.RED));
        else {
            long since = day - c.lastInspect;
            p.sendSystemMessage(Component.translatable("elevmod.diag.inspected", since).withStyle(since > 30 ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        for (String l : c.faultLog) {
            String[] parts = l.split("\\|", 2);
            if (parts.length == 2) p.sendSystemMessage(Component.translatable("elevmod.diag.log", parts[0], Component.translatable(parts[1])).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (type == Type.LINKER && p.isShiftKeyDown() && stored(st) != null) {
            if (!level.isClientSide) {
                st.removeTagKey("ctrl");
                p.displayClientMessage(Component.translatable("elevmod.msg.ctrl_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        return InteractionResultHolder.pass(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        String k = "item.elevmod." + type.name().toLowerCase(Locale.ROOT) + ".tip";
        lines.add(Component.translatable(k).withStyle(ChatFormatting.GRAY));
        BlockPos sp = stored(st);
        if (sp != null) lines.add(Component.translatable("elevmod.tip.selected", sp.getX(), sp.getY(), sp.getZ()).withStyle(ChatFormatting.AQUA));
    }
}

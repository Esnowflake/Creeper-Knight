package dev.creeperknight.scepter;

import dev.creeperknight.KnightSettings;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class FriendshipScepter extends Item {
    private static final String CAST = "CreeperKnightCastUsed";
    public FriendshipScepter() { super(new Properties().stacksTo(1).rarity(Rarity.RARE)); }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (!KnightSettings.get().wandEnabled) { ScepterLogic.message((ServerPlayer)player, "disabled"); return InteractionResultHolder.fail(stack); }
            stack.getOrCreateTag().putBoolean(CAST, false);
        }
        player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
    }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer player && getUseDuration(stack) - remaining >= 20 && !stack.getOrCreateTag().getBoolean(CAST)) {
            stack.getOrCreateTag().putBoolean(CAST, true); ScepterLogic.summon(player, stack);
        }
    }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player && getUseDuration(stack) - remaining < 20) {
            ServerPlayer target = ScepterLogic.aimedPlayer(player);
            if (target == null || !ScepterLogic.lock(player, target, stack)) ScepterLogic.message(player, "aim");
        }
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("creeperknight.wand.tooltip").withStyle(ChatFormatting.GRAY));
        if (stack.hasTag() && stack.getTag().hasUUID(ScepterLogic.TARGET)) lines.add(Component.translatable("creeperknight.wand.target",
            stack.getTag().getString(ScepterLogic.TARGET_NAME)).withStyle(ChatFormatting.AQUA));
    }
}

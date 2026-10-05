package dev.creeperknight.scepter;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.mixin.CreeperAccess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ScepterLogic {
    public static final String TARGET = "CreeperKnightTarget";
    public static final String TARGET_NAME = "CreeperKnightTargetName";
    public static final EntityType<?>[] RIDERS = {EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
        EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN};
    private ScepterLogic() {}
    public static SummonedKnight data(Creeper creeper) { return ((SummonedKnight.Access)creeper).creeperknight$getSummon(); }
    public static void mark(Creeper creeper, UUID target, long deadline) {
        ((SummonedKnight.Access)creeper).creeperknight$setSummon(new SummonedKnight(target, deadline));
    }
    public static ServerPlayer lockedPlayer(ServerPlayer player, ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(TARGET) ? player.server.getPlayerList().getPlayer(stack.getTag().getUUID(TARGET)) : null;
    }
    public static ServerPlayer aimedPlayer(ServerPlayer player) {
        double range = KnightSettings.get().wandLockRange;
        Vec3 from = player.getEyePosition(), to = from.add(player.getLookAngle().scale(range));
        var block = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double distance = block.getType() == HitResult.Type.MISS ? range * range : from.distanceToSqr(block.getLocation());
        var hit = ProjectileUtil.getEntityHitResult(player, from, to,
            player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1),
            entity -> entity instanceof ServerPlayer other && other != player && KnightLogic.targetAllowed(other), distance);
        return hit != null && hit.getEntity() instanceof ServerPlayer other ? other : null;
    }
    public static boolean lock(ServerPlayer user, ServerPlayer target, ItemStack stack) {
        if (!KnightSettings.get().wandEnabled || target == user || target.level() != user.level() || !KnightLogic.targetAllowed(target)) return false;
        stack.getOrCreateTag().putUUID(TARGET, target.getUUID());
        stack.getOrCreateTag().putString(TARGET_NAME, target.getGameProfile().getName());
        int glow = KnightSettings.get().wandGlowSeconds;
        if (glow > 0) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, glow * 20, 0, false, false, true));
        int notices = KnightSettings.get().wandNotifications;
        if (notices != 2) {
            for (ServerPlayer observer : user.server.getPlayerList().getPlayers()) {
                if (observer != user && observer != target) observer.sendSystemMessage(Component.translatable("creeperknight.wand.public",
                    target.getGameProfile().getName(), user.getGameProfile().getName()));
            }
            if (notices == 0) target.sendSystemMessage(Component.translatable("creeperknight.wand.warning", user.getGameProfile().getName()));
            user.sendSystemMessage(Component.translatable("creeperknight.wand.locked", target.getGameProfile().getName()));
        }
        user.getInventory().setChanged(); return true;
    }
    /** Null during death, logout, creative/spectator mode, or a different dimension. UUID lookup resumes after respawn. */
    public static ServerPlayer target(Creeper creeper) {
        SummonedKnight data = data(creeper);
        if (data == null || !(creeper.level() instanceof ServerLevel level)) return null;
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(data.target());
        return target != null && target.level() == level && KnightLogic.targetAllowed(target) ? target : null;
    }
    public static boolean tickDeadline(Creeper creeper) {
        SummonedKnight data = data(creeper);
        if (data == null || !creeper.isAlive()) return false;
        if (creeper.level() instanceof ServerLevel level && level.getServer().overworld().getGameTime() >= data.expiresAt()) {
            ((CreeperAccess)creeper).creeperknight$explode(); return true;
        }
        return false;
    }
    public static void pursue(Creeper creeper, Mob rider, ServerPlayer target) {
        rider.setTarget(target);
        if (target == null) { creeper.getNavigation().stop(); return; }
        int interval = creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.5 ? 2 : 10;
        if (creeper.getNavigation().isDone() || creeper.tickCount % interval == 0) creeper.getNavigation().moveTo(target, 1);
        creeper.getLookControl().setLookAt(target, 30, 30);
    }
    public static Creeper create(ServerLevel level, Vec3 position, UUID target) {
        Zombie rider = (Zombie)RIDERS[KnightSettings.get().wandRiderType].create(level);
        Creeper creeper = EntityType.CREEPER.create(level);
        if (rider == null || creeper == null) return null;
        rider.setBaby(true);
        if (!KnightLogic.eligible(rider)) return null;
        creeper.moveTo(position.x, position.y, position.z, 0, 0); rider.moveTo(position.x, position.y, position.z, 0, 0);
        creeper.setPersistenceRequired(); rider.setPersistenceRequired();
        if (!level.getWorldBorder().isWithinBounds(creeper.getBoundingBox()) || !level.noCollision(creeper)
            || !level.noCollision(rider) || !level.getBlockState(BlockPos.containing(position)).getFluidState().isEmpty()
            || !rider.startRiding(creeper)) return null;
        mark(creeper, target, level.getServer().overworld().getGameTime() + KnightSettings.get().wandLifetimeSeconds * 20L);
        if (!level.addFreshEntity(creeper) || !level.addFreshEntity(rider)) { creeper.discard(); rider.discard(); return null; }
        ScepterTickets.track(creeper);
        return creeper;
    }
    public static boolean summon(ServerPlayer user, ItemStack stack) {
        var config = KnightSettings.get();
        if (!config.wandEnabled || !config.attackPlayers) { message(user, "disabled"); return false; }
        if (user.getCooldowns().isOnCooldown(stack.getItem())) { message(user, "cooldown"); return false; }
        if (!stack.hasTag() || !stack.getTag().hasUUID(TARGET)) { message(user, "nolock"); return false; }
        ItemStack gunpowder = ItemStack.EMPTY;
        if (!user.isCreative() && config.wandConsumeGunpowder) {
            for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
                ItemStack candidate = user.getInventory().getItem(i);
                if (candidate.is(Items.GUNPOWDER)) { gunpowder = candidate; break; }
            }
            if (gunpowder.isEmpty()) { message(user, "gunpowder"); return false; }
        }
        Vec3 direction = new Vec3(user.getLookAngle().x, 0, user.getLookAngle().z);
        if (direction.lengthSqr() < 0.01) direction = Vec3.directionFromRotation(0, user.getYRot());
        Vec3 base = user.position().add(direction.normalize().scale(3));
        Creeper creeper = null;
        for (int offset : new int[]{0, 1, -1, 2, -2}) {
            Vec3 candidate = new Vec3(Math.floor(base.x) + 0.5, Math.floor(base.y) + offset, Math.floor(base.z) + 0.5);
            BlockPos below = BlockPos.containing(candidate).below();
            if (user.serverLevel().getBlockState(below).isSolidRender(user.serverLevel(), below)) {
                creeper = create(user.serverLevel(), candidate, stack.getTag().getUUID(TARGET));
                if (creeper != null) break;
            }
        }
        if (creeper == null) { message(user, "space"); return false; }
        if (!gunpowder.isEmpty()) gunpowder.shrink(1);
        user.getInventory().setChanged();
        if (config.wandCooldownSeconds > 0) user.getCooldowns().addCooldown(stack.getItem(), config.wandCooldownSeconds * 20);
        message(user, "summoned");
        KnightLogic.tickCreeper(creeper); return true;
    }
    public static void message(ServerPlayer user, String key) { user.displayClientMessage(Component.translatable("creeperknight.wand." + key), true); }
}

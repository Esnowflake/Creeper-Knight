package dev.creeperknight;

import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.mixin.CreeperAccess;
import dev.creeperknight.scepter.ScepterLogic;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;

public final class KnightLogic {
    private static final UUID SPEED_ID = UUID.fromString("36a5af70-783d-4b8a-88f1-2196fcd8a804");
    private KnightLogic() {}

    public static boolean eligible(Mob mob) {
        if (!(mob instanceof Zombie) || !mob.isBaby() || !mob.isAlive()) return false;
        KnightConfig config = KnightSettings.get();
        var type = mob.getType();
        if (type == EntityType.ZOMBIE) return config.zombie;
        if (type == EntityType.HUSK) return config.husk;
        if (type == EntityType.DROWNED) return config.drowned;
        if (type == EntityType.ZOMBIE_VILLAGER) return config.zombieVillager;
        if (type == EntityType.ZOMBIFIED_PIGLIN) return config.zombifiedPiglin;
        return false;
    }
    public static Mob rider(Creeper creeper) {
        return creeper.getFirstPassenger() instanceof Mob mob && eligible(mob) ? mob : null;
    }
    public static boolean isKnight(Mob mob) { return mob instanceof Creeper creeper && rider(creeper) != null; }
    public static boolean suppressGoals(Mob mob) {
        return isKnight(mob) || mob instanceof Creeper mount && PlayerRiding.rider(mount) != null
            || mob.getVehicle() instanceof Creeper creeper && ScepterLogic.data(creeper) != null && rider(creeper) == mob;
    }
    public static boolean targetAllowed(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        var config = KnightSettings.get();
        if (target instanceof Player player) return config.attackPlayers && !player.isCreative() && !player.isSpectator();
        if (target instanceof AbstractVillager) return config.attackVillagers;
        if (target instanceof IronGolem) return config.attackIronGolems;
        return config.attackOtherTargets;
    }
    public static boolean shouldRejectTarget(Mob mob, LivingEntity target) {
        return target != null && mob.getVehicle() instanceof Creeper creeper && rider(creeper) == mob
            && (!targetAllowed(target) || ScepterLogic.data(creeper) != null && !target.getUUID().equals(ScepterLogic.data(creeper).target()));
    }

    /** Called before vanilla advances the fuse. Never overwrites manual ignition. */
    public static void tickCreeper(Creeper creeper) {
        if (creeper.level().isClientSide || !creeper.isAlive()) return;
        if (PlayerRiding.tick(creeper)) { updateSpeed(creeper, false); return; }
        dev.creeperknight.scepter.ScepterTickets.track(creeper);
        if (ScepterLogic.tickDeadline(creeper)) return;
        if (creeper.getFirstPassenger() instanceof Zombie passenger && (!passenger.isAlive()
            || (passenger.isBaby() && !eligible(passenger)))) passenger.stopRiding();
        Mob rider = rider(creeper);
        updateSpeed(creeper, rider != null);
        if (rider == null) return;
        var config = KnightSettings.get();
        boolean summoned = ScepterLogic.data(creeper) != null;
        LivingEntity target = summoned ? ScepterLogic.target(creeper) : rider.getTarget();
        if (summoned) ScepterLogic.pursue(creeper, rider, (net.minecraft.server.level.ServerPlayer)target);
        if (!targetAllowed(target)) {
            if (target != null) rider.setTarget(null);
            target = null;
        }
        // Partial takeover retains proximity ignition, but never a separate pursuit AI.
        if (target == null && !summoned && !config.fullAiTakeover && config.attackPlayers)
            target = creeper.level().getNearestPlayer(creeper.getX(), creeper.getY(), creeper.getZ(),
                config.instantExplosion ? config.instantExplosionDistance + 0.001 : (creeper.getSwellDir() > 0 ? 7.0 : 3.0),
                entity -> entity instanceof LivingEntity living && targetAllowed(living));
        if (!targetAllowed(target)) target = null;
        creeper.setTarget(target);
        if (creeper.isIgnited()) return;
        if (config.instantExplosion) {
            // Switching modes also cancels any automatic fuse already in progress.
            creeper.setSwellDir(-1);
            ((CreeperAccess)creeper).creeperknight$setSwell(0);
            if (target != null && creeper.distanceToSqr(target) <= config.instantExplosionDistance * config.instantExplosionDistance
                && creeper.hasLineOfSight(target)) ((CreeperAccess)creeper).creeperknight$explode();
            return;
        }
        boolean continueFuse = creeper.getSwellDir() > 0;
        boolean inRange = target != null && (continueFuse ? creeper.distanceToSqr(target) <= 49.0 : creeper.distanceToSqr(target) < 9.0)
            && creeper.hasLineOfSight(target);
        creeper.setSwellDir(inRange ? 1 : -1);
        if (inRange && !config.chaseDuringFuse) creeper.getNavigation().stop();
    }
    private static void updateSpeed(Creeper creeper, boolean mounted) {
        var attribute = creeper.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        double amount = KnightSettings.get().speedMultiplier * (ScepterLogic.data(creeper) != null ? KnightSettings.get().wandSpeedMultiplier : 1) - 1.0;
        var old = attribute.getModifier(SPEED_ID);
        if (old != null && (!mounted || old.getAmount() != amount)) attribute.removeModifier(SPEED_ID);
        if (mounted && attribute.getModifier(SPEED_ID) == null)
            attribute.addTransientModifier(new AttributeModifier(SPEED_ID, "Creeper knight speed", amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static void naturalSpawn(Zombie zombie, ServerLevelAccessor level, MobSpawnType reason) {
        var config = KnightSettings.get();
        if (reason != MobSpawnType.NATURAL || !config.naturalSpawning || !eligible(zombie)
            || zombie.isPassenger() || zombie.getRandom().nextDouble() >= config.naturalChance) return;
        Creeper creeper = EntityType.CREEPER.create(level.getLevel());
        if (creeper == null) return;
        creeper.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), 0);
        creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.JOCKEY, null, null);
        if (!level.noCollision(creeper)) return;
        if (zombie.startRiding(creeper)) {
            if (!level.addFreshEntity(creeper)) zombie.stopRiding();
        }
    }
    public static Creeper findMount(Mob mob) {
        double range = KnightSettings.get().seekRange;
        return mob.level().getEntitiesOfClass(Creeper.class, mob.getBoundingBox().inflate(range),
                c -> c.isAlive() && !c.isVehicle() && !c.isPassenger() && !c.isIgnited() && !c.isNoAi()
                    && c.getSwellDir() <= 0 && mob.distanceToSqr(c) <= range * range && mob.hasLineOfSight(c))
            .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
    }
    public static void removeRiderOnExplosion(Creeper creeper) {
        if (creeper.level() instanceof ServerLevel && rider(creeper) != null) rider(creeper).discard();
    }
}

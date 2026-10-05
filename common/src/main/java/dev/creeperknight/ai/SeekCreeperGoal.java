package dev.creeperknight.ai;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;

/** Walk to a free mount instead of teleporting across the search radius. */
public final class SeekCreeperGoal extends Goal {
    private final Mob mob;
    private Creeper mount;
    private int nextAttempt;
    private int deadline;
    public SeekCreeperGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
        var config = KnightSettings.get();
        if (!config.seekMounts || !KnightLogic.eligible(mob) || mob.isPassenger() || mob.getTarget() != null
            || mob.tickCount < nextAttempt) return false;
        nextAttempt = mob.tickCount + config.seekIntervalTicks;
        if (mob.getRandom().nextDouble() >= config.seekChance) return false;
        mount = KnightLogic.findMount(mob);
        return mount != null;
    }
    @Override public void start() { deadline = mob.tickCount + 200; }
    @Override public boolean canContinueToUse() {
        return KnightSettings.get().seekMounts && KnightLogic.eligible(mob) && !mob.isPassenger()
            && mob.getTarget() == null && mount != null && mount.isAlive() && !mount.isVehicle()
            && !mount.isIgnited() && mount.getSwellDir() <= 0 && mob.tickCount < deadline
            && mob.distanceToSqr(mount) <= Math.pow(KnightSettings.get().seekRange, 2);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        if (mob.distanceToSqr(mount) <= Math.pow(KnightSettings.get().mountDistance, 2)) mob.startRiding(mount);
        else if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(mount, 1.0);
    }
    @Override public void stop() { mount = null; mob.getNavigation().stop(); }
}

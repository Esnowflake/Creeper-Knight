package dev.creeperknight.mixin;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.ai.SeekCreeperGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {
    @Unique private boolean creeperknight$mounted;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void creeperknight$addSeekGoal(CallbackInfo ci) {
        if ((Object)this instanceof Zombie) ((MobAccess)(Object)this).creeperknight$getGoals()
            .addGoal(3, new SeekCreeperGoal((Mob)(Object)this));
    }
    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void creeperknight$mountTransition(CallbackInfo ci) {
        boolean mounted = KnightLogic.suppressGoals((Mob)(Object)this);
        if (mounted && !creeperknight$mounted) {
            ((MobAccess)(Object)this).creeperknight$getGoals().getAvailableGoals().forEach(g -> { if (g.isRunning()) g.stop(); });
            ((MobAccess)(Object)this).creeperknight$getTargetGoals().getAvailableGoals().forEach(g -> { if (g.isRunning()) g.stop(); });
            ((Mob)(Object)this).getNavigation().stop();
        }
        if (!mounted && creeperknight$mounted) {
            ((Mob)(Object)this).setTarget(null);
            ((MobAccess)(Object)this).creeperknight$updateControlFlags();
        }
        creeperknight$mounted = mounted;
    }
    // Retain navigation and movement-control ticks, which vanilla routes to the rider's vehicle.
    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private void creeperknight$goals(GoalSelector selector) {
        if (!KnightLogic.suppressGoals((Mob)(Object)this)) selector.tick();
    }
    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
    private void creeperknight$runningGoals(GoalSelector selector, boolean everyTick) {
        if (!KnightLogic.suppressGoals((Mob)(Object)this)) selector.tickRunningGoals(everyTick);
    }
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void creeperknight$filterTarget(LivingEntity target, CallbackInfo ci) {
        if (KnightLogic.shouldRejectTarget((Mob)(Object)this, target)) ci.cancel();
    }
}

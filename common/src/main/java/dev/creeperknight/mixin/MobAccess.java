package dev.creeperknight.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public interface MobAccess {
    @Accessor("goalSelector") GoalSelector creeperknight$getGoals();
    @Accessor("targetSelector") GoalSelector creeperknight$getTargetGoals();
    @Accessor("moveControl") void creeperknight$setMoveControl(MoveControl control);
    @Invoker("updateControlFlags") void creeperknight$updateControlFlags();
}

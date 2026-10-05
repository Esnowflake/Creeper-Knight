package dev.creeperknight.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Creeper.class)
public interface CreeperAccess {
    @Accessor("swell") void creeperknight$setSwell(int swell);
    @Invoker("explodeCreeper") void creeperknight$explode();
}

package dev.creeperknight.mixin;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.PlayerRiding;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "die", at = @At("TAIL"))
    private void creeperknight$releaseOnDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity.level().isClientSide || entity.isAlive()) return;
        if (entity instanceof Creeper creeper && (KnightLogic.rider(creeper) != null || PlayerRiding.rider(creeper) != null)) {
            creeper.ejectPassengers(); PlayerRiding.tick(creeper);
        }
        else if (entity.getVehicle() instanceof Creeper creeper) {
            entity.stopRiding();
            KnightLogic.tickCreeper(creeper);
        }
    }
}

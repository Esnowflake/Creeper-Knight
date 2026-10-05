package dev.creeperknight.mixin;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.ai.KnightMoveControl;
import dev.creeperknight.scepter.ScepterLogic;
import dev.creeperknight.scepter.SummonedKnight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperMixin implements SummonedKnight.Access {
    @Unique private SummonedKnight creeperknight$summon;
    @Override public SummonedKnight creeperknight$getSummon() { return creeperknight$summon; }
    @Override public void creeperknight$setSummon(SummonedKnight data) { creeperknight$summon = data; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void creeperknight$movementControl(CallbackInfo ci) {
        ((MobAccess)this).creeperknight$setMoveControl(new KnightMoveControl((Creeper)(Object)this));
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void creeperknight$saveSummon(CompoundTag tag, CallbackInfo ci) {
        if (creeperknight$summon != null) tag.put("CreeperKnightScepter", creeperknight$summon.save());
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void creeperknight$loadSummon(CompoundTag tag, CallbackInfo ci) {
        creeperknight$summon = SummonedKnight.load(tag.getCompound("CreeperKnightScepter"));
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void creeperknight$tick(CallbackInfo ci) {
        Creeper creeper = (Creeper)(Object)this;
        KnightLogic.tickCreeper(creeper);
        if (creeper.isRemoved()) ci.cancel();
    }
    @Inject(method = "explodeCreeper", at = @At("HEAD"))
    private void creeperknight$explode(CallbackInfo ci) { KnightLogic.removeRiderOnExplosion((Creeper)(Object)this); }
    @ModifyArg(method = "explodeCreeper", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"), index = 5)
    private Level.ExplosionInteraction creeperknight$wandBlockDamage(Level.ExplosionInteraction mode) {
        return ScepterLogic.data((Creeper)(Object)this) != null && !KnightSettings.get().wandBlockDamage ? Level.ExplosionInteraction.NONE : mode;
    }
}

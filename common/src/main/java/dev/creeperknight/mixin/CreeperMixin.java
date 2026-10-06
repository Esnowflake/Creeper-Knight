package dev.creeperknight.mixin;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.PlayerRiding;
import dev.creeperknight.ai.KnightMoveControl;
import dev.creeperknight.scepter.ScepterLogic;
import dev.creeperknight.scepter.SummonedKnight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Creeper.class)
public abstract class CreeperMixin extends Monster implements SummonedKnight.Access, PlayerRiding.Access {
    protected CreeperMixin(EntityType<? extends Monster> type, Level level) { super(type, level); }
    @Unique private static final EntityDataAccessor<Float> creeperknight$CHARGE = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> creeperknight$SPEED = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.FLOAT);
    @Unique private final PlayerRiding.State creeperknight$ride = new PlayerRiding.State();
    @Override public PlayerRiding.State creeperknight$rideState() { return creeperknight$ride; }
    @Override public float creeperknight$remaining() { return entityData.get(creeperknight$CHARGE); }
    @Override public void creeperknight$remaining(float value) { entityData.set(creeperknight$CHARGE, value); }
    @Override public float creeperknight$rideSpeed() { return entityData.get(creeperknight$SPEED); }
    @Override public void creeperknight$rideSpeed(float value) { entityData.set(creeperknight$SPEED, value); }
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void creeperknight$rideData(CallbackInfo ci) {
        entityData.define(creeperknight$CHARGE, -1F);
        entityData.define(creeperknight$SPEED, PlayerRiding.BASE_SPEED);
    }
    @Override public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }
    @Override protected Vec3 getRiddenInput(Player player, Vec3 input) {
        return new Vec3(player.xxa * 0.5, 0, player.zza <= 0 ? player.zza * 0.25 : player.zza);
    }
    @Override protected float getRiddenSpeed(Player player) { return creeperknight$rideSpeed(); }
    @Override protected void tickRidden(Player player, Vec3 input) {
        setYRot(player.getYRot()); setXRot(player.getXRot() * 0.5F);
        yBodyRot = getYRot(); yHeadRot = getYRot(); yRotO = getYRot();
        if (((LivingEntityAccess)player).creeperknight$isJumping() && onGround()) jumpFromGround();
    }
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void creeperknight$mount(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Creeper creeper = (Creeper)(Object)this;
        if (getFirstPassenger() instanceof Player && player.getItemInHand(hand).is(ItemTags.CREEPER_IGNITERS)
            && !KnightSettings.get().playerAllowIgnition) { cir.setReturnValue(InteractionResult.FAIL); return; }
        if (player.getItemInHand(hand).is(ItemTags.CREEPER_IGNITERS)) return;
        if (!KnightSettings.get().playerRidingEnabled || !isAlive() || creeper.isIgnited() || isVehicle() || isPassenger()
            || !player.isAlive() || player.isPassenger() || player.isSpectator() || player.isSecondaryUseActive()) return;
        if (level().isClientSide) cir.setReturnValue(InteractionResult.SUCCESS);
        else if (player.startRiding(creeper)) { PlayerRiding.beginRide(creeper); cir.setReturnValue(InteractionResult.CONSUME); }
    }
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
        if (creeper.level().isClientSide && PlayerRiding.rider(creeper) != null) PlayerRiding.resetFuse(creeper);
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

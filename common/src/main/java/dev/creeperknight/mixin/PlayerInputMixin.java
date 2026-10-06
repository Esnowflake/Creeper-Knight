package dev.creeperknight.mixin;

import dev.creeperknight.client.PlayerRideClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class PlayerInputMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void creeperknight$input(CallbackInfo ci) { PlayerRideClient.tick(); }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void creeperknight$attack(CallbackInfoReturnable<Boolean> cir) {
        if (PlayerRideClient.chord()) cir.setReturnValue(false);
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void creeperknight$attackHold(boolean attack, CallbackInfo ci) { if (PlayerRideClient.chord()) ci.cancel(); }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void creeperknight$use(CallbackInfo ci) { if (PlayerRideClient.chord()) ci.cancel(); }
}

package dev.creeperknight.mixin;
import dev.creeperknight.client.PlayerRideClient;
import dev.creeperknight.test.ClientRideFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = PlayerRideClient.class, remap = false)
public abstract class PlayerRideCheckInputMixin {
    @Inject(method = "chord", at = @At("HEAD"), cancellable = true, remap = false)
    private static void creeperknight$testButtons(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRideFixture.active) cir.setReturnValue(ClientRideFixture.pressed);
    }
}

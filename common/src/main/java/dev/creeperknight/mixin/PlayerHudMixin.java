package dev.creeperknight.mixin;

import dev.creeperknight.client.PlayerRideClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PlayerHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void creeperknight$countdown(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        PlayerRideClient.render(graphics, partialTick);
    }
}

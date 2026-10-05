package dev.creeperknight.mixin;

import dev.creeperknight.KnightSettings;
import dev.creeperknight.scepter.FriendshipScepter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also blocks taking a cached crafting result after an administrator disables the recipe. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void creeperknight$craftPermission(Player player, CallbackInfoReturnable<Boolean> ci) {
        if ((Object)this instanceof ResultSlot slot && slot.getItem().getItem() instanceof FriendshipScepter
            && !player.level().isClientSide && !KnightSettings.get().wandCraftable) ci.setReturnValue(false);
    }
}

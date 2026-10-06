package dev.creeperknight.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Minecraft.class)
public interface ClientInputCheckAccess {
    @Invoker("startAttack") boolean creeperknight$startAttack();
    @Invoker("startUseItem") void creeperknight$startUse();
}

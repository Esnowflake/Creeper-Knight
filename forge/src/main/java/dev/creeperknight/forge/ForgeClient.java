package dev.creeperknight.forge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.client.KnightClient;
import dev.creeperknight.client.KnightConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = KnightSettings.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClient {
    private static final KeyMapping KEY = new KeyMapping("key.creeperknight.settings", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K, "key.categories.creeperknight");
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(KEY); }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> KnightClient.send = json -> ForgeNetwork.CHANNEL.sendToServer(new ForgeNetwork.Request(json)));
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> {
                KnightConfigScreen screen = new KnightConfigScreen(parent);
                if (minecraft.player != null) KnightClient.send.accept("");
                return screen;
            }));
    }
    @Mod.EventBusSubscriber(modid = KnightSettings.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) while (KEY.consumeClick()) KnightClient.open();
        }
        @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { KnightClient.reset(); }
    }
}

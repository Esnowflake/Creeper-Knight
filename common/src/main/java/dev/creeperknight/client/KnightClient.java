package dev.creeperknight.client;

import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.net.ConfigService;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client state is separate from the integrated server's authoritative configuration. */
public final class KnightClient {
    public static KnightConfig config;
    public static boolean editable;
    public static Consumer<String> send = ignored -> {};
    private KnightClient() {}
    public static void receive(ConfigService.Snapshot snapshot) {
        config = KnightConfig.fromJson(snapshot.json());
        editable = snapshot.editable();
        if (Minecraft.getInstance().screen instanceof KnightConfigScreen screen) screen.accept(snapshot);
    }
    public static void reset() { config = null; editable = false; PlayerRideClient.reset(); }
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(new KnightConfigScreen(minecraft.screen));
        send.accept("");
    }
}

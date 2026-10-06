package dev.creeperknight.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.creeperknight.client.KnightClient;
import dev.creeperknight.net.ConfigService;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class CreeperKnightFabricClient implements ClientModInitializer {
    private static volatile boolean handshakeAccepted;
    @Override public void onInitializeClient() {
        dev.creeperknight.client.PlayerRideClient.send = input -> {
            var buffer = PacketByteBufs.create(); buffer.writeVarInt(input.entityId()); buffer.writeBoolean(input.held());
            ClientPlayNetworking.send(CreeperKnightFabric.RIDE_INPUT, buffer);
        };
        KnightClient.send = json -> {
            var buffer = PacketByteBufs.create(); buffer.writeUtf(json, 8192);
            ClientPlayNetworking.send(CreeperKnightFabric.REQUEST, buffer);
        };
        ClientLoginNetworking.registerGlobalReceiver(CreeperKnightFabric.LOGIN, (client, handler, buffer, listenerAdder) -> {
            String serverVersion = buffer.readUtf(32);
            handshakeAccepted = CreeperKnightFabric.PROTOCOL.equals(serverVersion);
            var response = PacketByteBufs.create(); response.writeUtf(CreeperKnightFabric.PROTOCOL);
            return CompletableFuture.completedFuture(response);
        });
        ClientLoginConnectionEvents.INIT.register((handler, client) -> handshakeAccepted = false);
        ClientPlayNetworking.registerGlobalReceiver(CreeperKnightFabric.SNAPSHOT, (client, handler, buffer, sender) -> {
            var snapshot = new ConfigService.Snapshot(buffer.readUtf(8192), buffer.readBoolean(), buffer.readUtf(128));
            client.execute(() -> KnightClient.receive(snapshot));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!handshakeAccepted) handler.getConnection().disconnect(Component.literal(
                "This server must install Creeper Knight / 此服务器必须安装苦力怕骑士模组"));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> KnightClient.reset());
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.creeperknight.settings",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.creeperknight"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (key.consumeClick()) KnightClient.open(); });
    }
}

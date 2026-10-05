package dev.creeperknight.forge;

import dev.creeperknight.net.ConfigService;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeNetwork {
    private static final String PROTOCOL = "0.2.1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("creeperknight", "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    public record Request(String json) {}
    private ForgeNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(0, Request.class, (message, buffer) -> buffer.writeUtf(message.json(), 8192),
            buffer -> new Request(buffer.readUtf(8192)), (message, supplier) -> {
                var context = supplier.get();
                context.enqueueWork(() -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) ConfigService.handle(player, message.json(), snapshot -> send(player, snapshot),
                        () -> broadcast(player.server));
                }); context.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, ConfigService.Snapshot.class, (message, buffer) -> {
            buffer.writeUtf(message.json(), 8192); buffer.writeBoolean(message.editable()); buffer.writeUtf(message.message());
        }, buffer -> new ConfigService.Snapshot(buffer.readUtf(8192), buffer.readBoolean(), buffer.readUtf(128)),
            (message, supplier) -> {
                var context = supplier.get();
                context.enqueueWork(() -> ClientReceiver.receive(message)); context.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player, ConfigService.Snapshot snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
    public static void broadcast(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(player -> send(player, ConfigService.snapshot(player, "")));
    }
    private static class ClientReceiver {
        static void receive(ConfigService.Snapshot snapshot) { dev.creeperknight.client.KnightClient.receive(snapshot); }
    }
}

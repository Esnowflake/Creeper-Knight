package dev.creeperknight.fabric;

import dev.creeperknight.KnightSettings;
import dev.creeperknight.net.ConfigService;
import dev.creeperknight.scepter.FriendshipScepter;
import dev.creeperknight.scepter.ScepterRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class CreeperKnightFabric implements ModInitializer {
    public static final String PROTOCOL = "3.0";
    public static final ResourceLocation RIDE_INPUT = new ResourceLocation("creeperknight", "ride_input");
    public static final ResourceLocation LOGIN = new ResourceLocation("creeperknight", "handshake");
    public static final ResourceLocation REQUEST = new ResourceLocation("creeperknight", "config_request");
    public static final ResourceLocation SNAPSHOT = new ResourceLocation("creeperknight", "config_snapshot");
    @Override public void onInitialize() {
        var scepter = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation("creeperknight", "friendship_scepter"), new FriendshipScepter());
        var recipe = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, new ResourceLocation("creeperknight", "scepter"), new ScepterRecipe.Serializer());
        ScepterRecipe.serializer = () -> recipe;
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(scepter));
        ServerLifecycleEvents.SERVER_STARTING.register(KnightSettings::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ConfigService.clear(); dev.creeperknight.scepter.ScepterTickets.clear(); });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(dev.creeperknight.scepter.ScepterTickets::tick);
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            var buffer = PacketByteBufs.create(); buffer.writeUtf(PROTOCOL); sender.sendPacket(LOGIN, buffer);
        });
        ServerLoginNetworking.registerGlobalReceiver(LOGIN, (server, handler, understood, buffer, synchronizer, sender) -> {
            boolean compatible = understood && PROTOCOL.equals(buffer.readUtf(32));
            if (!compatible) handler.disconnect(Component.literal(
                "Creeper Knight " + PROTOCOL + " is required on both client and server / 客户端和服务端必须安装相同版本的苦力怕骑士模组"));
        });
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buffer, sender) -> {
            String json = buffer.readUtf(8192);
            server.execute(() -> ConfigService.handle(player, json, snapshot -> send(player, snapshot),
                () -> server.getPlayerList().getPlayers().forEach(p -> send(p, ConfigService.snapshot(p, "")))));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, ConfigService.snapshot(handler.player, "")));
        ServerPlayNetworking.registerGlobalReceiver(RIDE_INPUT, (server, player, handler, buffer, sender) -> {
            var input = new dev.creeperknight.PlayerRiding.Input(buffer.readVarInt(), buffer.readBoolean());
            server.execute(() -> dev.creeperknight.PlayerRiding.handle(player, input));
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dev.creeperknight.KnightCommands.register(dispatcher, server ->
                server.getPlayerList().getPlayers().forEach(p -> send(p, ConfigService.snapshot(p, "")))));
    }
    private static void send(ServerPlayer player, ConfigService.Snapshot snapshot) {
        var buffer = PacketByteBufs.create();
        buffer.writeUtf(snapshot.json(), 8192); buffer.writeBoolean(snapshot.editable()); buffer.writeUtf(snapshot.message());
        ServerPlayNetworking.send(player, SNAPSHOT, buffer);
    }
}

package dev.creeperknight.net;

import dev.creeperknight.KnightSettings;
import dev.creeperknight.config.KnightConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

public final class ConfigService {
    public record Snapshot(String json, boolean editable, String message) {}
    private static final Map<UUID, Integer> LAST_SAVE = new HashMap<>();
    private record Confirmation(String json, int tick) {}
    private static final Map<UUID, Confirmation> BLOCK_CONFIRMATIONS = new HashMap<>();
    private ConfigService() {}
    public static Snapshot snapshot(ServerPlayer player, String message) {
        return new Snapshot(KnightSettings.get().toJson(), KnightSettings.canEdit(player), message);
    }
    public static void handle(ServerPlayer player, String json, Consumer<Snapshot> reply, Runnable broadcast) {
        if (json.isEmpty()) { BLOCK_CONFIRMATIONS.remove(player.getUUID()); reply.accept(snapshot(player, "")); return; }
        if (!KnightSettings.canEdit(player)) { reply.accept(snapshot(player, "creeperknight.status.denied")); return; }
        int tick = player.server.getTickCount();
        Integer previous = LAST_SAVE.get(player.getUUID());
        if (previous != null && tick >= previous && tick - previous < 20) {
            reply.accept(snapshot(player, "creeperknight.status.rate")); return;
        }
        try {
            if (json.length() > 8192) throw new IllegalArgumentException("Config too large");
            var object = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            boolean confirmed = object.has("confirmBlockDamage") && object.get("confirmBlockDamage").getAsBoolean();
            KnightConfig config = KnightConfig.fromJson(confirmed ? object.get("config").toString() : json);
            if (config.wandBlockDamage && !KnightSettings.get().wandBlockDamage) {
                Confirmation request = BLOCK_CONFIRMATIONS.get(player.getUUID());
                if (!confirmed || request == null || !request.json().equals(config.toJson()) || tick - request.tick() > 600) {
                    BLOCK_CONFIRMATIONS.put(player.getUUID(), new Confirmation(config.toJson(), tick));
                    reply.accept(snapshot(player, "creeperknight.status.confirmblocks")); return;
                }
            }
            KnightSettings.save(config);
            LAST_SAVE.put(player.getUUID(), tick); BLOCK_CONFIRMATIONS.remove(player.getUUID());
            broadcast.run();
            reply.accept(snapshot(player, "creeperknight.status.saved"));
        } catch (Exception e) {
            KnightSettings.LOGGER.warn("Could not save creeper knight settings: {}", e.toString());
            reply.accept(snapshot(player, "creeperknight.status.invalid"));
        }
    }
    public static String confirmBlockDamage(String json) {
        var object = new com.google.gson.JsonObject(); object.addProperty("confirmBlockDamage", true);
        object.add("config", com.google.gson.JsonParser.parseString(json)); return object.toString();
    }
    public static void clear() { LAST_SAVE.clear(); BLOCK_CONFIRMATIONS.clear(); }
}

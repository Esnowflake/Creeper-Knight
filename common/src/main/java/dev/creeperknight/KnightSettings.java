package dev.creeperknight;

import dev.creeperknight.config.KnightConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KnightSettings {
    public static final String MOD_ID = "creeperknight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile KnightConfig serverConfig = new KnightConfig();
    private static Path configPath;
    private static String pendingReload;
    private static int pendingReloadTick;
    private KnightSettings() {}
    public static KnightConfig get() { return serverConfig; }
    public static void load(MinecraftServer server) {
        pendingReload = null;
        configPath = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/creeperknight.json");
        serverConfig = new KnightConfig();
        try {
            if (Files.exists(configPath)) serverConfig = KnightConfig.fromJson(Files.readString(configPath));
            else write(serverConfig);
        } catch (Exception e) {
            LOGGER.error("Cannot load {}; using defaults. Invalid file is preserved.", configPath, e);
        }
    }
    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }
    public static boolean reload(MinecraftServer server, boolean confirmed) throws IOException {
        KnightConfig candidate = KnightConfig.fromJson(Files.readString(configPath));
        if (candidate.wandBlockDamage && !serverConfig.wandBlockDamage && (!confirmed || !candidate.toJson().equals(pendingReload)
            || server.getTickCount() - pendingReloadTick > 600)) {
            pendingReload = candidate.toJson(); pendingReloadTick = server.getTickCount(); return false;
        }
        pendingReload = null; serverConfig = candidate; return true;
    }
    public static void save(KnightConfig config) throws IOException {
        config.validate();
        write(config);
        serverConfig = config;
    }
    private static void write(KnightConfig config) throws IOException {
        if (configPath == null) throw new IOException("No world is running");
        Files.createDirectories(configPath.getParent());
        Path temporary = configPath.resolveSibling("creeperknight.json.tmp");
        Files.writeString(temporary, config.toJson(), StandardCharsets.UTF_8);
        try { Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

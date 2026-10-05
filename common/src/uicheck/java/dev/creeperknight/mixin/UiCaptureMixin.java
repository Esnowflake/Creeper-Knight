package dev.creeperknight.mixin;

import dev.creeperknight.KnightSettings;
import dev.creeperknight.client.KnightClient;
import dev.creeperknight.client.KnightConfigScreen;
import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.net.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Automated render capture in an isolated dev client. Excluded from released jars. */
@Mixin(Minecraft.class)
public abstract class UiCaptureMixin {
    @Unique private int creeperknight$frame;
    @Unique private int creeperknight$category;
    @Unique private int creeperknight$locale;
    @Unique private boolean creeperknight$started;
    @Unique private int creeperknight$networkStage;
    @Unique private long creeperknight$networkDeadline;
    @Unique private boolean creeperknight$instantCaptured;
    @Unique private double creeperknight$distance;
    @Unique private int creeperknight$scepterPage;
    @Unique private int creeperknight$saveTick;
    @Inject(method = "runTick", at = @At("TAIL"))
    private void creeperknight$capture(boolean render, CallbackInfo ci) throws Exception {
        Minecraft minecraft = (Minecraft)(Object)this;
        if (creeperknight$networkStage != 0) { creeperknight$network(minecraft); return; }
        if (!Boolean.getBoolean("creeperknight.uiChecks") || minecraft.getOverlay() != null || minecraft.screen == null) return;
        if (!creeperknight$started) {
            creeperknight$started = true;
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
            minecraft.getLanguageManager().setSelected("en_us");
            minecraft.options.languageCode = "en_us";
            minecraft.reloadResourcePacks();
            return;
        }
        if (!(minecraft.screen instanceof KnightConfigScreen)) {
            minecraft.setScreen(new KnightConfigScreen(null));
            KnightClient.receive(new ConfigService.Snapshot(new KnightConfig().toJson(), true, "creeperknight.status.ready"));
            creeperknight$frame = 0;
            return;
        }
        if (++creeperknight$frame < 30) return;
        String language = creeperknight$locale == 0 ? "en_us" : "zh_cn";
        String category = KnightConfig.Group.values()[creeperknight$category].name().toLowerCase();
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots"); Files.createDirectories(directory);
        String variant = creeperknight$instantCaptured ? "-instant" : creeperknight$scepterPage == 1 ? "-warning" : creeperknight$scepterPage == 2 ? "-page2" : "";
        Path destination = directory.resolve(language + "-" + category + variant + ".png");
        try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) { screenshot.writeToFile(destination); }
        KnightSettings.LOGGER.info("UI render captured: {}", destination);
        creeperknight$frame = 0;
        if (KnightConfig.Group.values()[creeperknight$category] == KnightConfig.Group.MOVEMENT) {
            int tabs = KnightConfig.Group.values().length;
            if (!creeperknight$instantCaptured) {
                ((Button)minecraft.screen.children().get(tabs + 3)).onPress();
                if (((Button)minecraft.screen.children().get(tabs + 2)).active || !((EditBox)minecraft.screen.children().get(tabs + 4)).active)
                    throw new IllegalStateException("Instant mode did not disable countdown pursuit and enable distance editing");
                creeperknight$instantCaptured = true; return;
            }
            ((Button)minecraft.screen.children().get(tabs + 3)).onPress();
            if (!((Button)minecraft.screen.children().get(tabs + 2)).active)
                throw new IllegalStateException("Disabling instant mode did not re-enable countdown pursuit");
            creeperknight$instantCaptured = false;
        }
        if (KnightConfig.Group.values()[creeperknight$category] == KnightConfig.Group.SCEPTER) {
            if (creeperknight$scepterPage == 0) {
                ((EditBox)minecraft.screen.children().get(KnightConfig.Group.values().length + 3)).setValue("1");
                creeperknight$scepterPage = 1; return;
            }
            if (creeperknight$scepterPage == 1) {
                ((EditBox)minecraft.screen.children().get(KnightConfig.Group.values().length + 3)).setValue("30");
                minecraft.screen.children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals(">")).map(w -> (Button)w).findFirst().orElseThrow().onPress();
                creeperknight$scepterPage = 2; return;
            }
            creeperknight$scepterPage = 0;
        }
        creeperknight$category++;
        if (creeperknight$category < KnightConfig.Group.values().length) {
            ((Button)minecraft.screen.children().get(creeperknight$category)).onPress();
        } else if (creeperknight$locale == 0) {
            creeperknight$locale = 1; creeperknight$category = 0;
            minecraft.getLanguageManager().setSelected("zh_cn"); minecraft.options.languageCode = "zh_cn";
            minecraft.setScreen(null); minecraft.reloadResourcePacks();
        } else {
            KnightClient.reset();
            creeperknight$networkStage = 1;
            creeperknight$networkDeadline = System.currentTimeMillis() + 120000;
            String world = "ck-scepter-check";
            if (Files.exists(minecraft.gameDirectory.toPath().resolve("saves/" + world + "/level.dat")))
                minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
            else minecraft.createWorldOpenFlows().createFreshLevel(world,
                new LevelSettings("CK Network Check", GameType.CREATIVE, false, Difficulty.NORMAL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), WorldPresets::createNormalWorldDimensions);
        }
    }
    @Unique private void creeperknight$network(Minecraft minecraft) throws Exception {
        if (System.currentTimeMillis() > creeperknight$networkDeadline) throw new IllegalStateException("Integrated networking check timed out");
        if (creeperknight$networkStage == 1 && minecraft.player != null && KnightClient.config != null) {
            if (!KnightClient.editable) throw new IllegalStateException("Singleplayer owner cannot edit config");
            var config = KnightClient.config.copy(); config.naturalChance = 0.123;
            creeperknight$distance = config.instantExplosionDistance == 2.75 ? 3.25 : 2.75;
            config.instantExplosion = true; config.chaseDuringFuse = false; config.instantExplosionDistance = creeperknight$distance;
            config.wandBlockDamage = false; config.wandLifetimeSeconds = 1; config.wandRiderType = 4;
            config.wandSpeedMultiplier = 10; config.wandNotifications = 1;
            KnightClient.send.accept(config.toJson()); creeperknight$networkStage = 2;
        } else if (creeperknight$networkStage == 2 && KnightClient.config != null && KnightClient.config.instantExplosionDistance == creeperknight$distance) {
            if (!creeperknight$matches(KnightSettings.get())) throw new IllegalStateException("Save did not reach the server");
            Path saved = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig/creeperknight.json");
            if (!creeperknight$matches(KnightConfig.fromJson(Files.readString(saved)))) throw new IllegalStateException("Save did not persist");
            KnightClient.reset(); KnightClient.send.accept(""); creeperknight$networkStage = 3;
        } else if (creeperknight$networkStage == 3 && KnightClient.config != null) {
            if (!creeperknight$matches(KnightClient.config)) throw new IllegalStateException("Request returned stale config");
            KnightSettings.LOGGER.info("Integrated networking checks passed: handshake, administrator permission, save including scepter options, disk persistence, snapshot request");
            KnightClient.open(); creeperknight$networkStage = 4; creeperknight$frame = 0;
            creeperknight$saveTick = minecraft.getSingleplayerServer().getTickCount();
        } else if (creeperknight$networkStage == 4 && minecraft.getSingleplayerServer().getTickCount() - creeperknight$saveTick >= 25) {
            creeperknight$button(minecraft, "creeperknight.group.scepter").onPress();
            ((Button)minecraft.screen.children().get(KnightConfig.Group.values().length + 5)).onPress();
            creeperknight$button(minecraft, "creeperknight.save").onPress();
            creeperknight$networkStage = 5;
        } else if (creeperknight$networkStage == 5 && minecraft.screen instanceof ConfirmScreen) {
            creeperknight$screenshot(minecraft, "zh_cn-confirm-blocks.png");
            // Reject first, then test the successful second-confirmation path.
            creeperknight$button(minecraft, "gui.no").onPress();
            creeperknight$networkStage = 6; creeperknight$saveTick = minecraft.getSingleplayerServer().getTickCount();
        } else if (creeperknight$networkStage == 6 && minecraft.getSingleplayerServer().getTickCount() - creeperknight$saveTick >= 25) {
            if (KnightSettings.get().wandBlockDamage || KnightClient.config.wandBlockDamage) throw new IllegalStateException("Cancelled confirmation enabled block damage");
            ((Button)minecraft.screen.children().get(KnightConfig.Group.values().length + 5)).onPress();
            creeperknight$button(minecraft, "creeperknight.save").onPress(); creeperknight$networkStage = 7;
        } else if (creeperknight$networkStage == 7 && minecraft.screen instanceof ConfirmScreen) {
            creeperknight$button(minecraft, "gui.yes").onPress(); creeperknight$networkStage = 8;
        } else if (creeperknight$networkStage == 8 && KnightClient.config != null && KnightClient.config.wandBlockDamage) {
            if (!KnightSettings.get().wandBlockDamage) throw new IllegalStateException("Confirmed block damage did not reach server");
            creeperknight$screenshot(minecraft, "zh_cn-in-world.png");
            KnightSettings.LOGGER.info("Block confirmation UI checks passed: cancel keeps protection, confirm saves block damage");
            var server = minecraft.getSingleplayerServer(); var uuid = minecraft.player.getUUID();
            server.execute(() -> server.getPlayerList().getPlayer(uuid).getInventory().setItem(0,
                new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("creeperknight", "friendship_scepter")))));
            creeperknight$networkStage = 9;
        } else if (creeperknight$networkStage == 9 && minecraft.player.getInventory().getItem(0).getItem() instanceof dev.creeperknight.scepter.FriendshipScepter) {
            minecraft.setScreen(new InventoryScreen(minecraft.player)); creeperknight$networkStage = 10; creeperknight$frame = 0;
        } else if (creeperknight$networkStage == 10 && ++creeperknight$frame == 30) {
            creeperknight$screenshot(minecraft, "scepter-inventory.png");
            KnightSettings.LOGGER.info("Registered scepter model and texture rendered in inventory");
            minecraft.stop();
        }
    }
    @Unique private boolean creeperknight$matches(KnightConfig config) {
        return config.naturalChance == 0.123 && config.instantExplosion && !config.chaseDuringFuse
            && config.instantExplosionDistance == creeperknight$distance && config.wandLifetimeSeconds == 1
            && config.wandRiderType == 4 && config.wandSpeedMultiplier == 10 && config.wandNotifications == 1;
    }
    @Unique private Button creeperknight$button(Minecraft minecraft, String key) {
        return minecraft.screen.children().stream().filter(w -> w instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key))
            .map(w -> (Button)w).findFirst().orElseThrow();
    }
    @Unique private void creeperknight$screenshot(Minecraft minecraft, String filename) throws Exception {
        Path image = minecraft.gameDirectory.toPath().resolve("screenshots/" + filename);
        try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) { screenshot.writeToFile(image); }
    }
}

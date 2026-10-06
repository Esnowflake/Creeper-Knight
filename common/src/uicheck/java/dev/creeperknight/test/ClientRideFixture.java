package dev.creeperknight.test;

import dev.creeperknight.KnightSettings;
import dev.creeperknight.PlayerRiding;
import dev.creeperknight.client.KnightClient;
import dev.creeperknight.client.KnightConfigScreen;
import dev.creeperknight.mixin.ClientInputCheckAccess;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;

/** Dev-only keyboard and chord fixture; packets, entity movement and HUD are real. */
public final class ClientRideFixture {
    public static boolean active, pressed;
    private static int stage, tick;
    private static long lastTime = -1;
    private static double startZ, baseY, maxY;
    private static int mountId;
    private static double originalSeconds;
    private ClientRideFixture() {}
    public static void start(Minecraft mc) {
        mc.setScreen(null); stage = 1; tick = 0;
        originalSeconds = KnightClient.config.playerChargeSeconds;
        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel();
            KnightSettings.get().playerRidingEnabled = true; KnightSettings.get().playerChargeSeconds = 2; KnightSettings.get().playerSpeedMultiplier = 1;
            var pos = player.blockPosition().offset(0, 2, 0);
            for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
                level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            var creeper = EntityType.CREEPER.create(level); creeper.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            level.addFreshEntity(creeper); player.setGameMode(GameType.CREATIVE); player.startRiding(creeper); PlayerRiding.beginRide(creeper);
        });
    }
    public static void tick(Minecraft mc) throws Exception {
        if (mc.level == null || mc.player == null || lastTime == mc.level.getGameTime()) return;
        lastTime = mc.level.getGameTime(); tick++;
        if (stage == 1 && mc.player.getVehicle() instanceof Creeper creeper) {
            active = true; pressed = false; mountId = creeper.getId(); startZ = creeper.getZ(); baseY = creeper.getY(); maxY = baseY;
            mc.player.setYRot(0); mc.options.keyUp.setDown(true); stage = 2; tick = 0;
        } else if (stage == 2 && tick >= 20) {
            Creeper creeper = (Creeper)mc.player.getVehicle();
            check(creeper.getZ() > startZ + 1, "WASD moves ridden creeper");
            mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(true); stage = 3; tick = 0;
        } else if (stage == 3) {
            Creeper creeper = (Creeper)mc.player.getVehicle(); maxY = Math.max(maxY, creeper.getY());
            if (tick == 4) mc.options.keyJump.setDown(false);
            if (tick >= 20) {
                check(maxY > baseY + 0.2, "Space jumps ridden creeper");
                pressed = true; stage = 4; tick = 0;
                var input = (ClientInputCheckAccess)mc;
                check(!input.creeperknight$startAttack(), "chord suppresses attack"); input.creeperknight$startUse();
            }
        } else if (stage == 4 && tick >= 12) {
            Creeper creeper = (Creeper)mc.player.getVehicle();
            float remaining = ((PlayerRiding.Access)creeper).creeperknight$remaining();
            check(remaining > 0 && remaining < 2, "real C2S packets charge and S2C entity data updates HUD");
            if (tick == 12) screenshot(mc, "zh_cn-player-charge-hud.png");
            if (tick >= 24) { screenshot(mc, "zh_cn-player-charge-orange.png"); pressed = false; stage = 5; tick = 0; }
        } else if (stage == 5 && tick >= 4) {
            check(((PlayerRiding.Access)mc.player.getVehicle()).creeperknight$remaining() == -1, "client release packet clears server timer");
            pressed = true; stage = 6; tick = 0;
        } else if (stage == 6 && tick >= 4) {
            check(((PlayerRiding.Access)mc.player.getVehicle()).creeperknight$remaining() > 1.6, "client re-hold starts full countdown");
            pressed = false; mc.options.keyShift.setDown(true); stage = 7; tick = 0;
        } else if (stage == 7 && tick >= 4) {
            mc.options.keyShift.setDown(false);
            check(mc.player.getVehicle() == null, "Sneak dismounts"); active = false;
            KnightClient.open(); stage = 8; tick = 0;
        } else if (stage == 8 && tick >= 4 && mc.screen instanceof KnightConfigScreen) {
            button(mc, "creeperknight.group.player_riding").onPress();
            box(mc, "playerChargeSeconds").setValue("7.25");
            button(mc, "creeperknight.group.spawning").onPress(); button(mc, "creeperknight.group.player_riding").onPress();
            check(box(mc, "playerChargeSeconds").getValue().equals("7.25"), "category switches preserve numeric draft");
            box(mc, "playerChargeSeconds").setValue("-");
            button(mc, "creeperknight.group.spawning").onPress(); button(mc, "creeperknight.group.player_riding").onPress();
            check(box(mc, "playerChargeSeconds").getValue().equals("-"), "category switches preserve incomplete numeric text");
            button(mc, "creeperknight.save").onPress();
            check(KnightSettings.get().playerChargeSeconds == 2, "invalid text cannot be saved");
            button(mc, "gui.cancel").onPress(); KnightClient.open(); stage = 9; tick = 0;
        } else if (stage == 9 && tick >= 4 && mc.screen instanceof KnightConfigScreen) {
            button(mc, "creeperknight.group.player_riding").onPress();
            check(Double.parseDouble(box(mc, "playerChargeSeconds").getValue()) == originalSeconds, "cancel discards draft and reopens server values");
            box(mc, "playerChargeSeconds").setValue("9"); mc.screen.keyPressed(256, 0, 0); KnightClient.open(); stage = 10; tick = 0;
        } else if (stage == 10 && tick >= 4 && mc.screen instanceof KnightConfigScreen) {
            button(mc, "creeperknight.group.player_riding").onPress();
            check(Double.parseDouble(box(mc, "playerChargeSeconds").getValue()) == originalSeconds, "Escape discards draft");
            box(mc, "playerChargeSeconds").setValue("2.75"); box(mc, "playerSpeedMultiplier").setValue("10");
            check(KnightSettings.get().playerChargeSeconds == 2 && KnightSettings.get().playerSpeedMultiplier == 1, "numeric edits alone do not affect server");
            button(mc, "creeperknight.save").onPress(); stage = 11; tick = 0;
        } else if (stage == 11 && tick >= 4 && KnightClient.config.playerChargeSeconds == 2.75) {
            check(KnightSettings.get().playerChargeSeconds == 2.75 && KnightSettings.get().playerSpeedMultiplier == 10, "Save to server applies numeric riding options");
            button(mc, "creeperknight.reset").onPress(); button(mc, "gui.cancel").onPress();
            check(KnightClient.config.instantExplosion && KnightClient.config.wandSpeedMultiplier == 10 && KnightClient.config.playerChargeSeconds == 2.75, "reset without saving does not alter server config");
            KnightSettings.LOGGER.info("3.0 client checks passed: WASD, jump, real ride packets, charge HUD and orange warning, cancel/restart, sneak, input suppression, category draft, incomplete text, invalid input, Cancel, Escape, Save to server, defaults isolation");
            var server = mc.getSingleplayerServer(); server.execute(() -> { var entity = server.overworld().getEntity(mountId); if (entity != null) entity.discard(); });
            mc.stop();
        }
        if (tick > 200) throw new IllegalStateException("Client riding fixture timed out at stage " + stage);
    }
    private static void check(boolean passed, String message) { if (!passed) throw new IllegalStateException(message); }
    private static Button button(Minecraft mc, String key) {
        return mc.screen.children().stream().filter(w -> w instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)).map(w -> (Button)w).findFirst().orElseThrow();
    }
    private static EditBox box(Minecraft mc, String option) {
        return mc.screen.children().stream().filter(w -> w instanceof EditBox b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("creeperknight.option." + option)).map(w -> (EditBox)w).findFirst().orElseThrow();
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        Path path = mc.gameDirectory.toPath().resolve("screenshots/" + name);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
    }
}

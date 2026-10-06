package dev.creeperknight.client;

import dev.creeperknight.PlayerRiding;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Creeper;
import org.lwjgl.glfw.GLFW;

public final class PlayerRideClient {
    public static Consumer<PlayerRiding.Input> send = ignored -> {};
    private static int mount = -1;
    private static boolean held;
    private static int heartbeat;
    private static double started;
    private PlayerRideClient() {}
    public static boolean chord() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.isWindowActive() || !(mc.player.getVehicle() instanceof Creeper)) return false;
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
            && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int id = mc.player != null && mc.player.getVehicle() instanceof Creeper creeper ? creeper.getId() : -1;
        boolean both = chord();
        if (both && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
            if (mc.player.isUsingItem()) mc.player.stopUsingItem();
        }
        if (mount != id) {
            if (mount != -1 && held) send.accept(new PlayerRiding.Input(mount, false));
            mount = id; held = false; heartbeat = 0;
        }
        if (id != -1 && (held != both || both && ++heartbeat >= 3)) {
            if (both && !held) started = mc.level.getGameTime();
            held = both; heartbeat = 0;
            send.accept(new PlayerRiding.Input(id, both));
        }
    }
    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!held || !chord() || mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof Creeper creeper) || creeper.isIgnited()) return;
        float seconds = ((PlayerRiding.Access)creeper).creeperknight$remaining();
        if (seconds < 0 && KnightClient.config != null) seconds = (float)Math.max(0, KnightClient.config.playerChargeSeconds - (mc.level.getGameTime() - started) / 20);
        seconds = Math.max(0, seconds - partialTick / 20);
        Component text = Component.translatable("creeperknight.player.countdown", String.format(Locale.ROOT, "%.2f", seconds));
        graphics.drawCenteredString(mc.font, text, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 18, seconds <= 1 ? 0xFFAA00 : 0xFFFFFF);
    }
    public static void reset() { mount = -1; held = false; heartbeat = 0; }
}

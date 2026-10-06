package dev.creeperknight;

import dev.creeperknight.mixin.CreeperAccess;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

/** Charge state belongs to this mount and is driven exclusively by server ticks. */
public final class PlayerRiding {
    public static final float BASE_SPEED = 0.375F;
    public record Input(int entityId, boolean held) {}
    public static final class State {
        public UUID rider;
        public boolean held;
        public long lastInput;
        public double deadline;
    }
    public interface Access {
        State creeperknight$rideState();
        float creeperknight$remaining();
        void creeperknight$remaining(float value);
        float creeperknight$rideSpeed();
        void creeperknight$rideSpeed(float value);
    }
    private PlayerRiding() {}
    public static Player rider(Creeper creeper) {
        return creeper.getFirstPassenger() instanceof Player player && player.isAlive() && !player.isRemoved() ? player : null;
    }
    public static void resetFuse(Creeper creeper) {
        if (!creeper.isIgnited()) {
            creeper.setSwellDir(-1);
            ((CreeperAccess)creeper).creeperknight$setSwell(0);
        }
    }
    public static void beginRide(Creeper creeper) {
        resetFuse(creeper);
        creeper.setTarget(null);
        creeper.getNavigation().stop();
        creeper.setSpeed(0);
        creeper.setDeltaMovement(creeper.getDeltaMovement().multiply(0, 1, 0));
        tick(creeper);
    }
    public static void handle(ServerPlayer player, Input input) {
        if (!KnightSettings.get().playerRidingEnabled || player.isSpectator() || !player.isAlive()
            || !(player.getVehicle() instanceof Creeper creeper) || !creeper.isAlive()
            || creeper.getId() != input.entityId() || rider(creeper) != player) return;
        Access access = (Access)creeper;
        State state = access.creeperknight$rideState();
        if (!player.getUUID().equals(state.rider)) beginRide(creeper);
        long now = creeper.level().getGameTime();
        state.lastInput = now;
        if (input.held()) player.stopUsingItem();
        if (!input.held() || creeper.isIgnited()) { cancel(creeper); return; }
        if (!state.held) {
            state.held = true;
            state.deadline = now + KnightSettings.get().playerChargeSeconds * 20;
            access.creeperknight$remaining((float)KnightSettings.get().playerChargeSeconds);
            if (state.deadline <= now) ((CreeperAccess)creeper).creeperknight$explode();
        }
    }
    private static void cancel(Creeper creeper) {
        State state = ((Access)creeper).creeperknight$rideState();
        state.held = false;
        ((Access)creeper).creeperknight$remaining(-1);
    }
    public static boolean tick(Creeper creeper) {
        Access access = (Access)creeper;
        State state = access.creeperknight$rideState();
        Player player = rider(creeper);
        if (player == null || !KnightSettings.get().playerRidingEnabled || player.isSpectator()) {
            if (state.rider != null) {
                cancel(creeper); state.rider = null;
                resetFuse(creeper); creeper.setSpeed(0); creeper.getNavigation().stop();
            }
            if (player != null) player.stopRiding();
            return false;
        }
        if (!player.getUUID().equals(state.rider)) {
            cancel(creeper); state.rider = player.getUUID();
            // An abandoned scepter mount becomes a normal player mount on takeover.
            ((dev.creeperknight.scepter.SummonedKnight.Access)creeper).creeperknight$setSummon(null);
        }
        access.creeperknight$rideSpeed((float)(BASE_SPEED * KnightSettings.get().playerSpeedMultiplier));
        creeper.setTarget(null);
        resetFuse(creeper);
        long now = creeper.level().getGameTime();
        // Heartbeats expire if a client disconnects or stops sending input.
        if (creeper.isIgnited() || state.held && now - state.lastInput > 10) cancel(creeper);
        if (state.held) {
            double seconds = (state.deadline - now) / 20;
            access.creeperknight$remaining((float)Math.max(0, seconds));
            if (seconds <= 0) ((CreeperAccess)creeper).creeperknight$explode();
        }
        return true;
    }
}

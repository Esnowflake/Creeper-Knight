package dev.creeperknight.test;

import com.mojang.authlib.GameProfile;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.scepter.ScepterLogic;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Regression coverage uses real server movement and real player damage, not direct AI calls. */
public final class HighSpeedGameChecks {
    private record Trial(String name, boolean summoned, double ordinarySpeed, double wandSpeed,
                         boolean instant, double radius, boolean moving, boolean wall, int riderType, boolean chase) {}
    private static final Trial[] TRIALS = {
        new Trial("maximum stationary", true, 4, 10, true, 2, false, false, 0, true),
        new Trial("maximum moving", true, 4, 10, true, 2, true, false, 0, true),
        new Trial("small instant radius", true, 4, 10, true, 0.5, false, false, 2, true),
        new Trial("moving countdown", true, 1.5, 10, false, 2, true, false, 4, true),
        new Trial("ordinary knight moving", false, 4, 1, true, 2, true, false, 0, true),
        new Trial("maximum around wall", true, 4, 10, true, 2, false, true, 1, true),
        new Trial("villager rider moving", true, 1.5, 10, true, 2, true, false, 3, true),
        new Trial("stopped countdown", true, 4, 10, false, 2, false, false, 0, false)
    };
    private final GameTestHelper helper;
    private KnightConfig original;
    private Creeper creeper;
    private Entity rider;
    private ServerPlayer target;
    private int completed;
    private double closest;
    private double fastestStep;
    private Vec3 previous;
    private boolean hit;
    private boolean griefing;
    private Vec3 fusePosition;
    private int hitTick;
    private HighSpeedGameChecks(GameTestHelper helper) { this.helper = helper; }
    public static void run(GameTestHelper helper) {
        // Other tests exercise defaults in the same server; start after their moving fixtures finish.
        HighSpeedGameChecks checks = new HighSpeedGameChecks(helper);
        helper.runAtTickTime(50, checks::start);
        // Register callbacks before the test starts, rather than modifying GameTestInfo's map while it iterates.
        for (int index = 0; index < TRIALS.length; index++) {
            int scenario = index;
            long start = 115 + index * 81;
            helper.runAtTickTime(start, () -> checks.trial(scenario));
            for (int tick = 1; tick <= 80; tick++) {
                int age = tick;
                helper.runAtTickTime(start + tick, () -> checks.sample(scenario, age));
            }
        }
    }
    private void start() {
        original = KnightSettings.get().copy();
        griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
        helper.getLevel().setDayTime(13000);
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        target = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "SpeedTarget"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        channel.attr(io.netty.util.AttributeKey.<String>valueOf("fml:netversion")).set("FML3");
        channel.pipeline().addLast("packet_handler", connection); channel.pipeline().fireChannelActive();
        target.server.getPlayerList().placeNewPlayer(connection, target);
        target.setGameMode(GameType.SURVIVAL);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setPos(position(12.5, 11.5));
        // The first scenario starts 65 ticks later, after vanilla's login damage protection expires.
    }
    private Vec3 position(double x, double z) { return helper.absoluteVec(new Vec3(x, 2, z)); }
    private void trial(int index) {
        Trial trial = TRIALS[index];
        var config = KnightSettings.get();
        config.speedMultiplier = trial.ordinarySpeed; config.wandSpeedMultiplier = trial.wandSpeed;
        config.instantExplosion = trial.instant; config.instantExplosionDistance = trial.radius;
        config.chaseDuringFuse = trial.chase; config.fullAiTakeover = true; config.attackPlayers = true;
        config.wandLifetimeSeconds = 120; config.wandBlockDamage = false; config.wandRiderType = trial.riderType;
        config.seekMounts = false; config.naturalSpawning = false;
        target.setHealth(1000); target.invulnerableTime = 0;
        target.setPos(position(12.5, 11.5)); target.setDeltaMovement(Vec3.ZERO);
        for (int z = 2; z <= 11; z++) for (int y = 2; y <= 4; y++) helper.setBlock(8, y, z, trial.wall ? Blocks.STONE : Blocks.AIR);
        if (trial.summoned) creeper = ScepterLogic.create(helper.getLevel(), position(3.5, 3.5), target.getUUID());
        else {
            creeper = helper.spawn(EntityType.CREEPER, 3, 2, 3);
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3); zombie.setBaby(true); zombie.startRiding(creeper); zombie.setTarget(target);
        }
        helper.assertTrue(creeper != null, "speed fixture creates mount");
        rider = creeper.getFirstPassenger();
        creeper.setYRot(135); previous = creeper.position(); closest = 100; fastestStep = 0; hit = false; hitTick = 0; fusePosition = null;
    }
    private void sample(int index, int age) {
        Trial trial = TRIALS[index];
        try {
            if (!hit) {
                closest = Math.min(closest, creeper.distanceTo(target));
                fastestStep = Math.max(fastestStep, creeper.position().subtract(previous).horizontalDistance());
                previous = creeper.position();
                if (trial.wall && !creeper.isRemoved()) helper.assertTrue(
                    helper.getLevel().noCollision(creeper, creeper.getBoundingBox().deflate(0.001)), "high speed never enters solid wall");
                if (creeper.isRemoved()) {
                    helper.assertTrue(rider.isRemoved() && target.getHealth() < 1000, "pursuit explosion damages locked player: " + trial.name);
                    hit = true; hitTick = age;
                } else if (trial.moving) {
                    // A running player changes direction several times without leaving the fixture.
                    target.setPos(position(11.5 + 1.2 * Math.sin(age * 0.22), 11.5 + 1.2 * Math.cos(age * 0.22)));
                    target.setDeltaMovement(Vec3.ZERO);
                }
                if (!trial.chase && !creeper.isRemoved() && creeper.getSwellDir() > 0) {
                    if (fusePosition == null) fusePosition = creeper.position();
                    helper.assertTrue(creeper.position().subtract(fusePosition).horizontalDistance() < 0.02,
                        "high-speed countdown stops movement when pursuit is disabled");
                }
            }
            if (age == 80) {
                KnightSettings.LOGGER.info("High-speed pursuit: scenario={}, hit={}, hitTick={}, closest={}, fastestStep={}", trial.name, hit, hitTick, closest, fastestStep);
                helper.assertTrue(hit, "high-speed rider reaches and explodes on target: " + trial.name + ", closest=" + closest);
                if (trial.summoned && !trial.wall) helper.assertTrue(fastestStep > 0.7, "high speed remains fast: " + trial.name);
                completed++; clearMount();
                if (index + 1 == TRIALS.length) { KnightSettings.LOGGER.info("High-speed pursuit scenarios passed: {}", completed); cleanup(); helper.succeed(); }
            }
        } catch (RuntimeException | Error failure) { cleanup(); throw failure; }
    }
    private void clearMount() { if (rider != null) rider.discard(); if (creeper != null) creeper.discard(); }
    private void cleanup() {
        clearMount();
        if (target != null) target.server.getPlayerList().remove(target);
        if (original != null) for (var option : KnightConfig.OPTIONS) option.set(KnightSettings.get(), option.get(original));
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
    }
}

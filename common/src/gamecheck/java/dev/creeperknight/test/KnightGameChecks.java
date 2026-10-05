package dev.creeperknight.test;

import com.mojang.authlib.GameProfile;
import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.ai.SeekCreeperGoal;
import dev.creeperknight.config.KnightConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/** Compiled only with -PgameChecks; never included in distributable jars. */
public final class KnightGameChecks {
    private final GameTestHelper helper;
    private final List<Entity> entities = new ArrayList<>();
    private int assertions;
    private KnightGameChecks(GameTestHelper helper) { this.helper = helper; }
    public static void run(GameTestHelper helper) { new KnightGameChecks(helper).run(); }
    private <T extends Entity> T spawn(EntityType<T> type, int x, int z) {
        T entity = helper.spawn(type, x, 2, z); entities.add(entity); return entity;
    }
    private Zombie baby(EntityType<? extends Zombie> type, int x, int z) {
        Zombie zombie = spawn(type, x, z); zombie.setBaby(true); return zombie;
    }
    private Creeper knight(EntityType<? extends Zombie> type) {
        Creeper creeper = spawn(EntityType.CREEPER, 3, 3);
        creeper.setNoGravity(true);
        Zombie rider = baby(type, 3, 3);
        check(rider.startRiding(creeper), "baby boards creeper");
        return creeper;
    }
    private void clean() { entities.forEach(Entity::discard); entities.clear(); }
    private void check(boolean condition, String name) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(name);
        assertions++;
    }
    private void run() {
        KnightConfig config = KnightSettings.get();
        KnightConfig original = config.copy();
        boolean griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            KnightConfig defaults = new KnightConfig();
            for (var option : KnightConfig.OPTIONS) option.set(config, option.get(defaults));
            for (var type : List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN)) {
                Zombie zombie = baby(type, 3, 3);
                check(KnightLogic.eligible(zombie), "eligible: " + type);
                zombie.setBaby(false); check(!KnightLogic.eligible(zombie), "adult excluded: " + type);
            }
            clean();
            Creeper creeper = knight(EntityType.ZOMBIFIED_PIGLIN);
            Zombie rider = (Zombie)creeper.getFirstPassenger();
            double base = creeper.getAttributeValue(Attributes.MOVEMENT_SPEED);
            KnightLogic.tickCreeper(creeper);
            check(creeper.getSwellDir() == -1, "neutral rider does not ignite");
            check(Math.abs(creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) - base * 1.5) < 1e-6, "speed multiplier");
            check(rider.getNavigation() == creeper.getNavigation(), "vanilla rider navigation controls vehicle");
            Cow target = spawn(EntityType.COW, 5, 3);
            rider.setTarget(target); KnightLogic.tickCreeper(creeper);
            check(creeper.getSwellDir() == 1, "hostile target within original 3-block threshold ignites");
            target.setPos(creeper.getX() + 8, creeper.getY(), creeper.getZ());
            KnightLogic.tickCreeper(creeper);
            check(creeper.getSwellDir() == -1, "original 7-block cancellation threshold");
            config.attackOtherTargets = false; rider.setTarget(null); rider.setTarget(target);
            check(rider.getTarget() == null, "blocked target rejected by runtime mixin");
            config.attackOtherTargets = true;
            var villager = spawn(EntityType.VILLAGER, 5, 4);
            config.attackVillagers = false; rider.setTarget(villager);
            check(rider.getTarget() == null, "villagers disabled"); config.attackVillagers = true;
            var golem = spawn(EntityType.IRON_GOLEM, 5, 4);
            config.attackIronGolems = false; rider.setTarget(golem);
            check(rider.getTarget() == null, "iron golems disabled"); config.attackIronGolems = true;
            ServerPlayer nearby = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "CK-test"));
            var replies = new java.util.ArrayList<dev.creeperknight.net.ConfigService.Snapshot>();
            String beforeRequest = config.toJson();
            dev.creeperknight.net.ConfigService.handle(nearby, "", replies::add, () -> {});
            check(!replies.get(0).editable() && replies.get(0).json().equals(beforeRequest), "non-operator can read server settings");
            dev.creeperknight.net.ConfigService.handle(nearby, new KnightConfig().toJson(), replies::add, () -> {});
            check(replies.get(1).message().equals("creeperknight.status.denied") && KnightSettings.get().toJson().equals(beforeRequest),
                "non-operator cannot save or mutate server settings");
            nearby.setPos(creeper.getX() + 2, creeper.getY(), creeper.getZ());
            // Query-only player: no connection or player tick needed for proximity behavior.
            helper.getLevel().players().add(nearby);
            try {
                rider.setTarget(null); config.fullAiTakeover = true; KnightLogic.tickCreeper(creeper);
                check(creeper.getSwellDir() == -1, "neutral full takeover suppresses proximity fuse");
                for (int tick = 0; tick < 20; tick++) creeper.tick();
                check(creeper.getTarget() == null && creeper.getSwellDir() == -1,
                    "full creeper ticks suppress autonomous target acquisition while neutral rider is mounted");
                config.fullAiTakeover = false; KnightLogic.tickCreeper(creeper);
                check(creeper.getSwellDir() == 1, "partial takeover allows proximity fuse");
                check(rider.getTarget() == null, "partial takeover does not assign pursuit target to neutral rider");
                config.attackPlayers = false; KnightLogic.tickCreeper(creeper);
                check(creeper.getSwellDir() == -1, "player target switch suppresses automatic proximity fuse");
            } finally { helper.getLevel().players().remove(nearby); }
            config.attackPlayers = true; config.fullAiTakeover = true;
            rider.kill();
            check(!rider.isPassenger(), "rider death immediately dismounts");
            check(Math.abs(creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1e-6, "rider death immediately restores speed");
            clean();
            creeper = knight(EntityType.ZOMBIE); rider = (Zombie)creeper.getFirstPassenger();
            creeper.kill(); check(!rider.isPassenger() && rider.isAlive(), "mount death releases living rider");
            clean();
            explosionModes(config);
            creeper = knight(EntityType.ZOMBIFIED_PIGLIN); rider = (Zombie)creeper.getFirstPassenger();
            var igniter = helper.makeMockSurvivalPlayer(); igniter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            creeper.interact(igniter, InteractionHand.MAIN_HAND);
            check(creeper.isIgnited(), "flint and steel ignites neutral full-takeover knight");
            // Freeze movement while advancing the real vanilla fuse and explosion.
            creeper.setNoAi(true); for (int tick = 0; tick < 35 && !creeper.isRemoved(); tick++) creeper.tick();
            check(creeper.isRemoved() && rider.isRemoved(), "real explosion removes both entities");
            clean();
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
            float[] knightDamage = explosionDamage(true);
            float[] vanillaDamage = explosionDamage(false);
            check(knightDamage[0] > knightDamage[1] && knightDamage[1] > 0, "explosion damage falls off with distance");
            check(knightDamage[0] == vanillaDamage[0] && knightDamage[1] == vanillaDamage[1], "knight damage equals vanilla creeper damage");
            for (boolean destroy : new boolean[]{false, true}) {
                helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(destroy, helper.getLevel().getServer());
                helper.setBlock(3, 2, 4, Blocks.DIRT);
                creeper = knight(EntityType.ZOMBIFIED_PIGLIN); creeper.setNoAi(true); creeper.ignite();
                for (int tick = 0; tick < 35 && !creeper.isRemoved(); tick++) creeper.tick();
                check(helper.getBlockState(new BlockPos(3, 2, 4)).isAir() == destroy, "mobGriefing=" + destroy);
                helper.setBlock(3, 2, 4, Blocks.AIR); clean();
            }
            // Natural generation hooks and walking-to-mount goal, using deterministic settings.
            config.naturalChance = 1;
            Zombie natural = baby(EntityType.ZOMBIE, 8, 8);
            KnightLogic.naturalSpawn(natural, helper.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL);
            check(natural.getVehicle() instanceof Creeper, "natural spawn creates mount");
            entities.add(natural.getVehicle()); clean();
            config.seekChance = 1; config.seekIntervalTicks = 20;
            Zombie seeker = baby(EntityType.ZOMBIE, 3, 3);
            Creeper mount = spawn(EntityType.CREEPER, 4, 3);
            var goal = new SeekCreeperGoal(seeker);
            check(goal.canUse(), "search chooses free visible creeper"); goal.start(); goal.tick();
            check(seeker.getVehicle() == mount, "search boards creeper within boarding distance");
            KnightSettings.LOGGER.info("Creeper Knight runtime assertions passed: {}", assertions);
            helper.succeed();
        } finally {
            clean();
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
            for (var option : KnightConfig.OPTIONS) option.set(config, option.get(original));
        }
    }
    private void explosionModes(KnightConfig config) {
        config.instantExplosion = true;
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
            new GameProfile(UUID.randomUUID(), "CK-fuse"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
            new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
        helper.getLevel().players().add(player);
        try {
            Creeper creeper = knight(EntityType.ZOMBIFIED_PIGLIN);
            Zombie rider = (Zombie)creeper.getFirstPassenger();
            creeper.setNoAi(true);
            player.setPos(creeper.getX() + 1.5, creeper.getY(), creeper.getZ());
            creeper.tick();
            check(!creeper.isRemoved() && creeper.getSwellDir() == -1, "instant mode respects neutral full takeover");
            config.fullAiTakeover = false;
            player.setPos(creeper.getX() + 2.01, creeper.getY(), creeper.getZ());
            for (int tick = 0; tick < 40; tick++) creeper.tick();
            check(!creeper.isRemoved() && creeper.getSwellDir() == -1, "instant mode never starts vanilla countdown outside 2 blocks");
            config.attackPlayers = false;
            player.setPos(creeper.getX() + 2, creeper.getY(), creeper.getZ());
            creeper.tick();
            check(!creeper.isRemoved(), "instant mode respects disabled player attacks");
            config.attackPlayers = true;
            creeper.tick();
            check(creeper.isRemoved() && rider.isRemoved(), "instant mode explodes on the very first tick at exactly 2 blocks");
            clean();

            config.fullAiTakeover = true;
            creeper = knight(EntityType.ZOMBIE); rider = (Zombie)creeper.getFirstPassenger(); creeper.setNoAi(true);
            player.setPos(creeper.getX() + 2.5, creeper.getY(), creeper.getZ()); rider.setTarget(player);
            config.instantExplosion = false;
            for (int tick = 0; tick < 10; tick++) creeper.tick();
            check(!creeper.isRemoved() && creeper.getSwellDir() == 1, "ordinary mode retains vanilla countdown at 2.5 blocks");
            config.instantExplosion = true;
            for (int tick = 0; tick < 40; tick++) creeper.tick();
            check(!creeper.isRemoved() && creeper.getSwellDir() == -1, "enabling instant mode cancels an existing automatic countdown");
            config.instantExplosionDistance = 3;
            creeper.tick();
            check(creeper.isRemoved() && rider.isRemoved(), "custom instant distance takes effect immediately"); clean();

            config.instantExplosionDistance = 2;
            creeper = knight(EntityType.ZOMBIE); rider = (Zombie)creeper.getFirstPassenger(); creeper.setNoAi(true);
            player.setPos(creeper.getX() + 1.5, creeper.getY(), creeper.getZ()); rider.setTarget(player);
            var igniter = helper.makeMockSurvivalPlayer();
            igniter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            creeper.interact(igniter, InteractionHand.MAIN_HAND); creeper.tick();
            check(creeper.isIgnited() && !creeper.isRemoved(), "flint and steel uses countdown even inside instant threshold");
            for (int tick = 0; tick < 35 && !creeper.isRemoved(); tick++) creeper.tick();
            check(creeper.isRemoved() && rider.isRemoved(), "manual ignition still completes in instant mode"); clean();

            for (boolean destroy : new boolean[]{false, true}) {
                helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(destroy, helper.getLevel().getServer());
                helper.setBlock(3, 2, 4, Blocks.DIRT);
                creeper = knight(EntityType.ZOMBIE); rider = (Zombie)creeper.getFirstPassenger();
                player.setPos(creeper.getX() + 1.5, creeper.getY(), creeper.getZ()); rider.setTarget(player);
                creeper.tick();
                check(creeper.isRemoved() && helper.getBlockState(new BlockPos(3, 2, 4)).isAir() == destroy,
                    "instant explosion obeys mobGriefing=" + destroy);
                helper.setBlock(3, 2, 4, Blocks.AIR); clean();
            }
        } finally {
            helper.getLevel().players().remove(player);
            config.instantExplosion = false; config.instantExplosionDistance = 2;
            config.fullAiTakeover = true; config.attackPlayers = true;
        }
    }
    private float[] explosionDamage(boolean mounted) {
        Creeper creeper = mounted ? knight(EntityType.ZOMBIFIED_PIGLIN) : spawn(EntityType.CREEPER, 3, 3);
        creeper.setNoAi(true); creeper.setNoGravity(true);
        Cow near = spawn(EntityType.COW, 5, 3); Cow far = spawn(EntityType.COW, 8, 3);
        for (Cow cow : new Cow[]{near, far}) {
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200);
        }
        creeper.ignite(); for (int tick = 0; tick < 35 && !creeper.isRemoved(); tick++) creeper.tick();
        float[] damage = {200 - near.getHealth(), 200 - far.getHealth()}; clean(); return damage;
    }
    public static void movement(GameTestHelper helper) {
        helper.getLevel().setDayTime(13000); // Exclude the zombie's daytime sun-avoidance goal from this navigation fixture.
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        Creeper creeper = helper.spawn(EntityType.CREEPER, 3, 2, 3);
        Zombie rider = helper.spawn(EntityType.ZOMBIE, 3, 2, 3); rider.setBaby(true);
        Cow target = helper.spawn(EntityType.COW, 11, 2, 3); target.setNoAi(true);
        rider.startRiding(creeper); rider.setTarget(target);
        double start = creeper.getX();
        helper.runAtTickTime(35, () -> {
            try {
                KnightSettings.LOGGER.info("Movement test: start={}, end={}, ticks={}, riderTarget={}, path={}",
                    start, creeper.getX(), creeper.tickCount, rider.getTarget(), creeper.getNavigation().getPath());
                helper.assertTrue(creeper.getX() > start + 0.5, "rider's vanilla melee AI moves creeper towards target");
                helper.assertTrue(creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.25, "mounted movement is faster than vanilla creeper");
                helper.succeed();
            } finally { rider.discard(); creeper.discard(); target.discard(); }
        });
    }
    public static void fuseMovement(GameTestHelper helper) {
        helper.getLevel().setDayTime(13000);
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        Creeper creeper = helper.spawn(EntityType.CREEPER, 3, 2, 3);
        Zombie rider = helper.spawn(EntityType.ZOMBIE, 3, 2, 3); rider.setBaby(true);
        Cow target = helper.spawn(EntityType.COW, 6, 2, 3); target.setNoAi(true); target.setInvulnerable(true);
        target.setPos(creeper.getX() + 2.7, creeper.getY(), creeper.getZ());
        rider.startRiding(creeper); rider.setTarget(target);
        helper.runAtTickTime(3, () -> {
            var config = KnightSettings.get();
            creeper.getNavigation().moveTo(target, 1);
            config.chaseDuringFuse = false; KnightLogic.tickCreeper(creeper);
            helper.assertTrue(creeper.getNavigation().isDone(), "turning off fuse pursuit stops navigation");
            config.chaseDuringFuse = true;
            creeper.getNavigation().moveTo(target, 1); KnightLogic.tickCreeper(creeper);
            helper.assertTrue(!creeper.getNavigation().isDone(), "fuse pursuit retains rider navigation");
        });
        double start = creeper.getX();
        helper.runAtTickTime(12, () -> {
            try {
                KnightSettings.LOGGER.info("Fuse movement test: start={}, end={}, fuse={}", start, creeper.getX(), creeper.getSwellDir());
                helper.assertTrue(!creeper.isRemoved() && creeper.getSwellDir() == 1, "real vanilla countdown remains active while moving");
                helper.assertTrue(creeper.getX() > start + 0.1, "knight actually advances towards its target during countdown");
                helper.succeed();
            } finally { rider.discard(); creeper.discard(); target.discard(); }
        });
    }
}

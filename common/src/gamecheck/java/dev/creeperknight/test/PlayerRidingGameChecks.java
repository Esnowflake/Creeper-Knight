package dev.creeperknight.test;

import com.mojang.authlib.GameProfile;
import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.PlayerRiding;
import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.mixin.CreeperAccess;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real server ticks and vanilla explosion damage, after the existing pursuit fixtures finish. */
public final class PlayerRidingGameChecks {
    private final GameTestHelper helper;
    private KnightConfig original;
    private ServerPlayer player;
    private Creeper mount, manual, instant, timed;
    private boolean griefing;
    private PlayerRidingGameChecks(GameTestHelper helper) { this.helper = helper; }
    public static void run(GameTestHelper helper) {
        var test = new PlayerRidingGameChecks(helper);
        helper.runAtTickTime(820, test::start);
        helper.runAtTickTime(885, test::mountChecks);
        helper.runAtTickTime(888, () -> {
            test.check(!test.mount.isRemoved() && test.mount.getSwellDir() <= 0, "mount cancels a near-complete automatic fuse");
            test.check(KnightLogic.suppressGoals(test.mount) && test.mount.getTarget() == null, "player takes over creeper AI");
            PlayerRiding.handle(test.player, new PlayerRiding.Input(test.mount.getId(), true));
            test.check(!test.player.isUsingItem() && test.player.getInventory().countItem(Items.ARROW) == 1, "chord cancels an ongoing bow draw without firing");
        });
        helper.runAtTickTime(894, () -> {
            test.check(test.remaining(test.mount) > 1.5F && test.remaining(test.mount) < 2, "server countdown advances");
            PlayerRiding.handle(test.player, new PlayerRiding.Input(test.mount.getId(), false));
            test.check(test.remaining(test.mount) == -1, "release cancels charge");
            PlayerRiding.handle(test.player, new PlayerRiding.Input(test.mount.getId(), true));
            test.check(test.remaining(test.mount) == 2, "new hold restarts full countdown");
            PlayerRiding.handle(test.player, new PlayerRiding.Input(test.mount.getId() + 1, false));
            test.check(test.remaining(test.mount) == 2, "stale mount packet cannot cancel current charge");
        });
        helper.runAtTickTime(906, () -> {
            test.check(test.remaining(test.mount) == -1 && !test.mount.isRemoved(), "missing heartbeats fail closed");
            PlayerRiding.handle(test.player, new PlayerRiding.Input(test.mount.getId(), true));
            test.player.stopRiding(); KnightLogic.tickCreeper(test.mount);
            test.check(test.remaining(test.mount) == -1 && !KnightLogic.suppressGoals(test.mount), "dismount immediately restores AI and cancels charge");
            test.check(Math.abs(test.mount.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.25) < 0.0001, "dismounted creeper retains vanilla movement attribute");
            test.mount.discard(); test.ignitionChecks();
        });
        helper.runAtTickTime(938, () -> {
            test.check(test.manual.isRemoved(), "manual ignition continues after release and dismount");
            test.prepareArmor();
            test.timed = test.create(); test.timed.interact(test.player, InteractionHand.MAIN_HAND);
            test.player.setPos(test.timed.getX(), test.timed.getY() + test.timed.getPassengersRidingOffset(), test.timed.getZ());
            KnightSettings.get().instantExplosion = true; KnightSettings.get().instantExplosionDistance = 16;
            KnightSettings.get().playerChargeSeconds = 0.35;
            PlayerRiding.handle(test.player, new PlayerRiding.Input(test.timed.getId(), true));
        });
        for (int i = 939; i <= 945; i++) helper.runAtTickTime(i, () -> PlayerRiding.handle(test.player, new PlayerRiding.Input(test.timed.getId(), true)));
        helper.runAtTickTime(944, () -> test.check(!test.timed.isRemoved(), "fractional charge waits for its deadline even when mob instant mode is enabled"));
        helper.runAtTickTime(946, () -> {
            test.check(test.timed.isRemoved() && test.player.getHealth() < 20, "continued hold reaches server deadline and causes vanilla damage");
            test.check(test.player.isAlive(), "blast-protected rider survives timed explosion");
            test.player.setHealth(20); test.player.invulnerableTime = 0; test.explosionChecks();
        });
        helper.runAtTickTime(948, () -> {
            try {
                test.check(!test.player.isRemoved() && test.player.isAlive(), "blast-protected rider can survive the vanilla explosion");
                test.check(test.player.getHealth() < 20, "rider takes real vanilla explosion damage");
                test.check(test.helper.getBlockState(new net.minecraft.core.BlockPos(5, 1, 5)).is(Blocks.STONE), "mobGriefing false protects blocks");
                KnightSettings.LOGGER.info("Player riding GameTest passed: mounting, automatic/manual fuse, takeover, charge reset, timeout, stale packets, independent speed, charged mounting, zero seconds, armor survival, mobGriefing");
                helper.succeed();
            } finally { test.cleanup(); }
        });
    }
    private void check(boolean condition, String message) { helper.assertTrue(condition, message); }
    private float remaining(Creeper creeper) { return ((PlayerRiding.Access)creeper).creeperknight$remaining(); }
    private Creeper create() { return helper.spawn(EntityType.CREEPER, 5, 2, 5); }
    private void start() {
        original = KnightSettings.get().copy(); griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "PlayerRider"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        channel.attr(io.netty.util.AttributeKey.<String>valueOf("fml:netversion")).set("FML3");
        channel.pipeline().addLast("packet_handler", connection); channel.pipeline().fireChannelActive();
        player.server.getPlayerList().placeNewPlayer(connection, player); player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(5.5, 2, 5.5)));
        var cfg = KnightSettings.get(); cfg.playerRidingEnabled = true; cfg.playerChargeSeconds = 2;
        cfg.playerSpeedMultiplier = 1; cfg.playerAllowIgnition = true; cfg.seekMounts = false;
    }
    private void mountChecks() {
        Creeper occupied = create(); Zombie zombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 5); zombie.setBaby(true); zombie.startRiding(occupied);
        occupied.interact(player, InteractionHand.MAIN_HAND);
        check(player.getVehicle() == null && occupied.getFirstPassenger() == zombie, "occupied creeper refuses player"); zombie.discard(); occupied.discard();
        Creeper ignited = create(); ignited.ignite(); ignited.interact(player, InteractionHand.MAIN_HAND);
        check(player.getVehicle() == null, "manually ignited creeper refuses player"); ignited.discard();
        Creeper killed = create(); killed.interact(player, InteractionHand.MAIN_HAND);
        killed.hurt(killed.damageSources().generic(), 1000);
        check(player.getVehicle() == null && player.isAlive(), "killing mount immediately dismounts surviving player"); killed.discard();
        mount = create(); mount.setSwellDir(1); ((CreeperAccess)mount).creeperknight$setSwell(29);
        ((dev.creeperknight.scepter.SummonedKnight.Access)mount).creeperknight$setSummon(new dev.creeperknight.scepter.SummonedKnight(UUID.randomUUID(), helper.getLevel().getGameTime()));
        mount.interact(player, InteractionHand.MAIN_HAND);
        check(player.getVehicle() == mount && mount.getControllingPassenger() == player, "right-click mounts and gives control");
        check(dev.creeperknight.scepter.ScepterLogic.data(mount) == null, "player takeover clears abandoned scepter target and deadline");
        check(((PlayerRiding.Access)mount).creeperknight$rideSpeed() == PlayerRiding.BASE_SPEED, "fixed riding baseline");
        var cfg = KnightSettings.get(); cfg.speedMultiplier = 4; cfg.wandSpeedMultiplier = 10; cfg.playerSpeedMultiplier = 10;
        KnightLogic.tickCreeper(mount);
        check(((PlayerRiding.Access)mount).creeperknight$rideSpeed() == 3.75F, "independent player maximum speed");
        cfg.playerSpeedMultiplier = 1;
        cfg.playerRidingEnabled = false; PlayerRiding.handle(player, new PlayerRiding.Input(mount.getId(), true));
        check(remaining(mount) == -1, "disabled player riding rejects charge packets"); cfg.playerRidingEnabled = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.getInventory().add(new ItemStack(Items.ARROW)); player.startUsingItem(InteractionHand.MAIN_HAND);
    }
    private void ignitionChecks() {
        manual = create(); manual.interact(player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        KnightSettings.get().playerAllowIgnition = false;
        manual.interact(player, InteractionHand.MAIN_HAND);
        check(!manual.isIgnited(), "setting blocks mounted flint ignition");
        KnightSettings.get().playerAllowIgnition = true;
        manual.interact(player, InteractionHand.MAIN_HAND);
        check(manual.isIgnited(), "single right-click permits manual ignition");
        PlayerRiding.handle(player, new PlayerRiding.Input(manual.getId(), false)); player.stopRiding();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setPos(helper.absoluteVec(new Vec3(14, 2, 14)));
    }
    private void prepareArmor() {
        for (var item : new net.minecraft.world.item.Item[]{Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET}) {
            ItemStack stack = new ItemStack(item); stack.enchant(Enchantments.BLAST_PROTECTION, 4);
            player.setItemSlot(((net.minecraft.world.item.ArmorItem)item).getEquipmentSlot(), stack);
        }
        player.setHealth(20); player.invulnerableTime = 0;
    }
    private void explosionChecks() {
        Creeper charged = create(); var tag = new net.minecraft.nbt.CompoundTag(); charged.addAdditionalSaveData(tag); tag.putBoolean("powered", true); charged.readAdditionalSaveData(tag);
        charged.interact(player, InteractionHand.MAIN_HAND);
        check(charged.isPowered() && player.getVehicle() == charged, "charged creeper permits mounting"); player.stopRiding(); charged.discard();
        instant = create(); instant.interact(player, InteractionHand.MAIN_HAND);
        player.setPos(instant.getX(), instant.getY() + instant.getPassengersRidingOffset(), instant.getZ());
        KnightSettings.get().playerChargeSeconds = 0;
        PlayerRiding.handle(player, new PlayerRiding.Input(instant.getId(), true));
        check(instant.isRemoved(), "zero charge explodes immediately");
    }
    private void cleanup() {
        if (player != null) { player.stopRiding(); player.server.getPlayerList().remove(player); player.discard(); }
        for (Creeper entity : new Creeper[]{mount, manual, instant, timed}) if (entity != null) entity.discard();
        if (original != null) for (var option : KnightConfig.OPTIONS) option.set(KnightSettings.get(), option.get(original));
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
    }
}

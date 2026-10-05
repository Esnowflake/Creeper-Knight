package dev.creeperknight.test;

import com.mojang.authlib.GameProfile;
import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.net.ConfigService;
import dev.creeperknight.scepter.FriendshipScepter;
import dev.creeperknight.scepter.ScepterLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real server item use, recipe matching, player messages, UUID tracking and explosions. Dev only. */
public final class ScepterGameChecks {
    private final GameTestHelper helper;
    private final List<Entity> entities = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private int assertions;
    private ScepterGameChecks(GameTestHelper helper) { this.helper = helper; }
    public static void run(GameTestHelper helper) { new ScepterGameChecks(helper).run(); }
    private Vec3 pos(int x, int z) { return helper.absoluteVec(new Vec3(x + 0.5, 2, z + 0.5)); }
    private void check(boolean condition, String message) { helper.assertTrue(condition, message); assertions++; }
    private ServerPlayer player(String name, int x, List<Component> messages) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection(), player);
        player.connection = new ServerGamePacketListenerImpl(player.server, connection(), player) {
            @Override public void send(Packet<?> packet) { if (packet instanceof ClientboundSystemChatPacket chat) messages.add(chat.content()); }
            @Override public void send(Packet<?> packet, net.minecraft.network.PacketSendListener listener) { send(packet); }
        };
        player.setGameMode(GameType.SURVIVAL); player.setPos(pos(x, 3)); player.setInvulnerable(true); players.add(player); return player;
    }
    private static boolean has(List<Component> messages, String key) {
        return messages.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("creeperknight.wand." + key));
    }
    private static Connection connection() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        channel.attr(io.netty.util.AttributeKey.<String>valueOf("fml:netversion")).set("FML3");
        channel.pipeline().addLast("packet_handler", connection); channel.pipeline().fireChannelActive();
        return connection;
    }
    private Creeper create(UUID target, int x, int z) {
        Creeper creeper = ScepterLogic.create(helper.getLevel(), pos(x, z), target);
        check(creeper != null, "scepter creates a mounted knight");
        entities.add(creeper); entities.add(creeper.getFirstPassenger()); return creeper;
    }
    private void run() {
        KnightConfig original = KnightSettings.get().copy();
        boolean griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            var config = KnightSettings.get();
            var defaults = new KnightConfig(); for (var option : KnightConfig.OPTIONS) option.set(config, option.get(defaults));
            for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) helper.setBlock(x, 1, z, Blocks.STONE);
            List<Component> casterMessages = new ArrayList<>(), targetMessages = new ArrayList<>(), observerMessages = new ArrayList<>();
            ServerPlayer caster = player("Caster", 3, casterMessages), target = player("Target", 10, targetMessages), observer = player("Observer", 13, observerMessages);
            caster.setYRot(-90); caster.setXRot(0);
            FriendshipScepter item = (FriendshipScepter)BuiltInRegistries.ITEM.get(new ResourceLocation("creeperknight", "friendship_scepter"));
            ItemStack wand = new ItemStack(item); caster.setItemInHand(InteractionHand.MAIN_HAND, wand);
            check(ScepterLogic.aimedPlayer(caster) == target, "server ray cast selects aimed player");
            helper.setBlock(7, 3, 3, Blocks.STONE);
            check(ScepterLogic.aimedPlayer(caster) == null, "walls block initial locking"); helper.setBlock(7, 3, 3, Blocks.AIR);
            item.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND); item.releaseUsing(wand, helper.getLevel(), caster, 71996);
            check(wand.getTag().getUUID(ScepterLogic.TARGET).equals(target.getUUID()), "short right-click stores target UUID");
            check(target.hasEffect(MobEffects.GLOWING), "lock applies vanilla glowing outline");
            check(has(casterMessages, "locked") && has(targetMessages, "warning") && has(observerMessages, "public"), "all three chat notices delivered");
            var contents = (TranslatableContents)casterMessages.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().endsWith("wand.locked")).findFirst().orElseThrow().getContents();
            check(contents.getArgs()[0].equals("Target"), "private notice uses actual player name");
            for (int mode : new int[]{1, 2}) {
                casterMessages.clear(); targetMessages.clear(); observerMessages.clear(); config.wandNotifications = mode;
                ScepterLogic.lock(caster, target, wand);
                check(targetMessages.isEmpty(), "target notice hidden in mode=" + mode);
                check(mode == 1 ? has(casterMessages, "locked") && has(observerMessages, "public") : casterMessages.isEmpty() && observerMessages.isEmpty(), "other chat visibility mode=" + mode);
            }
            config.wandNotifications = 0;
            var grid = new TransientCraftingContainer(new AbstractContainerMenu(MenuType.CRAFTING, 0) {
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
            }, 3, 3);
            grid.setItem(1, new ItemStack(Items.CREEPER_HEAD)); grid.setItem(4, new ItemStack(Items.DIAMOND)); grid.setItem(7, new ItemStack(Items.GOLD_INGOT));
            var recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("creeperknight", "friendship_scepter")).orElseThrow();
            @SuppressWarnings("unchecked") var crafting = (net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer>)recipe;
            check(crafting.matches(grid, helper.getLevel()) && crafting.assemble(grid, helper.getLevel().registryAccess()).is(item), "actual head-diamond-gold recipe crafts the scepter");
            var result = new net.minecraft.world.inventory.ResultContainer(); result.setItem(0, new ItemStack(item));
            var resultSlot = new net.minecraft.world.inventory.ResultSlot(caster, grid, result, 0, 0, 0);
            config.wandCraftable = false; check(!crafting.matches(grid, helper.getLevel()), "server disables survival recipe");
            check(!resultSlot.mayPickup(caster), "disabled recipe also blocks taking an already cached crafting result");
            config.wandCraftable = true; check(resultSlot.mayPickup(caster), "enabled recipe allows result pickup");
            caster.getInventory().setItem(1, new ItemStack(Items.GUNPOWDER, 2));
            item.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
            item.onUseTick(helper.getLevel(), caster, wand, 71981);
            check(caster.getInventory().getItem(1).getCount() == 2, "less than one second does not summon");
            item.onUseTick(helper.getLevel(), caster, wand, 71980);
            check(caster.getInventory().getItem(1).getCount() == 1 && caster.getCooldowns().isOnCooldown(item), "long press summons and charges one gunpowder plus cooldown");
            item.onUseTick(helper.getLevel(), caster, wand, 71900);
            check(caster.getInventory().getItem(1).getCount() == 1, "continuous hold cannot repeatedly summon");
            var spawned = helper.getLevel().getEntitiesOfClass(Creeper.class, caster.getBoundingBox().inflate(16), c -> ScepterLogic.data(c) != null);
            for (Creeper c : spawned) { entities.add(c); entities.add(c.getFirstPassenger()); }
            check(!spawned.isEmpty(), "long press spawned a real marked creeper");
            check(!ScepterLogic.summon(caster, wand) && caster.getInventory().getItem(1).getCount() == 1, "cooldown prevents further summon without charging");
            caster.getCooldowns().removeCooldown(item); caster.getInventory().setItem(1, ItemStack.EMPTY);
            check(!ScepterLogic.summon(caster, wand), "survival summon without gunpowder is denied");
            for (int type = 0; type < 5; type++) {
                config.wandRiderType = type;
                Creeper c = create(target.getUUID(), 3, 8);
                check(c.getFirstPassenger().getType() == ScepterLogic.RIDERS[type], "configured rider type=" + type);
                c.getFirstPassenger().discard(); c.discard();
            }
            config.wandRiderType = 4;
            Creeper creeper = create(target.getUUID(), 3, 8); Zombie rider = (Zombie)creeper.getFirstPassenger();
            KnightLogic.tickCreeper(creeper); check(rider.getTarget() == target, "summoned zombified piglin deliberately pursues locked player");
            rider.setTarget(observer); check(rider.getTarget() == target, "summoned rider rejects other players as targets");
            long deadline = ScepterLogic.data(creeper).expiresAt();
            target.setHealth(0); KnightLogic.tickCreeper(creeper);
            check(rider.getTarget() == null && creeper.getNavigation().isDone(), "target death suspends pursuit");
            check(ScepterLogic.data(creeper).expiresAt() == deadline, "target death leaves expiry unchanged");
            var oldTarget = target;
            target = caster.server.getPlayerList().respawn(target, false); players.remove(oldTarget); players.add(target);
            target.setPos(pos(10, 3)); target.setGameMode(GameType.SURVIVAL); target.setInvulnerable(true);
            KnightLogic.tickCreeper(creeper);
            check(target != oldTarget && rider.getTarget() == target && target.getUUID().equals(oldTarget.getUUID()), "new respawned player entity resumes UUID pursuit");
            var nether = caster.server.getLevel(net.minecraft.world.level.Level.NETHER);
            if (nether != null) {
                target.teleportTo(nether, 0, 80, 0, 0, 0); KnightLogic.tickCreeper(creeper);
                check(rider.getTarget() == null && ScepterLogic.data(creeper).expiresAt() == deadline, "dimension change stops pursuit without resetting expiry");
                target.teleportTo(helper.getLevel(), pos(10,3).x, pos(10,3).y, pos(10,3).z, 0, 0); KnightLogic.tickCreeper(creeper);
                check(rider.getTarget() == target, "returning to the dimension resumes pursuit");
            }
            config.wandSpeedMultiplier = 10; KnightLogic.tickCreeper(creeper);
            check(Math.abs(creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.25 * config.speedMultiplier * 10) < 1e-6, "scepter-only speed allows ten times ordinary knight speed");
            Creeper ordinary = helper.spawn(EntityType.CREEPER, 6, 2, 8); entities.add(ordinary);
            Zombie baby = helper.spawn(EntityType.ZOMBIE, 6, 2, 8); entities.add(baby); baby.setBaby(true); baby.startRiding(ordinary); KnightLogic.tickCreeper(ordinary);
            check(Math.abs(ordinary.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.25 * config.speedMultiplier) < 1e-6, "ordinary knight speed remains unchanged");
            var saved = new net.minecraft.nbt.CompoundTag(); creeper.saveWithoutId(saved);
            Creeper restored = EntityType.CREEPER.create(helper.getLevel()); restored.load(saved);
            check(ScepterLogic.data(restored).target().equals(target.getUUID()) && ScepterLogic.data(restored).expiresAt() == deadline, "save and load persist target UUID and original expiry");
            creeper.getFirstPassenger().discard(); creeper.discard();
            config.wandSpeedMultiplier = 1;
            for (boolean allow : new boolean[]{false, true}) for (boolean gamerule : new boolean[]{false, true}) {
                config.wandBlockDamage = allow;
                helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(gamerule, caster.server);
                helper.setBlock(3, 2, 9, Blocks.DIRT);
                Creeper expired = create(target.getUUID(), 3, 8); MobPair pair = new MobPair(expired, expired.getFirstPassenger());
                ScepterLogic.mark(expired, target.getUUID(), caster.server.overworld().getGameTime()); expired.tick();
                check(expired.isRemoved() && pair.rider().isRemoved(), "timer expiry immediately removes both entities");
                check(helper.getBlockState(new net.minecraft.core.BlockPos(3,2,9)).isAir() == (allow && gamerule), "scepter block protection: option=" + allow + ", mobGriefing=" + gamerule);
                helper.setBlock(3, 2, 9, Blocks.AIR);
            }
            config.wandBlockDamage = false;
            Creeper killed = create(target.getUUID(), 3, 8); Entity survivor = killed.getFirstPassenger();
            killed.hurt(killed.damageSources().generic(), 100); ScepterLogic.mark(killed, target.getUUID(), caster.server.overworld().getGameTime()); killed.tick();
            check(!killed.isAlive() && !survivor.isPassenger() && survivor.isAlive(), "summoned creeper can be killed and releases its rider");
            check(!ScepterLogic.tickDeadline(killed), "killed summon never expires into an explosion");
            config.wandLifetimeSeconds = 0;
            Creeper zero = create(target.getUUID(), 3, 12); zero.tick(); check(zero.isRemoved(), "zero-second summon explodes immediately");
            config.wandLifetimeSeconds = 120;
            Creeper maximum = create(target.getUUID(), 3, 12);
            check(ScepterLogic.data(maximum).expiresAt() - caster.server.overworld().getGameTime() == 2400, "120-second lifetime captures 2400 ticks");
            var replies = new ArrayList<ConfigService.Snapshot>();
            caster.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(caster.getGameProfile(), 2, false)); ConfigService.clear();
            KnightConfig enabled = KnightSettings.get().copy(); enabled.wandBlockDamage = true;
            ConfigService.handle(caster, ConfigService.confirmBlockDamage(enabled.toJson()), replies::add, () -> {});
            check(!KnightSettings.get().wandBlockDamage && replies.get(0).message().endsWith("confirmblocks"), "forged confirmation without initial request cannot enable block damage");
            ConfigService.handle(caster, ConfigService.confirmBlockDamage(enabled.toJson()), replies::add, () -> {});
            check(KnightSettings.get().wandBlockDamage && replies.get(1).message().endsWith("saved"), "second confirmed request enables block damage");
            check(!KnightSettings.canEdit(observer), "normal player cannot edit any mod setting");
            KnightSettings.LOGGER.info("Scepter runtime assertions passed: {}", assertions); helper.succeed();
        } catch (Exception e) { throw new RuntimeException(e); }
        finally {
            entities.forEach(e -> { if (e != null) e.discard(); });
            for (ServerPlayer player : players) { player.server.getPlayerList().deop(player.getGameProfile()); player.server.getPlayerList().remove(player); }
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
            try { KnightSettings.save(original); } catch (Exception e) { throw new RuntimeException(e); }
            ConfigService.clear();
        }
    }
    private record MobPair(Creeper creeper, Entity rider) {}
    public static void movement(GameTestHelper helper) {
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        ServerPlayer target = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "PursuitTarget"));
        target.server.getPlayerList().placeNewPlayer(connection(), target);
        target.setGameMode(GameType.SURVIVAL); target.setInvulnerable(true);
        target.setPos(helper.absoluteVec(new Vec3(10.5, 2, 3.5)));
        Creeper creeper = ScepterLogic.create(helper.getLevel(), helper.absoluteVec(new Vec3(3.5,2,3.5)), target.getUUID());
        helper.assertTrue(creeper != null, "movement test creates summon");
        Entity rider = creeper.getFirstPassenger(); double start = creeper.getX();
        helper.runAtTickTime(20, () -> {
            try {
                KnightSettings.LOGGER.info("Scepter movement test: start={}, end={}, lockedTarget={}", start, creeper.getX(), creeper.getTarget());
                helper.assertTrue(creeper.isAlive() && creeper.getX() > start + 0.5, "summoned knight actually pursues locked player");
                helper.assertTrue(creeper.getTarget() == target, "pursuit keeps the specified player target"); helper.succeed();
            } finally { rider.discard(); creeper.discard(); target.server.getPlayerList().remove(target); }
        });
    }
}

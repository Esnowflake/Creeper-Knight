package dev.creeperknight;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;

public final class KnightCommands {
    private KnightCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Consumer<MinecraftServer> broadcast) {
        var root = Commands.literal("creeperknight")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.translatable("creeperknight.command.help"), false); return 1;
            })
            .then(Commands.literal("reload").requires(s -> s.hasPermission(2))
                .executes(context -> reload(context.getSource(), broadcast, false))
                .then(Commands.literal("confirm").executes(context -> reload(context.getSource(), broadcast, true))));
        var summon = Commands.literal("summon").requires(s -> s.hasPermission(2));
        for (var type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN}) {
            String name = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
            summon.then(Commands.literal(name).executes(context -> {
                var source = context.getSource(); var level = source.getLevel(); var pos = source.getPosition();
                Zombie rider = (Zombie)type.create(level); Creeper creeper = EntityType.CREEPER.create(level);
                if (rider == null || creeper == null) return 0;
                rider.setBaby(true); rider.setPersistenceRequired();
                creeper.moveTo(pos.x, pos.y, pos.z, 0, 0); rider.moveTo(pos.x, pos.y, pos.z, 0, 0);
                if (!rider.startRiding(creeper)) return 0;
                level.addFreshEntityWithPassengers(creeper);
                source.sendSuccess(() -> Component.translatable("creeperknight.command.summoned", name), true); return 1;
            }));
        }
        dispatcher.register(root.then(summon));
    }
    private static int reload(CommandSourceStack source, Consumer<MinecraftServer> broadcast, boolean confirmed) {
        try {
            if (!KnightSettings.reload(source.getServer(), confirmed)) { source.sendFailure(Component.translatable("creeperknight.command.confirmreload")); return 0; }
            broadcast.accept(source.getServer()); source.sendSuccess(() -> Component.translatable("creeperknight.command.reloaded"), true); return 1;
        } catch (Exception e) { source.sendFailure(Component.translatable("creeperknight.status.invalid")); return 0; }
    }
}

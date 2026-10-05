package dev.creeperknight.forge;

import dev.creeperknight.KnightSettings;
import dev.creeperknight.net.ConfigService;
import dev.creeperknight.scepter.FriendshipScepter;
import dev.creeperknight.scepter.ScepterRecipe;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;

@Mod(KnightSettings.MOD_ID)
public final class CreeperKnightForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KnightSettings.MOD_ID);
    private static final RegistryObject<Item> SCEPTER = ITEMS.register("friendship_scepter", FriendshipScepter::new);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, KnightSettings.MOD_ID);
    private static final RegistryObject<RecipeSerializer<?>> RECIPE = RECIPES.register("scepter", ScepterRecipe.Serializer::new);
    public CreeperKnightForge() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus); RECIPES.register(modBus); ScepterRecipe.serializer = RECIPE;
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(SCEPTER.get());
        });
        ForgeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(this::start);
        MinecraftForge.EVENT_BUS.addListener(this::stop);
        MinecraftForge.EVENT_BUS.addListener(this::login);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) dev.creeperknight.scepter.ScepterTickets.tick(event.getServer());
        });
    }
    private void start(ServerAboutToStartEvent event) { KnightSettings.load(event.getServer()); }
    private void stop(ServerStoppedEvent event) { ConfigService.clear(); dev.creeperknight.scepter.ScepterTickets.clear(); }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ForgeNetwork.send(player, ConfigService.snapshot(player, ""));
    }
    private void commands(RegisterCommandsEvent event) {
        dev.creeperknight.KnightCommands.register(event.getDispatcher(), ForgeNetwork::broadcast);
    }
}

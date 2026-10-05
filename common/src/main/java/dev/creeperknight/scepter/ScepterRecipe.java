package dev.creeperknight.scepter;

import com.google.gson.JsonObject;
import dev.creeperknight.KnightSettings;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public final class ScepterRecipe extends ShapedRecipe {
    public static Supplier<RecipeSerializer<?>> serializer;
    public ScepterRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(),
            recipe.getResultItem(RegistryAccess.EMPTY), recipe.showNotification());
    }
    @Override public boolean matches(CraftingContainer grid, Level level) {
        boolean allowed = level.isClientSide ? clientCraftable() : KnightSettings.get().wandCraftable;
        return allowed && super.matches(grid, level);
    }
    private static boolean clientCraftable() {
        var config = dev.creeperknight.client.KnightClient.config; return config == null || config.wandCraftable;
    }
    @Override public RecipeSerializer<?> getSerializer() { return serializer.get(); }
    public static final class Serializer implements RecipeSerializer<ScepterRecipe> {
        private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();
        @Override public ScepterRecipe fromJson(ResourceLocation id, JsonObject json) { return new ScepterRecipe(vanilla.fromJson(id, json)); }
        @Override public ScepterRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) { return new ScepterRecipe(vanilla.fromNetwork(id, buffer)); }
        @Override public void toNetwork(FriendlyByteBuf buffer, ScepterRecipe recipe) { vanilla.toNetwork(buffer, recipe); }
    }
}

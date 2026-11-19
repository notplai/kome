package net.notplai.kome.cosmicray;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** A shaped recipe whose Cosmic Ray Beacon takes the colour of whichever dye went into it. */
public class CosmicRayBeaconRecipe extends ShapedRecipe {
    public static final MapCodec<CosmicRayBeaconRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
                CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
                ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result)
            )
            .apply(i, CosmicRayBeaconRecipe::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmicRayBeaconRecipe> STREAM_CODEC = StreamCodec.composite(
        Recipe.CommonInfo.STREAM_CODEC,
        o -> o.commonInfo,
        CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
        o -> o.bookInfo,
        ShapedRecipePattern.STREAM_CODEC,
        o -> o.pattern,
        ItemStackTemplate.STREAM_CODEC,
        o -> o.result,
        CosmicRayBeaconRecipe::new
    );
    public static final RecipeSerializer<CosmicRayBeaconRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ItemStackTemplate result;

    public CosmicRayBeaconRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack beacon = super.assemble(input);
        for (ItemStack ingredient : input.items()) {
            DyeColor dye = ingredient.get(DataComponents.DYE);
            if (dye != null) {
                return CosmicRayBeaconItem.withColor(beacon, dye);
            }
        }
        return beacon;
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return unchecked(SERIALIZER);
    }

    @SuppressWarnings("unchecked")
    private static RecipeSerializer<ShapedRecipe> unchecked(RecipeSerializer<? extends ShapedRecipe> serializer) {
        return (RecipeSerializer<ShapedRecipe>)serializer;
    }
}

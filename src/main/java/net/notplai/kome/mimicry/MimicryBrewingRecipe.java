package net.notplai.kome.mimicry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * A brewing recipe whose result remembers a player: the one whose head went in as the reagent, or failing that
 * the one the potion going in already remembered. It only brews when one of the two names somebody.
 */
public class MimicryBrewingRecipe extends BrewingRecipe {
    public static final MapCodec<MimicryBrewingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                PotionIngredient.MAP_CODEC.fieldOf("input").forGetter(BrewingRecipe::getInput),
                PotionIngredient.MAP_CODEC.fieldOf("reagent").forGetter(BrewingRecipe::getReagent),
                ItemStackTemplate.CODEC.fieldOf("output").forGetter(BrewingRecipe::getOutput)
            )
            .apply(i, MimicryBrewingRecipe::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MimicryBrewingRecipe> STREAM_CODEC = StreamCodec.composite(
        PotionIngredient.STREAM_CODEC,
        BrewingRecipe::getInput,
        PotionIngredient.STREAM_CODEC,
        BrewingRecipe::getReagent,
        ItemStackTemplate.STREAM_CODEC,
        BrewingRecipe::getOutput,
        MimicryBrewingRecipe::new
    );
    public static final RecipeSerializer<MimicryBrewingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public MimicryBrewingRecipe(PotionIngredient input, PotionIngredient reagent, ItemStackTemplate output) {
        super(input, reagent, output);
    }

    private static ResolvableProfile remembered(BrewingInput input) {
        ResolvableProfile profile = input.reagent().get(DataComponents.PROFILE);
        if (profile == null) {
            profile = input.input().get(DataComponents.PROFILE);
        }
        return profile != null && profile.name().isPresent() ? profile : null;
    }

    @Override
    public boolean matches(BrewingInput input) {
        return super.matches(input) && remembered(input) != null;
    }

    @Override
    public ItemStack assemble(BrewingInput input) {
        ItemStack result = super.assemble(input);
        ResolvableProfile profile = remembered(input);
        if (profile != null) {
            result.set(DataComponents.PROFILE, profile);
        }
        return result;
    }

    @Override
    public RecipeSerializer<BrewingRecipe> getSerializer() {
        return unchecked(SERIALIZER);
    }

    @SuppressWarnings("unchecked")
    private static RecipeSerializer<BrewingRecipe> unchecked(RecipeSerializer<? extends BrewingRecipe> serializer) {
        return (RecipeSerializer<BrewingRecipe>)serializer;
    }
}

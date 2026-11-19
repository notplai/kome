package net.notplai.kome.ritualsign.client;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.notplai.kome.ritualsign.RitualSignItem;
import net.notplai.kome.ritualsign.Spell;

/** Tints a Ritual Sign in the colour of the magic it is bound to. */
public record SpellTint() implements ItemTintSource {
    public static final MapCodec<SpellTint> MAP_CODEC = MapCodec.unit(new SpellTint());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Spell spell = RitualSignItem.spellOf(stack);
        return ARGB.opaque(spell == null ? RitualSignClient.UNBOUND_COLOR : spell.color());
    }

    @Override
    public MapCodec<SpellTint> type() {
        return MAP_CODEC;
    }
}

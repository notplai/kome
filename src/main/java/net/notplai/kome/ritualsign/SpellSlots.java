package net.notplai.kome.ritualsign;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The spells a player carries, in {@link #SIZE} slots, and which of them is picked. Their Ritual Signs cast whichever
 * is picked. A slot may be empty.
 */
public record SpellSlots(List<Optional<Spell>> slots, int selected) {
    public static final int SIZE = 5;
    private static final String EMPTY = "empty";
    private static final Codec<Optional<Spell>> SLOT_CODEC = Codec.STRING.xmap(
        name -> Optional.ofNullable(byName(name)), slot -> slot.map(Spell::getSerializedName).orElse(EMPTY)
    );
    public static final Codec<SpellSlots> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SLOT_CODEC.listOf().fieldOf("slots").forGetter(SpellSlots::slots),
            Codec.INT.fieldOf("selected").forGetter(SpellSlots::selected)
        )
        .apply(instance, SpellSlots::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellSlots> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(Spell.STREAM_CODEC).apply(ByteBufCodecs.list(SIZE)),
        SpellSlots::slots,
        ByteBufCodecs.VAR_INT,
        SpellSlots::selected,
        SpellSlots::new
    );

    public SpellSlots {
        // Always exactly SIZE slots, whatever was saved.
        List<Optional<Spell>> fixed = new ArrayList<>(slots.subList(0, Math.min(slots.size(), SIZE)));
        while (fixed.size() < SIZE) {
            fixed.add(Optional.empty());
        }
        slots = List.copyOf(fixed);
        selected = Math.floorMod(selected, SIZE);
    }

    /** What every player starts with: each spell there is, in order, as far as the slots go. */
    public static SpellSlots starting() {
        List<Optional<Spell>> slots = new ArrayList<>();
        for (Spell spell : Spell.values()) {
            slots.add(Optional.of(spell));
        }
        return new SpellSlots(slots, 0);
    }

    /** The picked spell, or nothing if its slot is empty. */
    public @Nullable Spell selectedSpell() {
        return this.slots.get(this.selected).orElse(null);
    }

    /** Picks the next filled slot {@code step} ways along, going round; as it was if all the others are empty. */
    public SpellSlots cycle(int step) {
        int direction = step < 0 ? -1 : 1;
        for (int i = 1; i <= SIZE; i++) {
            int slot = Math.floorMod(this.selected + direction * i, SIZE);
            if (this.slots.get(slot).isPresent()) {
                return new SpellSlots(this.slots, slot);
            }
        }
        return this;
    }

    /** With {@code spell}, or nothing, put in {@code slot}. */
    public SpellSlots with(int slot, @Nullable Spell spell) {
        List<Optional<Spell>> slots = new ArrayList<>(this.slots);
        slots.set(slot, Optional.ofNullable(spell));
        return new SpellSlots(slots, this.selected);
    }

    private static @Nullable Spell byName(String name) {
        for (Spell spell : Spell.values()) {
            if (spell.getSerializedName().equals(name)) {
                return spell;
            }
        }
        return null;
    }
}

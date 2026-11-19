package net.notplai.kome.ritualsign;

import com.mojang.serialization.Codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.notplai.kome.Kome;

/**
 * The magic a Ritual Sign is bound to. Each kind of magic has its own colour and its own outer/inner sign textures,
 * used on the holder's arm, in the sky, and on the item.
 */
public enum Spell implements StringRepresentable {
    /** Signs open in the sky all round the target, and each lets a star fall on it. */
    SHOOTING_STAR("shooting_star", 0xFFD23C),
    /** A mark warns where it will fall while it charges, growing, and a bolt strikes it when the sign is let go. */
    LIGHTNING("lightning", 0xB24CFF),
    /** The sign itself becomes a sword of light, struck with as hard as a diamond sword. */
    SWORD("sword", 0x5AD8FF);

    public static final Codec<Spell> CODEC = StringRepresentable.fromEnum(Spell::values);
    public static final StreamCodec<FriendlyByteBuf, Spell> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(Spell.class);

    private final String name;
    private final int color;
    private final Identifier outerTexture;
    private final Identifier innerTexture;

    Spell(String name, int color) {
        this.name = name;
        this.color = color;
        this.outerTexture = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/spell/" + name + "_outer.png");
        this.innerTexture = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/spell/" + name + "_inner.png");
    }

    /** The colour its signs and magic are drawn in. */
    public int color() {
        return this.color;
    }

    /** The outer ring texture for this spell's ritual sign. */
    public Identifier outerTexture() {
        return this.outerTexture;
    }

    /** The inner sigil texture for this spell's ritual sign. */
    public Identifier innerTexture() {
        return this.innerTexture;
    }

    /** The spell after this one, round to the first again after the last. */
    public Spell next() {
        Spell[] spells = values();
        return spells[(this.ordinal() + 1) % spells.length];
    }

    public Component displayName() {
        return Component.translatable("spell.kome." + this.name);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

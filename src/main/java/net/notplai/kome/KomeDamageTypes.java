package net.notplai.kome;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The ways this mod's weapons and spells hurt. Each one is put down to whoever used it, so that the mobs it hurts
 * turn on them, kills count as theirs, and death messages say who it was.
 */
public final class KomeDamageTypes {
    public static final ResourceKey<DamageType> COSMIC_RAY = key("cosmic_ray");
    public static final ResourceKey<DamageType> SHOOTING_STAR = key("shooting_star");
    public static final ResourceKey<DamageType> LIGHTNING = key("lightning");
    public static final ResourceKey<DamageType> BLOOD_SLASH = key("blood_slash");
    public static final ResourceKey<DamageType> SOLARIS_DECREE = key("solaris_decree");
    /** A Solaris Decree whose caster has left the game, so there is no one to put it down to. */
    public static final ResourceKey<DamageType> SOLARIS_DECREE_UNCLAIMED = key("solaris_decree_unclaimed");
    public static final ResourceKey<DamageType> AURELIAN_EFFIGY = key("aurelian_effigy");
    /** An Aurelian Effigy whose owner has left the game, or that was placed by no one. */
    public static final ResourceKey<DamageType> AURELIAN_EFFIGY_UNCLAIMED = key("aurelian_effigy_unclaimed");

    private KomeDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Kome.MODID, name));
    }

    /** Hurt by {@code direct} on behalf of {@code by}. */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity by) {
        return level.damageSources().source(type, direct, by);
    }

    /** Hurt on behalf of {@code by}, or by nobody at all as {@code unclaimed} if there is no one to put it down to. */
    public static DamageSource claimed(Level level, ResourceKey<DamageType> type, ResourceKey<DamageType> unclaimed, @Nullable Entity direct, @Nullable Entity by) {
        return by == null ? level.damageSources().source(unclaimed) : level.damageSources().source(type, direct, by);
    }
}

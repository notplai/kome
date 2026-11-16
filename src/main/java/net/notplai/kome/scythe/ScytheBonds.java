package net.notplai.kome.scythe;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.notplai.kome.Kome;

/**
 * The world's record of which bonds still stand. A scythe and its victim are rarely in the same place, and either
 * may be offline or in a chest when the bond ends, so each of them checks against this instead of against the other.
 */
public class ScytheBonds extends SavedData {
    private static final Codec<ScytheBonds> CODEC = UUIDUtil.CODEC_SET.xmap(ScytheBonds::new, bonds -> bonds.active);
    private static final SavedDataType<ScytheBonds> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Kome.MODID, "scythe_bonds"), ScytheBonds::new, CODEC
    );

    private final Set<UUID> active;

    private ScytheBonds() {
        this(new HashSet<>());
    }

    private ScytheBonds(Set<UUID> active) {
        this.active = new HashSet<>(active);
    }

    public static ScytheBonds get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isActive(UUID bondId) {
        return this.active.contains(bondId);
    }

    public void add(UUID bondId) {
        if (this.active.add(bondId)) {
            this.setDirty();
        }
    }

    public void remove(UUID bondId) {
        if (this.active.remove(bondId)) {
            this.setDirty();
        }
    }
}

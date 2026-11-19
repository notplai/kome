package net.notplai.kome.effigy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.notplai.kome.Kome;

/**
 * The world's record of players who were held by an effigy's barrier and had logged out when it crashed.
 * The effigy is gone by the time they come back, so their death waits for them here.
 */
@EventBusSubscriber(modid = Kome.MODID)
public class EffigyCondemned extends SavedData {
    /** One condemned player, and who placed the effigy that condemned them, if anyone did. */
    private record Entry(UUID player, Optional<UUID> owner) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player), UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Entry::owner))
                .apply(instance, Entry::new)
        );
    }

    private static final Codec<EffigyCondemned> CODEC = Codec.withAlternative(
        Entry.CODEC.listOf().xmap(EffigyCondemned::fromEntries, EffigyCondemned::toEntries),
        // Saved before owners were kept: just the players.
        UUIDUtil.CODEC_SET.xmap(
            players -> fromEntries(players.stream().map(player -> new Entry(player, Optional.empty())).toList()), condemned -> condemned.players.keySet()
        )
    );
    private static final SavedDataType<EffigyCondemned> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Kome.MODID, "effigy_condemned"), EffigyCondemned::new, CODEC
    );

    /** Each condemned player, and who placed the effigy that condemned them. */
    private final Map<UUID, Optional<UUID>> players = new HashMap<>();

    private EffigyCondemned() {
    }

    private static EffigyCondemned fromEntries(List<Entry> entries) {
        EffigyCondemned condemned = new EffigyCondemned();
        for (Entry entry : entries) {
            condemned.players.put(entry.player(), entry.owner());
        }
        return condemned;
    }

    private static List<Entry> toEntries(EffigyCondemned condemned) {
        return condemned.players.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
    }

    public static EffigyCondemned get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(UUID player, @Nullable UUID owner) {
        this.players.put(player, Optional.ofNullable(owner));
        this.setDirty();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffigyCondemned condemned = get(player.level().getServer());
            Optional<UUID> owner = condemned.players.remove(player.getUUID());
            if (owner != null) {
                condemned.setDirty();
                AurelianEffigyBlockEntity.slay(player.level(), player, owner.orElse(null));
            }
        }
    }
}

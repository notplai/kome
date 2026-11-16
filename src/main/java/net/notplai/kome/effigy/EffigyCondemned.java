package net.notplai.kome.effigy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

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
    private static final Codec<EffigyCondemned> CODEC = UUIDUtil.CODEC_SET.xmap(EffigyCondemned::new, condemned -> condemned.players);
    private static final SavedDataType<EffigyCondemned> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Kome.MODID, "effigy_condemned"), EffigyCondemned::new, CODEC
    );

    private final Set<UUID> players;

    private EffigyCondemned() {
        this(new HashSet<>());
    }

    private EffigyCondemned(Set<UUID> players) {
        this.players = new HashSet<>(players);
    }

    public static EffigyCondemned get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(UUID player) {
        if (this.players.add(player)) {
            this.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffigyCondemned condemned = get(player.level().getServer());
            if (condemned.players.remove(player.getUUID())) {
                condemned.setDirty();
                AurelianEffigyBlockEntity.slay(player.level(), player);
            }
        }
    }
}

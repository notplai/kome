package net.notplai.kome.skystride;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.notplai.kome.Kome;

/**
 * The Skystride boot enchantment, which gives one more jump in mid-air for each of its three levels. The jump itself happens on the client, which owns player movement;
 * the server is only told so it can forgive the fall so far and show the jump to everyone else.
 */
public final class Skystride {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(
        Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(Kome.MODID, "skystride")
    );

    private Skystride() {
    }

    /** How many extra jumps the player's boots allow in mid-air. */
    public static int extraJumps(Player player) {
        return player.registryAccess()
            .lookup(Registries.ENCHANTMENT)
            .flatMap(enchantments -> enchantments.get(KEY))
            .map(enchantment -> EnchantmentHelper.getEnchantmentLevel((Holder<Enchantment>)enchantment, player))
            .orElse(0);
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Payload.TYPE, Payload.STREAM_CODEC, Skystride::handle);
    }

    private static void handle(Payload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.onGround() || extraJumps(player) <= 0) {
            return;
        }

        player.resetFallDistance();
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.25, 0.0, 0.25, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    public record Payload() implements CustomPacketPayload {
        public static final Payload INSTANCE = new Payload();
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Kome.MODID, "skystride")
        );
        public static final StreamCodec<Object, Payload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public CustomPacketPayload.Type<Payload> type() {
            return TYPE;
        }
    }
}

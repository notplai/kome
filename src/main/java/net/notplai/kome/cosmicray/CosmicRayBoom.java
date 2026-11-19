package net.notplai.kome.cosmicray;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;

/**
 * Tells everyone in sight that a Cosmic Ray Beacon's beam has ended in a blast at {@code at}, in the beam's
 * colour, fired by the entity numbered {@code shooter}.
 */
public record CosmicRayBoom(Vec3 at, int rgb, int shooter) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CosmicRayBoom> TYPE = new CustomPacketPayload.Type<>(
        Identifier.fromNamespaceAndPath(Kome.MODID, "cosmic_ray_boom")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmicRayBoom> STREAM_CODEC = StreamCodec.composite(
        Vec3.STREAM_CODEC, CosmicRayBoom::at, ByteBufCodecs.INT, CosmicRayBoom::rgb, ByteBufCodecs.VAR_INT, CosmicRayBoom::shooter, CosmicRayBoom::new
    );

    @Override
    public CustomPacketPayload.Type<CosmicRayBoom> type() {
        return TYPE;
    }
}

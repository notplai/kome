package net.notplai.kome.cosmicray;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;

/**
 * On a player: a Cosmic Ray Beacon they have set off. It runs its whole course from the game time it started,
 * charge and beam alike, without the use key being held; only taking the beacon out of that hand cuts it short.
 * Everyone who can see the player is told of it, but it is not kept through leaving the game.
 */
public record CosmicRayActivation(long startedAt, InteractionHand hand, DyeColor color) {
    public static final StreamCodec<ByteBuf, CosmicRayActivation> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        CosmicRayActivation::startedAt,
        InteractionHand.STREAM_CODEC,
        CosmicRayActivation::hand,
        DyeColor.STREAM_CODEC,
        CosmicRayActivation::color,
        CosmicRayActivation::new
    );
}

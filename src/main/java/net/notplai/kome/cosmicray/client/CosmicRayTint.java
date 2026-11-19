package net.notplai.kome.cosmicray.client;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.notplai.kome.cosmicray.CosmicRayBeaconItem;

/**
 * Tints a Cosmic Ray Beacon's core in the colour of the dye it was crafted with. While it rests after firing, the
 * core goes dark and grey, and its colour slowly fills back in until it is ready to be set off again.
 */
public record CosmicRayTint() implements ItemTintSource {
    public static final MapCodec<CosmicRayTint> MAP_CODEC = MapCodec.unit(new CosmicRayTint());
    /** The colour of a spent core. */
    private static final int SPENT = 0x3C3C40;

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        int color = CosmicRayBeaconItem.colorOf(stack).getTextureDiffuseColor();
        Player player = owner instanceof Player holder ? holder : Minecraft.getInstance().player;
        float resting = player == null ? 0.0F : player.getCooldowns().getCooldownPercent(stack, 0.0F);
        // Most of the colour comes back towards the end, so a core that looks ready nearly is.
        float charged = 1.0F - resting;
        return ARGB.opaque(ARGB.srgbLerp(charged * charged, SPENT, color));
    }

    @Override
    public MapCodec<CosmicRayTint> type() {
        return MAP_CODEC;
    }
}

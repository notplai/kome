package net.notplai.kome.skystride.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.notplai.kome.Kome;
import net.notplai.kome.skystride.Skystride;

/** Turns a fresh press of the jump key in mid-air into another jump while Skystride boots are worn, as many times as the enchantment has levels. */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class SkystrideClient {
    private static boolean wasJumpDown;
    private static int airTicks;
    private static int jumpsUsed;

    private SkystrideClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        boolean jumpDown = player.input.keyPresses.jump();
        boolean pressed = jumpDown && !wasJumpDown;
        wasJumpDown = jumpDown;
        if (player.onGround() || player.isInLiquid() || player.onClimbable() || player.isPassenger() || player.getAbilities().flying || player.isFallFlying()) {
            airTicks = 0;
            jumpsUsed = 0;
            return;
        }

        // The press that left the ground is still "fresh" on the first airborne tick; it must not count twice.
        if (++airTicks > 1 && pressed && jumpsUsed < Skystride.extraJumps(player)) {
            jumpsUsed++;
            player.jumpFromGround();
            player.resetFallDistance();
            ClientPacketDistributor.sendToServer(Skystride.Payload.INSTANCE);
        }
    }
}

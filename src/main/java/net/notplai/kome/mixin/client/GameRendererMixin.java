package net.notplai.kome.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.notplai.kome.cosmicray.client.CosmicRayClient;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Adds a Cosmic Ray Beacon's impact frame to the screen effects the game draws this frame, while one is striking. */
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void kome$impactFrame(DeltaTracker deltaTracker, CallbackInfo callback) {
        Identifier effect = CosmicRayClient.impactPostEffect(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (effect != null) {
            ((GameRenderer)(Object)this).getRequestedPostEffects().add(effect);
        }
    }
}

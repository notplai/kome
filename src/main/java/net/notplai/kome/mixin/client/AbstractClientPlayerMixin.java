package net.notplai.kome.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.notplai.kome.mimicry.client.MimicryClient;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    /** A player under a Potion of Mimicry is drawn in the skin of whoever they are disguised as. */
    @Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("HEAD"), cancellable = true)
    private void kome$mimickedSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerSkin skin = MimicryClient.skinOf((AbstractClientPlayer)(Object)this);
        if (skin != null) {
            callback.setReturnValue(skin);
        }
    }
}

package net.notplai.kome.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.notplai.kome.effigy.EffigyBarriers;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    /** The server only looks for blocks under a player, and would kick one standing on an effigy barrier's floor for flying. */
    @Inject(method = "noBlocksAround(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void kome$standingOnEffigyBarrier(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (EffigyBarriers.supports(entity)) {
            callback.setReturnValue(false);
        }
    }
}

package net.notplai.kome.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.notplai.kome.Kome;
import net.notplai.kome.scythe.SanguineScytheItem;

@Mixin(Player.class)
public abstract class PlayerMixin {
    /** Swaps the white sweep of a sword for the sanguine scythe's dark crimson one. */
    @ModifyArg(
        method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/phys/AABB;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
        ),
        index = 0
    )
    private ParticleOptions kome$sanguineSweep(ParticleOptions particle) {
        Player self = (Player)(Object)this;
        return self.getMainHandItem().getItem() instanceof SanguineScytheItem ? Kome.SANGUINE_SWEEP.get() : particle;
    }
}

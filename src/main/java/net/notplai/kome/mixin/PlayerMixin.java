package net.notplai.kome.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.notplai.kome.Kome;
import net.notplai.kome.ritualsign.RitualSignItem;
import net.notplai.kome.ritualsign.Spell;
import net.notplai.kome.scythe.SanguineScytheItem;

@Mixin(Player.class)
public abstract class PlayerMixin {
    /** Swaps the white sweep of a sword for the sanguine scythe's dark crimson one, or a Spell Sword's own colour. */
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
        ItemStack held = self.getMainHandItem();
        if (held.getItem() instanceof SanguineScytheItem) {
            return Kome.SANGUINE_SWEEP.get();
        }
        if (held.getItem() instanceof RitualSignItem && RitualSignItem.spellOf(held) == Spell.SWORD) {
            return Kome.SPELL_SWEEP.get();
        }
        return particle;
    }
}

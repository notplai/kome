package net.notplai.kome.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.notplai.kome.mimicry.Mimicry;

@Mixin(ThrownSplashPotion.class)
public abstract class ThrownSplashPotionMixin {
    private static final String ON_HIT = "onHitAsPotion(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/HitResult;)V";

    /** An effect does not know which bottle it came out of, so a bursting Potion of Mimicry says whose face it holds while it bursts. */
    @Inject(method = ON_HIT, at = @At("HEAD"))
    private void kome$mimicrySplashBegins(ServerLevel level, ItemStack potionItem, HitResult hitResult, CallbackInfo callback) {
        Mimicry.splashBegins(potionItem);
    }

    @Inject(method = ON_HIT, at = @At("RETURN"))
    private void kome$mimicrySplashEnds(ServerLevel level, ItemStack potionItem, HitResult hitResult, CallbackInfo callback) {
        Mimicry.splashEnds();
    }
}

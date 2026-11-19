package net.notplai.kome.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

/** Lets the movement keys be ignored outright while a Cosmic Ray Beacon holds its holder in place. */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector")
    void kome$setMoveVector(Vec2 moveVector);
}

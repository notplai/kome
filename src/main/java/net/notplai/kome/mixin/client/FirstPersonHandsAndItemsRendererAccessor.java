package net.notplai.kome.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Lends out the arm a map is held with in both hands, so a Cosmic Ray Beacon's core can be held the same way, and
 * the bare arm, so a Ritual Sign can be drawn turning round it.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public interface FirstPersonHandsAndItemsRendererAccessor {
    @Invoker("renderMapHand")
    void kome$renderMapHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HumanoidArm arm, PlayerRenderState playerState);

    @Invoker("renderPlayerArm")
    void kome$renderPlayerArm(
        PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight, float attackValue, HumanoidArm arm, PlayerRenderState playerState
    );
}

package net.notplai.kome.ritualsign.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.notplai.kome.ritualsign.RitualSignItem;
import net.notplai.kome.ritualsign.Spell;

/** A Ritual Sign held by someone else, or by yourself seen from outside: it turns round the hand holding it. */
public class HandSignLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public HandSignLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        for (HumanoidArm arm : HumanoidArm.values()) {
            ItemStack stack = state.getUseItemStackForArm(arm);
            // Bound to the Sword spell it is drawn as the sword it has become, like any other held item.
            if (stack.getItem() instanceof RitualSignItem && RitualSignItem.spellOf(stack) != Spell.SWORD) {
                poseStack.pushPose();
                this.getParentModel().translateToHand(state, arm, poseStack);
                RitualSignClient.submitHandSign(
                    poseStack, collector, arm, stack, state.ageInTicks, state.ticksUsingItem(arm), RitualSignClient.NumberStyle.inWorld()
                );
                poseStack.popPose();
            }
        }
    }
}

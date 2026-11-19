package net.notplai.kome.ritualsign.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.ritualsign.Spell;

/**
 * Draws a spell's ritual sign: its outer ring and, inside it, its inner sigil, turning against each other, flat in
 * the plane across the y axis and lit by itself. Both textures and default colour come from the {@link Spell}.
 */
final class RitualSigns {
    private static final float INNER_SCALE = 0.74F;
    private static final int FULL_BRIGHT = 15728880;

    private RitualSigns() {
    }

    /** A sign {@code radius} across from its middle, turned {@code angle} degrees, in {@code rgb} at {@code alpha}. */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, float radius, float angle, Spell spell, int rgb, float alpha) {
        if (radius <= 0.0F || alpha <= 0.0F) {
            return;
        }
        int color = ARGB.color(GoldenLight.alpha(alpha), rgb);
        RenderType outer = RenderTypes.beaconBeam(spell.outerTexture(), true);
        RenderType inner = RenderTypes.beaconBeam(spell.innerTexture(), true);
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, angle);
        collector.submitCustomGeometry(poseStack, outer, (pose, buffer) -> quad(pose, buffer, radius, color));
        poseStack.rotateDegrees(Axis.YP, -angle * 2.5F);
        collector.submitCustomGeometry(poseStack, inner, (pose, buffer) -> quad(pose, buffer, radius * INNER_SCALE, color));
        poseStack.popPose();
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color) {
        // Both windings, so it shows from either side.
        vertex(pose, buffer, color, -radius, -radius, 0.0F, 0.0F);
        vertex(pose, buffer, color, -radius, radius, 0.0F, 1.0F);
        vertex(pose, buffer, color, radius, radius, 1.0F, 1.0F);
        vertex(pose, buffer, color, radius, -radius, 1.0F, 0.0F);
        vertex(pose, buffer, color, radius, -radius, 1.0F, 0.0F);
        vertex(pose, buffer, color, radius, radius, 1.0F, 1.0F);
        vertex(pose, buffer, color, -radius, radius, 0.0F, 1.0F);
        vertex(pose, buffer, color, -radius, -radius, 0.0F, 0.0F);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float z, float u, float v) {
        buffer.addVertex(pose, x, 0.0F, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}

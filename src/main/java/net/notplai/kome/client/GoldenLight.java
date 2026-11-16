package net.notplai.kome.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.notplai.kome.Kome;

/** The glowing golden light the Solaris Decree's ritual and the Aurelian Effigy are both drawn with, and the shapes it comes in. */
public final class GoldenLight {
    // Heights of the beam's cross-sections: dense near the ground where it flares out, sparse up into the sky.
    private static final float[] BEAM_ROWS = {0.0F, 0.4F, 0.9F, 1.6F, 2.6F, 4.0F, 7.0F, 14.0F, 40.0F, 120.0F, 256.0F};
    private static final int BEAM_COLUMNS = 16;
    private static final int GLOW_SEGMENTS = 24;
    private static final int GLOW_RINGS = 6;

    /**
     * Untextured additive light. Unlike vanilla lightning it is double-sided and leaves the depth buffer alone,
     * so its soft transparent edges never cut holes into whatever is drawn behind them.
     */
    public static final RenderPipeline LIGHT_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Kome.MODID, "pipeline/golden_light"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build();
    public static final OitPipelineSet OIT_LIGHT_PIPELINES = OitPipelineSet.builder(
            Identifier.fromNamespaceAndPath(Kome.MODID, "golden_light"),
            RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET).withShaderDefine("OIT_ADDITIVE").withCull(false)
        )
        .build();
    public static final RenderType LIGHT = RenderType.create(
        "kome_golden_light", RenderSetup.builder(LIGHT_PIPELINE).setOitPipelines(OIT_LIGHT_PIPELINES).createRenderSetup()
    );

    private GoldenLight() {
    }

    public static int alpha(float alpha) {
        return Mth.clamp((int)(alpha * 255.0F), 0, 255);
    }

    /**
     * A vertical sheet of light. It is at full brightness out to {@code hardness} of its half-width and falls off to
     * nothing over the rest, widens towards the ground by {@code flare}, and fades out at the top.
     */
    public static void lightSheet(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        float rightX,
        float rightZ,
        float offset,
        float halfWidth,
        float bottom,
        float flare,
        float hardness,
        int rgb,
        float peakAlpha
    ) {
        if (peakAlpha <= 0.0F) {
            return;
        }

        for (int row = 0; row < BEAM_ROWS.length - 1; row++) {
            float lowY = BEAM_ROWS[row];
            float highY = BEAM_ROWS[row + 1];
            float lowWidth = halfWidth * (1.0F + flare * (float)Math.exp(-lowY / 1.5F));
            float highWidth = halfWidth * (1.0F + flare * (float)Math.exp(-highY / 1.5F));
            float highFade = row + 1 == BEAM_ROWS.length - 1 ? 0.0F : 1.0F;

            for (int column = 0; column < BEAM_COLUMNS; column++) {
                float left = column * 2.0F / BEAM_COLUMNS - 1.0F;
                float right = (column + 1) * 2.0F / BEAM_COLUMNS - 1.0F;
                int leftAlpha = alpha(peakAlpha * falloff(left, hardness));
                int rightAlpha = alpha(peakAlpha * falloff(right, hardness));
                sheetVertex(pose, buffer, rightX, rightZ, offset + left * lowWidth, bottom + lowY, ARGB.color(leftAlpha, rgb));
                sheetVertex(pose, buffer, rightX, rightZ, offset + right * lowWidth, bottom + lowY, ARGB.color(rightAlpha, rgb));
                sheetVertex(pose, buffer, rightX, rightZ, offset + right * highWidth, bottom + highY, ARGB.color((int)(rightAlpha * highFade), rgb));
                sheetVertex(pose, buffer, rightX, rightZ, offset + left * highWidth, bottom + highY, ARGB.color((int)(leftAlpha * highFade), rgb));
            }
        }
    }

    private static void sheetVertex(PoseStack.Pose pose, VertexConsumer buffer, float rightX, float rightZ, float across, float y, int color) {
        buffer.addVertex(pose, rightX * across, y, rightZ * across).setColor(color);
    }

    /** A disc of light centred on the beam at {@code centerY}, lying in the plane spanned by the axes u and v. */
    public static void lightDisc(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        float centerY,
        float uX,
        float uY,
        float uZ,
        float vX,
        float vY,
        float vZ,
        float radius,
        float hardness,
        int rgb,
        float peakAlpha
    ) {
        if (peakAlpha <= 0.0F) {
            return;
        }

        for (int ring = 0; ring < GLOW_RINGS; ring++) {
            float inner = (float)ring / GLOW_RINGS;
            float outer = (float)(ring + 1) / GLOW_RINGS;
            int innerColor = ARGB.color(alpha(peakAlpha * falloff(inner, hardness)), rgb);
            int outerColor = ARGB.color(alpha(peakAlpha * falloff(outer, hardness)), rgb);

            for (int segment = 0; segment < GLOW_SEGMENTS; segment++) {
                float startAngle = segment * Mth.TWO_PI / GLOW_SEGMENTS;
                float endAngle = (segment + 1) * Mth.TWO_PI / GLOW_SEGMENTS;
                float startU = Mth.cos(startAngle) * radius;
                float startV = Mth.sin(startAngle) * radius;
                float endU = Mth.cos(endAngle) * radius;
                float endV = Mth.sin(endAngle) * radius;
                discVertex(pose, buffer, centerY, uX, uY, uZ, vX, vY, vZ, startU * inner, startV * inner, innerColor);
                discVertex(pose, buffer, centerY, uX, uY, uZ, vX, vY, vZ, startU * outer, startV * outer, outerColor);
                discVertex(pose, buffer, centerY, uX, uY, uZ, vX, vY, vZ, endU * outer, endV * outer, outerColor);
                discVertex(pose, buffer, centerY, uX, uY, uZ, vX, vY, vZ, endU * inner, endV * inner, innerColor);
            }
        }
    }

    private static void discVertex(
        PoseStack.Pose pose, VertexConsumer buffer, float centerY, float uX, float uY, float uZ, float vX, float vY, float vZ, float u, float v, int color
    ) {
        buffer.addVertex(pose, uX * u + vX * v, centerY + uY * u + vY * v, uZ * u + vZ * v).setColor(color);
    }

    /** A flat ring of light on the ground, brightest along {@code radius} and fading out over {@code thickness} to either side. */
    public static void lightRing(PoseStack.Pose pose, VertexConsumer buffer, float y, float radius, float thickness, int rgb, float peakAlpha) {
        if (peakAlpha <= 0.0F) {
            return;
        }

        int peakColor = ARGB.color(alpha(peakAlpha), rgb);
        int edgeColor = ARGB.color(0, rgb);
        float innerRadius = Math.max(0.0F, radius - thickness);
        float outerRadius = radius + thickness;
        for (int segment = 0; segment < GLOW_SEGMENTS * 2; segment++) {
            float startAngle = segment * Mth.PI / GLOW_SEGMENTS;
            float endAngle = (segment + 1) * Mth.PI / GLOW_SEGMENTS;
            float startX = Mth.cos(startAngle);
            float startZ = Mth.sin(startAngle);
            float endX = Mth.cos(endAngle);
            float endZ = Mth.sin(endAngle);
            buffer.addVertex(pose, startX * innerRadius, y, startZ * innerRadius).setColor(edgeColor);
            buffer.addVertex(pose, startX * radius, y, startZ * radius).setColor(peakColor);
            buffer.addVertex(pose, endX * radius, y, endZ * radius).setColor(peakColor);
            buffer.addVertex(pose, endX * innerRadius, y, endZ * innerRadius).setColor(edgeColor);
            buffer.addVertex(pose, startX * radius, y, startZ * radius).setColor(peakColor);
            buffer.addVertex(pose, startX * outerRadius, y, startZ * outerRadius).setColor(edgeColor);
            buffer.addVertex(pose, endX * outerRadius, y, endZ * outerRadius).setColor(edgeColor);
            buffer.addVertex(pose, endX * radius, y, endZ * radius).setColor(peakColor);
        }
    }

    /** 1 out to a distance of {@code hardness}, then a smooth drop to 0 at a distance of 1. */
    private static float falloff(float distance, float hardness) {
        distance = Math.abs(distance);
        if (distance <= hardness) {
            return 1.0F;
        }

        float edge = (distance - hardness) / (1.0F - hardness);
        return Mth.square(Math.max(0.0F, 1.0F - edge * edge));
    }
}

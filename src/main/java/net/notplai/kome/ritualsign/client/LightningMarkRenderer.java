package net.notplai.kome.ritualsign.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.notplai.kome.Kome;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.ritualsign.LightningMarkEntity;
import net.notplai.kome.ritualsign.Spell;

/**
 * A Lightning spell's mark: a warning square on the ground flashing yellow and red; once it is let go, the spell's
 * sign in the sky above it, growing brighter as the bolt nears; and then the bolt.
 */
public class LightningMarkRenderer extends EntityRenderer<LightningMarkEntity, LightningMarkRenderState> {
    private static final RenderType WARNING = RenderTypes.beaconBeam(
        Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/spell/lightning_warning.png"), true
    );
    private static final int WARNING_YELLOW = 0xFFD800;
    private static final int WARNING_RED = 0xFF2A1A;
    /** Ticks the warning takes to turn from one colour to the other. */
    private static final int FLASH_TICKS = 5;
    private static final int OPEN_TICKS = 5;
    private static final int BOLT_SEGMENTS = 10;

    public LightningMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public LightningMarkRenderState createRenderState() {
        return new LightningMarkRenderState();
    }

    @Override
    public void extractRenderState(LightningMarkEntity entity, LightningMarkRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.size = entity.getSize();
        state.age = entity.tickCount + partialTicks;
        int sinceStrike = entity.ticksSinceStrike();
        state.sinceStrike = sinceStrike < 0 ? -1.0F : sinceStrike + partialTicks;
        state.land = entity.getLand();
        state.aiming = entity.isAiming();
        state.color = entity.getColor();
        state.seed = entity.getId();
    }

    @Override
    protected boolean affectedByCulling(LightningMarkEntity entity) {
        return false;
    }

    @Override
    public void submit(LightningMarkRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        boolean struck = state.sinceStrike >= 0.0F;
        float open = 1.0F - Mth.cube(1.0F - Mth.clamp(state.age / OPEN_TICKS, 0.0F, 1.0F));
        float fade = struck ? Mth.clamp(1.0F - state.sinceStrike / LightningMarkEntity.STRIKE_TICKS, 0.0F, 1.0F) : 1.0F;
        float blaze = struck ? fade : 0.0F;
        int rgb = state.color;

        // The warning on the ground, as wide as the bolt will be.
        float warningAlpha = struck ? Mth.clamp(1.0F - state.sinceStrike / 4.0F, 0.0F, 1.0F) : 0.9F;
        if (warningAlpha > 0.0F) {
            int warning = ((int)(state.age / FLASH_TICKS) & 1) == 0 ? WARNING_YELLOW : WARNING_RED;
            int color = ARGB.color(GoldenLight.alpha(warningAlpha * open), warning);
            float half = state.size / 2.0F * open;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.03F, 0.0F);
            collector.submitCustomGeometry(poseStack, WARNING, (pose, buffer) -> square(pose, buffer, half, color));
            poseStack.popPose();
        }

        if (state.aiming) {
            super.submit(state, poseStack, collector, camera);
            return;
        }

        // The sign in the sky, brighter as the bolt nears.
        float soon = struck ? 1.0F : Mth.clamp(state.age / LightningMarkEntity.WARNING_TICKS, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, LightningMarkEntity.SKY_HEIGHT, 0.0F);
        float radius = (0.6F + state.size * 0.55F) * open * (1.0F + 0.2F * blaze);
        float angle = state.age * (4.0F + 12.0F * soon);
        RitualSigns.submit(poseStack, collector, radius, angle, Spell.LIGHTNING, ARGB.srgbLerp(0.4F * blaze, rgb, 0xFFFFFF), fade);
        float glow = (0.2F + 0.35F * soon + 0.6F * blaze) * fade * open;
        collector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) ->
            GoldenLight.lightDisc(pose, buffer, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, radius * 1.3F, 0.0F, rgb, glow)
        );
        poseStack.popPose();

        if (struck && fade > 0.0F) {
            // It flickers: a new path every other tick.
            RandomSource random = RandomSource.createThreadLocalInstance(state.seed * 31L + (long)(state.sinceStrike / 2.0F));
            float size = state.size;
            // From the sign down to wherever it landed, which a roof may have stopped short of the mark.
            float fall = LightningMarkEntity.SKY_HEIGHT - state.land;
            poseStack.pushPose();
            poseStack.translate(0.0F, state.land, 0.0F);
            collector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) -> {
                bolt(pose, buffer, random, size, fall, fade, rgb);
                GoldenLight.lightDisc(pose, buffer, 0.1F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, size * 1.4F, 0.2F, rgb, 0.9F * fade);
            });
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** A jagged bolt from the sign down to the ground, with a few forks, all kept inside the mark. */
    private static void bolt(PoseStack.Pose pose, VertexConsumer buffer, RandomSource random, float size, float sky, float fade, int rgb) {
        float half = size / 2.0F;
        float[] xs = new float[BOLT_SEGMENTS + 1];
        float[] zs = new float[BOLT_SEGMENTS + 1];
        float step = sky / BOLT_SEGMENTS;
        for (int i = 1; i < BOLT_SEGMENTS; i++) {
            xs[i] = Mth.clamp(xs[i - 1] + (random.nextFloat() * 2.0F - 1.0F) * half * 0.6F, -half * 0.7F, half * 0.7F);
            zs[i] = Mth.clamp(zs[i - 1] + (random.nextFloat() * 2.0F - 1.0F) * half * 0.6F, -half * 0.7F, half * 0.7F);
        }
        // Thick and purple outside, thin and white at the heart.
        int pale = ARGB.srgbLerp(0.6F, rgb, 0xFFFFFF);
        float[] widths = {0.22F, 0.1F, 0.035F};
        int[] colors = {rgb, pale, 0xFFFFFF};
        float[] alphas = {0.35F, 0.55F, 0.9F};
        for (int layer = 0; layer < widths.length; layer++) {
            int color = ARGB.color(GoldenLight.alpha(alphas[layer] * fade), colors[layer]);
            float width = widths[layer] * size;
            for (int i = 0; i < BOLT_SEGMENTS; i++) {
                float topY = sky - i * step;
                // Points are counted from the ground up, so the sign's end is the last.
                ribbon(pose, buffer, xs[BOLT_SEGMENTS - i], topY, zs[BOLT_SEGMENTS - i], xs[BOLT_SEGMENTS - i - 1], topY - step, zs[BOLT_SEGMENTS - i - 1], width, color);
            }
        }

        for (int fork = 0; fork < 3; fork++) {
            int from = 2 + random.nextInt(BOLT_SEGMENTS - 4);
            float x = xs[from];
            float z = zs[from];
            float y = from * step;
            int color = ARGB.color(GoldenLight.alpha(0.5F * fade), pale);
            for (int i = 0; i < 3 && y > 0.0F; i++) {
                float nx = Mth.clamp(x + (random.nextFloat() * 2.0F - 1.0F) * half, -half, half);
                float nz = Mth.clamp(z + (random.nextFloat() * 2.0F - 1.0F) * half, -half, half);
                float ny = Math.max(0.0F, y - step * (0.6F + random.nextFloat() * 0.6F));
                ribbon(pose, buffer, x, y, z, nx, ny, nz, 0.06F * size, color);
                x = nx;
                z = nz;
                y = ny;
            }
        }
    }

    /** Two crossed strips of light from one point to another, so it shows from every side. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, float width, int color) {
        buffer.addVertex(pose, x0 - width, y0, z0).setColor(color);
        buffer.addVertex(pose, x0 + width, y0, z0).setColor(color);
        buffer.addVertex(pose, x1 + width, y1, z1).setColor(color);
        buffer.addVertex(pose, x1 - width, y1, z1).setColor(color);
        buffer.addVertex(pose, x0, y0, z0 - width).setColor(color);
        buffer.addVertex(pose, x0, y0, z0 + width).setColor(color);
        buffer.addVertex(pose, x1, y1, z1 + width).setColor(color);
        buffer.addVertex(pose, x1, y1, z1 - width).setColor(color);
    }

    /** The warning texture lying flat, {@code half} out from the middle each way, seen from above and below. */
    private static void square(PoseStack.Pose pose, VertexConsumer buffer, float half, int color) {
        warningVertex(pose, buffer, color, -half, -half, 0.0F, 0.0F);
        warningVertex(pose, buffer, color, -half, half, 0.0F, 1.0F);
        warningVertex(pose, buffer, color, half, half, 1.0F, 1.0F);
        warningVertex(pose, buffer, color, half, -half, 1.0F, 0.0F);
        warningVertex(pose, buffer, color, half, -half, 1.0F, 0.0F);
        warningVertex(pose, buffer, color, half, half, 1.0F, 1.0F);
        warningVertex(pose, buffer, color, -half, half, 0.0F, 1.0F);
        warningVertex(pose, buffer, color, -half, -half, 0.0F, 0.0F);
    }

    private static void warningVertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float z, float u, float v) {
        buffer.addVertex(pose, x, 0.0F, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}

package net.notplai.kome.decree.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.notplai.kome.Kome;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.decree.RitualEntity;

public class RitualRenderer extends EntityRenderer<RitualEntity, RitualRenderState> {
    private static final Identifier SIGN_OUTER = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/ritual_circle_outer.png");
    private static final Identifier SIGN_INNER = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/ritual_circle_inner.png");
    private static final int PALE_GOLD = 0xFFF6D8;
    private static final int FULL_BRIGHT = 15728880;
    // The inner sigil sits just inside the rune band of the outer ring.
    private static final float INNER_SIGN_SCALE = 0.74F;
    private static final float SIGN_Y = 0.03F;
    private static final float BEAM_DESCENT_TICKS = 8.0F;
    private static final float BEAM_DESCENT_HEIGHT = 64.0F;
    private static final int RAY_COUNT = 6;
    private static final int PULSE_RINGS = 3;
    private static final float FLASH_Y = 1.0F;
    private static final int WHITE_GOLD = 0xFFFAE6;
    private static final int WARM_GOLD = 0xFFDC78;
    private static final int DEEP_GOLD = 0xFFB43C;

    public RitualRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public RitualRenderState createRenderState() {
        return new RitualRenderState();
    }

    @Override
    public void extractRenderState(RitualEntity entity, RitualRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.age = entity.getAge(partialTicks);
        state.signRadius = entity.getSignRadius();
        state.beamWidth = entity.getBeamWidth();
        state.exploded = entity.hasExploded();
    }

    @Override
    protected boolean affectedByCulling(RitualEntity entity) {
        return false;
    }

    @Override
    public void submit(RitualRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        float age = state.age;
        float after = Mth.clamp((age - RitualEntity.KILL_TICK) / RitualEntity.FADE_TICKS, 0.0F, 1.0F);
        float fade = 1.0F - after;
        float power = RitualEntity.beamPower(age);
        float strength = power * Mth.sqrt(power);
        boolean exploded = state.exploded;
        // How far the blast has travelled: races out at first, then slows.
        float blast = exploded ? 1.0F - Mth.cube(fade) : 0.0F;
        int signColor = ARGB.srgbLerp(strength, RitualEntity.GOLD, PALE_GOLD);

        float drawn = Mth.clamp(age / RitualEntity.SIGN_TICKS, 0.0F, 1.0F);
        float signScale = state.signRadius * (1.0F - Mth.cube(1.0F - drawn)) * (1.0F + after * 0.3F);
        float signAlpha = drawn * (exploded ? Mth.cube(fade) : fade);
        float outerAngle = age * 1.2F + strength * 180.0F;
        float innerAngle = -(age * 2.0F + strength * 360.0F);
        submitSign(poseStack, submitNodeCollector, SIGN_OUTER, signScale, outerAngle, ARGB.color(GoldenLight.alpha(signAlpha), signColor));
        submitSign(poseStack, submitNodeCollector, SIGN_INNER, signScale * INNER_SIGN_SCALE, innerAngle, ARGB.color(GoldenLight.alpha(signAlpha), signColor));
        if (strength > 0.0F) {
            // A second, slightly larger pass makes the sign blaze as the beam strengthens.
            float pulse = 1.03F + 0.02F * Mth.sin(age * 0.6F);
            int glowColor = ARGB.color(GoldenLight.alpha(signAlpha * strength), PALE_GOLD);
            submitSign(poseStack, submitNodeCollector, SIGN_OUTER, signScale * pulse, outerAngle, glowColor);
            submitSign(poseStack, submitNodeCollector, SIGN_INNER, signScale * INNER_SIGN_SCALE * pulse, innerAngle, glowColor);
        }

        float beamAge = age - RitualEntity.SIGN_TICKS;
        if (beamAge > 0.0F && fade > 0.0F) {
            // The pillar is a stack of flat sheets of light turned to face the camera around the vertical axis.
            float toCameraX = (float)(camera.pos.x - state.x);
            float toCameraY = (float)(camera.pos.y - state.y) - FLASH_Y;
            float toCameraZ = (float)(camera.pos.z - state.z);
            float horizontalLength = Mth.sqrt(toCameraX * toCameraX + toCameraZ * toCameraZ);
            float rightX = horizontalLength < 1.0E-4F ? 1.0F : -toCameraZ / horizontalLength;
            float rightZ = horizontalLength < 1.0E-4F ? 0.0F : toCameraX / horizontalLength;
            float signRadius = state.signRadius;
            // The beam comes down out of the sky before it starts to grow.
            float bottom = Mth.square(1.0F - Mth.clamp(beamAge / BEAM_DESCENT_TICKS, 0.0F, 1.0F)) * BEAM_DESCENT_HEIGHT;
            float landed = 1.0F - bottom / BEAM_DESCENT_HEIGHT;
            float flare = (0.6F + 1.4F * strength) * landed;
            float throb = 1.0F + 0.08F * strength * Mth.sin(age * 0.5F + strength * 20.0F);
            // The blast tears the pillar wide open, a fizzled ritual just lets it drift apart.
            float width = state.beamWidth * throb * (1.0F + (exploded ? blast * 4.0F : after * 1.5F));
            float beamFade = exploded ? Mth.cube(fade) : fade;
            float hazeWidth = width * (2.5F + 4.5F * strength);
            float glowWidth = width * (1.2F + 1.8F * strength);
            float coreWidth = width * (0.25F + 0.85F * strength);
            submitNodeCollector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) -> {
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, hazeWidth, bottom, flare, 0.0F, DEEP_GOLD, (0.10F + 0.30F * strength) * beamFade);
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, glowWidth, bottom, flare, 0.35F, WARM_GOLD, (0.35F + 0.55F * strength) * beamFade);
                // A solid, hard-edged core: this is what makes it a beam rather than a glow.
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, coreWidth, bottom, flare * 0.5F, 0.8F, WHITE_GOLD, beamFade);

                // Two sharp lines mark the edges of the pillar.
                float edgeOffset = glowWidth * 0.62F;
                float edgeWidth = width * 0.06F + 0.03F;
                float edgeAlpha = (0.5F + 0.5F * strength) * beamFade;
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, edgeOffset, edgeWidth, bottom, 0.0F, 0.5F, WHITE_GOLD, edgeAlpha);
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, -edgeOffset, edgeWidth, bottom, 0.0F, 0.5F, WHITE_GOLD, edgeAlpha);

                // Thin rays wandering back and forth inside it.
                for (int ray = 0; ray < RAY_COUNT; ray++) {
                    float offset = glowWidth * 0.85F * Mth.sin(age * (0.021F + ray * 0.013F) + ray * 2.4F);
                    float flicker = 0.6F + 0.4F * Mth.sin(age * (0.35F + ray * 0.11F) + ray);
                    GoldenLight.lightSheet(pose, buffer, rightX, rightZ, offset, edgeWidth, bottom, 0.0F, 0.3F, WHITE_GOLD, (0.25F + 0.5F * strength) * beamFade * flicker);
                }

                float poolRadius = Math.max(signRadius, hazeWidth) * (1.0F + 0.8F * strength + after);
                GoldenLight.lightDisc(pose, buffer, SIGN_Y * 2.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, poolRadius, 0.0F, WARM_GOLD, (0.15F + 0.55F * strength) * beamFade * landed);

                if (after <= 0.0F) {
                    // Rings of light pulse outwards along the ground, faster and brighter as the beam charges.
                    for (int ring = 0; ring < PULSE_RINGS; ring++) {
                        float phase = Mth.frac(beamAge * 0.03F + strength * 2.5F + (float)ring / PULSE_RINGS);
                        float ringRadius = signRadius * (0.2F + 1.4F * phase);
                        GoldenLight.lightRing(pose, buffer, SIGN_Y * 3.0F, ringRadius, 0.2F + 0.4F * strength, WHITE_GOLD, (1.0F - phase) * (0.3F + 0.7F * strength) * landed);
                    }
                } else if (exploded) {
                    // Boom: shockwaves tear across the ground and a ball of light swallows the target.
                    float shockAlpha = Mth.square(fade);
                    GoldenLight.lightRing(pose, buffer, SIGN_Y * 3.0F, signRadius + 30.0F * blast, 1.5F + 3.0F * blast, WHITE_GOLD, shockAlpha);
                    GoldenLight.lightRing(pose, buffer, SIGN_Y * 3.0F, signRadius + 16.0F * blast, 1.0F + 2.0F * blast, WARM_GOLD, shockAlpha * 0.7F);

                    // The ball is a disc turned fully towards the camera.
                    float toCameraLength = Mth.sqrt(horizontalLength * horizontalLength + toCameraY * toCameraY);
                    float forwardX = toCameraLength < 1.0E-4F ? 0.0F : toCameraX / toCameraLength;
                    float forwardY = toCameraLength < 1.0E-4F ? 0.0F : toCameraY / toCameraLength;
                    float forwardZ = toCameraLength < 1.0E-4F ? 1.0F : toCameraZ / toCameraLength;
                    float upX = forwardY * rightZ;
                    float upY = forwardZ * rightX - forwardX * rightZ;
                    float upZ = -forwardY * rightX;
                    float flashRadius = signRadius + 22.0F * blast;
                    GoldenLight.lightDisc(pose, buffer, FLASH_Y, rightX, 0.0F, rightZ, upX, upY, upZ, flashRadius, 0.0F, DEEP_GOLD, shockAlpha);
                    GoldenLight.lightDisc(pose, buffer, FLASH_Y, rightX, 0.0F, rightZ, upX, upY, upZ, flashRadius * 0.6F, 0.5F, WHITE_GOLD, shockAlpha);
                }
            });
        }

        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static void submitSign(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Identifier texture, float radius, float angle, int color) {
        if (radius <= 0.0F || ARGB.alpha(color) == 0) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, SIGN_Y, 0.0F);
        poseStack.rotateDegrees(Axis.YP, angle);
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(texture, true), (pose, buffer) -> {
            // Both windings, so the sign is also visible from underneath.
            vertex(pose, buffer, color, -radius, 0.0F, -radius, 0.0F, 0.0F);
            vertex(pose, buffer, color, -radius, 0.0F, radius, 0.0F, 1.0F);
            vertex(pose, buffer, color, radius, 0.0F, radius, 1.0F, 1.0F);
            vertex(pose, buffer, color, radius, 0.0F, -radius, 1.0F, 0.0F);
            vertex(pose, buffer, color, radius, 0.0F, -radius, 1.0F, 0.0F);
            vertex(pose, buffer, color, radius, 0.0F, radius, 1.0F, 1.0F);
            vertex(pose, buffer, color, -radius, 0.0F, radius, 0.0F, 1.0F);
            vertex(pose, buffer, color, -radius, 0.0F, -radius, 0.0F, 0.0F);
        });
        poseStack.popPose();
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}

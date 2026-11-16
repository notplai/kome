package net.notplai.kome.effigy.client;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.effigy.AurelianEffigyBlockEntity;

/**
 * Draws the 3-block effigy's slowly expanding grey ground square and the golden diamond-lattice barrier that
 * collapses synced to 35% area over 5 minutes before crashing into the effigy, and then the blast and the beam into
 * the sky that follow the crash.
 */
public class AurelianEffigyRenderer implements BlockEntityRenderer<AurelianEffigyBlockEntity, AurelianEffigyRenderState> {
    private static final Identifier BARRIER_TEXTURE = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/effigy_barrier.png");
    private static final RenderType BARRIER = RenderTypes.eyes(BARRIER_TEXTURE);

    private static final float HALF_SIZE = AurelianEffigyBlockEntity.BARRIER_SIZE / 2.0F;
    private static final float TOTAL_SPAN = 4.0F * HALF_SIZE;
    /** The barrier is drawn in squares this wide, coloured at their corners; the glows below are all broader than this. */
    private static final float CELL = 2.0F;
    private static final float EDGE_GLOW_WIDTH = 6.0F;
    private static final float WAVE_WIDTH = 4.0F;
    private static final float PULSE_WIDTH = 5.0F;
    private static final int PULSE_COUNT = 4;
    private static final float PULSE_SPACING = TOTAL_SPAN / PULSE_COUNT;
    private static final int PULSE_TICKS = 480;
    private static final float EFFIGY_HEIGHT = 3.0F;
    private static final float GROUND_START_RADIUS = 0.45F;
    private static final float GROUND_MAX_RADIUS = 2.5F;
    private static final float GROUND_Y = 0.02F;
    private static final float MIN_AREA_FRACTION = AurelianEffigyBlockEntity.MIN_AREA_FRACTION;
    private static final float MIN_BARRIER_SCALE = Mth.sqrt(MIN_AREA_FRACTION);
    private static final float BLAST_FLASH_TICKS = 30.0F;
    private static final float BEAM_FADE_TICKS = 14.0F;
    private static final float BEAM_HALF_WIDTH = 0.55F;
    private static final float EFFIGY_MIDDLE_Y = EFFIGY_HEIGHT / 2.0F;

    private static final int DIM_MESH = 0xDCD6C8;
    private static final int AMBER = 0xFFB82E;
    private static final int PALE_GOLD = 0xFFF7D6;
    private static final int WHITE = 0xFFFFFF;
    private static final int GROUND_DARK_GREY = 0x34373D;
    private static final int GROUND_LIGHT_GREY = 0x8F949E;
    private static final int GROUND_MID_GREY = 0x5A5D66;
    private static final int GROUND_WARM_GOLD = 0xE6B64E;

    public AurelianEffigyRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AurelianEffigyRenderState createRenderState() {
        return new AurelianEffigyRenderState();
    }

    @Override
    public void extractRenderState(
        AurelianEffigyBlockEntity blockEntity,
        AurelianEffigyRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        state.barrierAge = blockEntity.getBarrierAge(partialTicks);
        state.pulsePhase = level != null ? (Math.floorMod(level.getGameTime(), PULSE_TICKS) + partialTicks) / PULSE_TICKS : 0.0F;
    }

    @Override
    public void submit(AurelianEffigyRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        float age = state.barrierAge;
        if (age >= AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS) {
            if (age < AurelianEffigyBlockEntity.END_TICK) {
                submitAftermath(state, poseStack, submitNodeCollector, camera);
            }
            return;
        }

        float collapseEnd = AurelianEffigyBlockEntity.COLLAPSE_TICKS;
        float waitEnd = collapseEnd + AurelianEffigyBlockEntity.WAIT_BEFORE_CRASH_TICKS;

        float barrierScale;
        float groundRadius;
        float crashFlash;
        float alphaScale;
        if (age <= collapseEnd) {
            float p = Mth.clamp(age / collapseEnd, 0.0F, 1.0F);
            barrierScale = Mth.sqrt(Mth.lerp(p, 1.0F, MIN_AREA_FRACTION));
            groundRadius = Mth.lerp(p, GROUND_START_RADIUS, GROUND_MAX_RADIUS);
            crashFlash = 0.0F;
            alphaScale = 1.0F;
        } else if (age <= waitEnd) {
            float waitT = (age - collapseEnd) / AurelianEffigyBlockEntity.WAIT_BEFORE_CRASH_TICKS;
            barrierScale = MIN_BARRIER_SCALE;
            groundRadius = GROUND_MAX_RADIUS;
            crashFlash = 0.35F * waitT;
            alphaScale = 1.0F;
        } else {
            float crashT = Mth.clamp((age - waitEnd) / AurelianEffigyBlockEntity.CRASH_TICKS, 0.0F, 1.0F);
            float implode = 1.0F - crashT * crashT;
            barrierScale = MIN_BARRIER_SCALE * implode;
            groundRadius = GROUND_MAX_RADIUS * implode;
            crashFlash = 1.0F;
            alphaScale = 1.0F - crashT * crashT * crashT;
        }

        float pulsePhase = state.pulsePhase;

        if (groundRadius > 0.01F && alphaScale > 0.01F) {
            poseStack.pushPose();
            poseStack.translate(0.5F, GROUND_Y, 0.5F);
            submitNodeCollector.submitCustomGeometry(
                poseStack, RenderTypes.debugQuads(), (pose, buffer) -> groundSquare(pose, buffer, groundRadius, alphaScale, crashFlash)
            );
            submitNodeCollector.submitCustomGeometry(
                poseStack, GoldenLight.LIGHT, (pose, buffer) -> groundCoreGlow(pose, buffer, groundRadius, alphaScale, crashFlash)
            );
            poseStack.popPose();
        }

        if (barrierScale > 0.005F && alphaScale > 0.01F) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 1.5F, 0.5F);
            poseStack.scale(barrierScale, barrierScale, barrierScale);
            submitNodeCollector.submitCustomGeometry(
                poseStack, GoldenLight.LIGHT, (pose, buffer) -> barrier(pose, buffer, age, pulsePhase, crashFlash, alphaScale, true)
            );
            submitNodeCollector.submitCustomGeometry(
                poseStack, BARRIER, (pose, buffer) -> barrier(pose, buffer, age, pulsePhase, crashFlash, alphaScale, false)
            );
            poseStack.popPose();
        }
    }

    /**
     * What follows the crash: a golden blast, then a glow swelling in the effigy as its ash is drawn back in, then
     * the beam, which bursts out wide and at once narrows to a needle of light over a broad base.
     */
    private static void submitAftermath(AurelianEffigyRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        float sinceBlast = state.barrierAge - AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS;
        float beamAge = state.barrierAge - AurelianEffigyBlockEntity.BEAM_START_TICK;
        float gather = Mth.clamp((sinceBlast - AurelianEffigyBlockEntity.ASH_LINGER_TICKS) / AurelianEffigyBlockEntity.ASH_GATHER_TICKS, 0.0F, 1.0F);
        float beamFade = beamAge < 0.0F ? 0.0F : 1.0F - smooth((beamAge - (AurelianEffigyBlockEntity.BEAM_TICKS - BEAM_FADE_TICKS)) / BEAM_FADE_TICKS);

        // The beam is flat sheets of light turned to face the camera around the vertical axis, the flashes are discs turned fully towards it.
        float toCameraX = (float)(camera.pos.x - (state.blockPos.getX() + 0.5));
        float toCameraY = (float)(camera.pos.y - state.blockPos.getY()) - EFFIGY_MIDDLE_Y;
        float toCameraZ = (float)(camera.pos.z - (state.blockPos.getZ() + 0.5));
        float horizontalLength = Mth.sqrt(toCameraX * toCameraX + toCameraZ * toCameraZ);
        float rightX = horizontalLength < 1.0E-4F ? 1.0F : -toCameraZ / horizontalLength;
        float rightZ = horizontalLength < 1.0E-4F ? 0.0F : toCameraX / horizontalLength;
        float toCameraLength = Mth.sqrt(horizontalLength * horizontalLength + toCameraY * toCameraY);
        float forwardX = toCameraLength < 1.0E-4F ? 0.0F : toCameraX / toCameraLength;
        float forwardY = toCameraLength < 1.0E-4F ? 0.0F : toCameraY / toCameraLength;
        float forwardZ = toCameraLength < 1.0E-4F ? 1.0F : toCameraZ / toCameraLength;
        float upX = forwardY * rightZ;
        float upY = forwardZ * rightX - forwardX * rightZ;
        float upZ = -forwardY * rightX;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        submitNodeCollector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) -> {
            if (sinceBlast < BLAST_FLASH_TICKS) {
                float fade = 1.0F - sinceBlast / BLAST_FLASH_TICKS;
                // How far the blast has travelled: races out at first, then slows.
                float blast = 1.0F - Mth.cube(fade);
                float alpha = Mth.square(fade);
                float flashRadius = 4.0F + 30.0F * blast;
                GoldenLight.lightRing(pose, buffer, GROUND_Y * 2.0F, 2.0F + 40.0F * blast, 1.5F + 3.0F * blast, PALE_GOLD, alpha);
                GoldenLight.lightRing(pose, buffer, GROUND_Y * 2.0F, 1.0F + 22.0F * blast, 1.0F + 2.0F * blast, AMBER, alpha * 0.7F);
                GoldenLight.lightDisc(pose, buffer, EFFIGY_MIDDLE_Y, rightX, 0.0F, rightZ, upX, upY, upZ, flashRadius, 0.0F, AMBER, alpha);
                GoldenLight.lightDisc(pose, buffer, EFFIGY_MIDDLE_Y, rightX, 0.0F, rightZ, upX, upY, upZ, flashRadius * 0.6F, 0.5F, PALE_GOLD, alpha);
            }

            if (gather > 0.0F && beamAge < 0.0F) {
                GoldenLight.lightDisc(
                    pose, buffer, EFFIGY_MIDDLE_Y, rightX, 0.0F, rightZ, upX, upY, upZ, 0.6F + 2.4F * gather, 0.2F, PALE_GOLD, 0.9F * Mth.square(gather)
                );
            }

            if (beamAge >= 0.0F && beamFade > 0.0F) {
                float burst = 1.0F - smooth(beamAge / 8.0F);
                float width = BEAM_HALF_WIDTH * (1.0F + 2.5F * burst) * (1.0F + 0.06F * Mth.sin(beamAge * 0.9F));
                // A haze that flares out wide over the ground, and inside it a hard core with a needle of white down its middle.
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, width * 4.5F, 0.0F, 2.5F, 0.0F, AMBER, 0.45F * beamFade);
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, width * 2.0F, 0.0F, 1.6F, 0.35F, GROUND_WARM_GOLD, 0.8F * beamFade);
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, width * 0.8F, 0.0F, 0.8F, 0.8F, PALE_GOLD, beamFade);
                GoldenLight.lightSheet(pose, buffer, rightX, rightZ, 0.0F, 0.1F + 0.1F * burst, 0.0F, 0.0F, 0.5F, WHITE, beamFade);
                GoldenLight.lightDisc(pose, buffer, GROUND_Y * 3.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 7.0F + 4.0F * burst, 0.0F, GROUND_WARM_GOLD, 0.6F * beamFade);
                if (burst > 0.0F) {
                    // The beam going up is a smaller echo of the crash.
                    GoldenLight.lightRing(pose, buffer, GROUND_Y * 4.0F, 1.0F + 14.0F * (1.0F - burst), 0.8F, PALE_GOLD, Mth.square(burst));
                    GoldenLight.lightDisc(
                        pose, buffer, EFFIGY_MIDDLE_Y, rightX, 0.0F, rightZ, upX, upY, upZ, 3.0F + 8.0F * (1.0F - burst), 0.0F, PALE_GOLD, 0.7F * Mth.square(burst)
                    );
                }
            }
        });
        poseStack.popPose();
    }

    // The barrier reaches far past the effigy's own block, so it has to be drawn while that is off screen or far away.

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(AurelianEffigyBlockEntity blockEntity) {
        // Wide enough for the blast that follows the crash as well, and tall enough for the beam.
        return new AABB(blockEntity.getBlockPos()).inflate(48.0, HALF_SIZE + 2.0, 48.0).expandTowards(0.0, 320.0, 0.0);
    }

    private static void groundSquare(PoseStack.Pose pose, VertexConsumer buffer, float radius, float alphaScale, float crashFlash) {
        int darkRim = ARGB.color(0.92F * alphaScale, ARGB.srgbLerp(crashFlash * 0.4F, GROUND_DARK_GREY, PALE_GOLD));
        int lightRim = ARGB.color(0.92F * alphaScale, ARGB.srgbLerp(crashFlash * 0.5F, GROUND_LIGHT_GREY, PALE_GOLD));
        int midTrim = ARGB.color(0.86F * alphaScale, ARGB.srgbLerp(crashFlash * 0.5F, GROUND_MID_GREY, AMBER));
        int goldOuter = ARGB.color(0.68F * alphaScale, ARGB.srgbLerp(crashFlash, GROUND_WARM_GOLD, WHITE));
        int goldInner = ARGB.color(0.52F * alphaScale, ARGB.srgbLerp(crashFlash, AMBER, WHITE));

        squareRing(pose, buffer, radius * 0.88F, radius, lightRim, darkRim);
        squareRing(pose, buffer, radius * 0.72F, radius * 0.88F, midTrim, lightRim);
        squareRing(pose, buffer, radius * 0.58F, radius * 0.72F, goldOuter, midTrim);
        squareRing(pose, buffer, 0.0F, radius * 0.58F, goldInner, goldOuter);
    }

    private static void groundCoreGlow(PoseStack.Pose pose, VertexConsumer buffer, float radius, float alphaScale, float crashFlash) {
        int center = ARGB.color((0.36F + 0.34F * crashFlash) * alphaScale, ARGB.srgbLerp(crashFlash, PALE_GOLD, WHITE));
        int edge = ARGB.color(0.0F, AMBER);
        squareRing(pose, buffer, 0.0F, radius * 0.70F, center, edge);
    }

    /** Four trapezoid strips forming a square frame from inner half-width {@code r0} to outer half-width {@code r1}. */
    private static void squareRing(PoseStack.Pose pose, VertexConsumer buffer, float r0, float r1, int innerColor, int outerColor) {
        // North (-Z)
        quad(pose, buffer, -r1, 0.0F, -r1, r1, 0.0F, -r1, r0, 0.0F, -r0, -r0, 0.0F, -r0, outerColor, outerColor, innerColor, innerColor);
        // South (+Z)
        quad(pose, buffer, r1, 0.0F, r1, -r1, 0.0F, r1, -r0, 0.0F, r0, r0, 0.0F, r0, outerColor, outerColor, innerColor, innerColor);
        // West (-X)
        quad(pose, buffer, -r1, 0.0F, r1, -r1, 0.0F, -r1, -r0, 0.0F, -r0, -r0, 0.0F, r0, outerColor, outerColor, innerColor, innerColor);
        // East (+X)
        quad(pose, buffer, r1, 0.0F, -r1, r1, 0.0F, r1, r0, 0.0F, r0, r0, 0.0F, -r0, outerColor, outerColor, innerColor, innerColor);
    }

    private static void quad(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        int c0, int c1, int c2, int c3
    ) {
        buffer.addVertex(pose, x0, y0, z0).setColor(c0);
        buffer.addVertex(pose, x1, y1, z1).setColor(c1);
        buffer.addVertex(pose, x2, y2, z2).setColor(c2);
        buffer.addVertex(pose, x3, y3, z3).setColor(c3);
    }

    private static void barrier(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        float age,
        float pulsePhase,
        float crashFlash,
        float alphaScale,
        boolean glowPass
    ) {
        int cells = Math.round(2.0F * HALF_SIZE / CELL);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f d = new Vector3f();
        for (int axis = 0; axis < 3; axis++) {
            for (int side = -1; side <= 1; side += 2) {
                float out = side * HALF_SIZE;
                for (int i = 0; i < cells; i++) {
                    float s0 = -HALF_SIZE + i * CELL;
                    float s1 = s0 + CELL;
                    for (int j = 0; j < cells; j++) {
                        float t0 = -HALF_SIZE + j * CELL;
                        float t1 = t0 + CELL;
                        onFace(a, axis, out, s0, t0);
                        onFace(b, axis, out, s0, t1);
                        onFace(c, axis, out, s1, t1);
                        onFace(d, axis, out, s1, t0);
                        // One repeat of the lattice to a block.
                        cell(
                            pose, buffer, age, pulsePhase, crashFlash, alphaScale, glowPass,
                            a, b, c, d, s0 + HALF_SIZE, t0 + HALF_SIZE, s1 + HALF_SIZE, t1 + HALF_SIZE
                        );
                    }
                }
            }
        }
    }

    /** A point on the face of the box that lies {@code out} along {@code axis}, at (s, t) across it. */
    private static void onFace(Vector3f point, int axis, float out, float s, float t) {
        switch (axis) {
            case 0 -> point.set(out, s, t);
            case 1 -> point.set(s, out, t);
            default -> point.set(s, t, out);
        }
    }

    /** One square of the barrier, with corners a, b, c, d in order around it. */
    private static void cell(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        float age,
        float pulsePhase,
        float crashFlash,
        float alphaScale,
        boolean glowPass,
        Vector3f a,
        Vector3f b,
        Vector3f c,
        Vector3f d,
        float u0,
        float v0,
        float u1,
        float v1
    ) {
        int colorA = colorAt(a, age, pulsePhase, crashFlash, alphaScale, glowPass);
        int colorB = colorAt(b, age, pulsePhase, crashFlash, alphaScale, glowPass);
        int colorC = colorAt(c, age, pulsePhase, crashFlash, alphaScale, glowPass);
        int colorD = colorAt(d, age, pulsePhase, crashFlash, alphaScale, glowPass);
        if ((ARGB.alpha(colorA) | ARGB.alpha(colorB) | ARGB.alpha(colorC) | ARGB.alpha(colorD)) < 4) {
            return;
        }

        if (glowPass) {
            buffer.addVertex(pose, a.x, a.y, a.z).setColor(colorA);
            buffer.addVertex(pose, b.x, b.y, b.z).setColor(colorB);
            buffer.addVertex(pose, c.x, c.y, c.z).setColor(colorC);
            buffer.addVertex(pose, d.x, d.y, d.z).setColor(colorD);
            return;
        }

        vertex(pose, buffer, colorA, a, u0, v0);
        vertex(pose, buffer, colorB, b, u0, v1);
        vertex(pose, buffer, colorC, c, u1, v1);
        vertex(pose, buffer, colorD, d, u1, v0);
        // And its back, so it shows from both inside and outside the barrier.
        vertex(pose, buffer, colorD, d, u1, v0);
        vertex(pose, buffer, colorC, c, u1, v1);
        vertex(pose, buffer, colorB, b, u0, v1);
        vertex(pose, buffer, colorA, a, u0, v0);
    }

    /**
     * The barrier is a dim mesh that the glows light up towards amber and then pale gold. Nothing shows beyond the
     * wave spreading from top to bottom on a freshly placed effigy, and the wave's own front burns white.
     */
    private static int colorAt(Vector3f point, float age, float pulsePhase, float crashFlash, float alphaScale, boolean glowPass) {
        float distance = topToBottomDistance(point);
        float radius = age * AurelianEffigyBlockEntity.BARRIER_SPREAD_PER_TICK;
        if (distance > radius) {
            return 0;
        }

        float wave = ring(distance, radius, WAVE_WIDTH);
        float lit = Mth.clamp(Math.max(Math.max(bandGlow(point, distance, pulsePhase), wave), crashFlash), 0.0F, 1.0F);
        if (glowPass) {
            float alpha = 0.26F * lit * lit * alphaScale;
            return alpha < 0.01F ? 0 : ARGB.color(alpha, ARGB.srgbLerp(lit, AMBER, PALE_GOLD));
        }

        int rgb;
        float alpha;
        if (lit < 0.5F) {
            float k = lit * 2.0F;
            rgb = ARGB.srgbLerp(k, DIM_MESH, AMBER);
            alpha = Mth.lerp(k, 0.34F, 0.78F);
        } else {
            float k = (lit - 0.5F) * 2.0F;
            rgb = ARGB.srgbLerp(k, AMBER, PALE_GOLD);
            alpha = Mth.lerp(k, 0.78F, 0.98F);
        }
        float whiteMix = Math.max(wave > 0.45F ? (wave - 0.45F) / 0.55F : 0.0F, crashFlash * 0.65F);
        if (whiteMix > 0.0F) {
            rgb = ARGB.srgbLerp(whiteMix, rgb, WHITE);
        }
        return ARGB.color(alpha * alphaScale, rgb);
    }

    /**
     * Surface distance from the top center of the barrier box, spreading outward as a square across the ceiling,
     * down the four walls, and inward as a square across the floor.
     */
    private static float topToBottomDistance(Vector3f point) {
        float rXZ = Math.max(Math.abs(point.x), Math.abs(point.z));
        if (point.y >= HALF_SIZE - 0.001F) {
            return rXZ;
        }
        if (point.y <= -HALF_SIZE + 0.001F) {
            return TOTAL_SPAN - rXZ;
        }
        return HALF_SIZE + (HALF_SIZE - point.y);
    }

    private static float bandGlow(Vector3f point, float distance, float pulsePhase) {
        float x = Math.abs(point.x);
        float y = Math.abs(point.y);
        float z = Math.abs(point.z);
        // On the box's surface the largest of the three is HALF_SIZE, so the middle one tells how near an edge this is.
        float middle = x + y + z - Math.max(x, Math.max(y, z)) - Math.min(x, Math.min(y, z));
        float edge = 0.9F * (1.0F - smooth((HALF_SIZE - middle) / EDGE_GLOW_WIDTH));
        float offset = positiveMod(distance - pulsePhase * TOTAL_SPAN, PULSE_SPACING);
        float distToPulse = Math.min(offset, PULSE_SPACING - offset);
        float pulse = 0.78F * (1.0F - smooth(distToPulse / PULSE_WIDTH));
        return Mth.clamp(edge + pulse, 0.0F, 1.0F);
    }

    private static float positiveMod(float value, float modulus) {
        float mod = value % modulus;
        return mod < 0.0F ? mod + modulus : mod;
    }

    private static float ring(float x, float center, float halfWidth) {
        return 1.0F - smooth(Math.abs(x - center) / halfWidth);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, Vector3f point, float u, float v) {
        buffer.addVertex(pose, point.x, point.y, point.z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}

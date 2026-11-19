package net.notplai.kome.ritualsign.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.ritualsign.ShootingStarEntity;

/** A falling star: a textured radiant 4-point star head, a glowing bloom around it, and a streaming textured tail. */
public class ShootingStarRenderer extends EntityRenderer<ShootingStarEntity, ShootingStarRenderState> {
    private static final RenderType STAR = RitualSignClient.starGlow(
        "kome_shooting_star", Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/shooting_star.png")
    );
    private static final RenderType TAIL = RitualSignClient.starGlow(
        "kome_shooting_star_tail", Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/shooting_star_tail.png")
    );
    private static final float TAIL_LENGTH = 6.5F;
    private static final float TAIL_WIDTH = 0.55F;
    private static final float STAR_RADIUS = 0.95F;
    private static final float GLOW_RADIUS = 1.15F;

    public ShootingStarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ShootingStarRenderState createRenderState() {
        return new ShootingStarRenderState();
    }

    @Override
    public void extractRenderState(ShootingStarEntity entity, ShootingStarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.color = entity.getColor();
        state.age = entity.tickCount + partialTicks;
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-6) {
            state.direction.set(motion.normalize().toVector3f());
        }
    }

    @Override
    protected boolean affectedByCulling(ShootingStarEntity entity) {
        return false;
    }

    @Override
    public void submit(ShootingStarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vector3f toCamera = new Vector3f((float)(camera.pos.x - state.x), (float)(camera.pos.y - state.y), (float)(camera.pos.z - state.z));
        if (toCamera.lengthSquared() < 1.0E-4F) {
            return;
        }
        toCamera.normalize();
        // Axes of the plane facing the camera, gently rotating for the star head.
        Vector3f baseU = new Vector3f(toCamera).cross(Math.abs(toCamera.y) > 0.99F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        Vector3f baseV = new Vector3f(toCamera).cross(baseU).normalize();
        float spin = state.age * 0.14F;
        float cos = Mth.cos(spin);
        float sin = Mth.sin(spin);
        Vector3f u = new Vector3f(baseU).mul(cos).add(new Vector3f(baseV).mul(sin));
        Vector3f v = new Vector3f(baseV).mul(cos).sub(new Vector3f(baseU).mul(sin));
        // Across the tail, as seen from the camera.
        Vector3f side = new Vector3f(state.direction).cross(toCamera);
        if (side.lengthSquared() < 1.0E-6F) {
            side.set(baseU);
        }
        side.normalize();

        int rgb = state.color;
        int pale = ARGB.srgbLerp(0.55F, rgb, 0xFFFFFF);
        float twinkle = 0.88F + 0.12F * Mth.sin(state.age * 2.3F);
        Vector3f tail = new Vector3f(state.direction).mul(-TAIL_LENGTH);

        collector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) ->
            GoldenLight.lightDisc(pose, buffer, 0.0F, baseU.x, baseU.y, baseU.z, baseV.x, baseV.y, baseV.z, GLOW_RADIUS * twinkle, 0.0F, rgb, 0.55F)
        );
        collector.submitCustomGeometry(poseStack, TAIL, (pose, buffer) -> {
            texturedTail(pose, buffer, side, tail, TAIL_WIDTH * 1.9F, rgb, 0.55F);
            texturedTail(pose, buffer, side, tail, TAIL_WIDTH * 0.95F, pale, 0.95F);
        });
        collector.submitCustomGeometry(poseStack, STAR, (pose, buffer) -> {
            starQuad(pose, buffer, u, v, STAR_RADIUS * twinkle, rgb, 0.92F);
            starQuad(pose, buffer, u, v, STAR_RADIUS * 0.62F, 0xFFFFFF, 0.95F);
        });
        super.submit(state, poseStack, collector, camera);
    }

    private static void texturedTail(PoseStack.Pose pose, VertexConsumer buffer, Vector3f side, Vector3f tail, float headHalfWidth, int rgb, float alpha) {
        int headColor = ARGB.color(GoldenLight.alpha(alpha), rgb);
        int tipColor = ARGB.color(0, rgb);
        float tipHalfWidth = headHalfWidth * 0.28F;
        starVertex(pose, buffer, -side.x * headHalfWidth, -side.y * headHalfWidth, -side.z * headHalfWidth, headColor, 0.0F, 0.0F);
        starVertex(pose, buffer, side.x * headHalfWidth, side.y * headHalfWidth, side.z * headHalfWidth, headColor, 1.0F, 0.0F);
        starVertex(pose, buffer, tail.x + side.x * tipHalfWidth, tail.y + side.y * tipHalfWidth, tail.z + side.z * tipHalfWidth, tipColor, 1.0F, 1.0F);
        starVertex(pose, buffer, tail.x - side.x * tipHalfWidth, tail.y - side.y * tipHalfWidth, tail.z - side.z * tipHalfWidth, tipColor, 0.0F, 1.0F);
    }

    private static void starQuad(PoseStack.Pose pose, VertexConsumer buffer, Vector3f u, Vector3f v, float radius, int rgb, float alpha) {
        int color = ARGB.color(GoldenLight.alpha(alpha), rgb);
        starVertex(pose, buffer, (-u.x - v.x) * radius, (-u.y - v.y) * radius, (-u.z - v.z) * radius, color, 0.0F, 0.0F);
        starVertex(pose, buffer, (u.x - v.x) * radius, (u.y - v.y) * radius, (u.z - v.z) * radius, color, 1.0F, 0.0F);
        starVertex(pose, buffer, (u.x + v.x) * radius, (u.y + v.y) * radius, (u.z + v.z) * radius, color, 1.0F, 1.0F);
        starVertex(pose, buffer, (-u.x + v.x) * radius, (-u.y + v.y) * radius, (-u.z + v.z) * radius, color, 0.0F, 1.0F);
    }

    private static void starVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color, float u, float v) {
        buffer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}

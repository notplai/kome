package net.notplai.kome.ritualsign.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.ritualsign.SpellSignEntity;

/** A spell's sign in the sky: it spins open facing the target, blazes as it lets its magic go, and fades. */
public class SpellSignRenderer extends EntityRenderer<SpellSignEntity, SpellSignRenderState> {
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);

    public SpellSignRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SpellSignRenderState createRenderState() {
        return new SpellSignRenderState();
    }

    @Override
    public void extractRenderState(SpellSignEntity entity, SpellSignRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.age = entity.tickCount + partialTicks;
        state.spell = entity.getSpell();
        state.color = entity.getColor();
        state.facing.set(entity.getFacing());
    }

    @Override
    protected boolean affectedByCulling(SpellSignEntity entity) {
        return false;
    }

    @Override
    public void submit(SpellSignRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age;
        float open = 1.0F - Mth.cube(1.0F - Mth.clamp(age / SpellSignEntity.OPEN_TICKS, 0.0F, 1.0F));
        float fade = Mth.clamp((SpellSignEntity.LIFE_TICKS - age) / 8.0F, 0.0F, 1.0F);
        // A blaze of light as the star leaves it.
        float blaze = Math.max(0.0F, 1.0F - Math.abs(age - SpellSignEntity.FIRE_TICK) / 4.0F);
        float radius = SpellSignEntity.RADIUS * open * (1.0F + 0.15F * blaze);
        float angle = age * 9.0F;
        int rgb = state.color;

        poseStack.pushPose();
        // Turned so that its face looks down at the target.
        poseStack.rotate(new Quaternionf().rotationTo(UP, state.facing));
        RitualSigns.submit(poseStack, collector, radius, angle, state.spell, ARGB.srgbLerp(0.35F * blaze, rgb, 0xFFFFFF), fade);
        float glow = (0.25F + 0.6F * blaze) * fade * open;
        collector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) -> {
            GoldenLight.lightDisc(pose, buffer, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, radius * 1.3F, 0.0F, rgb, glow);
            GoldenLight.lightDisc(pose, buffer, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, radius * 0.35F, 0.2F, 0xFFFFFF, blaze * fade);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

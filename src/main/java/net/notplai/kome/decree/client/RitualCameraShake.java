package net.notplai.kome.decree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.decree.RitualEntity;

/** Shakes the camera of everyone near a ritual's blast, harder the closer they are to it. */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class RitualCameraShake {
    /** Distance in blocks at which the blast can no longer be felt. */
    private static final float RANGE = 64.0F;
    private static final float DURATION_TICKS = 20.0F;
    /** Degrees the camera is thrown about when standing right at the blast. */
    private static final float MAX_ANGLE = 2.5F;

    private RitualCameraShake() {
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        float shake = 0.0F;
        float time = 0.0F;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RitualEntity ritual && ritual.hasExploded()) {
                float sinceBlast = ritual.getAge(event.getPartialTick()) - RitualEntity.KILL_TICK;
                float distance = (float)Math.sqrt(ritual.distanceToSqr(event.getCamera().position()));
                if (sinceBlast >= 0.0F && sinceBlast < DURATION_TICKS && distance < RANGE) {
                    float strength = Mth.square(1.0F - distance / RANGE) * Mth.square(1.0F - sinceBlast / DURATION_TICKS);
                    if (strength > shake) {
                        shake = strength;
                        time = sinceBlast;
                    }
                }
            }
        }

        if (shake > 0.0F) {
            float angle = shake * MAX_ANGLE;
            event.setYaw(event.getYaw() + angle * Mth.sin(time * 4.3F));
            event.setPitch(event.getPitch() + angle * Mth.sin(time * 5.9F + 1.3F));
            event.setRoll(event.getRoll() + angle * 0.6F * Mth.sin(time * 3.7F + 2.1F));
        }
    }
}

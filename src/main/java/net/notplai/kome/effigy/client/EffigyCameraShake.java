package net.notplai.kome.effigy.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.effigy.AurelianEffigyBlockEntity;

/**
 * Shakes the camera of everyone near an effigy twice: hard when its barrier crashes into it, and a little
 * when its beam goes up. Effigys report their age here every tick; one that stops reporting is forgotten.
 */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class EffigyCameraShake {
    /** Distance in blocks at which the crash can no longer be felt. */
    private static final float RANGE = 96.0F;
    private static final float BLAST_TICKS = 30.0F;
    private static final float BEAM_SHAKE_TICKS = 16.0F;
    /** Degrees the camera is thrown about when standing right at the effigy as the barrier crashes. */
    private static final float MAX_ANGLE = 3.0F;
    private static final float BEAM_STRENGTH = 0.3F;

    private static final Map<BlockPos, Report> EFFIGYS = new HashMap<>();

    private EffigyCameraShake() {
    }

    static void track(BlockPos pos, long age, long gameTime) {
        if (age >= AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS) {
            EFFIGYS.put(pos.immutable(), new Report(age, gameTime));
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || EFFIGYS.isEmpty()) {
            EFFIGYS.clear();
            return;
        }

        long now = level.getGameTime();
        Vec3 camera = event.getCamera().position();
        float shake = 0.0F;
        float time = 0.0F;
        for (Iterator<Map.Entry<BlockPos, Report>> effigys = EFFIGYS.entrySet().iterator(); effigys.hasNext(); ) {
            Map.Entry<BlockPos, Report> effigy = effigys.next();
            long sinceReport = now - effigy.getValue().gameTime();
            if (sinceReport < 0L || sinceReport > 2L) {
                effigys.remove();
                continue;
            }

            float distance = (float)Math.sqrt(AurelianEffigyBlockEntity.barrierCenter(effigy.getKey()).distanceToSqr(camera));
            if (distance >= RANGE) {
                continue;
            }

            float age = effigy.getValue().age() + sinceReport + event.getPartialTick();
            float sinceBlast = age - AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS;
            float sinceBeam = age - AurelianEffigyBlockEntity.BEAM_START_TICK;
            float near = Mth.square(1.0F - distance / RANGE);
            float strength = 0.0F;
            if (sinceBlast >= 0.0F && sinceBlast < BLAST_TICKS) {
                strength = near * Mth.square(1.0F - sinceBlast / BLAST_TICKS);
            } else if (sinceBeam >= 0.0F && sinceBeam < BEAM_SHAKE_TICKS) {
                strength = BEAM_STRENGTH * near * Mth.square(1.0F - sinceBeam / BEAM_SHAKE_TICKS);
            }

            if (strength > shake) {
                shake = strength;
                time = age;
            }
        }

        if (shake > 0.0F) {
            float angle = shake * MAX_ANGLE;
            event.setYaw(event.getYaw() + angle * Mth.sin(time * 4.3F));
            event.setPitch(event.getPitch() + angle * Mth.sin(time * 5.9F + 1.3F));
            event.setRoll(event.getRoll() + angle * 0.6F * Mth.sin(time * 3.7F + 2.1F));
        }
    }

    private record Report(long age, long gameTime) {
    }
}

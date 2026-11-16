package net.notplai.kome.effigy.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.effigy.AurelianEffigyBlockEntity;

/** The particles of an effigy's end: the ash its barrier leaves behind, and sparks climbing the beam. */
public final class EffigyEffects {
    private static final int ASH_BURST = 600;
    private static final int ASH_PER_TICK = 5;
    /** The ash is strewn about as far from the effigy as the barrier reached when it crashed. */
    private static final float ASH_RADIUS = AurelianEffigyBlockEntity.BARRIER_SIZE / 2.0F * AurelianEffigyBlockEntity.barrierScale(AurelianEffigyBlockEntity.COLLAPSE_TICKS);
    private static final float ASH_HEIGHT = 10.0F;

    private EffigyEffects() {
    }

    public static void tick(Level level, BlockPos pos, long last, long age) {
        EffigyCameraShake.track(pos, age, level.getGameTime());
        RandomSource random = level.getRandom();
        Vec3 center = AurelianEffigyBlockEntity.barrierCenter(pos);
        long sinceBlast = age - AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS;
        if (sinceBlast >= 0 && sinceBlast < AurelianEffigyBlockEntity.ASH_LINGER_TICKS) {
            int flecks = AurelianEffigyBlockEntity.reached(last, age, AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS) ? ASH_BURST : ASH_PER_TICK;
            for (int i = 0; i < flecks; i++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                float distance = ASH_RADIUS * Mth.sqrt(random.nextFloat());
                // Thickest near the ground.
                float height = ASH_HEIGHT * Mth.square(random.nextFloat());
                level.addParticle(
                    Kome.EFFIGY_ASH.get(),
                    center.x + Mth.cos(angle) * distance,
                    pos.getY() + 0.1 + height,
                    center.z + Mth.sin(angle) * distance,
                    center.x,
                    center.y,
                    center.z
                );
            }
        }

        long beamAge = age - AurelianEffigyBlockEntity.BEAM_START_TICK;
        if (beamAge >= 0 && beamAge < AurelianEffigyBlockEntity.BEAM_TICKS - 12) {
            for (int i = 0; i < 3; i++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                float distance = random.nextFloat() * 1.2F;
                level.addParticle(ParticleTypes.WAX_ON, center.x + Mth.cos(angle) * distance, pos.getY() + 0.1, center.z + Mth.sin(angle) * distance, 0.0, 14.0, 0.0);
            }

            float angle = random.nextFloat() * Mth.TWO_PI;
            level.addParticle(
                ParticleTypes.END_ROD, center.x, pos.getY() + random.nextFloat() * 6.0F, center.z, Mth.cos(angle) * 0.08, 0.25, Mth.sin(angle) * 0.08
            );
        }
    }
}

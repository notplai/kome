package net.notplai.kome.cosmicray;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a running Cosmic Ray Beacon is pointed. It swings round after its holder's gaze rather than snapping to
 * it, so the crystal and the beam trail a little behind as they turn. The server and every client work it out
 * the same way, tick by tick, so the beam burns where it is drawn.
 */
public final class CosmicRayAim {
    /** How much of the way to where its holder looks the beam turns each tick. */
    private static final double FOLLOW = 0.22;
    // The integrated server and the client share a JVM, so each logical side keeps its own aims.
    private static final Map<UUID, Aim> SERVER_AIMS = new HashMap<>();
    private static final Map<UUID, Aim> CLIENT_AIMS = new HashMap<>();

    private CosmicRayAim() {
    }

    private static Map<UUID, Aim> aims(Entity entity) {
        return entity.level().isClientSide() ? CLIENT_AIMS : SERVER_AIMS;
    }

    /** Turns the beam a tick's worth further towards where its holder is looking. */
    static void tick(Entity entity) {
        Vec3 look = entity.getViewVector(1.0F);
        Aim aim = aims(entity).get(entity.getUUID());
        Vec3 now = aim == null ? look : aim.current.lerp(look, FOLLOW).normalize();
        aims(entity).put(entity.getUUID(), new Aim(aim == null ? now : aim.current, now));
    }

    static void forget(Entity entity) {
        aims(entity).remove(entity.getUUID());
    }

    /** The way the beam points, between ticks by {@code partialTicks}; straight ahead if it has not been aimed yet. */
    public static Vec3 of(Entity entity, float partialTicks) {
        Aim aim = aims(entity).get(entity.getUUID());
        if (aim == null) {
            return entity.getViewVector(partialTicks);
        }
        return new Vec3(
            Mth.lerp(partialTicks, aim.previous.x, aim.current.x),
            Mth.lerp(partialTicks, aim.previous.y, aim.current.y),
            Mth.lerp(partialTicks, aim.previous.z, aim.current.z)
        ).normalize();
    }

    private record Aim(Vec3 previous, Vec3 current) {
    }
}

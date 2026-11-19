package net.notplai.kome.cosmicray;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.KomeDamageTypes;

/**
 * Everything caught in a Cosmic Ray Beacon's beam is stuck in it until the beam is over: it cannot walk, jump or
 * fall, and is driven down the middle of the beam to where it lands and pinned there, kept on the beam however fast
 * it swings. A beam landing on nothing drives it out of its far end instead, and lets it fly.
 */
@EventBusSubscriber(modid = Kome.MODID)
public final class CosmicRayHold {
    private static final Identifier HELD = Identifier.fromNamespaceAndPath(Kome.MODID, "cosmic_ray_held");
    /** How fast what the beam holds is driven along it towards where it lands, in blocks a tick. */
    private static final double PUSH_SPEED = 2.5;
    /** Who is held, by whom, and how far out along the beam. Only ever touched on the server. */
    private static final Map<LivingEntity, Held> HOLDING = new WeakHashMap<>();

    private CosmicRayHold() {
    }

    /** Whether {@code entity} is already stuck in a beam. */
    static boolean isHeld(LivingEntity entity) {
        return HOLDING.containsKey(entity);
    }

    /** Catches {@code entity} in {@code shooter}'s beam, where it is now, {@code out} blocks from their eyes. */
    static void catchIn(Player shooter, LivingEntity entity, double out) {
        HOLDING.put(entity, new Held(shooter.getUUID(), out, entity.level().getGameTime()));
        setHeld(entity, true);
    }

    /** Burns everything {@code shooter}'s beam holds for {@code damage}. */
    static void burn(Player shooter, float damage) {
        // Copied first, as being hurt can kill, and dying lets go.
        for (Map.Entry<LivingEntity, Held> entry : List.copyOf(HOLDING.entrySet())) {
            LivingEntity entity = entry.getKey();
            if (entry.getValue().shooter().equals(shooter.getUUID()) && entity.isAlive() && entity.level() instanceof ServerLevel level) {
                // Every tick, not just once the last hurt has worn off.
                entity.setInvulnerableTime(0);
                entity.hurtServer(level, KomeDamageTypes.source(level, KomeDamageTypes.COSMIC_RAY, shooter, shooter), damage);
            }
        }
    }

    /**
     * Moves everything {@code shooter}'s beam holds a tick further: on down the beam pointing {@code along} from
     * {@code eye} until it is up against where it lands, {@code stop} blocks out, then kept there. With nowhere to
     * land, whatever is pushed past {@code reach} is let go flying.
     */
    static void carry(Player shooter, Vec3 eye, Vec3 along, double stop, double reach) {
        long now = shooter.level().getGameTime();
        Iterator<Map.Entry<LivingEntity, Held>> held = HOLDING.entrySet().iterator();
        while (held.hasNext()) {
            Map.Entry<LivingEntity, Held> entry = held.next();
            LivingEntity entity = entry.getKey();
            if (!entry.getValue().shooter().equals(shooter.getUUID())) {
                continue;
            }
            if (entity.isRemoved() || !entity.isAlive() || entity.level() != shooter.level()) {
                held.remove();
                setHeld(entity, false);
                continue;
            }

            // Short of the block it lands on by half its own size, so it is pressed against it rather than into it.
            double rest = stop - Math.max(entity.getBbWidth(), entity.getBbHeight()) / 2.0 - 0.1;
            double out = entry.getValue().out();
            out = Math.min(out + PUSH_SPEED, Math.max(out, rest));
            if (out > reach) {
                held.remove();
                setHeld(entity, false);
                entity.setDeltaMovement(along.scale(PUSH_SPEED));
                entity.syncVelocity = true;
                continue;
            }
            entry.setValue(new Held(shooter.getUUID(), out, now));

            // Put straight there each tick, rather than sent there, so that nothing can lag behind the beam or be
            // knocked out of it, however fast it swings.
            Vec3 feet = eye.add(along.scale(out)).subtract(0.0, entity.getBbHeight() / 2.0, 0.0);
            if (entity instanceof ServerPlayer player) {
                // Their view is left as it is: only where they stand is set.
                player.connection.teleport(new PositionMoveRotation(feet, Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            } else {
                entity.teleportTo(feet.x, feet.y, feet.z);
                entity.setDeltaMovement(Vec3.ZERO);
            }
            entity.resetFallDistance();
        }
    }

    /** Lets go of everything {@code shooter}'s beam holds, where it is. */
    static void releaseAll(Player shooter) {
        Iterator<Map.Entry<LivingEntity, Held>> held = HOLDING.entrySet().iterator();
        while (held.hasNext()) {
            Map.Entry<LivingEntity, Held> entry = held.next();
            if (entry.getValue().shooter().equals(shooter.getUUID())) {
                held.remove();
                release(entry.getKey());
            }
        }
    }

    /** Lets go of everything whose beam is gone without saying so, such as when its holder left the game. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<LivingEntity, Held>> held = HOLDING.entrySet().iterator();
        while (held.hasNext()) {
            Map.Entry<LivingEntity, Held> entry = held.next();
            LivingEntity entity = entry.getKey();
            if (entity.isRemoved() || entity.level().getGameTime() > entry.getValue().at() + 1) {
                held.remove();
                release(entity);
            }
        }
    }

    private static void release(LivingEntity entity) {
        setHeld(entity, false);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.syncVelocity = true;
    }

    private static void setHeld(LivingEntity entity, boolean held) {
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(HELD);
                if (held) {
                    instance.addTransientModifier(new AttributeModifier(HELD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
    }

    /** Whose beam it is stuck in, how far out along it, and when it was last carried. */
    private record Held(UUID shooter, double out, long at) {
    }
}

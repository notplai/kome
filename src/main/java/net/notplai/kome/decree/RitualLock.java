package net.notplai.kome.decree;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.notplai.kome.Kome;

/**
 * Tracks which entities are currently bound by a ritual. A hold only lasts a couple of ticks and has to be
 * renewed by the ritual every tick, so a ritual that disappears for any reason can never leave its target stuck.
 */
@EventBusSubscriber(modid = Kome.MODID)
public final class RitualLock {
    private static final int HOLD_TICKS = 2;
    // The integrated server and the client share a JVM, so each logical side keeps its own holds.
    private static final Map<UUID, Long> SERVER_HOLDS = new HashMap<>();
    private static final Map<UUID, Long> CLIENT_HOLDS = new HashMap<>();

    private RitualLock() {
    }

    private static Map<UUID, Long> holds(Entity entity) {
        return entity.level().isClientSide() ? CLIENT_HOLDS : SERVER_HOLDS;
    }

    public static void hold(Entity entity) {
        Map<UUID, Long> holds = holds(entity);
        long now = entity.level().getGameTime();
        if (holds.size() > 64) {
            holds.values().removeIf(expiry -> expiry < now);
        }

        holds.put(entity.getUUID(), now + HOLD_TICKS);
    }

    public static void release(Entity entity) {
        holds(entity).remove(entity.getUUID());
    }

    public static boolean isHeld(Entity entity) {
        Map<UUID, Long> holds = holds(entity);
        if (holds.isEmpty()) {
            return false;
        }

        Long expiry = holds.get(entity.getUUID());
        if (expiry == null) {
            return false;
        }

        long now = entity.level().getGameTime();
        // A hold dated further ahead than it can be granted comes from a different world's clock.
        if (expiry < now || expiry > now + HOLD_TICKS) {
            holds.remove(entity.getUUID());
            return false;
        }

        return true;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        // Server players are driven by their client's packets, the ritual pins them in place itself.
        if (!(entity instanceof ServerPlayer) && isHeld(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isHeld(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfHeld(event, event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfHeld(event, event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfHeld(event, event.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfHeld(event, event.getEntity());
    }

    private static void cancelIfHeld(ICancellableEvent event, Player player) {
        if (isHeld(player)) {
            event.setCanceled(true);
        }
    }
}

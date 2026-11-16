package net.notplai.kome.effigy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.notplai.kome.Kome;

/**
 * Knows where every effigy's barrier currently stands, so that it can be solid to the players it holds
 * and nothing inside it can be broken. Effigys report in every tick; one that stops reporting is forgotten, so a
 * effigy that disappears for any reason can never leave its barrier behind.
 */
@EventBusSubscriber(modid = Kome.MODID)
public final class EffigyBarriers {
    private static final int REPORT_TICKS = 2;
    /** Thick enough that nothing moving at any sane speed starts a tick on the far side of a wall. */
    private static final double WALL_THICKNESS = 8.0;
    /** How near its floor counts as standing on it, to the server's check for players hanging in the air. */
    private static final double STANDING_MARGIN = 0.6;
    // The integrated server and the client share a JVM, so each logical side keeps its own barriers.
    private static final Map<GlobalPos, Barrier> SERVER_BARRIERS = new HashMap<>();
    private static final Map<GlobalPos, Barrier> CLIENT_BARRIERS = new HashMap<>();

    private EffigyBarriers() {
    }

    private static Map<GlobalPos, Barrier> barriers(Level level) {
        return level.isClientSide() ? CLIENT_BARRIERS : SERVER_BARRIERS;
    }

    /** {@code captives} is the effigy's own set of the players it holds, read again whenever one of them moves. */
    static void report(Level level, BlockPos pos, long age, Set<UUID> captives) {
        Map<GlobalPos, Barrier> barriers = barriers(level);
        long now = level.getGameTime();
        barriers.values().removeIf(barrier -> Math.abs(now - barrier.gameTime) > REPORT_TICKS);
        barriers.put(GlobalPos.of(level.dimension(), pos.immutable()), new Barrier(age, now, captives));
    }

    /** Whether the block at {@code pos} is inside a barrier, or inside what a crashed barrier left of itself. */
    public static boolean protects(Level level, BlockPos pos) {
        Map<GlobalPos, Barrier> barriers = barriers(level);
        if (barriers.isEmpty()) {
            return false;
        }

        long now = level.getGameTime();
        for (Map.Entry<GlobalPos, Barrier> entry : barriers.entrySet()) {
            Barrier barrier = entry.getValue();
            if (entry.getKey().dimension() == level.dimension() && Math.abs(now - barrier.gameTime) <= REPORT_TICKS) {
                Vec3 center = AurelianEffigyBlockEntity.barrierCenter(entry.getKey().pos());
                double half = AurelianEffigyBlockEntity.barrierHalfSize(barrier.age);
                if (Math.abs(pos.getX() + 0.5 - center.x) <= half && Math.abs(pos.getY() + 0.5 - center.y) <= half && Math.abs(pos.getZ() + 0.5 - center.z) <= half) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * The barrier as a held player, or whatever they are riding, runs into it: the slabs lying just outside those
     * of its faces that {@code testArea} reaches. Handed to the game's own collision, they make the barrier as
     * solid from inside as blocks are, so a player stands and jumps on its floor and slides along its walls.
     */
    public static List<VoxelShape> walls(Entity entity, AABB testArea) {
        Map<GlobalPos, Barrier> barriers = barriers(entity.level());
        if (barriers.isEmpty()) {
            return List.of();
        }

        List<VoxelShape> walls = List.of();
        for (Map.Entry<GlobalPos, Barrier> entry : barriers.entrySet()) {
            if (holds(entry, entity)) {
                Vec3 center = AurelianEffigyBlockEntity.barrierCenter(entry.getKey().pos());
                double half = AurelianEffigyBlockEntity.barrierHalfSize(entry.getValue().age);
                double far = half + WALL_THICKNESS;
                for (int axis = 0; axis < 3; axis++) {
                    for (int side = -1; side <= 1; side += 2) {
                        AABB wall = new AABB(
                            center.x + (axis == 0 && side > 0 ? half : -far),
                            center.y + (axis == 1 && side > 0 ? half : -far),
                            center.z + (axis == 2 && side > 0 ? half : -far),
                            center.x + (axis == 0 && side < 0 ? -half : far),
                            center.y + (axis == 1 && side < 0 ? -half : far),
                            center.z + (axis == 2 && side < 0 ? -half : far)
                        );
                        if (wall.intersects(testArea)) {
                            if (walls.isEmpty()) {
                                walls = new ArrayList<>();
                            }

                            walls.add(Shapes.create(wall));
                        }
                    }
                }
            }
        }

        return walls;
    }

    /** Whether a held player, or what they are riding, is standing on the floor of their barrier. */
    public static boolean supports(Entity entity) {
        Map<GlobalPos, Barrier> barriers = barriers(entity.level());
        if (barriers.isEmpty()) {
            return false;
        }

        for (Map.Entry<GlobalPos, Barrier> entry : barriers.entrySet()) {
            if (holds(entry, entity)) {
                double floor = AurelianEffigyBlockEntity.barrierCenter(entry.getKey().pos()).y - AurelianEffigyBlockEntity.barrierHalfSize(entry.getValue().age);
                double above = entity.getBoundingBox().minY - floor;
                if (above > -STANDING_MARGIN && above < STANDING_MARGIN) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Whether this is a barrier that still stands, in the entity's own world, and has hold of it. */
    private static boolean holds(Map.Entry<GlobalPos, Barrier> entry, Entity entity) {
        Barrier barrier = entry.getValue();
        return barrier.age < AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS
            && entry.getKey().dimension() == entity.level().dimension()
            && Math.abs(entity.level().getGameTime() - barrier.gameTime) <= REPORT_TICKS
            && carriesCaptive(entity, barrier.captives);
    }

    private static boolean carriesCaptive(Entity entity, Set<UUID> captives) {
        if (entity instanceof Player) {
            return captives.contains(entity.getUUID());
        }

        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player && captives.contains(passenger.getUUID())) {
                return true;
            }
        }

        return false;
    }

    private static boolean canBreak(Player player, BlockPos pos) {
        return player.isCreative() || !protects(player.level(), pos);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!canBreak(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!canBreak(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> protects(event.getLevel(), pos));
    }

    @SubscribeEvent
    public static void onPistonMove(PistonEvent.Pre event) {
        // A piston may neither reach into a barrier nor shove about what is inside one.
        if (event.getLevel() instanceof Level level && (protects(level, event.getPos()) || protects(level, event.getFaceOffsetPos()))) {
            event.setCanceled(true);
        }
    }

    private record Barrier(long age, long gameTime, Set<UUID> captives) {
    }
}

package net.notplai.kome.ritualsign;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Shooting Star spell. Whatever its caster is looking at as they raise the sign becomes its target, and for as
 * long as they hold it, signs keep opening in the sky all round the target, each letting a star fall on it.
 */
public final class ShootingStars {
    /** How far off a target can be picked out. */
    private static final double RANGE = 64.0;
    /** Ticks between one sign opening in the sky and the next. */
    private static final int SIGN_INTERVAL = 4;
    /** How far out from the target, across the ground, the signs open. */
    private static final double MIN_SPREAD = 3.0;
    private static final double MAX_SPREAD = 7.0;
    /** How high above the target the signs open. */
    private static final double MIN_HEIGHT = 11.0;
    private static final double MAX_HEIGHT = 17.0;
    /** What each caster is casting at. Only ever touched on the server. */
    private static final Map<Player, Target> TARGETS = new WeakHashMap<>();

    private ShootingStars() {
    }

    /** Picks out what {@code caster} is looking at: the first living thing in their sight, or else the block. */
    static void begin(ServerLevel level, Player caster) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = level.clip(new ClipContext(from, from.add(caster.getLookAngle().scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
            .getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            caster,
            from,
            to,
            new AABB(from, to).inflate(1.0),
            entity -> entity instanceof LivingEntity && entity.isAlive() && entity.isPickable() && !entity.isSpectator(),
            from.distanceToSqr(to)
        );
        TARGETS.put(caster, new Target(hit == null ? null : hit.getEntity(), to));
    }

    static void tick(ServerLevel level, Player caster, int ticksCasting) {
        if (ticksCasting % SIGN_INTERVAL != 0) {
            return;
        }
        Target target = TARGETS.get(caster);
        if (target == null) {
            begin(level, caster);
            target = TARGETS.get(caster);
        }

        Vec3 at = target.at();
        double angle = level.getRandom().nextDouble() * Mth.TWO_PI;
        double spread = Mth.lerp(level.getRandom().nextDouble(), MIN_SPREAD, MAX_SPREAD);
        double height = Mth.lerp(level.getRandom().nextDouble(), MIN_HEIGHT, MAX_HEIGHT);
        Vec3 sky = at.add(Math.cos(angle) * spread, height, Math.sin(angle) * spread);
        level.addFreshEntity(new SpellSignEntity(level, caster, target, sky, Spell.SHOOTING_STAR));
    }

    static void end(Player caster) {
        TARGETS.remove(caster);
    }

    /** A living thing to follow, or else a fixed spot. */
    static final class Target {
        private final @Nullable Entity entity;
        private Vec3 last;

        Target(@Nullable Entity entity, Vec3 point) {
            this.entity = entity;
            this.last = entity == null ? point : entity.getBoundingBox().getCenter();
        }

        /** Where the target is now: the middle of whatever it is following, or where that was last seen once it is gone. */
        Vec3 at() {
            if (this.entity != null && this.entity.isAlive()) {
                this.last = this.entity.getBoundingBox().getCenter();
            }
            return this.last;
        }
    }
}

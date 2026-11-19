package net.notplai.kome.ritualsign;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Lightning spell. While its caster holds the sign up, a warning mark that only they can see lies on the ground
 * where they look, growing from one block across to six. When they let go it is put down there for everyone to see,
 * a sign opens in the sky above it, and a few seconds later a bolt as wide as the mark strikes everything inside it.
 */
public final class Lightning {
    /** How far off the mark can be put. */
    private static final double RANGE = 48.0;
    /** How far below where it is aimed the mark looks for ground to lie on. */
    private static final double MAX_DROP = 32.0;
    /** Ticks it must charge before it can be let go at all; let go sooner, it fizzles. */
    private static final int MIN_CHARGE_TICKS = 10;
    /** Ticks for the mark to grow from its smallest to its largest. */
    private static final int FULL_CHARGE_TICKS = 100;
    /** Ticks before the sign can be raised again after a bolt is let go. */
    private static final int COOLDOWN_TICKS = 40;
    /** What each caster is aiming. Only ever touched on the server. */
    private static final Map<Player, LightningMarkEntity> AIMING = new WeakHashMap<>();

    private Lightning() {
    }

    /** How wide the bolt is after charging for {@code ticksCasting}. The same on both sides. */
    public static float sizeAfter(float ticksCasting) {
        float charged = Mth.clamp(ticksCasting / FULL_CHARGE_TICKS, 0.0F, 1.0F);
        return Mth.lerp(charged, LightningMarkEntity.MIN_SIZE, LightningMarkEntity.MAX_SIZE);
    }

    static void begin(ServerLevel level, Player caster) {
        LightningMarkEntity old = AIMING.remove(caster);
        if (old != null) {
            old.discard();
        }
        LightningMarkEntity mark = new LightningMarkEntity(level, caster, aimedAt(level, caster), LightningMarkEntity.MIN_SIZE, true);
        level.addFreshEntity(mark);
        AIMING.put(caster, mark);
    }

    static void tick(ServerLevel level, Player caster, int ticksCasting) {
        LightningMarkEntity mark = AIMING.get(caster);
        if (mark == null || mark.isRemoved()) {
            begin(level, caster);
            mark = AIMING.get(caster);
        }
        mark.setPos(aimedAt(level, caster));
        mark.setSize(sizeAfter(ticksCasting));
    }

    /** On the caster's own side: only they hear it is fully charged, as only they can see the mark yet. */
    static void tickLocally(Level level, Player caster, int ticksCasting) {
        if (ticksCasting == FULL_CHARGE_TICKS && caster.isLocalPlayer()) {
            level.playLocalSound(caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.5F, false);
        }
    }

    /**
     * Puts the mark {@code caster} has been aiming down where everyone can see it, to strike a few seconds later, if
     * it has charged long enough; otherwise it fizzles out.
     */
    static void release(ServerLevel level, Player caster, ItemStack stack, int ticksCasting) {
        LightningMarkEntity aiming = AIMING.remove(caster);
        if (aiming == null || aiming.isRemoved()) {
            return;
        }
        aiming.discard();
        if (ticksCasting < MIN_CHARGE_TICKS) {
            return;
        }
        LightningMarkEntity mark = new LightningMarkEntity(level, caster, aiming.position(), sizeAfter(ticksCasting), false);
        level.addFreshEntity(mark);
        level.playSound(null, mark.getX(), mark.getY(), mark.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0F, 1.6F);
        caster.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
    }

    /** Whether {@code mark} is still what {@code caster} is aiming. */
    static boolean isAiming(Player caster, LightningMarkEntity mark) {
        return AIMING.get(caster) == mark && RitualSignItem.isCasting(caster);
    }

    /** Where {@code caster} is looking, dropped onto the ground beneath it. */
    private static Vec3 aimedAt(ServerLevel level, Player caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 from = caster.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        // Drawn back off whatever face it hit a little, so that from a wall it drops to the foot of the wall.
        Vec3 aimed = hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation().subtract(look.scale(0.05)).add(0.0, 0.05, 0.0);
        return ground(level, aimed, caster);
    }

    /** The ground straight down from {@code above}, or {@code above} itself if there is none near enough. */
    private static Vec3 ground(ServerLevel level, Vec3 above, Entity except) {
        BlockHitResult hit = level.clip(new ClipContext(above, above.subtract(0.0, MAX_DROP, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, except));
        return hit.getType() == HitResult.Type.MISS ? above : hit.getLocation();
    }
}

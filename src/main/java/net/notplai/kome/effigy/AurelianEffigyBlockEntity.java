package net.notplai.kome.effigy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.KomeDamageTypes;

/**
 * Runs an effigy's whole cycle off the game time it was placed at: the barrier closes in for five minutes holding
 * everyone inside it, crashes into the effigy and kills them, and then the ash it leaves is drawn back in and
 * sent up into the sky as a beam before the effigy disappears.
 */
public class AurelianEffigyBlockEntity extends BlockEntity {
    /** The barrier is a box this many blocks across, centred on the effigy. */
    public static final int BARRIER_SIZE = 50;
    /** How fast the barrier spreads from top to bottom on a freshly placed effigy, in blocks per tick. */
    public static final float BARRIER_SPREAD_PER_TICK = 1.0F;
    /** Ticks over which the ground square spreads to 2.5 blocks and the barrier shrinks to 35% area (5 minutes). */
    public static final int COLLAPSE_TICKS = 6000;
    /** The share of its area the barrier has left when it stops closing in. */
    public static final float MIN_AREA_FRACTION = 0.35F;
    /** Ticks to hold at 2.5 blocks / 35% area before the final crash (1 second). */
    public static final int WAIT_BEFORE_CRASH_TICKS = 20;
    /** Ticks for the barrier to crash inward into the effigy and disappear. */
    public static final int CRASH_TICKS = 8;
    /** The tick the barrier has crashed into the effigy, killing everything that was inside it. */
    public static final int TOTAL_LIFETIME_TICKS = COLLAPSE_TICKS + WAIT_BEFORE_CRASH_TICKS + CRASH_TICKS;
    /** Ticks the ash left by the crash hangs in the air. */
    public static final int ASH_LINGER_TICKS = 70;
    /** Ticks the ash then takes to be drawn back into the effigy. */
    public static final int ASH_GATHER_TICKS = 24;
    public static final int BEAM_START_TICK = TOTAL_LIFETIME_TICKS + ASH_LINGER_TICKS + ASH_GATHER_TICKS;
    /** Ticks the beam into the sky lasts. */
    public static final int BEAM_TICKS = 50;
    /** The tick the effigy and everything about it is gone. */
    public static final int END_TICK = BEAM_START_TICK + BEAM_TICKS;

    // The clock strikes three times at two minutes, twice at four, and once at five, just before the crash.
    private static final int FIRST_WARNING_TICK = 2 * 60 * 20;
    private static final int SECOND_WARNING_TICK = 4 * 60 * 20;
    private static final int FINAL_WARNING_TICK = COLLAPSE_TICKS;
    /** How far past the barrier a player's client may put them before the server puts them back itself. */
    private static final double CONFINE_TOLERANCE = 1.0;
    // How far up, and then how far in towards the effigy, the barrier looks for room for a player it has squeezed against the ground.
    private static final double SQUEEZE_STEP = 0.5;
    private static final int SQUEEZE_STEPS_UP = 24;
    private static final int SQUEEZE_STEPS_IN = 6;
    private static final int GOLD = 0xFFC83C;
    private static final String TAG_PLACED_AT = "placed_at";
    private static final String TAG_ABSENT = "absent";
    private static final String TAG_OWNER = "owner";
    private static final long NOT_PLACED = -1L;
    private static final long NOT_TICKED = Long.MIN_VALUE;

    /** Set on the client to add the particles and camera shake that go with the cycle. */
    public static @Nullable ClientEffects clientEffects;

    /** The game time the effigy started its cycle, or {@link #NOT_PLACED} before initialization. */
    private long placedAt = NOT_PLACED;
    private long lastAge = NOT_TICKED;
    /** The players the barrier is holding. On the client this is only ever the local player. */
    private final Set<UUID> captives = new HashSet<>();
    /** Players who logged out while the barrier held them. Logging out is no way out of it: see {@link #settleAbsent}. */
    private final Set<UUID> absent = new HashSet<>();
    /** Who placed it. Everything it kills is put down to them. */
    private @Nullable UUID owner;

    public AurelianEffigyBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(Kome.AURELIAN_EFFIGY_BLOCK_ENTITY.get(), worldPosition, blockState);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    /** Whoever placed the effigy, if they are still in the game. */
    private static @Nullable Entity findOwner(ServerLevel level, @Nullable UUID owner) {
        if (owner == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        return player != null ? player : level.getEntity(owner);
    }

    public void markPlaced(long gameTime) {
        this.placedAt = gameTime;
        this.setChanged();
    }

    /** Ticks since the effigy was placed, clamped to {@link #END_TICK}. */
    public float getBarrierAge(float partialTicks) {
        if (this.placedAt == NOT_PLACED || this.level == null) {
            return 0.0F;
        }

        long ticks = this.level.getGameTime() - this.placedAt;
        if (ticks < 0L) {
            return 0.0F;
        }
        return Math.min((float)END_TICK, ticks + partialTicks);
    }

    /** How large the barrier is next to its full size while it closes in, before the crash. */
    public static float barrierScale(float age) {
        return Mth.sqrt(Mth.lerp(Mth.clamp(age / COLLAPSE_TICKS, 0.0F, 1.0F), 1.0F, MIN_AREA_FRACTION));
    }

    /** How far the barrier reaches from its centre. It stays at its smallest through the crash and what follows. */
    public static double barrierHalfSize(float age) {
        return BARRIER_SIZE / 2.0 * barrierScale(age);
    }

    public static Vec3 barrierCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AurelianEffigyBlockEntity effigy) {
        if (effigy.placedAt == NOT_PLACED) {
            if (!level.isClientSide()) {
                effigy.markPlaced(level.getGameTime());
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
            return;
        }

        long age = level.getGameTime() - effigy.placedAt;
        // An effigy that has only just been loaded picks its cycle up where it is, without replaying what it missed.
        long last = effigy.lastAge == NOT_TICKED ? age - 1L : effigy.lastAge;
        effigy.lastAge = age;
        if (age < END_TICK) {
            EffigyBarriers.report(level, pos, age, effigy.captives);
        }

        if (age < TOTAL_LIFETIME_TICKS) {
            effigy.confinePlayers(level, pos, age);
        }

        if (level instanceof ServerLevel serverLevel) {
            effigy.serverTick(serverLevel, pos, last, age);
        } else if (clientEffects != null) {
            clientEffects.tick(level, pos, last, age);
        }
    }

    /** Whether the cycle has passed {@code tick} since the effigy last ticked. */
    public static boolean reached(long last, long age, long tick) {
        return last < tick && age >= tick;
    }

    // ---- Holding players in ----

    /**
     * Anyone found inside the barrier is held there from then on. {@link EffigyBarriers} makes it solid to their own
     * movement; this moves them along with the barrier as it closes in, and fetches back anyone that
     * something else has carried straight out of it. It runs on the client as well, for its own player, so that
     * the barrier closing in is a steady push instead of the server snapping them back now and then.
     */
    private void confinePlayers(Level level, BlockPos pos, long age) {
        Vec3 center = barrierCenter(pos);
        double half = barrierHalfSize(age);
        if (level instanceof ServerLevel serverLevel && !this.absent.isEmpty()) {
            // Whoever has logged back in is held again as if they had never left.
            if (this.absent.removeIf(player -> serverLevel.getServer().getPlayerList().getPlayer(player) != null && this.captives.add(player))) {
                this.setChanged();
            }
        }

        Set<UUID> held = new HashSet<>();
        for (Player player : level.players()) {
            if (level.isClientSide() && !player.isLocalPlayer()) {
                continue;
            }

            if (!player.isAlive() || player.isCreative() || player.isSpectator() || carriesSigil(player)) {
                continue;
            }

            Vec3 middle = player.getBoundingBox().getCenter();
            boolean inside = Math.abs(middle.x - center.x) <= half && Math.abs(middle.y - center.y) <= half && Math.abs(middle.z - center.z) <= half;
            if (inside || this.captives.contains(player.getUUID())) {
                held.add(player.getUUID());
                hold(player, center, half);
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            for (UUID captive : this.captives) {
                if (!held.contains(captive) && serverLevel.getServer().getPlayerList().getPlayer(captive) == null && this.absent.add(captive)) {
                    this.setChanged();
                }
            }
        }

        this.captives.clear();
        this.captives.addAll(held);
    }

    /**
     * Once the barrier has crashed, deals with the players who had logged out while it held them: any who are back
     * die now, the rest when they next join. This also runs for an effigy that slept through its own crash because
     * everyone near it had left.
     */
    private void settleAbsent(ServerLevel level) {
        for (UUID id : this.absent) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                EffigyCondemned.get(level.getServer()).add(id, this.owner);
            } else if (player.isAlive()) {
                slay(player.level(), player, this.owner);
            }
        }

        this.absent.clear();
        this.setChanged();
    }

    public static boolean carriesSigil(Player player) {
        return player.getInventory().contains(stack -> stack.is(Kome.AURELIAN_SIGIL));
    }

    private static void hold(Player player, Vec3 center, double half) {
        double reach = half - player.getBbWidth() / 2.0;
        double x = Mth.clamp(player.getX(), center.x - reach, center.x + reach);
        double y = Mth.clamp(player.getY(), center.y - half, center.y + half - player.getBbHeight());
        double z = Mth.clamp(player.getZ(), center.z - reach, center.z + reach);
        if (x == player.getX() && y == player.getY() && z == player.getZ()) {
            return;
        }

        Vec3 spot = freeSpotInside(player, new Vec3(x, y, z), center, half);
        x = spot.x;
        y = spot.y;
        z = spot.z;
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.distanceToSqr(x, y, z) > CONFINE_TOLERANCE * CONFINE_TOLERANCE) {
                if (serverPlayer.isPassenger()) {
                    serverPlayer.stopRiding();
                }

                serverPlayer.setDeltaMovement(Vec3.ZERO);
                serverPlayer.connection.teleport(x, y, z, serverPlayer.getYRot(), serverPlayer.getXRot());
            }
        } else if (!player.isPassenger()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(x == player.getX() ? motion.x : 0.0, y == player.getY() ? motion.y : 0.0, z == player.getZ() ? motion.z : 0.0);
            player.setPos(x, y, z);
        }
    }

    /**
     * Where the barrier puts a player it has pushed to {@code pushed}. That is the spot itself unless the ground is
     * in the way there, in which case the barrier lifts them over it, and failing that draws them in towards the
     * effigy until they fit. Only a player it can find no room for at all is pushed into the blocks.
     */
    private static Vec3 freeSpotInside(Player player, Vec3 pushed, Vec3 center, double half) {
        AABB box = player.getBoundingBox().move(pushed.subtract(player.position()));
        double headroom = center.y + half - box.maxY;
        Vec3 inward = new Vec3(center.x - pushed.x, 0.0, center.z - pushed.z);
        inward = inward.lengthSqr() < 1.0E-6 ? Vec3.ZERO : inward.normalize().scale(SQUEEZE_STEP);
        for (int in = 0; in <= SQUEEZE_STEPS_IN; in++) {
            for (int up = 0; up <= SQUEEZE_STEPS_UP && up * SQUEEZE_STEP <= headroom; up++) {
                Vec3 shift = inward.scale(in).add(0.0, up * SQUEEZE_STEP, 0.0);
                if (player.level().noCollision(player, box.move(shift))) {
                    return pushed.add(shift);
                }
            }
        }

        return pushed;
    }

    // ---- The cycle ----

    private void serverTick(ServerLevel level, BlockPos pos, long last, long age) {
        if (age >= TOTAL_LIFETIME_TICKS && !this.absent.isEmpty()) {
            this.settleAbsent(level);
        }

        if (age >= END_TICK) {
            playSound(level, pos, SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.6F);
            // The effigy is simply gone: taken out without telling its other parts, which would break and drop it.
            for (int offset = 2; offset >= 0; offset--) {
                BlockPos targetPos = pos.above(offset);
                if (level.getBlockState(targetPos).is(Kome.AURELIAN_EFFIGY.get())) {
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
            return;
        }

        if (reached(last, age, FIRST_WARNING_TICK)) {
            playSound(level, pos, Kome.EFFIGY_CLOCK_THREE.get(), 6.0F, 1.0F);
        } else if (reached(last, age, SECOND_WARNING_TICK)) {
            playSound(level, pos, Kome.EFFIGY_CLOCK_TWO.get(), 6.0F, 1.0F);
        } else if (reached(last, age, FINAL_WARNING_TICK)) {
            playSound(level, pos, Kome.EFFIGY_CLOCK_ONE.get(), 6.0F, 1.0F);
        }

        Vec3 center = barrierCenter(pos);
        if (reached(last, age, COLLAPSE_TICKS + WAIT_BEFORE_CRASH_TICKS)) {
            playSound(level, pos, SoundEvents.BEACON_DEACTIVATE, 6.0F, 0.5F);
        }

        if (reached(last, age, TOTAL_LIFETIME_TICKS)) {
            this.slayEverythingInside(level, center);
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 240, 0.4, 1.0, 0.4, 0.9);
            level.sendParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 120, 1.0, 1.5, 1.0, 12.0);
            playSound(level, pos, Kome.EFFIGY_CRASH.get(), 10.0F, 1.0F);
            playSound(level, pos, SoundEvents.TOTEM_USE, 2.0F, 0.6F);
        }

        if (reached(last, age, TOTAL_LIFETIME_TICKS + ASH_LINGER_TICKS)) {
            playSound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 4.0F, 0.5F);
            playSound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0F, 1.6F);
        }

        if (reached(last, age, BEAM_START_TICK)) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 80, 0.2, 1.0, 0.2, 0.4);
            playSound(level, pos, Kome.EFFIGY_BELL.get(), 8.0F, 1.0F);
        }
    }

    /** Kills every living thing in what was left of the barrier when it crashed, along with anyone it was still holding. */
    private void slayEverythingInside(ServerLevel level, Vec3 center) {
        double size = 2.0 * (barrierHalfSize(COLLAPSE_TICKS) + CONFINE_TOLERANCE);
        AABB box = AABB.ofSize(center, size, size, size);
        Set<UUID> captives = Set.copyOf(this.captives);
        this.captives.clear();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            slay(level, victim, this.owner);
        }

        for (UUID captive : captives) {
            if (level.getPlayerByUUID(captive) instanceof ServerPlayer player && player.isAlive()) {
                slay(level, player, this.owner);
            }
        }
    }

    /** Kills {@code victim} outright, past armour, resistance and even a totem, and puts it down to the effigy's owner. */
    static void slay(ServerLevel level, LivingEntity victim, @Nullable UUID owner) {
        if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }

        Entity by = findOwner(level, owner);
        victim.hurtServer(
            level, KomeDamageTypes.claimed(level, KomeDamageTypes.AURELIAN_EFFIGY, KomeDamageTypes.AURELIAN_EFFIGY_UNCLAIMED, null, by), Float.MAX_VALUE
        );
    }

    private static void playSound(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.placedAt = input.getLongOr(TAG_PLACED_AT, NOT_PLACED);
        this.absent.clear();
        input.read(TAG_ABSENT, UUIDUtil.CODEC_SET).ifPresent(this.absent::addAll);
        this.owner = input.read(TAG_OWNER, UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong(TAG_PLACED_AT, this.placedAt);
        if (!this.absent.isEmpty()) {
            output.store(TAG_ABSENT, UUIDUtil.CODEC_SET, this.absent);
        }
        if (this.owner != null) {
            output.store(TAG_OWNER, UUIDUtil.CODEC, this.owner);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @FunctionalInterface
    public interface ClientEffects {
        void tick(Level level, BlockPos pos, long last, long age);
    }
}

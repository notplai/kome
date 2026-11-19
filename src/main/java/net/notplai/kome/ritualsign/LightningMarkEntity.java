package net.notplai.kome.ritualsign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.notplai.kome.Kome;
import net.notplai.kome.KomeDamageTypes;

/**
 * Where a Lightning spell will strike: a warning mark on the ground, as wide as the bolt will be.
 * <p>
 * While its caster is still charging, the mark is only being aimed: only they can see it, and it follows where they
 * look. Once they let go, a new mark is put down where everyone can see it, the spell's sign opens in the sky above
 * it, and after a few seconds' warning the bolt falls from the sign. It stops at the first block in its way, so a
 * roof shelters whatever is under it. Where it lands, air and ash are blown out across the ground and the blocks are
 * left cracked, worst in the middle and the softer ones more; any cracked right through are broken.
 */
public class LightningMarkEntity extends Entity {
    /** How wide the mark, and the bolt, are, in blocks, uncharged and fully charged. */
    public static final float MIN_SIZE = 1.0F;
    public static final float MAX_SIZE = 6.0F;
    /** How high above the mark its sign opens. */
    public static final float SKY_HEIGHT = 20.0F;
    /** Ticks of warning between the mark being put down and the bolt falling. */
    public static final int WARNING_TICKS = 70;
    /** Ticks the bolt is seen for after it strikes. */
    public static final int STRIKE_TICKS = 12;
    /** Ticks the air and ash keep flowing out after it strikes. */
    private static final int AFTERMATH_TICKS = 40;
    /** Ticks the cracks it leaves stay at their worst, and ticks between each step of them fading after that. */
    private static final int CRACK_HOLD_TICKS = 100;
    private static final int CRACK_FADE_STEP_TICKS = 8;
    /** How hard a strike cracks a block, against how hard the block is, at the smallest and largest. */
    private static final float MIN_CRACK_STRENGTH = 1.5F;
    private static final float MAX_CRACK_STRENGTH = 5.0F;
    /** The last stage of cracking; a block cracked past it breaks. */
    private static final int FULL_CRACK = 9;
    /** Damage a bolt does for every block across it is, and on top of that. */
    private static final float DAMAGE_PER_BLOCK = 3.0F;
    private static final float BASE_DAMAGE = 4.0F;

    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(LightningMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LAND = SynchedEntityData.defineId(LightningMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(LightningMarkEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STRUCK = SynchedEntityData.defineId(LightningMarkEntity.class, EntityDataSerializers.BOOLEAN);

    private @Nullable Player caster;
    /** When, by this side's own count, it struck; or -1 if it has not yet. */
    private int struckAt = -1;
    /** The blocks it has cracked, and how badly. Only on the server. */
    private final List<Crack> cracks = new ArrayList<>();

    public LightningMarkEntity(EntityType<? extends LightningMarkEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    LightningMarkEntity(ServerLevel level, Player caster, Vec3 at, float size, boolean aiming) {
        this(Kome.LIGHTNING_MARK.get(), level);
        this.caster = caster;
        this.setPos(at);
        this.entityData.set(DATA_SIZE, size);
        this.entityData.set(DATA_AIMING, aiming);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_SIZE, MIN_SIZE);
        entityData.define(DATA_LAND, 0.0F);
        entityData.define(DATA_AIMING, false);
        entityData.define(DATA_STRUCK, false);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        // It slides after where its caster looks smoothly, rather than jumping a tick at a time.
        return SteppedInterpolationHandler.create(this);
    }

    /** How wide it is, in blocks. */
    public float getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    void setSize(float size) {
        this.entityData.set(DATA_SIZE, size);
    }

    /** How far above the mark the bolt landed: on the ground, or on whatever roof was over it. */
    public float getLand() {
        return this.entityData.get(DATA_LAND);
    }

    /** Whether it is still only being aimed, seen by its caster alone. */
    public boolean isAiming() {
        return this.entityData.get(DATA_AIMING);
    }

    public int getColor() {
        return Spell.LIGHTNING.color();
    }

    /** Ticks since it struck, or -1 if it has not yet. */
    public int ticksSinceStrike() {
        return this.struckAt < 0 ? -1 : this.tickCount - this.struckAt;
    }

    /** While it is being aimed, nobody but its caster is told it is there. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return !this.isAiming() || player == this.caster;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_STRUCK.equals(accessor) && this.entityData.get(DATA_STRUCK) && this.struckAt < 0) {
            this.struckAt = this.tickCount;
            if (this.level().isClientSide()) {
                this.level().setSkyFlashTime(2);
                this.blast();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel) {
            if (this.isAiming()) {
                if (this.caster == null || this.caster.isRemoved() || !Lightning.isAiming(this.caster, this)) {
                    this.discard();
                }
            } else if (this.struckAt < 0) {
                if (this.tickCount >= WARNING_TICKS) {
                    this.strike();
                }
            } else {
                this.fadeCracks(this.ticksSinceStrike());
                if (this.ticksSinceStrike() >= AFTERMATH_TICKS && this.cracks.isEmpty()) {
                    this.discard();
                }
            }
        } else if (this.struckAt < 0) {
            this.crackle();
        } else {
            this.flowOut(this.ticksSinceStrike());
        }
    }

    /**
     * Where a bolt falling from the sign straight down through {@code x}, {@code z} lands: the first block in its way.
     * Below the mark if there is nothing at all.
     */
    private BlockHitResult fall(double x, double z) {
        Vec3 from = new Vec3(x, this.getY() + SKY_HEIGHT, z);
        return this.level().clip(new ClipContext(from, new Vec3(x, this.getY() - 1.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    /** Whether the bolt reaches {@code victim}, with nothing between the sign and the top of it. */
    private boolean reaches(LivingEntity victim) {
        AABB box = victim.getBoundingBox();
        Vec3 top = new Vec3(victim.getX(), box.maxY, victim.getZ());
        Vec3 sky = new Vec3(victim.getX(), Math.max(box.maxY, this.getY() + SKY_HEIGHT), victim.getZ());
        return this.level().clip(new ClipContext(top, sky, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Lets the bolt fall: everything living under the sign that it can reach, with no roof in the way, is struck. */
    private void strike() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        BlockHitResult middle = this.fall(this.getX(), this.getZ());
        float land = middle.getType() == HitResult.Type.MISS ? 0.0F : (float)Math.max(0.0, middle.getLocation().y - this.getY());
        this.entityData.set(DATA_LAND, land);
        float size = this.getSize();
        double half = size / 2.0;
        AABB column = new AABB(this.getX() - half, this.getY() - 0.5, this.getZ() - half, this.getX() + half, this.getY() + SKY_HEIGHT, this.getZ() + half);
        DamageSource source = KomeDamageTypes.source(level, KomeDamageTypes.LIGHTNING, this, this.caster);
        float damage = BASE_DAMAGE + DAMAGE_PER_BLOCK * size;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, column)) {
            if (victim != this.caster && victim.isAlive() && !victim.isSpectator() && this.reaches(victim) && victim.hurtServer(level, source, damage)) {
                victim.igniteForSeconds(size);
            }
        }
        this.crackBlocks(level, size);

        this.entityData.set(DATA_STRUCK, true);
        this.struckAt = this.tickCount;
        level.playSound(null, this.getX(), this.getY() + land, this.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 4.0F + size, 1.0F);
    }

    /**
     * Cracks the blocks the bolt lands on across the mark: the softer the block, the bigger the bolt and the nearer
     * the middle, the worse, dying away to little or nothing at the edges. A block cracked right through breaks, as
     * if its caster had mined it; the hardest blocks are not even marked.
     */
    private void crackBlocks(ServerLevel level, float size) {
        double half = size / 2.0;
        float strength = Mth.lerp((size - MIN_SIZE) / (MAX_SIZE - MIN_SIZE), MIN_CRACK_STRENGTH, MAX_CRACK_STRENGTH);
        Set<BlockPos> seen = new HashSet<>();
        int minX = Mth.floor(this.getX() - half);
        int maxX = Mth.floor(this.getX() + half);
        int minZ = Mth.floor(this.getZ() - half);
        int maxZ = Mth.floor(this.getZ() + half);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = Mth.clamp(x + 0.5, this.getX() - half, this.getX() + half);
                double cz = Mth.clamp(z + 0.5, this.getZ() - half, this.getZ() + half);
                BlockHitResult hit = this.fall(cx, cz);
                if (hit.getType() == HitResult.Type.MISS || !seen.add(hit.getBlockPos())) {
                    continue;
                }
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                if (hardness < 0.0F) {
                    continue;
                }
                // Full in the middle, falling away to nothing just past the edge of the mark.
                double out = Math.hypot(pos.getX() + 0.5 - this.getX(), pos.getZ() + 0.5 - this.getZ()) / (half + 0.5);
                float falloff = 1.0F - (float)Mth.clamp(out, 0.0, 1.0);
                int progress = (int)(strength * falloff / Math.max(hardness, 0.2F) * 3.0F);
                if (progress > FULL_CRACK) {
                    if (this.mayBreak(level, pos, state)) {
                        level.destroyBlock(pos, true, this.caster);
                    } else {
                        progress = FULL_CRACK;
                    }
                }
                if (progress > 0 && progress <= FULL_CRACK) {
                    // Each crack needs a breaker of its own, or the next would take its place; these are far from any real entity's.
                    Crack crack = new Crack(pos, Integer.MIN_VALUE + (this.getId() & 0xFFFFF) * 128 + this.cracks.size(), progress);
                    this.cracks.add(crack);
                    level.destroyBlockProgress(crack.breaker, pos, progress);
                }
            }
        }
    }

    /** Whether its caster would be let break the block at {@code pos} themselves, by protection and by other mods. */
    private boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(this.caster instanceof ServerPlayer player) || player.isRemoved()) {
            return false;
        }
        return !CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled();
    }

    /** After a while, the cracks heal a stage at a time until they are gone. */
    private void fadeCracks(int since) {
        if (!(this.level() instanceof ServerLevel level) || since < CRACK_HOLD_TICKS || (since - CRACK_HOLD_TICKS) % CRACK_FADE_STEP_TICKS != 0) {
            return;
        }
        Iterator<Crack> cracks = this.cracks.iterator();
        while (cracks.hasNext()) {
            Crack crack = cracks.next();
            crack.progress--;
            // Below nothing takes the crack away altogether.
            level.destroyBlockProgress(crack.breaker, crack.pos, crack.progress);
            if (crack.progress < 0) {
                cracks.remove();
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            for (Crack crack : this.cracks) {
                level.destroyBlockProgress(crack.breaker, crack.pos, -1);
            }
            this.cracks.clear();
        }
        super.remove(reason);
    }

    // ---- Particles, on the client only ----

    /** Sparks crawling round the edge of the mark while it waits. */
    private void crackle() {
        if (this.tickCount % 2 != 0) {
            return;
        }
        double half = this.getSize() / 2.0;
        for (int spark = 0; spark < Math.max(1, (int)half); spark++) {
            double along = (this.random.nextDouble() * 2.0 - 1.0) * half;
            boolean alongX = this.random.nextBoolean();
            double side = this.random.nextBoolean() ? half : -half;
            this.level().addParticle(
                ParticleTypes.ELECTRIC_SPARK, this.getX() + (alongX ? along : side), this.getY() + 0.1, this.getZ() + (alongX ? side : along), 0.0, 0.05, 0.0
            );
        }
    }

    /** As the bolt lands: a flash, debris thrown up, and a ring of air, smoke and ash blown out across the ground. */
    private void blast() {
        Level level = this.level();
        float size = this.getSize();
        double half = size / 2.0;
        double x = this.getX();
        double y = this.getY() + this.getLand();
        double z = this.getZ();
        level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, ARGB.opaque(this.getColor())), x, y + 0.5, z, 0.0, 0.0, 0.0);

        int ring = 20 + (int)(10 * size);
        for (int i = 0; i < ring; i++) {
            double angle = (i + this.random.nextDouble()) * Mth.TWO_PI / ring;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double out = half * 0.4;
            double speed = (0.35 + 0.08 * size) * (0.7 + this.random.nextDouble() * 0.6);
            level.addParticle(ParticleTypes.CLOUD, x + dx * out, y + 0.15, z + dz * out, dx * speed, 0.02, dz * speed);
            level.addParticle(ParticleTypes.LARGE_SMOKE, x + dx * out, y + 0.3, z + dz * out, dx * speed * 0.5, 0.06, dz * speed * 0.5);
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.ASH, x + dx * out, y + 0.4, z + dz * out, dx * speed, 0.1, dz * speed);
            }
        }
        for (int gust = 0; gust < 2 + (int)size; gust++) {
            level.addParticle(
                ParticleTypes.GUST, x + (this.random.nextDouble() * 2.0 - 1.0) * half, y + 0.5, z + (this.random.nextDouble() * 2.0 - 1.0) * half, 0.0, 0.0, 0.0
            );
        }
        for (int smoke = 0; smoke < 3 + (int)size; smoke++) {
            level.addAlwaysVisibleParticle(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                true,
                x + (this.random.nextDouble() * 2.0 - 1.0) * half * 0.6,
                y + 0.2,
                z + (this.random.nextDouble() * 2.0 - 1.0) * half * 0.6,
                0.0,
                0.05 + this.random.nextDouble() * 0.05,
                0.0
            );
        }
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.1, z));
        if (!ground.isAir()) {
            ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int bit = 0; bit < 12 * (int)Math.ceil(size); bit++) {
                double angle = this.random.nextDouble() * Mth.TWO_PI;
                double out = this.random.nextDouble() * half;
                level.addParticle(debris, x + Math.cos(angle) * out, y + 0.1, z + Math.sin(angle) * out, Math.cos(angle) * 0.3, 0.4 + this.random.nextDouble() * 0.4, Math.sin(angle) * 0.3);
            }
        }
        for (int spark = 0; spark < 16 * (int)Math.ceil(size); spark++) {
            level.addParticle(
                ParticleTypes.ELECTRIC_SPARK,
                x + (this.random.nextDouble() * 2.0 - 1.0) * half,
                y + 0.2 + this.random.nextDouble() * 1.5,
                z + (this.random.nextDouble() * 2.0 - 1.0) * half,
                (this.random.nextDouble() - 0.5) * 0.6,
                this.random.nextDouble() * 0.4,
                (this.random.nextDouble() - 0.5) * 0.6
            );
        }
    }

    /** After the bolt, ash and smoke keep swirling out from where it landed, thinning away. */
    private void flowOut(int since) {
        float left = 1.0F - (float)since / AFTERMATH_TICKS;
        if (left <= 0.0F) {
            return;
        }
        double half = this.getSize() / 2.0;
        int wisps = 1 + (int)(this.getSize() * 1.5F * left);
        for (int wisp = 0; wisp < wisps; wisp++) {
            double angle = this.random.nextDouble() * Mth.TWO_PI;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double out = half * (0.3 + this.random.nextDouble() * 0.9);
            double speed = 0.06 + 0.1 * left;
            ParticleOptions type = switch (this.random.nextInt(4)) {
                case 0 -> ParticleTypes.WHITE_ASH;
                case 1 -> ParticleTypes.ASH;
                case 2 -> ParticleTypes.SMOKE;
                default -> ParticleTypes.CLOUD;
            };
            // Outwards, and round, as if stirred.
            this.level().addParticle(
                type,
                this.getX() + dx * out,
                this.getY() + this.getLand() + 0.1 + this.random.nextDouble() * 0.5,
                this.getZ() + dz * out,
                dx * speed - dz * speed * 0.6,
                0.02,
                dz * speed + dx * speed * 0.6
            );
        }
        if (since < 15 && this.random.nextInt(2) == 0) {
            this.level().addParticle(
                ParticleTypes.ELECTRIC_SPARK,
                this.getX() + (this.random.nextDouble() * 2.0 - 1.0) * half,
                this.getY() + this.getLand() + 0.2,
                this.getZ() + (this.random.nextDouble() * 2.0 - 1.0) * half,
                0.0,
                0.1,
                0.0
            );
        }
    }

    /** A block left cracked, the breaker its crack is shown under, and how badly. */
    private static final class Crack {
        private final BlockPos pos;
        private final int breaker;
        private int progress;

        private Crack(BlockPos pos, int breaker, int progress) {
            this.pos = pos;
            this.breaker = breaker;
            this.progress = progress;
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE_ENTITY;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }
}

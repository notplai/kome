package net.notplai.kome.ritualsign;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.KomeDamageTypes;

/**
 * A star let fall by a spell sign. It streaks straight down at the target and bursts on whatever it meets first,
 * hurting everything living close by, but never the one who cast it.
 */
public class ShootingStarEntity extends Projectile {
    private static final float DAMAGE = 3.0F;
    /** How far its burst reaches. */
    private static final double BURST_RADIUS = 1.8;
    private static final int LIFETIME_TICKS = 60;

    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ShootingStarEntity.class, EntityDataSerializers.INT);

    public ShootingStarEntity(EntityType<? extends ShootingStarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    ShootingStarEntity(ServerLevel level, @Nullable Entity caster, Vec3 from, Vec3 velocity, int color) {
        this(Kome.SHOOTING_STAR.get(), level);
        this.setOwner(caster);
        this.setPos(from);
        this.setDeltaMovement(velocity);
        this.entityData.set(DATA_COLOR, color);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_COLOR, Spell.SHOOTING_STAR.color());
    }

    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = this.position();
        Vec3 movement = this.getDeltaMovement();
        Vec3 to = from.add(movement);
        if (this.level() instanceof ServerLevel level) {
            BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) {
                to = blockHit.getLocation();
            }
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, this, from, to, this.getBoundingBox().expandTowards(movement).inflate(1.0), this::canHitEntity
            );
            if (entityHit != null) {
                to = entityHit.getLocation();
            }

            this.setPos(to);
            if (blockHit.getType() != HitResult.Type.MISS || entityHit != null) {
                this.burst(level, to);
            } else if (this.tickCount >= LIFETIME_TICKS) {
                this.discard();
            }
        } else {
            // Sparks shed along its tail.
            int color = this.getColor();
            for (int spark = 0; spark < 2; spark++) {
                Vec3 at = from.add(movement.scale(this.random.nextDouble()));
                this.level().addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0, 0.0, 0.0);
                this.level().addParticle(new DustParticleOptions(color, 1.2F), at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // It falls straight past its own caster.
        return entity instanceof LivingEntity && entity != this.getOwner() && super.canHitEntity(entity);
    }

    private void burst(ServerLevel level, Vec3 at) {
        Entity caster = this.getOwner();
        DamageSource source = KomeDamageTypes.source(level, KomeDamageTypes.SHOOTING_STAR, this, caster);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS))) {
            if (victim != caster && victim.isAlive() && !victim.isSpectator() && victim.getBoundingBox().distanceToSqr(at) <= BURST_RADIUS * BURST_RADIUS) {
                victim.hurtServer(level, source, DAMAGE);
            }
        }

        int color = this.getColor();
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, ARGB.opaque(color)), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.25);
        level.sendParticles(new DustParticleOptions(color, 1.6F), at.x, at.y, at.z, 20, 0.8, 0.4, 0.8, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5F, 1.3F + this.random.nextFloat() * 0.3F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2F, 1.2F);
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }
}

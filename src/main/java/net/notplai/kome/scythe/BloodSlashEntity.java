package net.notplai.kome.scythe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.KomeDamageTypes;

/**
 * A slash of the wielder's blood. It flies straight, cuts through every living thing in its path, and leaves each one
 * it wounds with the effects the wielder shed when throwing it.
 */
public class BloodSlashEntity extends Projectile {
    private static final float SPEED = 1.4F;
    private static final float DAMAGE = 6.0F;
    private static final int LIFETIME_TICKS = 30;
    private static final int BLOOD_COLOR = 0x7A0A12;
    private static final int DRIED_BLOOD_COLOR = 0x1A0406;

    private List<MobEffectInstance> carriedEffects = List.of();
    private final Set<Integer> wounded = new HashSet<>();
    private int life;

    public BloodSlashEntity(EntityType<? extends BloodSlashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public BloodSlashEntity(ServerLevel level, LivingEntity owner, List<MobEffectInstance> carriedEffects) {
        this(Kome.BLOOD_SLASH.get(), level);
        this.setOwner(owner);
        this.carriedEffects = carriedEffects;
        this.setPos(owner.getX(), owner.getEyeY() - 0.3, owner.getZ());
        this.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, SPEED, 0.0F);
        // Thrown blood flies where the wielder is looking, regardless of how they are moving.
        this.setDeltaMovement(owner.getLookAngle().scale(SPEED));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = this.position();
        Vec3 movement = this.getDeltaMovement();
        if (this.level() instanceof ServerLevel level) {
            Vec3 to = from.add(movement);
            BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean stopped = blockHit.getType() != HitResult.Type.MISS;
            if (stopped) {
                to = blockHit.getLocation();
            }

            this.woundAlong(level, to.subtract(from));
            this.setPos(to);
            if (stopped || ++this.life >= LIFETIME_TICKS) {
                level.sendParticles(new DustParticleOptions(BLOOD_COLOR, 1.5F), to.x, to.y, to.z, 30, 0.5, 0.3, 0.5, 0.0);
                this.discard();
            }
        } else {
            for (int step = 0; step < 2; step++) {
                Vec3 at = from.add(movement.scale(step * 0.5));
                this.level().addParticle(Kome.SANGUINE_SWEEP.get(), at.x, at.y, at.z, 0.0, 0.0, 0.0);
                for (int drop = 0; drop < 3; drop++) {
                    this.level()
                        .addParticle(
                            new DustParticleOptions(this.random.nextBoolean() ? BLOOD_COLOR : DRIED_BLOOD_COLOR, 1.0F + this.random.nextFloat() * 0.6F),
                            at.x + (this.random.nextDouble() - 0.5) * 1.4,
                            at.y + (this.random.nextDouble() - 0.5) * 0.4,
                            at.z + (this.random.nextDouble() - 0.5) * 1.4,
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }
        }
    }

    private void woundAlong(ServerLevel level, Vec3 movement) {
        Entity owner = this.getOwner();
        DamageSource source = KomeDamageTypes.source(level, KomeDamageTypes.BLOOD_SLASH, this, owner);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().expandTowards(movement).inflate(0.3))) {
            if (target == owner || !target.isAlive() || target.isSpectator() || !this.wounded.add(target.getId())) {
                continue;
            }

            if (target.hurtServer(level, source, DAMAGE)) {
                for (MobEffectInstance effect : this.carriedEffects) {
                    target.addEffect(new MobEffectInstance(effect), owner);
                }
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }
}

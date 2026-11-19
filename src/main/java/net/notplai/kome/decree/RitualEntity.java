package net.notplai.kome.decree;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;
import net.notplai.kome.KomeDamageTypes;

/**
 * The ritual itself: sits under its target, keeps it bound, and slays it once the beam reaches full strength.
 * The sign and the beam are drawn client-side from {@link #getAge(float)}.
 */
public class RitualEntity extends Entity {
    /** Ticks the sign takes to draw itself before the beam appears. */
    public static final int SIGN_TICKS = 20;
    /** Ticks the beam takes to grow to full strength. */
    public static final int BEAM_TICKS = 80;
    public static final int KILL_TICK = SIGN_TICKS + BEAM_TICKS;
    /** Ticks the sign and beam linger after the ritual ends. */
    public static final int FADE_TICKS = 24;
    public static final int GOLD = 0xFFC83C;

    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(RitualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID = SynchedEntityData.defineId(RitualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIGN_RADIUS = SynchedEntityData.defineId(RitualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_WIDTH = SynchedEntityData.defineId(RitualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_EXPLODED = SynchedEntityData.defineId(RitualEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int AGE_SYNC_INTERVAL = 10;
    // Client mobs are still gliding to their last server position when the ritual appears.
    private static final int CLIENT_HOLD_DELAY = 5;

    private int age;
    private @Nullable LivingEntity target;
    private @Nullable UUID casterId;

    public RitualEntity(EntityType<? extends RitualEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static boolean tryStart(ServerLevel level, Player caster, LivingEntity target) {
        if (!target.isAlive() || target == caster || RitualLock.isHeld(target)) {
            return false;
        }

        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        UUID casterId = caster.getUUID();
        if (!level.getEntities(Kome.RITUAL.get(), ritual -> casterId.equals(ritual.casterId) && ritual.age < KILL_TICK).isEmpty()) {
            return false;
        }

        target.stopRiding();
        RitualEntity ritual = new RitualEntity(Kome.RITUAL.get(), level);
        ritual.target = target;
        ritual.casterId = casterId;
        ritual.setPos(target.position());
        ritual.entityData.set(DATA_TARGET_ID, target.getId());
        ritual.entityData.set(DATA_SIGN_RADIUS, Math.max(3.0F, target.getBbWidth() * 2.0F + 1.5F));
        ritual.entityData.set(DATA_BEAM_WIDTH, Math.max(0.6F, target.getBbWidth()));
        level.addFreshEntity(ritual);
        RitualLock.hold(target);
        ritual.playSound(level, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.6F);
        ritual.playSound(level, SoundEvents.ENCHANTMENT_TABLE_USE, 1.5F, 0.7F);
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_AGE, 0);
        entityData.define(DATA_TARGET_ID, 0);
        entityData.define(DATA_SIGN_RADIUS, 3.0F);
        entityData.define(DATA_BEAM_WIDTH, 0.6F);
        entityData.define(DATA_EXPLODED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_AGE.equals(accessor) && this.level().isClientSide()) {
            this.age = this.entityData.get(DATA_AGE);
        }
    }

    public float getAge(float partialTicks) {
        return this.age + partialTicks;
    }

    public float getSignRadius() {
        return this.entityData.get(DATA_SIGN_RADIUS);
    }

    public float getBeamWidth() {
        return this.entityData.get(DATA_BEAM_WIDTH);
    }

    /** Whether the ritual ended by slaying its target, as opposed to fizzling out. */
    public boolean hasExploded() {
        return this.entityData.get(DATA_EXPLODED);
    }

    /** How strong the beam is, from 0 when it first appears to 1 at the moment it kills. */
    public static float beamPower(float age) {
        return Mth.clamp((age - SIGN_TICKS) / BEAM_TICKS, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        } else {
            this.clientTick();
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.age >= KILL_TICK) {
            if (++this.age > KILL_TICK + FADE_TICKS) {
                this.discard();
            }

            return;
        }

        LivingEntity target = this.target;
        if (target == null || !target.isAlive() || target.level() != level) {
            this.fizzle(level);
            return;
        }

        this.bind(target);
        this.age++;
        if (this.age == SIGN_TICKS) {
            this.playSound(level, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.8F);
        } else if (this.age > SIGN_TICKS && this.age % 6 == 0) {
            float power = beamPower(this.age);
            this.playSound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F + power * 2.0F, 0.5F + power * 1.5F);
        }

        if (this.age >= KILL_TICK) {
            this.complete(level, target);
        } else if (this.age % AGE_SYNC_INTERVAL == 0) {
            this.entityData.set(DATA_AGE, this.age);
        }
    }

    private void bind(LivingEntity target) {
        RitualLock.hold(target);
        target.setDeltaMovement(Vec3.ZERO);
        if (target instanceof ServerPlayer player) {
            if (player.isPassenger()) {
                player.stopRiding();
            }

            if (player.position().distanceToSqr(this.position()) > 0.01) {
                player.connection.teleport(this.getX(), this.getY(), this.getZ(), player.getYRot(), player.getXRot());
            }
        } else if (!target.position().equals(this.position())) {
            // Something other than the target itself moved it (a piston, a teleport), so the sign follows.
            this.setPos(target.position());
        }
    }

    private void complete(ServerLevel level, LivingEntity target) {
        ServerPlayer caster = this.casterId == null ? null : level.getServer().getPlayerList().getPlayer(this.casterId);
        this.endRitual(target);
        this.entityData.set(DATA_EXPLODED, true);
        // Past armour, resistance and even a totem, and put down to the caster.
        target.hurtServer(
            level, KomeDamageTypes.claimed(level, KomeDamageTypes.SOLARIS_DECREE, KomeDamageTypes.SOLARIS_DECREE_UNCLAIMED, this, caster), Float.MAX_VALUE
        );

        double centerY = this.getY() + target.getBbHeight() * 0.5;
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), this.getX(), centerY, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), centerY, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), centerY, this.getZ(), 160, 0.2, 0.4, 0.2, 0.6);
        level.sendParticles(ParticleTypes.WAX_ON, this.getX(), centerY, this.getZ(), 60, 0.6, 0.8, 0.6, 8.0);
        this.playSound(level, SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.6F);
        this.playSound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 1.3F);
        this.playSound(level, SoundEvents.LIGHTNING_BOLT_IMPACT, 3.0F, 1.4F);
        this.playSound(level, SoundEvents.TOTEM_USE, 1.5F, 1.6F);
        this.playSound(level, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.7F);
    }

    private void fizzle(ServerLevel level) {
        this.endRitual(this.target);
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 30, this.getSignRadius() * 0.4, 0.1, this.getSignRadius() * 0.4, 0.01);
        this.playSound(level, SoundEvents.BEACON_DEACTIVATE, 1.5F, 1.2F);
    }

    private void endRitual(@Nullable LivingEntity target) {
        if (target != null) {
            RitualLock.release(target);
        }

        this.age = KILL_TICK;
        this.entityData.set(DATA_AGE, this.age);
    }

    private void clientTick() {
        this.age++;
        if (this.age >= KILL_TICK) {
            return;
        }

        if (this.age > CLIENT_HOLD_DELAY) {
            Entity target = this.level().getEntity(this.entityData.get(DATA_TARGET_ID));
            if (target != null && target.isAlive()) {
                RitualLock.hold(target);
            }
        }

        float radius = this.getSignRadius();
        float power = beamPower(this.age);
        // Motes drifting up off the sign.
        for (int i = this.random.nextInt(2 + (int)(power * 4.0F)); i > 0; i--) {
            float angle = this.random.nextFloat() * Mth.TWO_PI;
            float distance = radius * Mth.sqrt(this.random.nextFloat());
            this.level()
                .addParticle(
                    ParticleTypes.WAX_ON,
                    this.getX() + Mth.cos(angle) * distance,
                    this.getY() + 0.1,
                    this.getZ() + Mth.sin(angle) * distance,
                    0.0,
                    4.0 + power * 12.0,
                    0.0
                );
        }

        // Sparks thrown out by the beam as it strengthens.
        if (power > 0.0F && this.random.nextFloat() < power * 1.5F) {
            float angle = this.random.nextFloat() * Mth.TWO_PI;
            float speed = 0.02F + power * 0.08F;
            this.level()
                .addParticle(
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + this.random.nextFloat() * 3.0F,
                    this.getZ(),
                    Mth.cos(angle) * speed,
                    0.05 + power * 0.2,
                    Mth.sin(angle) * speed
                );
        }
    }

    private void playSound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
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

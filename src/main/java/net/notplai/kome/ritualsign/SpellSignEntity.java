package net.notplai.kome.ritualsign;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.notplai.kome.Kome;

/**
 * A ritual sign opened in the sky by a spell. It draws itself facing down at the spell's target, lets its magic go
 * at it, and fades away again.
 */
public class SpellSignEntity extends Entity {
    /** Ticks it takes to draw itself. */
    public static final int OPEN_TICKS = 5;
    /** When it lets its magic go. */
    public static final int FIRE_TICK = 8;
    /** When it is gone. */
    public static final int LIFE_TICKS = 22;
    /** How far it reaches across, in blocks, once fully drawn. */
    public static final float RADIUS = 1.6F;
    /** How far it lets the star fall from the middle of the target, at most. */
    private static final double AIM_SPREAD = 1.2;
    private static final float STAR_SPEED = 1.6F;

    private static final EntityDataAccessor<Integer> DATA_SPELL = SynchedEntityData.defineId(SpellSignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_FACING = SynchedEntityData.defineId(SpellSignEntity.class, EntityDataSerializers.VECTOR3);

    private @Nullable Player caster;
    private ShootingStars.@Nullable Target target;

    public SpellSignEntity(EntityType<? extends SpellSignEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    SpellSignEntity(ServerLevel level, Player caster, ShootingStars.Target target, Vec3 at, Spell spell) {
        this(Kome.SPELL_SIGN.get(), level);
        this.caster = caster;
        this.target = target;
        this.setPos(at);
        this.entityData.set(DATA_SPELL, spell.ordinal());
        this.entityData.set(DATA_FACING, target.at().subtract(at).normalize().toVector3f());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_SPELL, Spell.SHOOTING_STAR.ordinal());
        entityData.define(DATA_FACING, new Vector3f(0.0F, -1.0F, 0.0F));
    }

    /** The spell whose sign and magic this entity draws. */
    public Spell getSpell() {
        Spell[] spells = Spell.values();
        int ordinal = this.entityData.get(DATA_SPELL);
        return ordinal >= 0 && ordinal < spells.length ? spells[ordinal] : Spell.SHOOTING_STAR;
    }

    /** The colour of the magic it is drawn in. */
    public int getColor() {
        return this.getSpell().color();
    }

    /** The way it faces: down at the target. */
    public Vector3fc getFacing() {
        return this.entityData.get(DATA_FACING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.tickCount == 1) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 1.4F + this.random.nextFloat() * 0.4F);
        } else if (this.tickCount == FIRE_TICK && this.target != null) {
            Vec3 aim = this.target.at().add(
                (this.random.nextDouble() * 2.0 - 1.0) * AIM_SPREAD, (this.random.nextDouble() * 2.0 - 1.0) * AIM_SPREAD * 0.5, (this.random.nextDouble() * 2.0 - 1.0) * AIM_SPREAD
            );
            Vec3 velocity = aim.subtract(this.position()).normalize().scale(STAR_SPEED);
            level.addFreshEntity(new ShootingStarEntity(level, this.caster, this.position(), velocity, this.getColor()));
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0F, 1.6F);
        } else if (this.tickCount >= LIFE_TICKS) {
            this.discard();
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

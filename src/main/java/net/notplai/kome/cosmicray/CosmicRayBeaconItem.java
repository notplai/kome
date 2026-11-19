package net.notplai.kome.cosmicray;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.notplai.kome.Kome;

/**
 * A beacon's core, coloured with the dye it was crafted with. One click sets the whole of it going: it is raised
 * ahead in both hands, wraps itself in a glowing barrier, and calls up an end crystal in front of it, then fires
 * its beam through the crystal for ten seconds, burning everything living it touches, which sticks in it and is
 * driven down to where the beam lands, to be held fast there until the end however the beam swings. Its holder
 * cannot move from where they stand, and turns slower and slower until they cannot turn at all; the crystal spins
 * ever faster, bands of light run down the beam, and it ends in a blast where it lands. Then the core must rest for five minutes.
 */
@EventBusSubscriber(modid = Kome.MODID)
public class CosmicRayBeaconItem extends Item {
    /** How far the beam reaches, in blocks. */
    public static final double RANGE = 256.0;
    /** How much it burns everything stuck in it, every tick: two and a half hearts a second. */
    private static final float DAMAGE = 0.25F;
    /** How wide the beam is, in blocks across, once it has come out through the crystal. It burns just as wide. */
    public static final float BEAM_WIDTH = 3.5F;
    /** About how far ahead of its holder's eyes the crystal floats, where the beam widens out. */
    private static final double CRYSTAL_REACH = 2.25;
    /** Half the width of the thin beam between the core and the crystal. */
    private static final double NARROW_HALF_WIDTH = 0.3;
    private static final int HUM_INTERVAL = 40;
    /** How long a stretch of the beam is searched at once for what it touches. */
    private static final double SEARCH_STRETCH = 16.0;

    // The charge, in ticks since the use key went down.
    /** Raised up ahead in both hands. */
    public static final int RAISE_TICKS = 8;
    /** The glowing barrier forms around the core. */
    public static final int GLOW_TICK = 8;
    /** The end crystal appears in front of it and starts to spin. */
    public static final int CRYSTAL_TICK = 24;
    /** The beam fires. */
    public static final int FIRE_TICK = 48;
    /** How long the beam lasts before it gives out on its own. */
    public static final int BEAM_TICKS = 200;
    public static final int END_TICK = FIRE_TICK + BEAM_TICKS;
    // The finale, counted back from the end.
    /** The crystal starts spinning faster and faster. */
    public static final int SPIN_UP_TICK = END_TICK - 80;
    /** Bands of light start running down the beam. */
    public static final int BAND_TICK = END_TICK - 40;
    /** Its holder can no longer turn at all, a little before the bands appear. */
    public static final int FROZEN_TICK = BAND_TICK - 10;
    /** How long the core must rest once it is over, however it ended. */
    private static final int COOLDOWN_TICKS = 300 * 20;
    /** How hard the blast where the beam lands is at the end: twice an end crystal, three times a block of TNT. */
    private static final float BOOM_POWER = 12.0F;
    private static final Identifier FREEZE = Identifier.fromNamespaceAndPath(Kome.MODID, "cosmic_ray_freeze");

    /** Set on the client, to show the crystal blowing apart. */
    public static @Nullable Consumer<CosmicRayBoom> boomListener;

    public CosmicRayBeaconItem(Item.Properties properties) {
        super(properties);
    }

    public static DyeColor colorOf(ItemStack stack) {
        return stack.getOrDefault(Kome.COSMIC_RAY_COLOR, DyeColor.WHITE);
    }

    public static ItemStack withColor(ItemStack stack, DyeColor color) {
        stack.set(Kome.COSMIC_RAY_COLOR, color);
        return stack;
    }

    /** The Cosmic Ray Beacon this entity has set off, or null if none is running. */
    public static @Nullable CosmicRayActivation activation(Entity entity) {
        return entity.hasData(Kome.COSMIC_RAY_ACTIVATION) ? entity.getData(Kome.COSMIC_RAY_ACTIVATION) : null;
    }

    /** Whether this entity has a Cosmic Ray Beacon running, charging or firing. */
    public static boolean isFiring(Entity entity) {
        return entity.hasData(Kome.COSMIC_RAY_ACTIVATION);
    }

    /** Ticks since this entity set its beacon off, between ticks by {@code partialTicks}; zero if none is running. */
    public static float ticksActive(Entity entity, float partialTicks) {
        CosmicRayActivation activation = activation(entity);
        return activation == null ? 0.0F : Math.max(0.0F, entity.level().getGameTime() - activation.startedAt() + partialTicks);
    }

    /** Whether this entity's Cosmic Ray Beacon has finished charging and is firing its beam. */
    public static boolean isBeaming(Entity entity) {
        return isFiring(entity) && ticksActive(entity, 0.0F) >= FIRE_TICK;
    }

    /** Where a beam fired by {@code shooter} ends: at the first block in its way, or as far as it reaches. */
    public static Vec3 beamEnd(Entity shooter, float partialTicks) {
        return beamHit(shooter, partialTicks).getLocation();
    }

    /** The block a beam fired by {@code shooter} lands on; a miss if it lands on nothing within its reach. */
    public static BlockHitResult beamHit(Entity shooter, float partialTicks) {
        Vec3 from = shooter.getEyePosition(partialTicks);
        Vec3 to = from.add(CosmicRayAim.of(shooter, partialTicks).scale(RANGE));
        return shooter.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
    }

    /** One click sets it off; from then on it charges and fires by itself. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (isFiring(player)) {
            return InteractionResult.FAIL;
        }

        // The client starts it straight away too, so that it does not wait on the server to begin drawing it.
        player.setData(Kome.COSMIC_RAY_ACTIVATION, new CosmicRayActivation(level.getGameTime(), hand, colorOf(player.getItemInHand(hand))));
        if (!level.isClientSide()) {
            playSound(level, player, SoundEvents.BEACON_POWER_SELECT, 1.8F);
        }
        return InteractionResult.CONSUME;
    }

    /** Drives a running beacon through its charge and beam, and ends it when its time is up or it leaves the hand. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        CosmicRayActivation activation = activation(player);
        if (activation == null) {
            return;
        }

        long ticks = player.level().getGameTime() - activation.startedAt();
        if (ticks >= END_TICK) {
            if (player.level() instanceof ServerLevel level) {
                boom(level, player, activation);
            }
            stop(player);
            return;
        }
        if (!(player.getItemInHand(activation.hand()).getItem() instanceof CosmicRayBeaconItem) || !player.isAlive()) {
            if (!player.level().isClientSide()) {
                playSound(player.level(), player, SoundEvents.BEACON_DEACTIVATE, 1.4F);
            }
            stop(player);
            return;
        }

        CosmicRayAim.tick(player);
        freeze(player);
        if (player.level() instanceof ServerLevel level) {
            tick(level, player, (int)ticks);
        }
    }

    private static void tick(ServerLevel level, Player shooter, int ticks) {
        if (ticks == BAND_TICK) {
            // The panes of light springing up across the beam.
            playSound(level, shooter, SoundEvents.BEACON_POWER_SELECT, 2.0F);
            playSound(level, shooter, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F);
            playSound(level, shooter, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F);
        }

        if (ticks == GLOW_TICK) {
            playSound(level, shooter, SoundEvents.BEACON_ACTIVATE, 1.5F);
        } else if (ticks == CRYSTAL_TICK) {
            playSound(level, shooter, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.4F);
            playSound(level, shooter, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F);
        } else if (ticks == FIRE_TICK) {
            playSound(level, shooter, SoundEvents.BEACON_POWER_SELECT, 0.7F);
            playSound(level, shooter, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.8F);
            playSound(level, shooter, SoundEvents.WARDEN_SONIC_BOOM, 1.4F);
        }

        if (ticks < FIRE_TICK) {
            return;
        }

        if ((ticks - FIRE_TICK) % HUM_INTERVAL == 0) {
            playSound(level, shooter, SoundEvents.BEACON_AMBIENT, 1.6F);
        }

        // Everything the beam as drawn touches gets stuck in it: the thin stretch up to the crystal, and the square
        // beam, as wide as it looks, from there on.
        Vec3 from = shooter.getEyePosition();
        Vec3 along = CosmicRayAim.of(shooter, 1.0F);
        BlockHitResult landing = beamHit(shooter, 1.0F);
        Vec3 to = landing.getLocation();
        Vec3 widens = from.add(along.scale(Math.min(CRYSTAL_REACH, from.distanceTo(to))));
        double wide = BEAM_WIDTH / 2.0;
        for (LivingEntity entity : caught(level, shooter, from, to, along)) {
            if (!CosmicRayHold.isHeld(entity)
                && (touches(entity.getBoundingBox().inflate(NARROW_HALF_WIDTH), from, widens) || touches(entity.getBoundingBox().inflate(wide), widens, to))) {
                CosmicRayHold.catchIn(shooter, entity, entity.getBoundingBox().getCenter().subtract(from).dot(along));
            }
        }
        // Nothing to be pushed up against when it lands on nothing: whatever it holds is pushed right out of its end.
        double stop = landing.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.distanceTo(to);
        CosmicRayHold.carry(shooter, from, along, stop, RANGE);
        CosmicRayHold.burn(shooter, DAMAGE);
    }

    /**
     * Everything living near enough the beam to be touched by it, looked for a stretch at a time, since a box round
     * the whole of a long beam pointed slantwise would take in a great deal of nothing.
     */
    private static Set<LivingEntity> caught(ServerLevel level, Player shooter, Vec3 from, Vec3 to, Vec3 along) {
        Set<LivingEntity> near = new LinkedHashSet<>();
        double length = from.distanceTo(to);
        for (double start = 0.0; start < length; start += SEARCH_STRETCH) {
            AABB stretch = new AABB(from.add(along.scale(start)), from.add(along.scale(Math.min(start + SEARCH_STRETCH, length))))
                .inflate(BEAM_WIDTH / 2.0 + 1.0);
            for (Entity entity : level.getEntities(shooter, stretch, entity -> entity instanceof LivingEntity && entity.isAlive() && entity.isPickable())) {
                near.add((LivingEntity)entity);
            }
        }
        return near;
    }

    private static boolean touches(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }

    /**
     * How far its holder's turning has slowed, from 0 as it is set off to 1 a little before the bands of light
     * appear, when the view will not move at all.
     */
    public static float frozen(Entity entity, float partialTicks) {
        float progress = Math.clamp(ticksActive(entity, partialTicks) / FROZEN_TICK, 0.0F, 1.0F);
        return isFiring(entity) ? progress * progress : 0.0F;
    }

    /** Its holder cannot walk, jump or be pushed from the moment it is set off; only gravity still pulls on them. */
    private static void freeze(Player player) {
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null && !instance.hasModifier(FREEZE)) {
                instance.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        player.setSprinting(false);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
    }

    /**
     * The beam ends in a blast where it lands, as hard as an end crystal blowing apart: it breaks blocks and hurts
     * everything near it, all of it put down to the one who set it off, who is themselves spared. Everyone near is
     * told so they can see and feel it.
     */
    private static void boom(ServerLevel level, Player shooter, CosmicRayActivation activation) {
        Vec3 target = beamEnd(shooter, 1.0F);
        ExplosionDamageCalculator sparingShooter = new ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return entity != shooter && super.shouldDamageEntity(explosion, entity);
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                return entity == shooter ? 0.0F : super.getKnockbackMultiplier(entity);
            }
        };
        // Let go first, so the blast throws them.
        CosmicRayHold.releaseAll(shooter);
        level.explode(shooter, level.damageSources().explosion(shooter, shooter), sparingShooter, target, BOOM_POWER, false, Level.ExplosionInteraction.TNT);
        // More blasts of smoke and fire all round it, sparks thrown far, and a roar heard from afar.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y, target.z, 8, 4.0, 2.5, 4.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 300, 0.5, 0.5, 0.5, 1.4);
        level.sendParticles(ParticleTypes.FLAME, target.x, target.y, target.z, 200, 2.0, 1.0, 2.0, 0.6);
        level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 16.0F, 0.5F);
        level.playSound(null, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 16.0F, 0.8F);
        level.playSound(null, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 8.0F, 0.6F);
        playSound(level, shooter, SoundEvents.BEACON_DEACTIVATE, 0.8F);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new CosmicRayBoom(target, activation.color().getTextureDiffuseColor() & 0xFFFFFF, shooter.getId()));
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(CosmicRayBoom.TYPE, CosmicRayBoom.STREAM_CODEC, (boom, context) -> {
            if (boomListener != null) {
                boomListener.accept(boom);
            }
        });
    }

    /** Over, however it ended: its holder is let go, and every Cosmic Ray Beacon they carry must rest. */
    private static void stop(Player player) {
        player.removeData(Kome.COSMIC_RAY_ACTIVATION);
        CosmicRayAim.forget(player);
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(FREEZE);
            }
        }
        // Whether or not the beacon is still in hand, so that putting it away cannot get round the rest.
        if (!player.level().isClientSide()) {
            CosmicRayHold.releaseAll(player);
            player.setData(Kome.COSMIC_RAY_RESTS_UNTIL, player.level().getGameTime() + COOLDOWN_TICKS);
            rest(player);
        }
    }

    /**
     * Puts the cooldown back on from what is remembered of it. The game forgets item cooldowns when a player leaves,
     * dies or comes back from the End, so without this the core could be used again straight after rejoining.
     */
    private static void rest(Player player) {
        long left = player.getData(Kome.COSMIC_RAY_RESTS_UNTIL) - player.level().getGameTime();
        if (left > 0) {
            player.getCooldowns().addCooldown(new ItemStack(Kome.COSMIC_RAY_BEACON.get()), (int)Math.min(left, COOLDOWN_TICKS));
        }
    }

    @SubscribeEvent
    public static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        rest(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        rest(event.getEntity());
    }

    /** Leaving the game while it runs ends it, and the core must rest just the same. */
    @SubscribeEvent
    public static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (isFiring(event.getEntity())) {
            stop(event.getEntity());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        DyeColor color = colorOf(stack);
        builder.accept(
            Component.translatable("item.kome.cosmic_ray_beacon.color", Component.translatable("color.minecraft." + color.getName()))
                .withColor(color.getTextColor())
        );
    }

    private static void playSound(Level level, Entity shooter, SoundEvent sound, float pitch) {
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
    }
}

package net.notplai.kome.ritualsign;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.notplai.kome.Kome;

/**
 * A ritual sign that turns slowly around the hand holding it, drawn in the colour of the magic it is bound to: the
 * spell its holder has picked from those they carry. Held up, it casts that spell for as long as it is held; all that
 * while its caster can only creep along and turn slowly. Bound to the Sword spell it is no sign at all but a sword
 * of light, swung like a diamond sword.
 */
public class RitualSignItem extends Item {
    /** Longer than anyone will hold it. */
    private static final int USE_DURATION = 72000;
    /** What makes a diamond sword a sword, lent to the sign while it is one. */
    private static final List<DataComponentType<?>> SWORD_COMPONENTS = List.of(DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.TOOL, DataComponents.WEAPON);

    public RitualSignItem(Item.Properties properties) {
        super(properties);
    }

    /** The spell {@code stack} is bound to, or nothing if its holder has none picked. */
    public static @Nullable Spell spellOf(ItemStack stack) {
        return stack.get(Kome.SPELL);
    }

    /**
     * Binds {@code stack} to {@code spell}, or to nothing, making it a sword or no longer one as need be. If
     * {@code holder} is given, the change is seen and heard.
     */
    public static void bind(ItemStack stack, @Nullable Spell spell, @Nullable Player holder) {
        Spell was = spellOf(stack);
        if (was == spell) {
            return;
        }
        if (spell == null) {
            stack.remove(Kome.SPELL);
        } else {
            stack.set(Kome.SPELL, spell);
        }
        for (DataComponentType<?> type : SWORD_COMPONENTS) {
            if (spell == Spell.SWORD) {
                lend(type, stack);
            } else {
                stack.remove(type);
            }
        }

        if (holder != null && holder.level() instanceof ServerLevel level) {
            level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (spell == Spell.SWORD) {
                // The sign folds up into a blade.
                level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 1.4F);
                Vec3 hand = holder.getEyePosition().add(holder.getLookAngle().scale(0.6)).subtract(0.0, 0.4, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 12, 0.15, 0.25, 0.15, 0.04);
                level.sendParticles(new DustParticleOptions(spell.color(), 1.0F), hand.x, hand.y, hand.z, 16, 0.2, 0.3, 0.2, 0.0);
            }
        }
    }

    private static <T> void lend(DataComponentType<T> type, ItemStack stack) {
        T value = Items.DIAMOND_SWORD.components().get(type);
        if (value != null) {
            stack.set(type, value);
        }
    }

    /** Whether {@code entity} is holding a Ritual Sign up and casting with it. */
    public static boolean isCasting(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof RitualSignItem;
    }

    /** Kept bound to whatever spell its holder has picked. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (owner instanceof Player player && !(player.isUsingItem() && player.getUseItem() == stack)) {
            bind(stack, player.getData(Kome.SPELL_SLOTS).selectedSpell(), null);
        }
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        if (ability == ItemAbilities.SWORD_SWEEP) {
            return stack.get(Kome.SPELL) == Spell.SWORD;
        }
        return super.canPerformAction(stack, ability);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Spell spell = spellOf(stack);
        if (spell == null || spell == Spell.SWORD) {
            // Nothing to cast: no spell picked, or the sign is a sword just now.
            return InteractionResult.PASS;
        }

        player.startUsingItem(hand);
        if (level instanceof ServerLevel serverLevel) {
            switch (spell) {
                case SHOOTING_STAR -> ShootingStars.begin(serverLevel, player);
                case LIGHTNING -> Lightning.begin(serverLevel, player);
                case SWORD -> {
                }
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        // Holding the sign up they can only creep along, slowed as if drawing a bow, and cannot sprint.
        entity.setSprinting(false);
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            int ticksCasting = USE_DURATION - ticksRemaining;
            switch (spellOf(stack)) {
                case SHOOTING_STAR -> ShootingStars.tick(serverLevel, player, ticksCasting);
                case LIGHTNING -> Lightning.tick(serverLevel, player, ticksCasting);
                case null, default -> {
                }
            }
        } else if (entity instanceof Player player && spellOf(stack) == Spell.LIGHTNING) {
            Lightning.tickLocally(level, player, USE_DURATION - ticksRemaining);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            switch (spellOf(stack)) {
                case SHOOTING_STAR -> ShootingStars.end(player);
                case LIGHTNING -> Lightning.release(serverLevel, player, stack, USE_DURATION - remainingTime);
                case null, default -> {
                }
            }
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Spell spell = spellOf(stack);
        builder.accept(
            spell == null
                ? Component.translatable("item.kome.ritual_sign.spell", Component.translatable("spell.kome.empty")).withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.kome.ritual_sign.spell", spell.displayName()).withColor(spell.color())
        );
    }
}

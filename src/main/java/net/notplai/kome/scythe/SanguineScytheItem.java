package net.notplai.kome.scythe;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.notplai.kome.Kome;

public class SanguineScytheItem extends Item {
    /** Health the wielder pays to throw a blood slash. They must have more than this left to throw one. */
    public static final float BLOOD_COST = 6.0F;
    private static final int THROW_COOLDOWN_TICKS = 20;
    /** How long a scythe refuses to bind again after its wielder releases a victim. */
    private static final int UNBIND_COOLDOWN_TICKS = 5 * 60 * 20;
    private static final int VALIDATE_INTERVAL = 20;

    public SanguineScytheItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(target instanceof ServerPlayer victim) || !(attacker instanceof ServerPlayer wielder) || !victim.isAlive() || victim.isCreative()) {
            return;
        }

        if (stack.has(Kome.SCYTHE_BOND) || stack.has(Kome.SCYTHE_REBIND_AT) || ScytheBindHandler.isBound(victim)) {
            return;
        }

        UUID bondId = UUID.randomUUID();
        stack.set(Kome.SCYTHE_BOND, new ScytheBond(bondId, victim.getUUID(), victim.getGameProfile().name()));
        ScytheBindHandler.bind(victim, bondId);
        wielder.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        ScytheBond bond = stack.get(Kome.SCYTHE_BOND);
        if (bond == null || !player.isSecondaryUseActive() || !bond.victim().equals(target.getUUID())) {
            return InteractionResult.PASS;
        }

        if (player.level() instanceof ServerLevel level) {
            // The interaction may have been handed a copy of the stack, the bond has to come off the real one.
            ItemStack held = player.getItemInHand(hand);
            ScytheBindHandler.endBond(level.getServer(), bond);
            held.remove(Kome.SCYTHE_BOND);
            held.set(Kome.SCYTHE_REBIND_AT, level.getGameTime() + UNBIND_COOLDOWN_TICKS);
            player.getCooldowns().addCooldown(held, UNBIND_COOLDOWN_TICKS);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        if (!player.hasInfiniteMaterials() && player.getHealth() <= BLOOD_COST) {
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            // Everything afflicting or empowering the wielder leaves with the blood.
            List<MobEffectInstance> carried = player.getActiveEffects()
                .stream()
                .filter(effect -> !effect.is(Kome.EXSANGUINATE))
                .map(MobEffectInstance::new)
                .toList();
            carried.forEach(effect -> player.removeEffect(effect.getEffect()));
            if (!player.hasInfiniteMaterials()) {
                player.setHealth(player.getHealth() - BLOOD_COST);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);
            }

            serverLevel.addFreshEntity(new BloodSlashEntity(serverLevel, player, carried));
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);
            player.getCooldowns().addCooldown(player.getItemInHand(hand), THROW_COOLDOWN_TICKS);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if ((level.getGameTime() + owner.getId()) % VALIDATE_INTERVAL != 0) {
            return;
        }

        // The victim died, or the bond was ended some other way while this scythe was not looking.
        ScytheBond bond = stack.get(Kome.SCYTHE_BOND);
        if (bond != null && !ScytheBonds.get(level.getServer()).isActive(bond.bondId())) {
            stack.remove(Kome.SCYTHE_BOND);
        }

        Long rebindAt = stack.get(Kome.SCYTHE_REBIND_AT);
        if (rebindAt != null) {
            long remaining = rebindAt - level.getGameTime();
            if (remaining <= 0) {
                stack.remove(Kome.SCYTHE_REBIND_AT);
            } else if (owner instanceof Player player && !player.getCooldowns().isOnCooldown(stack)) {
                // The visible cooldown does not survive a relog, the scythe's own does.
                player.getCooldowns().addCooldown(stack, (int)Math.min(remaining, UNBIND_COOLDOWN_TICKS));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ScytheBond bond = stack.get(Kome.SCYTHE_BOND);
        if (bond != null) {
            builder.accept(Component.translatable("item.kome.sanguine_scythe.bound", bond.victimName()).withStyle(ChatFormatting.DARK_RED));
        } else {
            builder.accept(Component.translatable("item.kome.sanguine_scythe.unbound").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

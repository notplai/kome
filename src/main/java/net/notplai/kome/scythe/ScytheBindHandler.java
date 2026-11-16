package net.notplai.kome.scythe;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.notplai.kome.Kome;

/** Keeps bound players limited to the health they have left, and ends bonds when their conditions are met. */
@EventBusSubscriber(modid = Kome.MODID)
public final class ScytheBindHandler {
    private static final Identifier HEALTH_LIMIT_ID = Identifier.fromNamespaceAndPath(Kome.MODID, "exsanguinate");

    private ScytheBindHandler() {
    }

    public static boolean isBound(ServerPlayer player) {
        return player.hasData(Kome.EXSANGUINATE_BIND);
    }

    public static void bind(ServerPlayer victim, UUID bondId) {
        ScytheBonds.get(victim.level().getServer()).add(bondId);
        victim.setData(Kome.EXSANGUINATE_BIND, new ExsanguinateBind(bondId, victim.getHealth()));
        applyLimit(victim);
    }

    /** Ends a bond from the scythe's side. A victim who is offline is freed the moment they return. */
    public static void endBond(MinecraftServer server, ScytheBond bond) {
        ScytheBonds.get(server).remove(bond.bondId());
        ServerPlayer victim = server.getPlayerList().getPlayer(bond.victim());
        if (victim != null) {
            ExsanguinateBind bind = victim.getExistingDataOrNull(Kome.EXSANGUINATE_BIND);
            if (bind != null && bind.bondId().equals(bond.bondId())) {
                free(victim);
            }
        }
    }

    private static void free(ServerPlayer victim) {
        victim.removeData(Kome.EXSANGUINATE_BIND);
        victim.removeEffect(Kome.EXSANGUINATE);
        AttributeInstance maxHealth = victim.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(HEALTH_LIMIT_ID);
        }
    }

    private static void applyLimit(ServerPlayer victim) {
        ExsanguinateBind bind = victim.getExistingDataOrNull(Kome.EXSANGUINATE_BIND);
        AttributeInstance maxHealth = victim.getAttribute(Attributes.MAX_HEALTH);
        if (bind == null || maxHealth == null) {
            return;
        }

        // The limit only ever follows the victim's health downwards.
        if (victim.getHealth() < bind.healthLimit()) {
            bind = new ExsanguinateBind(bind.bondId(), victim.getHealth());
            victim.setData(Kome.EXSANGUINATE_BIND, bind);
        }

        AttributeModifier current = maxHealth.getModifier(HEALTH_LIMIT_ID);
        double unlimited;
        if (current == null) {
            unlimited = maxHealth.getValue();
        } else if (maxHealth.getValue() > 1.0001) {
            unlimited = maxHealth.getValue() / (1.0 + current.amount());
        } else {
            // Max health bottoms out at 1, which hides what it would be without the limit.
            maxHealth.removeModifier(HEALTH_LIMIT_ID);
            unlimited = maxHealth.getValue();
            current = null;
        }

        double amount = Math.min(0.0, bind.healthLimit() / unlimited - 1.0);
        if (current == null || Math.abs(current.amount() - amount) > 1.0E-5) {
            maxHealth.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_LIMIT_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (!victim.hasEffect(Kome.EXSANGUINATE)) {
            victim.addEffect(new MobEffectInstance(Kome.EXSANGUINATE, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExsanguinateBind bind = player.getExistingDataOrNull(Kome.EXSANGUINATE_BIND);
            if (bind == null) {
                return;
            }

            if (ScytheBonds.get(player.level().getServer()).isActive(bind.bondId())) {
                applyLimit(player);
            } else {
                free(player);
            }
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isBound(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExsanguinateBind bind = player.getExistingDataOrNull(Kome.EXSANGUINATE_BIND);
            if (bind != null) {
                ScytheBonds.get(player.level().getServer()).remove(bind.bondId());
                free(player);
            }
        }
    }

    @SubscribeEvent
    public static void onItemDestroyed(PlayerDestroyItemEvent event) {
        ItemStack broken = event.getOriginal();
        ScytheBond bond = broken.get(Kome.SCYTHE_BOND);
        if (bond != null && event.getEntity() instanceof ServerPlayer player) {
            endBond(player.level().getServer(), bond);
        }
    }
}

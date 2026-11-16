package net.notplai.kome.mimicry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.mixin.ThrownSplashPotionMixin;

/**
 * The Potion of Mimicry: whoever takes it wears another player's skin and name for as long as the effect lasts.
 * The potion remembers which player, in the same component a player head does; the effect is only the timer,
 * and the disguise itself is kept on the player and sent to everyone who can see them.
 */
@EventBusSubscriber(modid = Kome.MODID)
public final class Mimicry {
    /** Whose face the splash potion bursting right now holds; set around the burst by {@code ThrownSplashPotionMixin}. */
    private static @Nullable ResolvableProfile splashing;

    private Mimicry() {
    }

    /** Who a potion turns its drinker into, or null if it is not a Potion of Mimicry that remembers anyone. */
    public static @Nullable ResolvableProfile faceIn(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        return contents != null && contents.is(Kome.MIMICRY_POTION) && profile != null && profile.name().isPresent() ? profile : null;
    }

    public static void splashBegins(ItemStack potion) {
        splashing = faceIn(potion);
    }

    public static void splashEnds() {
        splashing = null;
    }

    /** The name a disguised player goes by, or null for one who is themselves. */
    public static @Nullable String nameOf(Player player) {
        return player.hasData(Kome.DISGUISE) ? player.getData(Kome.DISGUISE).name().orElse(null) : null;
    }

    private static void disguise(ServerPlayer player, ResolvableProfile face) {
        player.setData(Kome.DISGUISE, face);
        refreshNames(player);
    }

    private static void refreshNames(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResolvableProfile face = faceIn(event.getItem());
            if (face != null) {
                disguise(player, face);
            }
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (splashing != null && event.getEntity() instanceof ServerPlayer player && event.getEffectInstance().is(Kome.MIMICRY)) {
            disguise(player, splashing);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // However the effect ended (it ran out, milk, death, a command), the disguise goes with it.
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(Kome.DISGUISE) && !player.hasEffect(Kome.MIMICRY)) {
            player.removeData(Kome.DISGUISE);
            refreshNames(player);
        }
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        String name = nameOf(event.getEntity());
        if (name != null) {
            event.setDisplayname(Component.literal(name));
        }
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        String name = nameOf(event.getEntity());
        if (name != null) {
            event.setDisplayName(Component.literal(name));
        }
    }
}

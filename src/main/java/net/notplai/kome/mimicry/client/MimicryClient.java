package net.notplai.kome.mimicry.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.mimicry.Mimicry;

/** Shows a disguised player with the skin and the name of whoever they are disguised as. */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class MimicryClient {
    private static final String PROFILE_KIND_KEY = "component.profile.dynamic";
    private static final Map<Player, String> SHOWN_NAMES = new WeakHashMap<>();
    private static final int ITEM_SIZE = 16;
    /** The head drawn as the effect's icon, kept for as long as the disguise stays the same. */
    private static ItemStack iconHead = ItemStack.EMPTY;

    private MimicryClient() {
    }

    /** The skin a disguised player wears, or null for one who is themselves. */
    public static @Nullable PlayerSkin skinOf(Player player) {
        return player.hasData(Kome.DISGUISE) ? Minecraft.getInstance().playerSkinRenderCache().getOrDefault(player.getData(Kome.DISGUISE)).playerSkin() : null;
    }

    /**
     * The Mimicry effect's icon is the head of whoever the player has become, drawn as the head item is. Someone
     * under the effect without a disguise, as from a potion that holds no one, keeps the effect's own icon.
     */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean extractInventoryIcon(
                MobEffectInstance instance, AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color
            ) {
                return extractHeadIcon(graphics, x, y, width, height);
            }

            @Override
            public boolean extractHudIcon(MobEffectInstance instance, Hud hud, GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
                return extractHeadIcon(graphics, x, y, width, height);
            }
        }, Kome.MIMICRY);
    }

    private static boolean extractHeadIcon(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasData(Kome.DISGUISE)) {
            return false;
        }

        ResolvableProfile face = player.getData(Kome.DISGUISE);
        if (!face.equals(iconHead.get(DataComponents.PROFILE))) {
            iconHead = new ItemStack(Items.PLAYER_HEAD);
            iconHead.set(DataComponents.PROFILE, face);
        }

        graphics.fakeItem(iconHead, x + (width - ITEM_SIZE) / 2, y + (height - ITEM_SIZE) / 2);
        return true;
    }

    /** A Potion of Mimicry says whose face it holds, where the game would only say what kind of profile it carries. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ResolvableProfile face = Mimicry.faceIn(event.getItemStack());
        if (face == null) {
            return;
        }

        Component line = Component.translatable("item.kome.mimicry.face", face.name().orElseThrow()).withStyle(ChatFormatting.GRAY);
        List<Component> tooltip = event.getToolTip();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getContents() instanceof TranslatableContents contents && contents.getKey().equals(PROFILE_KIND_KEY)) {
                tooltip.set(i, line);
                return;
            }
        }

        tooltip.add(Math.min(1, tooltip.size()), line);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // A player's name over their head is only worked out again when asked for, and nothing says when a disguise arrives or goes.
        for (Player player : level.players()) {
            String name = Mimicry.nameOf(player);
            if (!Objects.equals(name, SHOWN_NAMES.get(player))) {
                if (name == null) {
                    SHOWN_NAMES.remove(player);
                } else {
                    SHOWN_NAMES.put(player, name);
                }

                player.refreshDisplayName();
            }
        }
    }
}

package net.notplai.kome.ritualsign.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.notplai.kome.Kome;
import net.notplai.kome.ritualsign.RitualSignItem;
import net.notplai.kome.ritualsign.Spell;
import net.notplai.kome.ritualsign.SpellSlots;
import net.notplai.kome.ritualsign.SpellSwitching;

/**
 * The spells a player carries, shown as a row of slots in the bottom right corner of the screen while they hold a
 * Ritual Sign, and the keys that pick the next and previous of them.
 */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class SpellSlotsClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Kome.MODID, "spells"));
    public static final KeyMapping NEXT_SPELL = new KeyMapping(
        "key.kome.next_spell", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_RIGHT, CATEGORY
    );
    public static final KeyMapping PREVIOUS_SPELL = new KeyMapping(
        "key.kome.previous_spell", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_LEFT, CATEGORY
    );

    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    /** The vanilla hotbar sprite's size, and the width of one slot on it. */
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SLOT_WIDTH = 20;
    /** Gap kept from the right and bottom edges of the screen. */
    private static final int MARGIN = 2;
    /** How long the picked spell's name is shown over the slots after it changes, in milliseconds. */
    private static final long NAME_SHOWN_MILLIS = 2000L;
    private static final long NAME_FADE_MILLIS = 500L;
    /** A Ritual Sign bound to each spell, to draw in its slot. */
    private static final Map<Spell, ItemStack> ICONS = new EnumMap<>(Spell.class);

    private static int lastSelected = -1;
    private static long selectedAt;

    private SpellSlotsClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(NEXT_SPELL);
        event.register(PREVIOUS_SPELL);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Kome.MODID, "spell_slots"), SpellSlotsClient::render);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        while (NEXT_SPELL.consumeClick()) {
            ask(player, 1);
        }
        while (PREVIOUS_SPELL.consumeClick()) {
            ask(player, -1);
        }
    }

    private static void ask(LocalPlayer player, int step) {
        // Only while there is a sign in hand to see it change, and not in the middle of casting.
        if (player != null && holdsSign(player) && !RitualSignItem.isCasting(player)) {
            ClientPacketDistributor.sendToServer(new SpellSwitching.Payload(step));
        }
    }

    private static boolean holdsSign(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof RitualSignItem) {
                return true;
            }
        }
        return false;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || player.isSpectator() || !holdsSign(player)) {
            return;
        }

        SpellSlots slots = player.getData(Kome.SPELL_SLOTS);
        long now = Util.getMillis();
        if (slots.selected() != lastSelected) {
            lastSelected = slots.selected();
            selectedAt = now;
        }

        int width = 1 + SpellSlots.SIZE * SLOT_WIDTH + 1;
        int left = graphics.guiWidth() - width - MARGIN;
        int top = graphics.guiHeight() - HOTBAR_HEIGHT - MARGIN;
        // The vanilla hotbar, cut down to as many slots as there are: its left end and slots, then its right edge.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_WIDTH, HOTBAR_HEIGHT, 0, 0, left, top, width - 1, HOTBAR_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_WIDTH - 1, 0, left + width - 1, top, 1, HOTBAR_HEIGHT);

        for (int slot = 0; slot < SpellSlots.SIZE; slot++) {
            Optional<Spell> spell = slots.slots().get(slot);
            if (spell.isPresent()) {
                graphics.item(icon(spell.get()), left + 3 + slot * SLOT_WIDTH, top + 3);
            }
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, left - 1 + slots.selected() * SLOT_WIDTH, top - 1, 24, 23);

        // The picked spell's name, for a moment after it changes.
        long shown = now - selectedAt;
        Spell picked = slots.selectedSpell();
        if (picked != null && shown < NAME_SHOWN_MILLIS) {
            float alpha = Mth.clamp((float)(NAME_SHOWN_MILLIS - shown) / NAME_FADE_MILLIS, 0.0F, 1.0F);
            Component name = picked.displayName();
            int textWidth = minecraft.font.width(name);
            graphics.text(minecraft.font, name, left + width - textWidth, top - 11, ARGB.color(Math.max(0.05F, alpha), picked.color()));
        }
    }

    private static ItemStack icon(Spell spell) {
        return ICONS.computeIfAbsent(spell, key -> {
            ItemStack stack = new ItemStack(Kome.RITUAL_SIGN.get());
            stack.set(Kome.SPELL, key);
            return stack;
        });
    }
}

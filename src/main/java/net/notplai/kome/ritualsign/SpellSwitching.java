package net.notplai.kome.ritualsign;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.notplai.kome.Kome;

/**
 * Picking which of their spells a player's Ritual Signs cast: the next or previous one, from a key on the client;
 * and, for operators, {@code /kome spell <slot> <spell|empty>} to fill or clear a slot.
 */
@EventBusSubscriber(modid = Kome.MODID)
public final class SpellSwitching {
    private SpellSwitching() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Payload.TYPE, Payload.STREAM_CODEC, SpellSwitching::handle);
    }

    private static void handle(Payload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || RitualSignItem.isCasting(player)) {
            return;
        }
        SpellSlots slots = player.getData(Kome.SPELL_SLOTS);
        SpellSlots picked = slots.cycle(payload.step());
        if (picked.selected() != slots.selected()) {
            set(player, picked);
        }
    }

    /** Gives {@code player} {@code slots}, and turns the Ritual Signs in their hands over to the picked spell at once. */
    static void set(ServerPlayer player, SpellSlots slots) {
        player.setData(Kome.SPELL_SLOTS, slots);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RitualSignItem) {
                RitualSignItem.bind(stack, slots.selectedSpell(), player);
            }
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        var slot = Commands.argument("slot", IntegerArgumentType.integer(1, SpellSlots.SIZE));
        slot.then(Commands.literal("empty").executes(context -> fill(context, null)));
        for (Spell spell : Spell.values()) {
            slot.then(Commands.literal(spell.getSerializedName()).executes(context -> fill(context, spell)));
        }
        ArgumentBuilder<CommandSourceStack, ?> spellCommand = Commands.literal("spell").then(slot);
        event.getDispatcher().register(Commands.literal("kome").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(spellCommand));
    }

    private static int fill(CommandContext<CommandSourceStack> context, @Nullable Spell spell) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int slot = IntegerArgumentType.getInteger(context, "slot");
        set(player, player.getData(Kome.SPELL_SLOTS).with(slot - 1, spell));
        Component what = spell == null ? Component.translatable("spell.kome.empty") : spell.displayName();
        context.getSource().sendSuccess(() -> Component.translatable("commands.kome.spell.set", slot, what), false);
        return 1;
    }

    /** Asks for the spell {@code step} ways along: 1 for the next, -1 for the previous. */
    public record Payload(int step) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Kome.MODID, "switch_spell")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Payload::step, Payload::new
        );

        @Override
        public CustomPacketPayload.Type<Payload> type() {
            return TYPE;
        }
    }
}

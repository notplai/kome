package net.notplai.kome.ritualsign.client;

import java.util.Locale;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.mixin.client.FirstPersonHandsAndItemsRendererAccessor;
import net.notplai.kome.ritualsign.Lightning;
import net.notplai.kome.ritualsign.RitualSignItem;
import net.notplai.kome.ritualsign.Spell;

/**
 * Draws a Ritual Sign turning round the hand that holds it, in first person and seen from outside, and slows its
 * caster down while they cast.
 */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class RitualSignClient {
    /** How far the sign reaches across, round a hand at rest and round one casting. */
    private static final float HELD_RADIUS = 0.3F;
    private static final float CASTING_RADIUS = 0.5F;
    /** Ticks for it to grow to its full size as casting starts. */
    private static final float SWELL_TICKS = 6.0F;
    /** Degrees it turns a tick, at rest and casting. */
    private static final float HELD_SPIN = 3.0F;
    private static final float CASTING_SPIN = 12.0F;
    /** How fast its caster can turn while casting, against how fast they otherwise would. */
    private static final double CASTING_TURN_SPEED = 0.15;
    private static final int NUMBER_OUTLINE = 0xFF1E0833;
    /** A sign with no spell picked. */
    static final int UNBOUND_COLOR = 0x8A8A8A;
    /** Textured additive pipeline for the shooting stars and their comet tails. */
    private static final RenderPipeline STAR_PIPELINE = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Kome.MODID, "pipeline/shooting_star"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build();
    private static final OitPipelineSet OIT_STAR_PIPELINES = OitPipelineSet.builder(
            Identifier.fromNamespaceAndPath(Kome.MODID, "shooting_star"),
            RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET).withShaderDefine("OIT_ADDITIVE").withCull(false)
        )
        .build();

    private RitualSignClient() {
    }

    static RenderType starGlow(String name, Identifier texture) {
        return RenderType.create(
            name,
            RenderSetup.builder(STAR_PIPELINE)
                .setOitPipelines(OIT_STAR_PIPELINES)
                .withTexture("Sampler0", texture)
                .sortOnUpload()
                .createRenderSetup()
        );
    }

    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(STAR_PIPELINE);
        event.registerOitPipelineSet(OIT_STAR_PIPELINES);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Kome.SPELL_SIGN.get(), SpellSignRenderer::new);
        event.registerEntityRenderer(Kome.SHOOTING_STAR.get(), ShootingStarRenderer::new);
        event.registerEntityRenderer(Kome.LIGHTNING_MARK.get(), LightningMarkRenderer::new);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<?> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new HandSignLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerTints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(Kome.MODID, "spell_color"), SpellTint.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity holder, InteractionHand hand, ItemStack stack) {
                // Raised high to call the stars down.
                return holder.isUsingItem() && holder.getUsedItemHand() == hand ? HumanoidModel.ArmPose.THROW_TRIDENT : null;
            }
        }, Kome.RITUAL_SIGN);
    }

    /**
     * Draws a sign round a hand. {@code poseStack} is at the shoulder, turned with the arm, as an arm model part
     * leaves it; the sign is put round the hand at the end, square across the arm.
     */
    static void submitHandSign(
        PoseStack poseStack, SubmitNodeCollector collector, HumanoidArm arm, ItemStack stack, float age, float ticksCasting, NumberStyle number
    ) {
        float casting = Mth.clamp(ticksCasting / SWELL_TICKS, 0.0F, 1.0F);
        casting = casting * casting * (3.0F - 2.0F * casting);
        // Around the wrist/forearm, a little down from the fingertips.
        poseStack.translate((arm == HumanoidArm.RIGHT ? -1.0F : 1.0F) / 16.0F, 6.0F / 16.0F, 0.0F);
        float radius = Mth.lerp(casting, HELD_RADIUS, CASTING_RADIUS);
        float angle = age * HELD_SPIN + ticksCasting * (CASTING_SPIN - HELD_SPIN);
        Spell spell = RitualSignItem.spellOf(stack);
        // With no spell picked it is a dull, empty sign.
        RitualSigns.submit(
            poseStack,
            collector,
            radius,
            angle,
            spell == null ? Spell.SHOOTING_STAR : spell,
            spell == null ? UNBOUND_COLOR : spell.color(),
            spell == null ? 0.5F : 0.75F + 0.25F * casting
        );
        if (spell == Spell.LIGHTNING && ticksCasting > 0.0F) {
            submitNumber(poseStack, collector, Lightning.sizeAfter(ticksCasting), spell.color(), number);
        }
    }

    /**
     * How wide the bolt being charged will be, written just above the sign round the hand. The arm's turn is undone
     * first, so that it stands upright and faces the camera.
     */
    private static void submitNumber(PoseStack poseStack, SubmitNodeCollector collector, float size, int rgb, NumberStyle style) {
        FormattedCharSequence text = FormattedCharSequence.forward(String.format(Locale.ROOT, "%.1f", size), Style.EMPTY);
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        PoseStack.Pose last = poseStack.last();
        last.pose().set3x3(new Matrix3f());
        last.normal().identity();
        poseStack.translate(0.0F, style.lift(), 0.0F);
        poseStack.rotate(style.facing());
        poseStack.scale(style.scale(), -style.scale(), style.scale());
        collector.submitText(
            poseStack,
            -font.width(text) / 2.0F,
            -font.lineHeight / 2.0F,
            text,
            false,
            Font.DisplayMode.NORMAL,
            LightCoordsUtil.FULL_BRIGHT,
            ARGB.opaque(rgb),
            0,
            NUMBER_OUTLINE
        );
        poseStack.popPose();
    }

    /**
     * How the number over a hand is set out: how far above the sign, turned which way to face the camera, and how
     * big. In first person the pose is already the camera's own, so it needs no turning.
     */
    record NumberStyle(float lift, Quaternionfc facing, float scale) {
        static final NumberStyle FIRST_PERSON = new NumberStyle(0.3F, new Quaternionf(), 0.012F);

        static NumberStyle inWorld() {
            return new NumberStyle(0.6F, Minecraft.getInstance().gameRenderer.mainCamera().rotation(), 0.02F);
        }
    }

    /** In first person a held sign is not drawn as an item: the bare arm is, with the sign turning round its hand. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!(stack.getItem() instanceof RitualSignItem) || RitualSignItem.spellOf(stack) == Spell.SWORD || player == null) {
            return;
        }

        event.setCanceled(true);
        PlayerRenderState playerState = event.getPlayerState();
        if (playerState.avatarRenderState == null || playerState.avatarRenderState.isInvisible) {
            return;
        }

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        boolean castingHere = player.isUsingItem() && player.getUsedItemHand() == event.getHand();
        float ticksCasting = castingHere ? player.getTicksUsingItem() + event.getPartialTick() : 0.0F;
        float raised = Mth.clamp(ticksCasting / SWELL_TICKS, 0.0F, 1.0F);
        raised = raised * raised * (3.0F - 2.0F * raised);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // Casting, the hand comes up and in, towards the middle of the view.
        poseStack.translate(invert * -0.18F * raised, 0.22F * raised, -0.1F * raised);
        FirstPersonHandsAndItemsRendererAccessor hands = (FirstPersonHandsAndItemsRendererAccessor)minecraft.gameRenderer.firstPersonHandsAndItemsRenderer;
        hands.kome$renderPlayerArm(
            poseStack, event.getSubmitNodeCollector(), event.getPackedLight(), event.getEquipProgress(), event.getSwingProgress(), arm, playerState
        );
        // As the arm model part sets itself up when drawn on its own in first person.
        poseStack.translate(invert * -5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
        poseStack.rotate(Axis.ZP, invert * 0.1F);
        submitHandSign(poseStack, event.getSubmitNodeCollector(), arm, stack, player.tickCount + event.getPartialTick(), ticksCasting, NumberStyle.FIRST_PERSON);
        poseStack.popPose();
    }

    /**
     * Casting, its caster can still walk, slowed as anyone holding an item up is, but cannot jump or sprint. Sneaking
     * is left alone.
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (RitualSignItem.isCasting(event.getEntity())) {
            Input keys = event.getInput().keyPresses;
            event.getInput().keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), false, keys.shift(), false);
        }
    }

    /** And can turn only slowly. */
    @SubscribeEvent
    public static void onCalculateTurn(CalculatePlayerTurnEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && RitualSignItem.isCasting(player)) {
            // The game turns the view by (sensitivity * 0.6 + 0.2) cubed, so to turn at a fraction of the speed,
            // that part is scaled by the fraction's cube root.
            double factor = (event.getMouseSensitivity() * 0.6 + 0.2) * Math.cbrt(CASTING_TURN_SPEED);
            event.setMouseSensitivity((factor - 0.2) / 0.6);
        }
    }
}

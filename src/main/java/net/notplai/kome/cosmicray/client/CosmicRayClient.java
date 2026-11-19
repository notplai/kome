package net.notplai.kome.cosmicray.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.client.GoldenLight;
import net.notplai.kome.cosmicray.CosmicRayActivation;
import net.notplai.kome.cosmicray.CosmicRayAim;
import net.notplai.kome.cosmicray.CosmicRayBeaconItem;
import net.notplai.kome.mixin.client.ClientInputAccessor;
import net.notplai.kome.mixin.client.FirstPersonHandsAndItemsRendererAccessor;

/**
 * Everything seen of a Cosmic Ray Beacon being charged and fired: the core raised ahead, the barrier glowing
 * around it, the end crystal spinning up in front of it, the beam through the crystal, and the sparks where the
 * beam lands. All of it is drawn in the world, the core included while it is raised in first person, so that the
 * beam always leaves exactly from the core. Only its holder sees each stage grow into the next; anyone watching
 * them sees each stage arrive whole. As the beam fires, everyone near is struck by an impact frame: the scene
 * flickering in black and white, the beam's colour washing off the screen, and a jolt of the camera. At the end,
 * square panes of light spring up across the beam and the beam ends in a blast where it lands, with the strongest impact of all.
 */
@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class CosmicRayClient {
    private static final Identifier CORE_TEXTURE = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/cosmic_core.png");
    private static final Identifier BEAM_CORE_TEXTURE = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/cosmic_ray_beam_core.png");
    private static final Identifier BEAM_AURA_TEXTURE = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/cosmic_ray_beam_aura.png");
    private static final Identifier PANE_TEXTURE = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/cosmic_ray_pane.png");
    private static final Identifier BARRIER_TEXTURE = Identifier.fromNamespaceAndPath(Kome.MODID, "textures/entity/cosmic_ray_barrier.png");
    // Solid, so that water drawn after it is hidden behind it rather than painted over it.
    private static final RenderType CORE = RenderTypes.entityCutout(CORE_TEXTURE);
    /**
     * Textured opaque plasma core for the heart of the beam. Keeps its depth so water drawn after it is hidden behind
     * it instead of painted over it, while letting scrolling cosmic plasma veins show along every face.
     */
    private static final RenderPipeline BEAM_CORE_PIPELINE = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Kome.MODID, "pipeline/cosmic_ray_beam_core"))
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .withCull(false)
        .build();
    private static final RenderType BEAM_CORE = RenderType.create(
        "kome_cosmic_ray_beam_core", RenderSetup.builder(BEAM_CORE_PIPELINE).withTexture("Sampler0", BEAM_CORE_TEXTURE).sortOnUpload().createRenderSetup()
    );
    /**
     * Textured additive energy pipeline for the outer cosmic sheaths, focusing panes, and containment barrier.
     * Leaves depth unwritten so soft glowing edges never cut holes into things behind them.
     */
    private static final RenderPipeline BEAM_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Kome.MODID, "pipeline/cosmic_ray_beam_glow"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build();
    private static final OitPipelineSet OIT_BEAM_GLOW_PIPELINES = OitPipelineSet.builder(
            Identifier.fromNamespaceAndPath(Kome.MODID, "cosmic_ray_beam_glow"),
            RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET).withShaderDefine("OIT_ADDITIVE").withCull(false)
        )
        .build();
    private static final RenderType BEAM_AURA = glowType("kome_cosmic_ray_beam_aura", BEAM_AURA_TEXTURE);
    private static final RenderType PANE = glowType("kome_cosmic_ray_pane", PANE_TEXTURE);
    private static final RenderType BARRIER = glowType("kome_cosmic_ray_barrier", BARRIER_TEXTURE);
    private static final Identifier CRYSTAL_TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");

    private static RenderType glowType(String name, Identifier texture) {
        return RenderType.create(
            name,
            RenderSetup.builder(BEAM_GLOW_PIPELINE)
                .setOitPipelines(OIT_BEAM_GLOW_PIPELINES)
                .withTexture("Sampler0", texture)
                .sortOnUpload()
                .createRenderSetup()
        );
    }

    // Where the core is held. In first person it comes up from the hand to just below the middle of the view;
    // for everyone else it is out in front of their chest, between their raised hands.
    private static final float FIRST_PERSON_FORWARD = 0.75F;
    private static final float FIRST_PERSON_SIDE = 0.42F;
    private static final float FIRST_PERSON_LOW = 0.42F;
    private static final float FIRST_PERSON_RAISED = 0.36F;
    /** Where the holder's two hands are held while it runs, in first person, and how far below that they start. */
    private static final float HANDS_RAISED = -0.12F;
    private static final float HANDS_LOWERED = 0.5F;
    private static final float CHEST_HEIGHT = 1.3F;
    private static final float THIRD_PERSON_FORWARD = 0.75F;
    /** Half the size of the core as it is drawn in first person, a little larger than the core in a hand. */
    private static final float CORE_HALF = 0.09F;
    /** The core's block on the beacon texture: it covers 3 to 13 of its 16 pixels. */
    private static final float CORE_UV_MIN = 3.0F / 16.0F;
    private static final float CORE_UV_MAX = 13.0F / 16.0F;

    private static final float GLOW_GROW_TICKS = 10.0F;
    private static final float BARRIER_HALF = 0.17F;
    private static final float BARRIER_PULSE = 0.12F;
    private static final float CRYSTAL_DISTANCE = 1.5F;
    /**
     * How large the end crystal looks, in blocks across, and so how wide the beam is: past the crystal it is just
     * as wide as the crystal, and between the core and the crystal a fraction of that.
     */
    private static final float CRYSTAL_SIZE = 0.9F;
    /** An end crystal's glass is half a block across at its own size, but it is turned on its corner, so it looks wider. */
    private static final float CRYSTAL_SCALE = CRYSTAL_SIZE / (0.5F * 1.6F);
    private static final float CRYSTAL_APPEAR_TICKS = 8.0F;
    /** How the crystal's spin builds up: it keeps getting faster for this many ticks, then holds its speed. */
    private static final float SPIN_RAMP_TICKS = 40.0F;
    private static final float SPIN_GAIN = 0.06F;
    /** The height the end crystal model lifts its glass to, in model pixels, before it bobs. */
    private static final float CRYSTAL_GLASS_HEIGHT = 24.0F;

    // The beam: textured plasma core, scrolling additive sheaths, Solaris-style light sheets & rays,
    // and cosmic focusing panes in the finale.
    /** The beam's width between the core and the crystal, as a share of the crystal's size. */
    private static final float NARROW_BEAM = 0.25F;
    /** Past the crystal the beam starts from a point and swells out to its full width over this many blocks. */
    private static final float WIDEN_LENGTH = 3.0F;
    private static final int WIDEN_STEPS = 6;
    private static final int SHEET_COLUMNS = 10;
    private static final int WANDERING_RAYS = 5;
    private static final int RING_SEGMENTS = 24;
    // In the finale, square panes of light spring up across the beam all at once: glowing cosmic rune frames,
    // small near the crystal and larger the further down the beam they stand.
    private static final float PANE_SPACING = 10.0F;
    /** Blocks across the nearest pane would be, at the crystal, and how much wider each block further makes them. */
    private static final float PANE_NEAR_SIZE = 5.0F;
    private static final float PANE_GROWTH = 0.4F;
    private static final float PANE_RISE_TICKS = 4.0F;
    /** How sharply the crystal's spin builds again in the finale. */
    private static final float SPIN_FINALE = 0.125F;

    // The impact frame as the beam fires, for everyone close enough: the scene struck to flat black and white,
    // flickering between dark-on-light and light-on-dark, then the dye's colour washing off the screen, while
    // the view is shaken hard.
    private static final float IMPACT_RANGE = 48.0F;
    private static final Identifier IMPACT_FRAME = Identifier.fromNamespaceAndPath(Kome.MODID, "impact_frame");
    private static final Identifier IMPACT_FRAME_INVERTED = Identifier.fromNamespaceAndPath(Kome.MODID, "impact_frame_inverted");
    /** When each of the black-and-white frames ends, in ticks after the beam fires; they start inverted and alternate. */
    private static final float[] IMPACT_FRAMES = {1.5F, 3.0F, 4.5F};
    private static final float COLOR_WASH_TICKS = 5.0F;
    private static final float IMPACT_TICKS = 4.5F + 5.0F;
    private static final float SHAKE_TICKS = 18.0F;
    private static final float SHAKE_ANGLE = 6.0F;
    private static final int FLARE_SEGMENTS = 16;

    // The blast where the beam lands at the end: the strongest impact of all, felt further off, and a flash there.
    private static final float BOOM_RANGE = 72.0F;
    private static final float[] BOOM_FRAMES = {2.0F, 3.5F, 5.0F, 6.5F, 8.0F, 10.0F};
    private static final float BOOM_WASH_TICKS = 16.0F;
    private static final float BOOM_SHAKE_TICKS = 50.0F;
    private static final float BOOM_SHAKE_ANGLE = 16.0F;
    private static final float BOOM_FLASH_TICKS = 16.0F;
    private static final float BOOM_FLASH_RADIUS = 16.0F;

    private static final List<Charge> CHARGES = new ArrayList<>();
    private static final List<Boom> BOOMS = new ArrayList<>();
    private static final List<BoomFlash> FLASHES = new ArrayList<>();
    private static float time;
    private static EndCrystalModel crystal;

    private CosmicRayClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CosmicRayBeaconItem.boomListener = boom -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                BOOMS.add(new Boom(boom.at(), level.getGameTime(), boom.rgb(), boom.shooter()));
            }
        };
    }

    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(BEAM_CORE_PIPELINE);
        event.registerPipeline(BEAM_GLOW_PIPELINE);
        event.registerOitPipelineSet(OIT_BEAM_GLOW_PIPELINES);
    }

    @SubscribeEvent
    public static void registerTints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(Kome.MODID, "cosmic_ray_color"), CosmicRayTint.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity holder, InteractionHand hand, ItemStack stack) {
                // Both hands up, holding the core out ahead.
                CosmicRayActivation activation = CosmicRayBeaconItem.activation(holder);
                return activation != null && activation.hand() == hand ? HumanoidModel.ArmPose.BOW_AND_ARROW : null;
            }
        }, Kome.COSMIC_RAY_BEACON);
    }

    /**
     * In first person the raised core is drawn in the world with everything else, so neither hand draws its item;
     * instead both come up together, as for a map held in both hands, to hold the core between them.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        CosmicRayActivation activation = player == null ? null : CosmicRayBeaconItem.activation(player);
        if (activation == null) {
            return;
        }

        event.setCanceled(true);
        PlayerRenderState playerState = event.getPlayerState();
        if (event.getHand() != activation.hand() || playerState.avatarRenderState == null || playerState.avatarRenderState.isInvisible) {
            return;
        }

        float raised = smooth(CosmicRayBeaconItem.ticksActive(player, event.getPartialTick()) / CosmicRayBeaconItem.RAISE_TICKS);
        FirstPersonHandsAndItemsRendererAccessor hands = (FirstPersonHandsAndItemsRendererAccessor)minecraft.gameRenderer.firstPersonHandsAndItemsRenderer;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0F, HANDS_RAISED - HANDS_LOWERED * (1.0F - raised), -0.72F);
        poseStack.rotateDegrees(Axis.YP, 90.0F);
        hands.kome$renderMapHand(poseStack, event.getSubmitNodeCollector(), event.getPackedLight(), HumanoidArm.RIGHT, playerState);
        hands.kome$renderMapHand(poseStack, event.getSubmitNodeCollector(), event.getPackedLight(), HumanoidArm.LEFT, playerState);
        poseStack.popPose();
    }

    // ---- Working out where everything is ----

    @SubscribeEvent
    public static void onExtract(ExtractLevelRenderStateEvent event) {
        CHARGES.clear();
        FLASHES.clear();
        ClientLevel level = event.getLevel();
        Camera camera = event.getCamera();
        float partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        time = (level.getGameTime() % 24000L) + partialTicks;
        Vec3 cameraPos = camera.position();
        BOOMS.removeIf(boom -> level.getGameTime() - boom.gameTime > BOOM_SHAKE_TICKS || boom.gameTime > level.getGameTime() + 1);
        for (Boom boom : BOOMS) {
            float since = level.getGameTime() - boom.gameTime + partialTicks;
            if (since < BOOM_FLASH_TICKS) {
                FLASHES.add(new BoomFlash(boom.at.subtract(cameraPos).toVector3f(), since, boom.rgb));
            }
        }
        for (Player player : level.players()) {
            CosmicRayActivation activation = CosmicRayBeaconItem.activation(player);
            if (activation == null) {
                continue;
            }

            float ticks = CosmicRayBeaconItem.ticksActive(player, partialTicks);
            boolean firstPerson = player == camera.entity() && !camera.isDetached();
            boolean watched = player != Minecraft.getInstance().player;
            Vec3 look = CosmicRayAim.of(player, partialTicks);
            Vec3 core = firstPerson ? firstPersonCore(player, camera, ticks) : thirdPersonCore(player, look, partialTicks);
            Vec3 end = CosmicRayBeaconItem.beamEnd(player, partialTicks);
            Vec3 crystalAt = core.add(look.scale(CRYSTAL_DISTANCE));
            // Up against a wall the crystal forms where the beam meets it.
            if (crystalAt.distanceToSqr(core) > end.distanceToSqr(core)) {
                crystalAt = end;
            }

            int rgb = activation.color().getTextureDiffuseColor() & 0xFFFFFF;
            CHARGES.add(new Charge(
                core.subtract(cameraPos).toVector3f(), crystalAt.subtract(cameraPos).toVector3f(), end.subtract(cameraPos).toVector3f(),
                rgb, ticks, firstPerson, watched, new Quaternionf(camera.rotation())
            ));
        }
    }

    private static float side(Player player) {
        CosmicRayActivation activation = CosmicRayBeaconItem.activation(player);
        InteractionHand hand = activation == null ? InteractionHand.MAIN_HAND : activation.hand();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
    }

    private static Vec3 firstPersonCore(Player player, Camera camera, float ticks) {
        float raised = smooth(ticks / CosmicRayBeaconItem.RAISE_TICKS);
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(camera.rotation());
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.rotation());
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.rotation());
        return camera.position()
            .add(new Vec3(forward).scale(FIRST_PERSON_FORWARD))
            .add(new Vec3(right).scale(FIRST_PERSON_SIDE * side(player) * (1.0F - raised)))
            .add(new Vec3(up).scale(-Mth.lerp(raised, FIRST_PERSON_LOW, FIRST_PERSON_RAISED)));
    }

    private static Vec3 thirdPersonCore(Player player, Vec3 look, float partialTicks) {
        return player.getPosition(partialTicks).add(0.0, CHEST_HEIGHT * player.getScale(), 0.0).add(look.scale(THIRD_PERSON_FORWARD * player.getScale()));
    }

    // ---- Drawing ----

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (CHARGES.isEmpty() && FLASHES.isEmpty()) {
            return;
        }

        List<Charge> charges = List.copyOf(CHARGES);
        float now = time;
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        for (Charge charge : charges) {
            if (charge.firstPerson) {
                collector.submitCustomGeometry(poseStack, CORE, (pose, buffer) -> core(pose, buffer, charge));
            }
            if (charge.ticks >= CosmicRayBeaconItem.CRYSTAL_TICK) {
                submitCrystal(poseStack, collector, charge);
            }
            if (charge.ticks >= CosmicRayBeaconItem.FIRE_TICK) {
                collector.submitCustomGeometry(poseStack, BEAM_CORE, (pose, buffer) -> beamCore(pose, buffer, charge, now));
                collector.submitCustomGeometry(poseStack, BEAM_AURA, (pose, buffer) -> beamAura(pose, buffer, charge, now));
            }
            if (charge.ticks >= CosmicRayBeaconItem.GLOW_TICK) {
                collector.submitCustomGeometry(poseStack, BARRIER, (pose, buffer) -> submitBarrier(pose, buffer, charge, now));
            }
            if (charge.ticks >= CosmicRayBeaconItem.BAND_TICK) {
                collector.submitCustomGeometry(poseStack, PANE, (pose, buffer) -> panes(pose, buffer, charge));
            }
        }

        List<BoomFlash> flashes = List.copyOf(FLASHES);
        collector.submitCustomGeometry(poseStack, GoldenLight.LIGHT, (pose, buffer) -> {
            for (Charge charge : charges) {
                light(pose, buffer, charge, now);
            }
            for (BoomFlash flash : flashes) {
                float spread = 1.0F - Mth.square(1.0F - flash.since / BOOM_FLASH_TICKS);
                float left = 1.0F - flash.since / BOOM_FLASH_TICKS;
                flare(pose, buffer, flash.at, BOOM_FLASH_RADIUS * spread, flash.rgb, left);
                flare(pose, buffer, flash.at, BOOM_FLASH_RADIUS * 0.5F * spread, 0xFFFFFF, left);
            }
        });
    }

    /** How the core, and the barrier around it, sit before the one holding them: still, turned a little to show their sides. */
    private static Quaternionf held(Charge charge) {
        return new Quaternionf(charge.view).rotateX(-0.35F).rotateY(Mth.PI / 4.0F);
    }

    /** The core itself, held still before the one holding it up in first person. */
    private static void core(PoseStack.Pose pose, VertexConsumer buffer, Charge charge) {
        int color = 0xFF000000 | charge.rgb;
        Quaternionf turn = held(charge);
        Vector3f[] corners = new Vector3f[8];
        for (int corner = 0; corner < 8; corner++) {
            corners[corner] = new Vector3f((corner & 1) == 0 ? -CORE_HALF : CORE_HALF, (corner & 2) == 0 ? -CORE_HALF : CORE_HALF, (corner & 4) == 0 ? -CORE_HALF : CORE_HALF)
                .rotate(turn)
                .add(charge.core);
        }

        // Each face as four corners going round it, and the way it faces.
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}};
        float[][] uvs = {{CORE_UV_MIN, CORE_UV_MAX}, {CORE_UV_MIN, CORE_UV_MIN}, {CORE_UV_MAX, CORE_UV_MIN}, {CORE_UV_MAX, CORE_UV_MAX}};
        for (int face = 0; face < faces.length; face++) {
            Vector3f normal = new Vector3f(normals[face][0], normals[face][1], normals[face][2]).rotate(turn);
            for (int i = 0; i < 4; i++) {
                Vector3f at = corners[faces[face][i]];
                buffer.addVertex(pose, at.x, at.y, at.z)
                    .setColor(color)
                    .setUv(uvs[i][0], uvs[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(pose, normal.x, normal.y, normal.z);
            }
        }
    }

    /** The end crystal in front of the core: it pops into being, then spins faster and faster. */
    private static void submitCrystal(PoseStack poseStack, SubmitNodeCollector collector, Charge charge) {
        if (crystal == null) {
            crystal = new EndCrystalModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL));
        }

        float since = charge.ticks - CosmicRayBeaconItem.CRYSTAL_TICK;
        float appear = charge.watched ? 1.0F : Mth.clamp(since / CRYSTAL_APPEAR_TICKS, 0.0F, 1.0F);
        // A little overshoot as it pops in.
        float size = CRYSTAL_SCALE * (1.0F + 2.2F * Mth.cube(appear - 1.0F) + 1.2F * Mth.square(appear - 1.0F));
        if (size <= 0.01F) {
            return;
        }

        EndCrystalRenderState state = new EndCrystalRenderState();
        state.showsBottom = false;
        float finale = Math.max(0.0F, charge.ticks - CosmicRayBeaconItem.SPIN_UP_TICK);
        state.ageInTicks = (charge.watched ? spin(SPIN_RAMP_TICKS + since) : spin(since)) + SPIN_FINALE * finale * finale;
        poseStack.pushPose();
        poseStack.translate(charge.crystal.x, charge.crystal.y, charge.crystal.z);
        poseStack.scale(size, size, size);
        // The model turns its glass about a point it lifts and bobs above its base; this holds that point still.
        poseStack.translate(0.0F, -(CRYSTAL_GLASS_HEIGHT + EndCrystalRenderer.getY(state.ageInTicks) * 8.0F) / 16.0F, 0.0F);
        collector.submitModel(crystal, state, poseStack, CRYSTAL_TEXTURE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** How far round the crystal has turned, in the end crystal's own ticks: an ever faster spin that then holds its pace. */
    private static float spin(float since) {
        float ramp = Math.min(since, SPIN_RAMP_TICKS);
        return ramp + SPIN_GAIN * ramp * ramp + (since - ramp) * (1.0F + 2.0F * SPIN_GAIN * SPIN_RAMP_TICKS);
    }

    @FunctionalInterface
    private interface BeamSegmentVisitor {
        void accept(Vector3f from, Vector3f to, float fromHalf, float toHalf, float vFrom, float vTo, boolean capped);
    }

    /**
     * Walks the beam's geometry once (narrow stretch from core to crystal, widening cone past the crystal, and straight
     * shaft to the end) so both the opaque plasma core and the additive energy sheaths share the exact same shape.
     */
    private static void walkBeam(Charge charge, float widthScale, float vScale, float vScroll, BeamSegmentVisitor visitor) {
        float swell = beamSwell(charge) * widthScale;
        float narrow = CRYSTAL_SIZE / 2.0F * NARROW_BEAM * swell;
        float wide = CosmicRayBeaconItem.BEAM_WIDTH / 2.0F * swell;
        float narrowLen = charge.core.distance(charge.crystal);
        if (narrowLen > 1.0E-4F) {
            visitor.accept(charge.core, charge.crystal, 0.0F, narrow, vScroll, vScroll + narrowLen * vScale, false);
        }

        Vector3f along = new Vector3f(charge.end).sub(charge.crystal);
        float length = along.length();
        if (length < 1.0E-4F) {
            return;
        }
        along.div(length);
        float widen = Math.min(WIDEN_LENGTH, length);
        float widenRatio = widen / WIDEN_LENGTH;
        Vector3f from = new Vector3f(charge.crystal);
        float fromHalf = 0.0F;
        float dist = 0.0F;
        boolean hasShaft = length > widen + 1.0E-4F;
        for (int step = 1; step <= WIDEN_STEPS; step++) {
            float t = (float)step / WIDEN_STEPS;
            float nextDist = widen * t;
            Vector3f to = new Vector3f(along).mul(nextDist).add(charge.crystal);
            float toHalf = wide * (1.0F - Mth.square(1.0F - t)) * widenRatio;
            boolean capLast = !hasShaft && step == WIDEN_STEPS;
            visitor.accept(from, to, fromHalf, toHalf, vScroll + dist * vScale, vScroll + nextDist * vScale, capLast);
            from = to;
            fromHalf = toHalf;
            dist = nextDist;
        }
        if (hasShaft) {
            visitor.accept(from, charge.end, fromHalf, fromHalf, vScroll + dist * vScale, vScroll + length * vScale, true);
        }
    }

    /**
     * The textured opaque heart of the beam: writes depth so water drawn behind it stays hidden, while scrolling
     * plasma veins and edge shading give every face rich volume in the beacon's dye colour.
     */
    private static void beamCore(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, float now) {
        int coreRgb = ARGB.srgbLerp(0.20F, charge.rgb, 0xFFFFFF);
        walkBeam(
            charge,
            1.0F,
            0.22F,
            -now * 0.55F,
            (from, to, fromHalf, toHalf, vFrom, vTo, capped) -> prism(pose, buffer, from, to, fromHalf, toHalf, vFrom, vTo, coreRgb, 1.0F, true, capped)
        );
    }

    /**
     * Two counter-scrolling additive energy sheaths wrapping the beam core in braided cosmic filaments.
     */
    private static void beamAura(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, float now) {
        int pale = ARGB.srgbLerp(0.50F, charge.rgb, 0xFFFFFF);
        walkBeam(
            charge,
            1.14F,
            0.18F,
            -now * 0.78F,
            (from, to, fromHalf, toHalf, vFrom, vTo, capped) -> prism(pose, buffer, from, to, fromHalf, toHalf, vFrom, vTo, pale, 0.65F, false, false)
        );
        walkBeam(
            charge,
            1.32F,
            0.13F,
            -now * 0.46F + 0.37F,
            (from, to, fromHalf, toHalf, vFrom, vTo, capped) -> prism(pose, buffer, from, to, fromHalf, toHalf, vFrom, vTo, charge.rgb, 0.44F, false, false)
        );
    }

    /**
     * A textured square-sided stretch of beam from {@code from} to {@code to}, {@code fromHalf} and {@code toHalf}
     * across from its middle to each side at its two ends.
     */
    private static void prism(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        Vector3f from,
        Vector3f to,
        float fromHalf,
        float toHalf,
        float vFrom,
        float vTo,
        int rgb,
        float alpha,
        boolean directionalShade,
        boolean capped
    ) {
        Vector3f along = new Vector3f(to).sub(from);
        if (along.lengthSquared() < 1.0E-8F) {
            return;
        }
        along.normalize();
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        across(along, u, v);
        // The four corners, going round: +u+v, -u+v, -u-v, +u-v.
        float[][] signs = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        int alphaByte = GoldenLight.alpha(alpha);
        for (int side = 0; side < 4; side++) {
            float[] a = signs[side];
            float[] b = signs[(side + 1) % 4];
            Vector3f facing = new Vector3f(u).mul(a[0] + b[0]).add(new Vector3f(v).mul(a[1] + b[1])).normalize();
            int color = directionalShade ? shade(rgb, facing) : ARGB.color(alphaByte, rgb);
            prismVertex(pose, buffer, from, u, v, a, fromHalf, color, 0.0F, vFrom);
            prismVertex(pose, buffer, from, u, v, b, fromHalf, color, 1.0F, vFrom);
            prismVertex(pose, buffer, to, u, v, b, toHalf, color, 1.0F, vTo);
            prismVertex(pose, buffer, to, u, v, a, toHalf, color, 0.0F, vTo);
        }
        if (capped) {
            int color = directionalShade ? shade(rgb, along) : ARGB.color(alphaByte, rgb);
            float[][] capUvs = {{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};
            for (int i = 0; i < signs.length; i++) {
                prismVertex(pose, buffer, to, u, v, signs[i], toHalf, color, capUvs[i][0], capUvs[i][1]);
            }
        }
    }

    /** Two directions across the beam, square to it and to each other, the first kept level. */
    private static void across(Vector3f along, Vector3f u, Vector3f v) {
        along.cross(Math.abs(along.y) > 0.99F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F), u).normalize();
        along.cross(u, v).normalize();
    }

    private static void prismVertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, Vector3f u, Vector3f v, float[] sign, float half, int color, float texU, float texV) {
        beamVertex(
            pose,
            buffer,
            at.x + (u.x * sign[0] + v.x * sign[1]) * half,
            at.y + (u.y * sign[0] + v.y * sign[1]) * half,
            at.z + (u.z * sign[0] + v.z * sign[1]) * half,
            color,
            texU,
            texV
        );
    }

    private static void beamVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color, float u, float v) {
        buffer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** The beam's colour on a face looking towards {@code facing}: brightest towards the sky, darkest towards the ground. */
    private static int shade(int rgb, Vector3f facing) {
        float light = 0.80F + 0.20F * facing.y;
        return ARGB.color(255, Math.round(ARGB.red(rgb) * light), Math.round(ARGB.green(rgb) * light), Math.round(ARGB.blue(rgb) * light));
    }

    private static float beamSwell(Charge charge) {
        // It bursts out wide for an instant as it fires, then holds steady.
        return charge.watched ? 1.0F : 1.0F + 0.8F * (1.0F - smooth((charge.ticks - CosmicRayBeaconItem.FIRE_TICK) / 6.0F));
    }

    /** Smooth additive Solaris-style light sheets, internal rays, flares, and impact rings. */
    private static void light(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, float now) {
        int rgb = charge.rgb;
        int pale = ARGB.srgbLerp(0.45F, rgb, 0xFFFFFF);
        int white = ARGB.srgbLerp(0.85F, rgb, 0xFFFFFF);
        float pulse = 0.85F + 0.15F * Mth.sin(now * 2.3F);

        float glow = glowProgress(charge);
        if (glow > 0.0F) {
            flare(pose, buffer, charge.core, 0.42F * glow * pulse, rgb, 0.78F * glow);
            flare(pose, buffer, charge.core, 0.18F * glow, white, 0.65F * glow);
        }

        // A burst of light as the crystal appears, and its halo until the beam fires through it.
        float since = charge.ticks - CosmicRayBeaconItem.CRYSTAL_TICK;
        if (since >= 0.0F) {
            float appear = charge.watched ? 1.0F : Mth.clamp(since / CRYSTAL_APPEAR_TICKS, 0.0F, 1.0F);
            flare(pose, buffer, charge.crystal, 1.4F * (1.0F - appear) + 0.1F, white, 0.9F * (1.0F - appear));
            if (charge.ticks < CosmicRayBeaconItem.FIRE_TICK) {
                float charged = charge.watched ? 1.0F : Mth.clamp(since / (CosmicRayBeaconItem.FIRE_TICK - CosmicRayBeaconItem.CRYSTAL_TICK), 0.0F, 1.0F);
                flare(pose, buffer, charge.crystal, CRYSTAL_SIZE * (0.8F + 0.7F * charged) * pulse, rgb, (0.35F + 0.4F * charged) * appear);
            }
        }

        if (charge.ticks >= CosmicRayBeaconItem.FIRE_TICK) {
            beamLight(pose, buffer, charge, now, rgb, pale, white, pulse);
        }
    }

    /**
     * Solaris-style additive light sheets, sharp edge lines, wandering rays, crystal muzzle ring, and impact bloom.
     */
    private static void beamLight(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, float now, int rgb, int pale, int white, float pulse) {
        float swell = beamSwell(charge);
        float narrow = CRYSTAL_SIZE / 2.0F * NARROW_BEAM * swell;
        float wide = CosmicRayBeaconItem.BEAM_WIDTH / 2.0F * swell;
        float throb = 1.0F + 0.05F * Mth.sin(now * 0.9F);

        // Narrow pre-crystal channel glow and white-hot needle.
        Vector3f preAlong = new Vector3f(charge.crystal).sub(charge.core);
        float preLength = preAlong.length();
        if (preLength > 1.0E-4F) {
            preAlong.div(preLength);
            Vector3f preSide = cameraRibbonSide(charge.core, preAlong, preLength);
            lightStrip(pose, buffer, charge.core, charge.crystal, preSide, 0.0F, 0.0F, 0.0F, narrow * 1.9F, 0.0F, rgb, 0.30F);
            lightStrip(pose, buffer, charge.core, charge.crystal, preSide, 0.0F, 0.0F, 0.0F, narrow * 0.65F, 0.75F, white, 0.85F);
        }

        Vector3f along = new Vector3f(charge.end).sub(charge.crystal);
        float length = along.length();
        if (length < 1.0E-4F) {
            return;
        }
        along.div(length);
        float widen = Math.min(WIDEN_LENGTH, length);
        Vector3f side = cameraRibbonSide(charge.crystal, along, length);

        // Layered Solaris sheets: soft outer bloom, radiant body, and a hard white-hot central needle.
        lightBeamSheet(pose, buffer, charge.crystal, charge.end, along, side, widen, length, 0.0F, wide * 2.25F * throb, 0.0F, rgb, 0.24F);
        lightBeamSheet(pose, buffer, charge.crystal, charge.end, along, side, widen, length, 0.0F, wide * 1.32F * throb, 0.35F, pale, 0.46F);
        lightBeamSheet(pose, buffer, charge.crystal, charge.end, along, side, widen, length, 0.0F, wide * 0.26F, 0.80F, white, 0.88F);

        // Two sharp edge accent lines framing the beam.
        float edgeOffset = wide * 0.96F * throb;
        float edgeHalf = wide * 0.09F;
        lightBeamSheet(pose, buffer, charge.crystal, charge.end, along, side, widen, length, edgeOffset, edgeHalf, 0.5F, white, 0.55F);
        lightBeamSheet(pose, buffer, charge.crystal, charge.end, along, side, widen, length, -edgeOffset, edgeHalf, 0.5F, white, 0.55F);

        // Thin wandering cosmic rays weaving gently inside the beam.
        for (int ray = 0; ray < WANDERING_RAYS; ray++) {
            float offset = wide * 0.74F * Mth.sin(now * (0.04F + ray * 0.021F) + ray * 2.4F);
            float flicker = 0.6F + 0.4F * Mth.sin(now * (0.42F + ray * 0.13F) + ray);
            lightBeamSheet(pose, buffer, charge.crystal, charge.end, along, side, widen, length, offset, wide * 0.065F, 0.35F, white, 0.48F * flicker);
        }

        // Muzzle flare & pulsing focusing ring around the crystal as the beam erupts out of it.
        flare(pose, buffer, charge.crystal, CRYSTAL_SIZE * 1.15F * pulse, rgb, 0.55F);
        flare(pose, buffer, charge.crystal, CRYSTAL_SIZE * 0.55F, white, 0.65F);
        beamRing(pose, buffer, charge.crystal, along, CRYSTAL_SIZE * (0.85F + 0.15F * Mth.sin(now * 0.7F)), 0.22F, pale, 0.55F);

        // Impact bloom & ring where the beam lands.
        flare(pose, buffer, charge.end, wide * 1.55F * pulse, rgb, 0.60F);
        flare(pose, buffer, charge.end, wide * 0.75F, white, 0.75F);
        beamRing(pose, buffer, charge.end, along, wide * (1.15F + 0.12F * Mth.sin(now * 0.8F)), wide * 0.24F, pale, 0.50F);
    }

    private static Vector3f cameraRibbonSide(Vector3f start, Vector3f along, float length) {
        Vector3f mid = new Vector3f(along).mul(length * 0.5F).add(start);
        Vector3f side = new Vector3f(along).cross(-mid.x, -mid.y, -mid.z);
        if (side.lengthSquared() < 1.0E-6F) {
            across(along, side, new Vector3f());
            return side;
        }
        return side.normalize();
    }

    /** A camera-facing additive ribbon following the beam's widening profile from {@code start} to {@code end}. */
    private static void lightBeamSheet(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        Vector3f start,
        Vector3f end,
        Vector3f along,
        Vector3f side,
        float widen,
        float length,
        float offset,
        float targetHalfWidth,
        float hardness,
        int rgb,
        float peakAlpha
    ) {
        if (peakAlpha <= 0.0F || targetHalfWidth <= 0.0F) {
            return;
        }
        float widenRatio = widen / WIDEN_LENGTH;
        Vector3f from = new Vector3f(start);
        float fromScale = 0.0F;
        boolean hasShaft = length > widen + 1.0E-4F;
        for (int step = 1; step <= WIDEN_STEPS; step++) {
            float t = (float)step / WIDEN_STEPS;
            Vector3f to = new Vector3f(along).mul(widen * t).add(start);
            float toScale = (1.0F - Mth.square(1.0F - t)) * widenRatio;
            lightStrip(
                pose, buffer, from, to, side, offset * fromScale, offset * toScale, targetHalfWidth * fromScale, targetHalfWidth * toScale, hardness, rgb, peakAlpha
            );
            from = to;
            fromScale = toScale;
        }
        if (hasShaft) {
            lightStrip(
                pose, buffer, from, end, side, offset * fromScale, offset * fromScale, targetHalfWidth * fromScale, targetHalfWidth * fromScale, hardness, rgb, peakAlpha
            );
        }
    }

    private static void lightStrip(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        Vector3f from,
        Vector3f to,
        Vector3f side,
        float fromOffset,
        float toOffset,
        float fromHalfWidth,
        float toHalfWidth,
        float hardness,
        int rgb,
        float peakAlpha
    ) {
        for (int col = 0; col < SHEET_COLUMNS; col++) {
            float left = col * 2.0F / SHEET_COLUMNS - 1.0F;
            float right = (col + 1) * 2.0F / SHEET_COLUMNS - 1.0F;
            int leftColor = ARGB.color(GoldenLight.alpha(peakAlpha * sheetFalloff(left, hardness)), rgb);
            int rightColor = ARGB.color(GoldenLight.alpha(peakAlpha * sheetFalloff(right, hardness)), rgb);
            float fl = fromOffset + left * fromHalfWidth;
            float fr = fromOffset + right * fromHalfWidth;
            float tl = toOffset + left * toHalfWidth;
            float tr = toOffset + right * toHalfWidth;
            buffer.addVertex(pose, from.x + side.x * fl, from.y + side.y * fl, from.z + side.z * fl).setColor(leftColor);
            buffer.addVertex(pose, from.x + side.x * fr, from.y + side.y * fr, from.z + side.z * fr).setColor(rightColor);
            buffer.addVertex(pose, to.x + side.x * tr, to.y + side.y * tr, to.z + side.z * tr).setColor(rightColor);
            buffer.addVertex(pose, to.x + side.x * tl, to.y + side.y * tl, to.z + side.z * tl).setColor(leftColor);
        }
    }

    private static float sheetFalloff(float distance, float hardness) {
        distance = Math.abs(distance);
        if (distance <= hardness) {
            return 1.0F;
        }
        float edge = (distance - hardness) / (1.0F - hardness);
        return Mth.square(Math.max(0.0F, 1.0F - edge * edge));
    }

    /** A soft ring of light standing perpendicular to {@code along} about {@code center}. */
    private static void beamRing(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f along, float radius, float thickness, int rgb, float peakAlpha) {
        if (peakAlpha <= 0.0F || radius <= 0.0F) {
            return;
        }
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        across(along, u, v);
        float inner = Math.max(0.0F, radius - thickness);
        float outer = radius + thickness;
        int peak = ARGB.color(GoldenLight.alpha(peakAlpha), rgb);
        int edge = ARGB.color(0, rgb);
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            float a0 = segment * Mth.TWO_PI / RING_SEGMENTS;
            float a1 = (segment + 1) * Mth.TWO_PI / RING_SEGMENTS;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            ringQuad(pose, buffer, center, u, v, c0, s0, c1, s1, inner, radius, edge, peak);
            ringQuad(pose, buffer, center, u, v, c0, s0, c1, s1, radius, outer, peak, edge);
        }
    }

    private static void ringQuad(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        Vector3f center,
        Vector3f u,
        Vector3f v,
        float c0,
        float s0,
        float c1,
        float s1,
        float r0,
        float r1,
        int color0,
        int color1
    ) {
        buffer.addVertex(pose, center.x + (u.x * c0 + v.x * s0) * r0, center.y + (u.y * c0 + v.y * s0) * r0, center.z + (u.z * c0 + v.z * s0) * r0).setColor(color0);
        buffer.addVertex(pose, center.x + (u.x * c0 + v.x * s0) * r1, center.y + (u.y * c0 + v.y * s0) * r1, center.z + (u.z * c0 + v.z * s0) * r1).setColor(color1);
        buffer.addVertex(pose, center.x + (u.x * c1 + v.x * s1) * r1, center.y + (u.y * c1 + v.y * s1) * r1, center.z + (u.z * c1 + v.z * s1) * r1).setColor(color1);
        buffer.addVertex(pose, center.x + (u.x * c1 + v.x * s1) * r0, center.y + (u.y * c1 + v.y * s1) * r0, center.z + (u.z * c1 + v.z * s1) * r0).setColor(color0);
    }

    /**
     * The finale's cosmic rune panes: textured focusing seals standing across the beam, small near the crystal and
     * larger the further down the beam they stand, counter-rotating gently till the blast.
     */
    private static void panes(PoseStack.Pose pose, VertexConsumer buffer, Charge charge) {
        Vector3f along = new Vector3f(charge.end).sub(charge.crystal);
        float length = along.length();
        if (length < WIDEN_LENGTH) {
            return;
        }
        along.div(length);
        Vector3f baseSide = new Vector3f();
        Vector3f baseUp = new Vector3f();
        across(along, baseSide, baseUp);
        float rising = charge.watched ? 1.0F : smooth((charge.ticks - CosmicRayBeaconItem.BAND_TICK) / PANE_RISE_TICKS);
        float ageSinceBand = Math.max(0.0F, charge.ticks - CosmicRayBeaconItem.BAND_TICK);
        int pale = ARGB.srgbLerp(0.55F, charge.rgb, 0xFFFFFF);
        int primary = ARGB.color(GoldenLight.alpha(0.92F), pale);
        int halo = ARGB.color(GoldenLight.alpha(0.38F), charge.rgb);
        int index = 0;
        for (float d = WIDEN_LENGTH + PANE_SPACING / 2.0F; d < length; d += PANE_SPACING, index++) {
            Vector3f at = new Vector3f(along).mul(d).add(charge.crystal);
            float half = (PANE_NEAR_SIZE + PANE_GROWTH * d) / 2.0F * rising;
            float angle = ((index & 1) == 0 ? 1.0F : -1.0F) * ageSinceBand * 0.025F;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            Vector3f side = new Vector3f(baseSide).mul(cos).add(new Vector3f(baseUp).mul(sin));
            Vector3f up = new Vector3f(baseUp).mul(cos).sub(new Vector3f(baseSide).mul(sin));
            texturedQuad(pose, buffer, at, side, up, half, primary);
            texturedQuad(pose, buffer, at, side, up, half * 1.06F, halo);
        }
    }

    private static void texturedQuad(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, Vector3f side, Vector3f up, float half, int color) {
        beamVertex(pose, buffer, at.x - side.x * half - up.x * half, at.y - side.y * half - up.y * half, at.z - side.z * half - up.z * half, color, 0.0F, 0.0F);
        beamVertex(pose, buffer, at.x + side.x * half - up.x * half, at.y + side.y * half - up.y * half, at.z + side.z * half - up.z * half, color, 1.0F, 0.0F);
        beamVertex(pose, buffer, at.x + side.x * half + up.x * half, at.y + side.y * half + up.y * half, at.z + side.z * half + up.z * half, color, 1.0F, 1.0F);
        beamVertex(pose, buffer, at.x - side.x * half + up.x * half, at.y - side.y * half + up.y * half, at.z - side.z * half + up.z * half, color, 0.0F, 1.0F);
    }

    /** A disc of light turned fully towards the camera, fading out to its rim. */
    private static void flare(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, float radius, int rgb, float alpha) {
        if (radius <= 0.0F || alpha <= 0.0F || at.lengthSquared() < 1.0E-6F) {
            return;
        }
        Vector3f toCamera = new Vector3f(at).negate().normalize();
        Vector3f u = toCamera.cross(Math.abs(toCamera.y) > 0.99F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f()).normalize(radius);
        Vector3f v = toCamera.cross(u, new Vector3f()).normalize(radius);
        int peak = ARGB.color(GoldenLight.alpha(alpha), rgb);
        int edge = ARGB.color(0, rgb);
        for (int segment = 0; segment < FLARE_SEGMENTS; segment++) {
            float a0 = segment * Mth.TWO_PI / FLARE_SEGMENTS;
            float a1 = (segment + 1) * Mth.TWO_PI / FLARE_SEGMENTS;
            buffer.addVertex(pose, at.x, at.y, at.z).setColor(peak);
            rim(pose, buffer, at, u, v, a0, edge);
            rim(pose, buffer, at, u, v, a1, edge);
            buffer.addVertex(pose, at.x, at.y, at.z).setColor(peak);
        }
    }

    private static void rim(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, Vector3f u, Vector3f v, float angle, int color) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        buffer.addVertex(pose, at.x + u.x * c + v.x * s, at.y + u.y * c + v.y * s, at.z + u.z * c + v.z * s).setColor(color);
    }

    private static float glowProgress(Charge charge) {
        return charge.watched
            ? (charge.ticks >= CosmicRayBeaconItem.GLOW_TICK ? 1.0F : 0.0F)
            : smooth((charge.ticks - CosmicRayBeaconItem.GLOW_TICK) / GLOW_GROW_TICKS);
    }

    private static void submitBarrier(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, float now) {
        float glow = glowProgress(charge);
        if (glow <= 0.0F) {
            return;
        }
        float throb = 1.0F + BARRIER_PULSE * Mth.sin(now * 0.45F);
        float half = BARRIER_HALF * (0.6F + 0.4F * glow) * throb;
        Quaternionf turn = held(charge);
        int pale = ARGB.srgbLerp(0.45F, charge.rgb, 0xFFFFFF);
        barrierCube(pose, buffer, charge.core, turn, half, charge.rgb, (0.44F + 0.22F * Mth.sin(now * 0.45F)) * glow);
        barrierCube(pose, buffer, charge.core, turn, half * 0.92F, pale, 0.32F * glow);
    }

    /** A textured arcane containment cube about {@code at}, turned the way the core is. */
    private static void barrierCube(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, Quaternionf turn, float half, int rgb, float alpha) {
        Vector3f[] corners = new Vector3f[8];
        for (int corner = 0; corner < 8; corner++) {
            corners[corner] = new Vector3f((corner & 1) == 0 ? -half : half, (corner & 2) == 0 ? -half : half, (corner & 4) == 0 ? -half : half).rotate(turn).add(at);
        }
        int color = ARGB.color(GoldenLight.alpha(alpha), rgb);
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        float[][] uvs = {{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};
        for (int[] face : faces) {
            for (int i = 0; i < 4; i++) {
                Vector3f p = corners[face[i]];
                beamVertex(pose, buffer, p.x, p.y, p.z, color, uvs[i][0], uvs[i][1]);
            }
        }
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ---- The impact frame as the beam fires ----

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Kome.MODID, "cosmic_ray_impact"), CosmicRayClient::impactFrame);
    }

    /** The screen effect for this frame of an impact, if one is striking: called by {@code GameRendererMixin}. */
    public static @Nullable Identifier impactPostEffect(float partialTicks) {
        Impact impact = strongestImpact(partialTicks);
        if (impact == null) {
            return null;
        }
        float[] frames = impact.boom ? BOOM_FRAMES : IMPACT_FRAMES;
        for (int frame = 0; frame < frames.length; frame++) {
            if (impact.since < frames[frame]) {
                return frame % 2 == 0 ? IMPACT_FRAME_INVERTED : IMPACT_FRAME;
            }
        }
        return null;
    }

    /** After the black-and-white frames, the beam's colour washing off the screen. */
    private static void impactFrame(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Impact impact = strongestImpact(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (impact == null) {
            return;
        }
        float[] frames = impact.boom ? BOOM_FRAMES : IMPACT_FRAMES;
        float lastFrame = frames[frames.length - 1];
        float wash = impact.boom ? BOOM_WASH_TICKS : COLOR_WASH_TICKS;
        if (impact.since < lastFrame || impact.since >= lastFrame + wash) {
            return;
        }

        float left = 1.0F - (impact.since - lastFrame) / wash;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(GoldenLight.alpha((impact.boom ? 0.85F : 0.6F) * left * left), impact.rgb));
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Impact impact = strongestImpact((float)event.getPartialTick());
        if (impact == null) {
            return;
        }
        float ticks = impact.boom ? BOOM_SHAKE_TICKS : SHAKE_TICKS;
        if (impact.since >= ticks) {
            return;
        }

        float angle = (impact.boom ? BOOM_SHAKE_ANGLE : SHAKE_ANGLE) * impact.nearness * Mth.square(1.0F - impact.since / ticks);
        event.setYaw(event.getYaw() + angle * Mth.sin(impact.since * 4.3F));
        event.setPitch(event.getPitch() + angle * Mth.sin(impact.since * 5.9F + 1.3F));
        event.setRoll(event.getRoll() + angle * 0.6F * Mth.sin(impact.since * 3.7F + 2.1F));
    }

    /**
     * What is striking the camera right now: a crystal blowing apart, which outdoes everything, or else the beam
     * that most recently fired nearby. Null if nothing has just struck.
     */
    private static @Nullable Impact strongestImpact(float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        if (level == null || camera == null) {
            return null;
        }

        Impact latest = null;
        for (Boom boom : BOOMS) {
            float since = level.getGameTime() - boom.gameTime + partialTicks;
            // Whoever fired it feels it in full however far off it lands; anyone else, the nearer the harder.
            float distance = (float)Math.sqrt(camera.distanceToSqr(boom.at));
            boolean fired = camera.getId() == boom.shooter;
            if (since >= 0.0F && since < BOOM_SHAKE_TICKS && (fired || distance < BOOM_RANGE) && (latest == null || since < latest.since)) {
                latest = new Impact(since, fired ? 1.0F : Mth.square(1.0F - distance / BOOM_RANGE), boom.rgb, true);
            }
        }
        if (latest != null) {
            return latest;
        }

        for (Player player : level.players()) {
            CosmicRayActivation activation = CosmicRayBeaconItem.activation(player);
            if (activation == null) {
                continue;
            }
            float since = CosmicRayBeaconItem.ticksActive(player, partialTicks) - CosmicRayBeaconItem.FIRE_TICK;
            float distance = (float)Math.sqrt(player.distanceToSqr(camera));
            if (since >= 0.0F && since < Math.max(IMPACT_TICKS, SHAKE_TICKS) && distance < IMPACT_RANGE && (latest == null || since < latest.since)) {
                latest = new Impact(since, Mth.square(1.0F - distance / IMPACT_RANGE), activation.color().getTextureDiffuseColor() & 0xFFFFFF, false);
            }
        }
        return latest;
    }

    private record Impact(float since, float nearness, int rgb, boolean boom) {
    }

    /** The blast that ends a beam, at {@code at}, at the client's {@code gameTime}, fired by the entity numbered {@code shooter}. */
    private record Boom(Vec3 at, long gameTime, int rgb, int shooter) {
    }

    /** The flash of a crystal blowing apart, as seen this frame. */
    private record BoomFlash(Vector3f at, float since, int rgb) {
    }

    // ---- Held fast: slowed turning, and no letting go of the core ----

    /**
     * Being slowed would narrow the view, as slowness does; while the core runs the view stays exactly as it is.
     */
    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (CosmicRayBeaconItem.isFiring(event.getPlayer())) {
            event.setNewFovModifier(1.0F);
        }
    }

    /** Its holder cannot walk, jump, sneak or sprint at all while it runs. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (CosmicRayBeaconItem.isFiring(event.getEntity())) {
            event.getInput().keyPresses = Input.EMPTY;
            ((ClientInputAccessor)event.getInput()).kome$setMoveVector(Vec2.ZERO);
        }
    }

    /** Turning slows more and more, until the view will not move at all. */
    @SubscribeEvent
    public static void onCalculateTurn(CalculatePlayerTurnEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && CosmicRayBeaconItem.isFiring(player)) {
            // The game turns the view by (sensitivity * 0.6 + 0.2) cubed, so it only stops at a third below nothing.
            event.setMouseSensitivity(Mth.lerp(CosmicRayBeaconItem.frozen(player, 0.0F), event.getMouseSensitivity(), -1.0 / 3.0));
        }
    }

    /** The hotbar cannot be scrolled while the core is running. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && CosmicRayBeaconItem.isFiring(player)) {
            event.setCanceled(true);
        }
    }

    /** Nor can a slot be picked, the core swapped to the other hand, or dropped. */
    @SubscribeEvent
    public static void onClientTickStart(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !CosmicRayBeaconItem.isFiring(minecraft.player)) {
            return;
        }
        for (KeyMapping key : minecraft.options.keyHotbarSlots) {
            while (key.consumeClick()) {
            }
        }
        while (minecraft.options.keySwapOffhand.consumeClick()) {
        }
        while (minecraft.options.keyDrop.consumeClick()) {
        }
    }

    // ---- Sparks where the beam lands ----

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().isPaused()) {
            return;
        }

        RandomSource random = level.getRandom();
        for (Player player : level.players()) {
            if (!CosmicRayBeaconItem.isBeaming(player)) {
                continue;
            }

            Vec3 end = CosmicRayBeaconItem.beamEnd(player, 1.0F);
            if (end.distanceToSqr(player.getEyePosition()) >= CosmicRayBeaconItem.RANGE * CosmicRayBeaconItem.RANGE - 0.01) {
                continue;
            }

            int rgb = CosmicRayBeaconItem.activation(player).color().getTextureDiffuseColor();
            for (int i = 0; i < 3; i++) {
                level.addParticle(
                    new DustParticleOptions(rgb, 1.2F),
                    end.x, end.y, end.z,
                    (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4
                );
            }
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.END_ROD, end.x, end.y, end.z, (random.nextDouble() - 0.5) * 0.15, random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.15);
            }
        }
    }

    /** One player's charge, as seen this frame; {@code watched} when it is someone else's, {@code view} the camera's turn. */
    private record Charge(Vector3f core, Vector3f crystal, Vector3f end, int rgb, float ticks, boolean firstPerson, boolean watched, Quaternionf view) {
    }
}

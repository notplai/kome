package net.notplai.kome;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.notplai.kome.cosmicray.CosmicRayActivation;
import net.notplai.kome.cosmicray.CosmicRayBeaconItem;
import net.notplai.kome.cosmicray.CosmicRayBeaconRecipe;
import net.notplai.kome.decree.RitualEntity;
import net.notplai.kome.decree.SolarisDecreeItem;
import net.notplai.kome.effigy.AurelianEffigyBlock;
import net.notplai.kome.effigy.AurelianEffigyBlockEntity;
import net.notplai.kome.mimicry.MimicryBrewingRecipe;
import net.notplai.kome.mimicry.MimicryEffect;
import net.notplai.kome.ritualsign.LightningMarkEntity;
import net.notplai.kome.ritualsign.RitualSignItem;
import net.notplai.kome.ritualsign.SpellSlots;
import net.notplai.kome.ritualsign.SpellSwitching;
import net.notplai.kome.ritualsign.ShootingStarEntity;
import net.notplai.kome.ritualsign.Spell;
import net.notplai.kome.ritualsign.SpellSignEntity;
import net.notplai.kome.scythe.BloodSlashEntity;
import net.notplai.kome.scythe.ExsanguinateBind;
import net.notplai.kome.scythe.ExsanguinateEffect;
import net.notplai.kome.scythe.SanguineScytheItem;
import net.notplai.kome.scythe.ScytheBond;
import net.notplai.kome.skystride.Skystride;

@Mod(Kome.MODID)
public class Kome {
    public static final String MODID = "kome";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    // ---- Aurelian Effigy ----

    public static final DeferredBlock<AurelianEffigyBlock> AURELIAN_EFFIGY = BLOCKS.registerBlock(
        "aurelian_effigy",
        AurelianEffigyBlock::new,
        properties -> properties.mapColor(MapColor.GOLD).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> AURELIAN_EFFIGY_ITEM = ITEMS.registerSimpleBlockItem(AURELIAN_EFFIGY, properties -> properties.rarity(Rarity.EPIC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AurelianEffigyBlockEntity>> AURELIAN_EFFIGY_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "aurelian_effigy", () -> new BlockEntityType<>(AurelianEffigyBlockEntity::new, AURELIAN_EFFIGY.get())
    );

    /** Whoever carries one walks through an effigy's barrier as if it were not there. */
    public static final DeferredItem<Item> AURELIAN_SIGIL = ITEMS.registerSimpleItem(
        "aurelian_sigil", properties -> properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
    );
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EFFIGY_ASH = PARTICLE_TYPES.register(
        "effigy_ash", () -> new SimpleParticleType(true)
    );
    // Its clock striking three times, twice and once.
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFIGY_CLOCK_THREE = SOUND_EVENTS.register(
        "effigy_clock_three", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "effigy_clock_three"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFIGY_CLOCK_TWO = SOUND_EVENTS.register(
        "effigy_clock_two", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "effigy_clock_two"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFIGY_CLOCK_ONE = SOUND_EVENTS.register(
        "effigy_clock_one", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "effigy_clock_one"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFIGY_CRASH = SOUND_EVENTS.register(
        "effigy_crash", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "effigy_crash"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFIGY_BELL = SOUND_EVENTS.register(
        "effigy_bell", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "effigy_bell"))
    );

    // ---- Solaris Decree ----

    public static final DeferredItem<SolarisDecreeItem> SOLARIS_DECREE = ITEMS.registerItem(
        "solaris_decree",
        SolarisDecreeItem::new,
        properties -> properties.stacksTo(16).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    );

    public static final DeferredHolder<EntityType<?>, EntityType<RitualEntity>> RITUAL = ENTITY_TYPES.registerEntityType(
        "ritual",
        RitualEntity::new,
        MobCategory.MISC,
        builder -> builder.sized(0.5F, 0.5F).noSave().noSummon().fireImmune().noLootTable().clientTrackingRange(10).updateInterval(1)
    );

    // ---- Sanguine Scythe ----

    public static final DeferredItem<SanguineScytheItem> SANGUINE_SCYTHE = ITEMS.registerItem(
        "sanguine_scythe", SanguineScytheItem::new, properties -> properties.sword(ToolMaterial.NETHERITE, 4.0F, -2.8F).rarity(Rarity.EPIC).fireResistant()
    );

    public static final DeferredHolder<EntityType<?>, EntityType<BloodSlashEntity>> BLOOD_SLASH = ENTITY_TYPES.registerEntityType(
        "blood_slash",
        BloodSlashEntity::new,
        MobCategory.MISC,
        builder -> builder.sized(1.5F, 0.6F).noSave().noSummon().noLootTable().clientTrackingRange(6).updateInterval(1)
    );

    /** On a scythe: the bond it holds over a player. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ScytheBond>> SCYTHE_BOND = DATA_COMPONENTS.registerComponentType(
        "scythe_bond", builder -> builder.persistent(ScytheBond.CODEC).networkSynchronized(ScytheBond.STREAM_CODEC)
    );
    /** On a scythe: the game time at which it may bind again after its wielder released a victim. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SCYTHE_REBIND_AT = DATA_COMPONENTS.registerComponentType(
        "scythe_rebind_at", builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG)
    );
    /** On a player: the scythe bond limiting their health. Deliberately not kept through death, which ends the bond. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ExsanguinateBind>> EXSANGUINATE_BIND = ATTACHMENT_TYPES.register(
        "exsanguinate_bind",
        () -> AttachmentType.<ExsanguinateBind>builder(() -> { throw new IllegalStateException("An exsanguinate bind has no default"); })
            .serialize(ExsanguinateBind.CODEC)
            .build()
    );
    public static final DeferredHolder<MobEffect, ExsanguinateEffect> EXSANGUINATE = MOB_EFFECTS.register("exsanguinate", ExsanguinateEffect::new);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SANGUINE_SWEEP = PARTICLE_TYPES.register(
        "sanguine_sweep", () -> new SimpleParticleType(true)
    );

    // ---- Potion of Mimicry ----

    public static final DeferredHolder<MobEffect, MimicryEffect> MIMICRY = MOB_EFFECTS.register("mimicry", MimicryEffect::new);
    public static final DeferredHolder<Potion, Potion> MIMICRY_POTION = POTIONS.register(
        "mimicry", () -> new Potion("mimicry", new MobEffectInstance(MIMICRY, 3600))
    );
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MimicryBrewingRecipe>> MIMICRY_BREWING = RECIPE_SERIALIZERS.register(
        "mimicry_brewing", () -> MimicryBrewingRecipe.SERIALIZER
    );
    /** On a player: who a Potion of Mimicry has them disguised as. Kept through a relog, but not through death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResolvableProfile>> DISGUISE = ATTACHMENT_TYPES.register(
        "disguise",
        () -> AttachmentType.<ResolvableProfile>builder(() -> { throw new IllegalStateException("A disguise has no default"); })
            .serialize(ResolvableProfile.CODEC.fieldOf("profile"))
            .sync(ResolvableProfile.STREAM_CODEC)
            .build()
    );

    // ---- Cosmic Ray Beacon ----

    public static final DeferredItem<CosmicRayBeaconItem> COSMIC_RAY_BEACON = ITEMS.registerItem(
        "cosmic_ray_beacon", CosmicRayBeaconItem::new, properties -> properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
    );
    /** On a Cosmic Ray Beacon: the colour of its beam, from the dye it was crafted with. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> COSMIC_RAY_COLOR = DATA_COMPONENTS.registerComponentType(
        "cosmic_ray_color", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    /** On a player: the Cosmic Ray Beacon they have set off, while it runs. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CosmicRayActivation>> COSMIC_RAY_ACTIVATION = ATTACHMENT_TYPES.register(
        "cosmic_ray_activation",
        () -> AttachmentType.<CosmicRayActivation>builder(() -> { throw new IllegalStateException("A cosmic ray activation has no default"); })
            .sync(CosmicRayActivation.STREAM_CODEC)
            .build()
    );
    /** On a player: the game time their Cosmic Ray Beacons are rested again, kept so the rest outlasts leaving. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> COSMIC_RAY_RESTS_UNTIL = ATTACHMENT_TYPES.register(
        "cosmic_ray_rests_until",
        () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("until")).copyOnDeath().build()
    );
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CosmicRayBeaconRecipe>> COSMIC_RAY_BEACON_RECIPE = RECIPE_SERIALIZERS.register(
        "cosmic_ray_beacon", () -> CosmicRayBeaconRecipe.SERIALIZER
    );

    // ---- Ritual Sign ----

    public static final DeferredItem<RitualSignItem> RITUAL_SIGN = ITEMS.registerItem(
        "ritual_sign", RitualSignItem::new, properties -> properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
    );
    /** On a player: the spells they carry, and which their Ritual Signs cast. Only they are told of it. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpellSlots>> SPELL_SLOTS = ATTACHMENT_TYPES.register(
        "spell_slots",
        () -> AttachmentType.builder(SpellSlots::starting)
            .serialize(SpellSlots.CODEC.fieldOf("slots"))
            .copyOnDeath()
            .sync((holder, player) -> holder == player, SpellSlots.STREAM_CODEC)
            .build()
    );
    /** On a Ritual Sign: the magic it is bound to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Spell>> SPELL = DATA_COMPONENTS.registerComponentType(
        "spell", builder -> builder.persistent(Spell.CODEC).networkSynchronized(Spell.STREAM_CODEC)
    );
    /** A Spell Sword's sweep, in the sword's own colour. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPELL_SWEEP = PARTICLE_TYPES.register(
        "spell_sweep", () -> new SimpleParticleType(true)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<SpellSignEntity>> SPELL_SIGN = ENTITY_TYPES.registerEntityType(
        "spell_sign",
        SpellSignEntity::new,
        MobCategory.MISC,
        builder -> builder.sized(0.5F, 0.5F).noSave().noSummon().fireImmune().noLootTable().clientTrackingRange(8).updateInterval(20)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ShootingStarEntity>> SHOOTING_STAR = ENTITY_TYPES.registerEntityType(
        "shooting_star",
        ShootingStarEntity::new,
        MobCategory.MISC,
        builder -> builder.sized(0.5F, 0.5F).noSave().noSummon().fireImmune().noLootTable().clientTrackingRange(8).updateInterval(1)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<LightningMarkEntity>> LIGHTNING_MARK = ENTITY_TYPES.registerEntityType(
        "lightning_mark",
        LightningMarkEntity::new,
        MobCategory.MISC,
        builder -> builder.sized(0.5F, 0.5F).noSave().noSummon().fireImmune().noLootTable().clientTrackingRange(8).updateInterval(1)
    );

    public Kome(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(Kome::addCreativeTabItems);
        modEventBus.addListener(Skystride::registerPayloads);
        modEventBus.addListener(CosmicRayBeaconItem::registerPayloads);
        modEventBus.addListener(SpellSwitching::registerPayloads);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(SOLARIS_DECREE);
            event.accept(SANGUINE_SCYTHE);
            for (DyeColor color : DyeColor.values()) {
                event.accept(CosmicRayBeaconItem.withColor(new ItemStack(COSMIC_RAY_BEACON.get()), color));
            }
            event.accept(RITUAL_SIGN);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(AURELIAN_EFFIGY_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(AURELIAN_SIGIL);
        }
    }
}

package net.notplai.kome.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.notplai.kome.Kome;
import net.notplai.kome.decree.client.RitualRenderer;
import net.notplai.kome.effigy.AurelianEffigyBlockEntity;
import net.notplai.kome.effigy.client.AurelianEffigyRenderer;
import net.notplai.kome.effigy.client.EffigyAshParticle;
import net.notplai.kome.effigy.client.EffigyEffects;
import net.notplai.kome.scythe.client.SanguineSweepParticle;

@EventBusSubscriber(modid = Kome.MODID, value = Dist.CLIENT)
public final class KomeClient {
    private KomeClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AurelianEffigyBlockEntity.clientEffects = EffigyEffects::tick;
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Kome.RITUAL.get(), RitualRenderer::new);
        // The blood slash is made entirely of particles.
        event.registerEntityRenderer(Kome.BLOOD_SLASH.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(Kome.AURELIAN_EFFIGY_BLOCK_ENTITY.get(), AurelianEffigyRenderer::new);
    }

    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GoldenLight.LIGHT_PIPELINE);
        event.registerOitPipelineSet(GoldenLight.OIT_LIGHT_PIPELINES);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Kome.SANGUINE_SWEEP.get(), SanguineSweepParticle.Provider::new);
        event.registerSpriteSet(Kome.EFFIGY_ASH.get(), EffigyAshParticle.Provider::new);
    }
}

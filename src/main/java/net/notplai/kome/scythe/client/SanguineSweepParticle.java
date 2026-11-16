package net.notplai.kome.scythe.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.AttackSweepParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The sword sweep, but it starts out dark crimson and clots to black as it passes. */
public class SanguineSweepParticle extends AttackSweepParticle {
    public SanguineSweepParticle(ClientLevel level, double x, double y, double z, double size, SpriteSet sprites) {
        super(level, x, y, z, size, sprites);
        this.lifetime = 6;
        this.quadSize *= 1.3F;
        this.setSpriteFromAge(sprites);
        this.clot();
    }

    @Override
    public void tick() {
        super.tick();
        this.clot();
    }

    private void clot() {
        float rot = Mth.clamp((float)this.age / this.lifetime, 0.0F, 1.0F);
        this.setColor(Mth.lerp(rot, 0.62F, 0.04F), Mth.lerp(rot, 0.03F, 0.0F), Mth.lerp(rot, 0.06F, 0.01F));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new SanguineSweepParticle(level, x, y, z, xAux, this.sprites);
        }
    }
}

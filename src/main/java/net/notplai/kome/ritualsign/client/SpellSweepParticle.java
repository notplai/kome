package net.notplai.kome.ritualsign.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.AttackSweepParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.notplai.kome.ritualsign.Spell;

/** A Spell Sword's sweep: the sword's sweep in the sword's own colour, lit by itself. */
public class SpellSweepParticle extends AttackSweepParticle {
    public SpellSweepParticle(ClientLevel level, double x, double y, double z, double size, SpriteSet sprites) {
        super(level, x, y, z, size, sprites);
        this.quadSize *= 1.15F;
        this.setSpriteFromAge(sprites);
        int color = Spell.SWORD.color();
        this.setColor(ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color));
    }

    @Override
    public int getLightCoords(float partialTicks) {
        return LightCoordsUtil.FULL_BRIGHT;
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
            return new SpellSweepParticle(level, x, y, z, xAux, this.sprites);
        }
    }
}

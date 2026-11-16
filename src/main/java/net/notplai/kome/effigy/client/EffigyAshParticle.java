package net.notplai.kome.effigy.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.notplai.kome.effigy.AurelianEffigyBlockEntity;

/**
 * A fleck of the ash an effigy's barrier leaves behind. It hangs in the air until the effigy starts drawing
 * its ash back in, then rushes to the effigy with all the rest. It follows the effigy's own clock, so every fleck
 * arrives at the same moment. Its speed arguments are not a speed but the point it is drawn to.
 */
public class EffigyAshParticle extends SingleQuadParticle {
    private static final int GATHER_START_TICK = AurelianEffigyBlockEntity.TOTAL_LIFETIME_TICKS + AurelianEffigyBlockEntity.ASH_LINGER_TICKS;

    private final BlockPos effigyPos;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final float fullSize;
    private final boolean ember;
    private boolean gathering;
    private double startX;
    private double startY;
    private double startZ;

    public EffigyAshParticle(ClientLevel level, double x, double y, double z, double targetX, double targetY, double targetZ, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        // The target is the middle of the three-block effigy; the block entity is in its bottom block.
        this.effigyPos = BlockPos.containing(targetX, targetY - 1.0, targetZ);
        this.hasPhysics = false;
        this.lifetime = AurelianEffigyBlockEntity.ASH_LINGER_TICKS + AurelianEffigyBlockEntity.ASH_GATHER_TICKS + 20;
        this.xd = (this.random.nextFloat() - 0.5F) * 0.03F;
        this.yd = (this.random.nextFloat() - 0.7F) * 0.02F;
        this.zd = (this.random.nextFloat() - 0.5F) * 0.03F;
        this.quadSize *= 0.8F + this.random.nextFloat() * 1.2F;
        this.fullSize = this.quadSize;
        // A few flecks are still glowing.
        this.ember = this.random.nextFloat() < 0.12F;
        if (this.ember) {
            this.setColor(1.0F, 0.62F + this.random.nextFloat() * 0.25F, 0.16F);
        } else {
            float shade = 0.03F + this.random.nextFloat() * 0.13F;
            this.setColor(shade, shade, shade * 1.05F);
        }
        this.setAlpha(0.92F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime || !(this.level.getBlockEntity(this.effigyPos) instanceof AurelianEffigyBlockEntity effigy)) {
            this.remove();
            return;
        }

        float gather = (effigy.getBarrierAge(0.0F) - GATHER_START_TICK) / AurelianEffigyBlockEntity.ASH_GATHER_TICKS;
        if (gather >= 1.0F) {
            this.remove();
        } else if (gather <= 0.0F) {
            this.x += this.xd;
            this.y += this.yd;
            this.z += this.zd;
        } else {
            if (!this.gathering) {
                this.gathering = true;
                this.startX = this.x;
                this.startY = this.y;
                this.startZ = this.z;
            }

            // Slow to get going, then faster and faster.
            float pull = gather * gather * gather;
            this.x = Mth.lerp(pull, this.startX, this.targetX);
            this.y = Mth.lerp(pull, this.startY, this.targetY);
            this.z = Mth.lerp(pull, this.startZ, this.targetZ);
            this.quadSize = this.fullSize * (1.0F - 0.6F * pull);
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTicks) {
        return this.ember ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTicks);
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
            return new EffigyAshParticle(level, x, y, z, xAux, yAux, zAux, this.sprites.get(random));
        }
    }
}

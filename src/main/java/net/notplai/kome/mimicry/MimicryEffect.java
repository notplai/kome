package net.notplai.kome.mimicry;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Only the timer on a disguise; whose face it is lives on the player, see {@link Mimicry}. */
public class MimicryEffect extends MobEffect {
    public MimicryEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9B7FD1);
    }
}

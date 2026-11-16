package net.notplai.kome.scythe;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Only the icon a bound player sees; the bind itself lives in {@link ExsanguinateBind} and cannot be cured. */
public class ExsanguinateEffect extends MobEffect {
    public ExsanguinateEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A0A12);
    }
}

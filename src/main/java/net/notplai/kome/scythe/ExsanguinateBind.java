package net.notplai.kome.scythe;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/** Stored on a bound player: the bond holding them and the health they are limited to. */
public record ExsanguinateBind(UUID bondId, float healthLimit) {
    public static final MapCodec<ExsanguinateBind> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                UUIDUtil.CODEC.fieldOf("bond_id").forGetter(ExsanguinateBind::bondId),
                Codec.FLOAT.fieldOf("health_limit").forGetter(ExsanguinateBind::healthLimit)
            )
            .apply(i, ExsanguinateBind::new)
    );
}

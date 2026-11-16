package net.notplai.kome.scythe;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Stored on a scythe: the bond it currently holds and who it holds it over. */
public record ScytheBond(UUID bondId, UUID victim, String victimName) {
    public static final Codec<ScytheBond> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                UUIDUtil.CODEC.fieldOf("bond_id").forGetter(ScytheBond::bondId),
                UUIDUtil.CODEC.fieldOf("victim").forGetter(ScytheBond::victim),
                Codec.STRING.fieldOf("victim_name").forGetter(ScytheBond::victimName)
            )
            .apply(i, ScytheBond::new)
    );
    public static final StreamCodec<ByteBuf, ScytheBond> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        ScytheBond::bondId,
        UUIDUtil.STREAM_CODEC,
        ScytheBond::victim,
        ByteBufCodecs.STRING_UTF8,
        ScytheBond::victimName,
        ScytheBond::new
    );
}

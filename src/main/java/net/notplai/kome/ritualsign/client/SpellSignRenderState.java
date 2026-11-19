package net.notplai.kome.ritualsign.client;

import org.joml.Vector3f;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.notplai.kome.ritualsign.Spell;

public class SpellSignRenderState extends EntityRenderState {
    public float age;
    public Spell spell = Spell.SHOOTING_STAR;
    public int color;
    public final Vector3f facing = new Vector3f(0.0F, -1.0F, 0.0F);
}

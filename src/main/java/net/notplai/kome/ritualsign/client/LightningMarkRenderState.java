package net.notplai.kome.ritualsign.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class LightningMarkRenderState extends EntityRenderState {
    public float size;
    public float age;
    /** Ticks since the bolt struck, or below nothing while it is still charging. */
    public float sinceStrike = -1.0F;
    /** How far above it the bolt lands: on the ground, or on a roof over it. */
    public float land;
    /** Whether it is still only being aimed: just the mark is shown, to its caster. */
    public boolean aiming;
    public int color;
    public int seed;
}

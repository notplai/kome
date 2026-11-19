package net.notplai.kome.ritualsign.client;

import org.joml.Vector3f;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class ShootingStarRenderState extends EntityRenderState {
    public int color;
    public float age;
    /** The way it is falling. */
    public final Vector3f direction = new Vector3f(0.0F, -1.0F, 0.0F);
}

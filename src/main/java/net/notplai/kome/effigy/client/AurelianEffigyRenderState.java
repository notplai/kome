package net.notplai.kome.effigy.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class AurelianEffigyRenderState extends BlockEntityRenderState {
    public float barrierAge;
    /** Where the top-to-bottom barrier square wave is in its loop, from 0 to 1. */
    public float pulsePhase;
}
package net.notplai.kome.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.notplai.kome.effigy.EffigyBarriers;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /**
     * Makes an effigy's barrier solid to the players it holds. Its walls go in with the boats and shulkers an
     * entity can already bump into and stand on, so the game treats them just as it does those.
     */
    @ModifyExpressionValue(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        )
    )
    private List<VoxelShape> kome$collideWithEffigyBarrier(List<VoxelShape> colliders, @Local(argsOnly = true) Vec3 movement) {
        Entity self = (Entity)(Object)this;
        List<VoxelShape> walls = EffigyBarriers.walls(self, self.getBoundingBox().expandTowards(movement).expandTowards(0.0, self.maxUpStep(), 0.0));
        if (walls.isEmpty()) {
            return colliders;
        }

        List<VoxelShape> all = new ArrayList<>(colliders);
        all.addAll(walls);
        return all;
    }
}

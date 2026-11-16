package net.notplai.kome.decree;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

public class SolarisDecreeItem extends Item {
    public SolarisDecreeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof PartEntity<?> part) {
            entity = part.getParent();
        }

        if (!(entity instanceof LivingEntity target)) {
            return false;
        }

        if (player.level() instanceof ServerLevel level) {
            if (RitualEntity.tryStart(level, player, target)) {
                stack.consume(1, player);
            }
        }

        // The decree binds its target instead of hitting it.
        return true;
    }
}

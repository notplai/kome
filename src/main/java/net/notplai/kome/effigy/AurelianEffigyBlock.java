package net.notplai.kome.effigy;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.notplai.kome.Kome;

/** A three-block-tall hooded monolith effigy. While it stands, a golden barrier collapses around it over 5 minutes. */
public class AurelianEffigyBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum EffigyPart implements StringRepresentable {
        LOWER("lower", 0),
        MIDDLE("middle", 1),
        UPPER("upper", 2);

        private final String name;
        private final int offset;

        EffigyPart(String name, int offset) {
            this.name = name;
            this.offset = offset;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int yOffset() {
            return this.offset;
        }
    }

    public static final EnumProperty<EffigyPart> PART = EnumProperty.create("part", EffigyPart.class);
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

    public AurelianEffigyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, EffigyPart.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() <= level.getMaxY() - 2
            && level.getBlockState(pos.above()).canBeReplaced(context)
            && level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PART, EffigyPart.LOWER);
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        // Only the lower part holds the block entity so the barrier, ground square, and glow render once.
        return blockState.getValue(PART) == EffigyPart.LOWER ? new AurelianEffigyBlockEntity(worldPosition, blockState) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != EffigyPart.LOWER || type != Kome.AURELIAN_EFFIGY_BLOCK_ENTITY.get()) {
            return null;
        }
        // On the client too, which holds its own player inside the barrier and adds the particles.
        return (lvl, pos, st, be) -> AurelianEffigyBlockEntity.tick(lvl, pos, st, (AurelianEffigyBlockEntity)be);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        level.setBlock(pos.above(), state.setValue(PART, EffigyPart.MIDDLE), Block.UPDATE_ALL);
        level.setBlock(pos.above(2), state.setValue(PART, EffigyPart.UPPER), Block.UPDATE_ALL);
        // Also on the client, so the barrier starts spreading at once instead of flashing up whole until the server answers.
        if (level.getBlockEntity(pos) instanceof AurelianEffigyBlockEntity effigy) {
            effigy.markPlaced(level.getGameTime());
            if (by != null) {
                effigy.setOwner(by.getUUID());
            }
            if (!level.isClientSide()) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        EffigyPart part = state.getValue(PART);
        if (direction == Direction.UP && part != EffigyPart.UPPER) {
            EffigyPart expectedAbove = part == EffigyPart.LOWER ? EffigyPart.MIDDLE : EffigyPart.UPPER;
            if (!neighborState.is(this) || neighborState.getValue(PART) != expectedAbove) {
                return Blocks.AIR.defaultBlockState();
            }
        } else if (direction == Direction.DOWN && part != EffigyPart.LOWER) {
            EffigyPart expectedBelow = part == EffigyPart.UPPER ? EffigyPart.MIDDLE : EffigyPart.LOWER;
            if (!neighborState.is(this) || neighborState.getValue(PART) != expectedBelow) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        EffigyPart part = state.getValue(PART);
        if (part == EffigyPart.LOWER) {
            return true;
        }
        EffigyPart expectedBelow = part == EffigyPart.UPPER ? EffigyPart.MIDDLE : EffigyPart.LOWER;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(PART) == expectedBelow;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state))) {
            preventDropsOnOtherParts(level, pos, state, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void preventDropsOnOtherParts(Level level, BlockPos pos, BlockState state, Player player) {
        EffigyPart part = state.getValue(PART);
        if (part == EffigyPart.LOWER) {
            return;
        }
        BlockPos basePos = pos.below(part.yOffset());
        for (EffigyPart other : EffigyPart.values()) {
            if (other == part) {
                continue;
            }
            BlockPos targetPos = basePos.above(other.yOffset());
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.is(this) && targetState.getValue(PART) == other) {
                BlockState replacement = targetState.getFluidState().is(Fluids.WATER)
                    ? Blocks.WATER.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
                level.setBlock(targetPos, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, targetPos, Block.getId(targetState));
            }
        }
    }
}

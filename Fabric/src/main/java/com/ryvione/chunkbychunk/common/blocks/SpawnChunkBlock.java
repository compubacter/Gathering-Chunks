/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.common.blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.ryvione.chunkbychunk.server.world.ChunkSpawnController;
import com.ryvione.chunkbychunk.interop.Services;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
public class SpawnChunkBlock extends Block {
    private static final EnumSet<Direction> HORIZONTAL_DIR = EnumSet.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private final String biomeTheme;
    private final boolean random;
    public SpawnChunkBlock(String biomeTheme, boolean random, Properties blockProperties) {
        super(blockProperties);
        this.biomeTheme = biomeTheme;
        this.random = random;
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel) {
            ChunkSpawnController chunkSpawnController = ChunkSpawnController.get(serverLevel.getServer());
            if (chunkSpawnController.isValidForLevel(serverLevel, biomeTheme, random)) {
                List<BlockPos> targetPositions = new ArrayList<>();
                BlockPos initialPos = pos.atY(level.getMaxY() - 1);
                targetPositions.add(initialPos);
                Direction targetDirection = hit.getDirection();
                if (!HORIZONTAL_DIR.contains(targetDirection)) {
                    targetDirection = Direction.NORTH;
                }
                targetPositions.add(initialPos.relative(targetDirection.getOpposite()));
                targetPositions.add(initialPos.relative(targetDirection.getCounterClockWise()));
                targetPositions.add(initialPos.relative(targetDirection.getClockWise()));
                targetPositions.add(initialPos.relative(targetDirection));
                for (BlockPos targetPos : targetPositions) {
                    if (chunkSpawnController.request(serverLevel, biomeTheme, random, targetPos)) {
                        level.playSound(null, pos, Services.PLATFORM.spawnChunkSoundEffect(), SoundSource.BLOCKS, 1.0f, 1.0f);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }
    public String getBiomeTheme() {
        return biomeTheme;
    }
    public boolean isRandom() {
        return random;
    }
}


/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.server.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import com.ryvione.chunkbychunk.common.util.ChunkUtil;
import com.ryvione.chunkbychunk.config.ChunkByChunkConfig;
import com.ryvione.chunkbychunk.interop.Services;

import java.util.List;
import java.util.Random;

public final class SpawnChunkHelper {
    private SpawnChunkHelper() {
    }

    public static boolean isEmptyChunk(LevelAccessor level, ChunkPos chunkPos) {
        BlockPos bedrockCheckBlock = chunkPos.getMiddleBlockPosition(level.getMinY());
        return !Blocks.BEDROCK.equals(level.getBlockState(bedrockCheckBlock).getBlock());
    }

    public static void createNextSpawner(ServerLevel targetLevel, ChunkPos chunkPos) {
        Random random = ChunkUtil.getChunkRandom(targetLevel, chunkPos);
        int minPos = Math.min(ChunkByChunkConfig.get().getGeneration().getMinChestSpawnDepth(), ChunkByChunkConfig.get().getGeneration().getMaxChestSpawnDepth());
        int maxPos = Math.max(ChunkByChunkConfig.get().getGeneration().getMinChestSpawnDepth(), ChunkByChunkConfig.get().getGeneration().getMaxChestSpawnDepth());

        while (maxPos > minPos && (targetLevel.getBlockState(new BlockPos(chunkPos.getMiddleBlockX(), maxPos, chunkPos.getMiddleBlockZ())).getBlock() instanceof AirBlock)) {
            maxPos--;
        }

        int yPos;
        if (minPos == maxPos) {
            yPos = minPos;
        } else {
            yPos = random.nextInt(minPos, maxPos + 1);
        }

        int xPos = chunkPos.getMinBlockX() + random.nextInt(0, 16);
        int zPos = chunkPos.getMinBlockZ() + random.nextInt(0, 16);
        BlockPos blockPos = new BlockPos(xPos, yPos, zPos);

        if (ChunkByChunkConfig.get().getGeneration().useBedrockChest()) {
            targetLevel.setBlock(blockPos, Services.PLATFORM.bedrockChestBlock().defaultBlockState(), Block.UPDATE_CLIENTS);
        } else {
            targetLevel.setBlock(blockPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        if (targetLevel.getBlockEntity(blockPos) instanceof RandomizableContainerBlockEntity chestEntity) {
            List<ItemStack> items = ChunkByChunkConfig.get().getGeneration().getChestContents().getItems(random, ChunkByChunkConfig.get().getGeneration().getChestQuantity());
            for (int i = 0; i < items.size(); i++) {
                chestEntity.setItem(i, items.get(i));
            }
        }

        ChestTracker tracker = ChestTracker.get(targetLevel.getServer());
        tracker.addChest(blockPos);
    }
}

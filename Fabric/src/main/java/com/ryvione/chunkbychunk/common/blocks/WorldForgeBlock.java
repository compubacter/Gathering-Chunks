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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.ryvione.chunkbychunk.common.blockEntities.WorldForgeBlockEntity;
import com.ryvione.chunkbychunk.interop.Services;
public class WorldForgeBlock extends AbstractContainerBlock {
    public static final MapCodec<WorldForgeBlock> CODEC = Block.simpleCodec(WorldForgeBlock::new);
    public WorldForgeBlock(Properties blockProperties) {
        super(blockProperties);
    }
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldForgeBlockEntity(pos, state);
    }
    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof WorldForgeBlockEntity) {
            player.openMenu((MenuProvider) blockEntity);
        }
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
        return level.isClientSide() ? null : createTickerHelper(entityType, Services.PLATFORM.worldForgeEntity(), WorldForgeBlockEntity::serverTick);
    }
}


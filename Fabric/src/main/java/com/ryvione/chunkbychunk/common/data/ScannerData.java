/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.common.data;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import com.ryvione.chunkbychunk.common.ChunkByChunkConstants;
import com.ryvione.chunkbychunk.common.blockEntities.WorldScannerBlockEntity;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
public class ScannerData {
    private final Set<String> inputItems = new LinkedHashSet<>();
    private final Set<String> targetBlocks = new LinkedHashSet<>();
    public ScannerData(Collection<String> items, Collection<String> blocks) {
        this.inputItems.addAll(items);
        this.targetBlocks.addAll(blocks);
    }
    public void process(Identifier context, RegistryAccess registryAccess) {
        if (registryAccess == null) {
            ChunkByChunkConstants.LOGGER.warn("Cannot process scanner data '{}' without RegistryAccess", context);
            return;
        }
        RegistryAccess access = registryAccess;
        Set<Item> inputItems = getInputItems(context, access);
        Set<Block> targetBlocks = getTargetBlocks(context, access);
        if (!inputItems.isEmpty() && !targetBlocks.isEmpty()) {
            WorldScannerBlockEntity.addItemMappings(inputItems, targetBlocks);
        } else {
            ChunkByChunkConstants.LOGGER.error("Invalid scanner data '{}', missing source items or target blocks", context);
        }
    }
    private Set<Block> getTargetBlocks(Identifier context, RegistryAccess registryAccess) {
        Registry<Block> blockRegistry = registryAccess.lookupOrThrow(Registries.BLOCK);
        return targetBlocks.stream()
                .map(x -> {
                    Identifier loc = Identifier.tryParse(x);
                    if (loc == null) {
                        ChunkByChunkConstants.LOGGER.warn("Invalid block location {} in scanner data {}", x, context);
                        return Optional.<Block>empty();
                    }
                    Optional<Block> block = blockRegistry.getOptional(loc);
                    if (block.isEmpty()) {
                        ChunkByChunkConstants.LOGGER.warn("Could not resolve block {} in scanner data {}", x, context);
                    }
                    return block;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
    private Set<Item> getInputItems(Identifier context, RegistryAccess registryAccess) {
        Registry<Item> itemRegistry = registryAccess.lookupOrThrow(Registries.ITEM);
        return inputItems.stream()
                .map(x -> {
                    Identifier loc = Identifier.tryParse(x);
                    if (loc == null) {
                        ChunkByChunkConstants.LOGGER.warn("Invalid item location {} in scanner data {}", x, context);
                        return Optional.<Item>empty();
                    }
                    Optional<Item> item = itemRegistry.getOptional(loc);
                    if (item.isEmpty()) {
                        ChunkByChunkConstants.LOGGER.warn("Could not resolve item {} in scanner data {}", x, context);
                    }
                    return item;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
}



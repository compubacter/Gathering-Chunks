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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChestTracker extends SavedData {
    private static final String DATA_ID = "chunkbychunk_chest_tracker";
    private static final Codec<ChestTrackerData> DATA_CODEC = ChestTrackerData.CODEC;

    private final Set<BlockPos> chestPositions = new HashSet<>();
    private final Map<UUID, Boolean> playerTrackerEnabled = new HashMap<>();
    private final MinecraftServer server;

    public static ChestTracker get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new ChestTracker(server);
        }
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(savedDataType(server));
    }

    private static SavedDataType<ChestTracker> savedDataType(MinecraftServer server) {
        Codec<ChestTracker> codec = DATA_CODEC.xmap(
                data -> new ChestTracker(server, data.chests, data.playerSettings),
                tracker -> new ChestTrackerData(tracker.chestPositions, tracker.playerTrackerEnabled)
        );
        return new SavedDataType<>(
                DATA_ID,
                () -> new ChestTracker(server),
                codec,
                DataFixTypes.LEVEL
        );
    }

    private ChestTracker(MinecraftServer server) {
        this.server = server;
    }

    private ChestTracker(MinecraftServer server, Set<BlockPos> chestPositions, Map<UUID, Boolean> playerTrackerEnabled) {
        this.server = server;
        for (BlockPos pos : chestPositions) {
            this.chestPositions.add(pos.immutable());
        }
        this.playerTrackerEnabled.putAll(playerTrackerEnabled);
    }

    public void addChest(BlockPos pos) {
        chestPositions.add(pos.immutable());
        setDirty();
    }

    public void removeChest(BlockPos pos) {
        boolean removed = chestPositions.remove(pos);
        if (!removed) {
            removed = chestPositions.remove(pos.immutable());
        }
        if (removed) {
            setDirty();
        }
    }

    public Set<BlockPos> getChestPositions() {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : chestPositions) {
            result.add(pos.immutable());
        }
        return result;
    }

    public void checkAndRemoveIfEmpty(BlockPos pos, ServerLevel level) {
        BlockPos immutablePos = pos.immutable();
        if (!chestPositions.contains(immutablePos)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(immutablePos);
        if (blockEntity instanceof RandomizableContainerBlockEntity chest) {
            boolean isEmpty = true;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!chest.getItem(i).isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            if (isEmpty) {
                removeChest(immutablePos);
            }
        } else {
            removeChest(immutablePos);
        }
    }

    public boolean isTracked(BlockPos pos) {
        return chestPositions.contains(pos.immutable());
    }

    public void setTrackerEnabled(UUID playerUUID, boolean enabled) {
        playerTrackerEnabled.put(playerUUID, enabled);
        setDirty();
    }

    public boolean isTrackerEnabled(UUID playerUUID) {
        return playerTrackerEnabled.getOrDefault(playerUUID, true);
    }

    private static final class ChestTrackerData {
        private static final Codec<ChestTrackerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.listOf().optionalFieldOf("chests", List.of()).forGetter(data -> List.copyOf(data.chests)),
                Codec.unboundedMap(UUIDUtil.CODEC, Codec.BOOL).optionalFieldOf("playerSettings", Map.of()).forGetter(data -> data.playerSettings)
        ).apply(instance, ChestTrackerData::new));

        private final Set<BlockPos> chests;
        private final Map<UUID, Boolean> playerSettings;

        private ChestTrackerData(List<BlockPos> chests, Map<UUID, Boolean> playerSettings) {
            this.chests = new HashSet<>(chests);
            this.playerSettings = new HashMap<>(playerSettings);
        }

        private ChestTrackerData(Set<BlockPos> chests, Map<UUID, Boolean> playerSettings) {
            this.chests = new HashSet<>(chests);
            this.playerSettings = new HashMap<>(playerSettings);
        }
    }
}

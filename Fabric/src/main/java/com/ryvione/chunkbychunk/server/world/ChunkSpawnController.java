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
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.ryvione.chunkbychunk.common.ChunkByChunkConstants;
import com.ryvione.chunkbychunk.common.util.ChangeDimensionHelper;
import com.ryvione.chunkbychunk.config.ChunkByChunkConfig;
import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ChunkSpawnController extends SavedData {
    private static final String DATA_ID = "chunkspawncontroller";
    private static final Codec<ResourceKey<Level>> LEVEL_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
    private static final Codec<SpawnPhase> SPAWN_PHASE_CODEC = Codec.STRING.xmap(SpawnPhase::valueOf, SpawnPhase::name);

    private final MinecraftServer server;
    private final Deque<SpawnRequest> requests = new ArrayDeque<>();

    @Nullable
    private SpawnRequest currentSpawnRequest = null;
    @Nullable
    private SpawnPhase phase = null;
    private boolean forcedTargetChunk;
    private int currentLayer;

    @Nullable
    private transient ServerLevel sourceLevel;
    @Nullable
    private transient ServerLevel targetLevel;
    @Nullable
    private transient CompletableFuture<ChunkResult<ChunkAccess>> sourceChunkFuture;

    public static ChunkSpawnController get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new ChunkSpawnController(server);
        }
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(savedDataType(server));
    }

    private static SavedDataType<ChunkSpawnController> savedDataType(MinecraftServer server) {
        Codec<ChunkSpawnController> codec = SavedState.CODEC.xmap(
                state -> new ChunkSpawnController(server, state),
                ChunkSpawnController::toSavedState
        );
        return new SavedDataType<>(
                DATA_ID,
                () -> new ChunkSpawnController(server),
                codec,
                DataFixTypes.LEVEL
        );
    }

    private ChunkSpawnController(MinecraftServer server) {
        this.server = server;
    }

    private ChunkSpawnController(MinecraftServer server, SavedState state) {
        this.server = server;
        this.requests.addAll(state.requests());
        this.currentSpawnRequest = state.currentRequest().orElse(null);
        this.phase = state.phase().orElse(null);
        this.forcedTargetChunk = state.forcedTargetChunk();
        this.currentLayer = state.currentLayer();
        resumeCurrentRequest();
    }

    private SavedState toSavedState() {
        return new SavedState(
                List.copyOf(requests),
                Optional.ofNullable(currentSpawnRequest),
                Optional.ofNullable(phase),
                forcedTargetChunk,
                currentLayer
        );
    }

    private void resumeCurrentRequest() {
        if (currentSpawnRequest == null) {
            return;
        }
        sourceLevel = server.getLevel(currentSpawnRequest.sourceLevel());
        targetLevel = server.getLevel(currentSpawnRequest.targetLevel());
        if (sourceLevel == null || targetLevel == null) {
            currentSpawnRequest = null;
            phase = null;
            forcedTargetChunk = false;
            currentLayer = 0;
            sourceLevel = null;
            targetLevel = null;
            sourceChunkFuture = null;
            return;
        }
        sourceChunkFuture = sourceLevel.getChunkSource().getChunkFuture(
                currentSpawnRequest.sourceChunkPos().x,
                currentSpawnRequest.sourceChunkPos().z,
                ChunkStatus.FULL,
                true
        );
        if (phase == null) {
            phase = currentSpawnRequest.immediate() ? SpawnPhase.SYNCH_CHUNKS : SpawnPhase.COPY_BIOMES;
        }
    }

    public void tick() {
        if (currentSpawnRequest != null) {
            if (sourceChunkFuture == null || !sourceChunkFuture.isDone() || sourceLevel == null || targetLevel == null || phase == null) {
                return;
            }
            switch (phase) {
                case COPY_BIOMES -> {
                    ChunkAccess sourceChunk = sourceChunkFuture.getNow(ChunkResult.error("Chunk not loaded")).orElse(null);
                    if (sourceChunk != null) {
                        updateBiomes(
                                sourceLevel,
                                sourceChunk,
                                targetLevel,
                                targetLevel.getChunk(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z),
                                currentSpawnRequest.targetChunkPos()
                        );
                    }
                    phase = SpawnPhase.SPAWN_BLOCKS;
                    currentLayer = targetLevel.getMinY();
                    setDirty();
                }
                case SPAWN_BLOCKS -> {
                    int minLayer = currentLayer;
                    int maxLayer = Math.min(currentLayer + ChunkByChunkConfig.get().getGeneration().getChunkLayerSpawnRate(), targetLevel.getMaxY() + 1);
                    copyBlocks(
                            sourceLevel,
                            currentSpawnRequest.sourceChunkPos(),
                            targetLevel,
                            currentSpawnRequest.targetChunkPos(),
                            minLayer,
                            maxLayer
                    );
                    if (maxLayer > targetLevel.getMaxY()) {
                        if (ChunkByChunkConfig.get().getGeneration().spawnNewChunkChest() && !ChunkByChunkConfig.get().getGeneration().spawnChestInInitialChunkOnly()) {
                            SpawnChunkHelper.createNextSpawner(targetLevel, currentSpawnRequest.targetChunkPos());
                        }
                        phase = SpawnPhase.SYNCH_CHUNKS;
                    } else {
                        currentLayer = maxLayer;
                    }
                    setDirty();
                }
                case SYNCH_CHUNKS -> {
                    synchChunks();
                    phase = SpawnPhase.SPAWN_ENTITIES;
                    setDirty();
                }
                case SPAWN_ENTITIES -> {
                    if (sourceLevel.areEntitiesLoaded(currentSpawnRequest.sourceChunkPos().toLong())) {
                        spawnChunkEntities();
                        completeSpawnRequest();
                        setDirty();
                    }
                }
            }
        } else if (!requests.isEmpty()) {
            currentSpawnRequest = requests.removeFirst();
            targetLevel = server.getLevel(currentSpawnRequest.targetLevel());
            sourceLevel = server.getLevel(currentSpawnRequest.sourceLevel());
            if (sourceLevel == null || targetLevel == null) {
                completeSpawnRequest();
                return;
            }
            forcedTargetChunk = targetLevel.setChunkForced(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z, true);
            sourceLevel.setChunkForced(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, true);
            sourceChunkFuture = sourceLevel.getChunkSource().getChunkFuture(
                    currentSpawnRequest.sourceChunkPos().x,
                    currentSpawnRequest.sourceChunkPos().z,
                    ChunkStatus.FULL,
                    true
            );
            phase = currentSpawnRequest.immediate() ? SpawnPhase.SYNCH_CHUNKS : SpawnPhase.COPY_BIOMES;
            ChunkByChunkConstants.LOGGER.info("Spawning chunk {} in {}", currentSpawnRequest.targetChunkPos(), targetLevel.dimension());
            setDirty();
        }
    }

    private void spawnChunkEntities() {
        if (sourceLevel == null || targetLevel == null || currentSpawnRequest == null) {
            return;
        }
        AABB boundingBox = new AABB(
                currentSpawnRequest.sourceChunkPos().getMinBlockX(),
                sourceLevel.getMinY(),
                currentSpawnRequest.sourceChunkPos().getMinBlockZ(),
                currentSpawnRequest.sourceChunkPos().getMaxBlockX(),
                sourceLevel.getMaxY(),
                currentSpawnRequest.sourceChunkPos().getMaxBlockZ()
        );
        List<Entity> entities = sourceLevel.getEntitiesOfClass(Entity.class, boundingBox, ignored -> true);
        for (Entity e : entities) {
            Vec3 pos = new Vec3(
                    e.getX() + (currentSpawnRequest.targetChunkPos().x - currentSpawnRequest.sourceChunkPos().x) * 16,
                    e.getY(),
                    e.getZ() + (currentSpawnRequest.targetChunkPos().z - currentSpawnRequest.sourceChunkPos().z) * 16
            );
            Entity movedEntity = ChangeDimensionHelper.changeDimension(e, targetLevel, pos);
            if (movedEntity != null) {
                movedEntity.setPos(pos);
            }
        }
    }

    private void completeSpawnRequest() {
        if (currentSpawnRequest != null && sourceLevel != null && targetLevel != null && forcedTargetChunk) {
            targetLevel.setChunkForced(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z, false);
            sourceLevel.setChunkForced(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, false);
        }
        currentSpawnRequest = null;
        phase = null;
        forcedTargetChunk = false;
        currentLayer = 0;
        sourceLevel = null;
        targetLevel = null;
        sourceChunkFuture = null;
    }

    private static void copyBlocks(ServerLevel sourceLevel, ChunkPos sourceChunkPos, ServerLevel targetLevel, ChunkPos targetChunkPos, int fromLayer, int toLayer) {
        int xOffset = targetChunkPos.getMinBlockX() - sourceChunkPos.getMinBlockX();
        int zOffset = targetChunkPos.getMinBlockZ() - sourceChunkPos.getMinBlockZ();
        Block sealedBlock = Blocks.BEDROCK;
        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator skyChunkGenerator
                && skyChunkGenerator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed) {
            sealedBlock = skyChunkGenerator.getSealBlock();
        }
        BlockPos.MutableBlockPos sourceBlock = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos targetBlock = new BlockPos.MutableBlockPos();
        for (int y = fromLayer; y < toLayer; y++) {
            for (int z = sourceChunkPos.getMinBlockZ(); z <= sourceChunkPos.getMaxBlockZ(); z++) {
                for (int x = sourceChunkPos.getMinBlockX(); x <= sourceChunkPos.getMaxBlockX(); x++) {
                    sourceBlock.set(x, y, z);
                    targetBlock.set(x + xOffset, y, z + zOffset);
                    Block existingBlock = targetLevel.getBlockState(targetBlock).getBlock();
                    if (existingBlock instanceof AirBlock || existingBlock instanceof LiquidBlock || existingBlock == Blocks.BEDROCK || existingBlock == sealedBlock || existingBlock == Blocks.SNOW) {
                        BlockState newBlock = sourceLevel.getBlockState(sourceBlock);
                        if (ChunkByChunkConfig.get().getGameplayConfig().isChunkSpawnLeafDecayDisabled() && newBlock.getBlock() instanceof LeavesBlock) {
                            newBlock = newBlock.setValue(LeavesBlock.PERSISTENT, true);
                        }
                        targetLevel.setBlock(targetBlock, newBlock, Block.UPDATE_ALL);
                        BlockEntity fromBlockEntity = sourceLevel.getBlockEntity(sourceBlock);
                        BlockEntity toBlockEntity = targetLevel.getBlockEntity(targetBlock);
                        if (fromBlockEntity != null && toBlockEntity != null) {
                            CompoundTag blockEntityData = fromBlockEntity.saveWithFullMetadata(targetLevel.registryAccess());
                            toBlockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, targetLevel.registryAccess(), blockEntityData));
                            targetLevel.setBlockEntity(toBlockEntity);
                        }
                    }
                }
            }
        }
    }

    private static void updateBiomes(ServerLevel sourceLevel, ChunkAccess sourceChunk, ServerLevel targetLevel, ChunkAccess targetChunk, ChunkPos targetChunkPos) {
        if (sourceChunk.getSections().length != targetChunk.getSections().length) {
            ChunkByChunkConstants.LOGGER.warn("Section count mismatch between {} and {} - {} vs {}", sourceLevel.dimension(), targetLevel.dimension(), sourceChunk.getSections().length, targetChunk.getSections().length);
        }
        if (!(targetChunk instanceof LevelChunk levelChunk)) {
            return;
        }

        boolean biomesUpdated = false;
        for (int targetIndex = 0; targetIndex < targetChunk.getSections().length; targetIndex++) {
            int sourceIndex = (targetIndex < sourceChunk.getSections().length) ? targetIndex : sourceChunk.getSections().length - 1;
            PalettedContainerRO<Holder<Biome>> sourceBiomes = sourceChunk.getSections()[sourceIndex].getBiomes();
            LevelChunkSection targetSection = levelChunk.getSections()[targetIndex];

            byte[] sourceBuffer = new byte[sourceBiomes.getSerializedSize()];
            FriendlyByteBuf sourceBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(sourceBuffer));
            sourceBuf.writerIndex(0);
            sourceBiomes.write(sourceBuf);

            PalettedContainerRO<Holder<Biome>> targetBiomesRO = targetSection.getBiomes();
            byte[] targetBuffer = new byte[targetBiomesRO.getSerializedSize()];
            FriendlyByteBuf targetBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(targetBuffer));
            targetBuf.writerIndex(0);
            targetBiomesRO.write(targetBuf);

            if (!Arrays.equals(sourceBuffer, targetBuffer)) {
                sourceBuf.readerIndex(0);
                PalettedContainer<Holder<Biome>> newBiomes = targetBiomesRO.recreate();
                newBiomes.read(sourceBuf);
                try {
                    @SuppressWarnings("unchecked")
                    PalettedContainer<BlockState> blockStates = (PalettedContainer<BlockState>) targetSection.getStates();
                    LevelChunkSection newSection = new LevelChunkSection(blockStates, newBiomes);
                    levelChunk.getSections()[targetIndex] = newSection;
                    biomesUpdated = true;
                } catch (Exception e) {
                    ChunkByChunkConstants.LOGGER.error("Failed to update biomes", e);
                }
                targetChunk.markUnsaved();
            }
        }
        if (biomesUpdated) {
            ((ControllableChunkMap) targetLevel.getChunkSource().chunkMap).forceReloadChunk(targetChunkPos);
        }
    }

    private void synchChunks() {
        if (targetLevel == null || currentSpawnRequest == null) {
            return;
        }
        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            for (ResourceKey<Level> synchLevelId : generator.getSynchedLevels()) {
                ServerLevel synchLevel = server.getLevel(synchLevelId);
                if (synchLevel != null && synchLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator synchGenerator) {
                    double scale = DimensionType.getTeleportationScale(targetLevel.dimensionType(), synchLevel.dimensionType());
                    BlockPos pos = currentSpawnRequest.targetChunkPos().getMiddleBlockPosition(0);
                    ChunkPos synchChunk = new ChunkPos(new BlockPos((int) (pos.getX() * scale), 0, (int) (pos.getZ() * scale)));
                    request(synchChunk, synchLevelId, synchChunk, synchGenerator.getGenerationLevel(), false);
                }
            }
        }
    }

    public boolean isValidForLevel(ServerLevel level, String biomeTheme, boolean random) {
        if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            if (!biomeTheme.isEmpty()) {
                return generator.getBiomeDimension(biomeTheme) != null;
            } else if (random) {
                return generator.isRandomChunkSpawnerAllowed();
            } else {
                return generator.isChunkSpawnerAllowed();
            }
        }
        return false;
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos blockPos) {
        return request(level, biomeTheme, random, blockPos, false);
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos blockPos, boolean immediate) {
        ChunkPos targetChunkPos = new ChunkPos(blockPos);
        if (isValidForLevel(level, biomeTheme, random)
                && SpawnChunkHelper.isEmptyChunk(level, targetChunkPos)
                && level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            ChunkPos sourceChunkPos;
            if (random) {
                Random rng = new Random(blockPos.asLong());
                sourceChunkPos = new ChunkPos(rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE), rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE));
            } else {
                sourceChunkPos = new ChunkPos(targetChunkPos.x, targetChunkPos.z);
            }
            ResourceKey<Level> sourceLevel;
            if (biomeTheme.isEmpty()) {
                sourceLevel = generator.getGenerationLevel();
            } else {
                sourceLevel = generator.getBiomeDimension(biomeTheme);
            }
            return request(targetChunkPos, level.dimension(), sourceChunkPos, sourceLevel, immediate);
        }
        return false;
    }

    public boolean request(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos, ResourceKey<Level> sourceLevel, boolean immediate) {
        SpawnRequest spawnRequest = new SpawnRequest(targetChunkPos, targetLevel, sourceChunkPos, sourceLevel, immediate);
        if (!spawnRequest.equals(currentSpawnRequest) && !requests.contains(spawnRequest)) {
            if (immediate) {
                ServerLevel toLevel = server.getLevel(targetLevel);
                ServerLevel fromLevel = server.getLevel(sourceLevel);
                if (toLevel == null || fromLevel == null) {
                    return false;
                }
                LevelChunk toChunk = toLevel.getChunk(targetChunkPos.x, targetChunkPos.z);
                LevelChunk fromChunk = fromLevel.getChunk(sourceChunkPos.x, sourceChunkPos.z);
                updateBiomes(fromLevel, fromChunk, toLevel, toChunk, targetChunkPos);
                copyBlocks(fromLevel, spawnRequest.sourceChunkPos(), toLevel, spawnRequest.targetChunkPos(), toLevel.getMinY(), toLevel.getMaxY() + 1);
                requests.addFirst(spawnRequest);
            } else {
                requests.add(spawnRequest);
            }
            setDirty();
            return true;
        }
        return false;
    }

    public boolean isBusy() {
        return currentSpawnRequest != null || !requests.isEmpty();
    }

    private record SpawnRequest(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos,
                                ResourceKey<Level> sourceLevel, boolean immediate) {
        private static final Codec<SpawnRequest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ChunkPos.CODEC.fieldOf("targetPos").forGetter(SpawnRequest::targetChunkPos),
                LEVEL_KEY_CODEC.fieldOf("targetLevel").forGetter(SpawnRequest::targetLevel),
                ChunkPos.CODEC.fieldOf("sourcePos").forGetter(SpawnRequest::sourceChunkPos),
                LEVEL_KEY_CODEC.fieldOf("sourceLevel").forGetter(SpawnRequest::sourceLevel),
                Codec.BOOL.optionalFieldOf("immediate", false).forGetter(SpawnRequest::immediate)
        ).apply(instance, SpawnRequest::new));

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SpawnRequest that = (SpawnRequest) o;
            if (!targetChunkPos.equals(that.targetChunkPos)) return false;
            return targetLevel.equals(that.targetLevel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetChunkPos, targetLevel);
        }
    }

    private enum SpawnPhase {
        COPY_BIOMES,
        SPAWN_BLOCKS,
        SYNCH_CHUNKS,
        SPAWN_ENTITIES
    }

    private record SavedState(List<SpawnRequest> requests, Optional<SpawnRequest> currentRequest, Optional<SpawnPhase> phase,
                              boolean forcedTargetChunk, int currentLayer) {
        private static final Codec<SavedState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SpawnRequest.CODEC.listOf().optionalFieldOf("requests", List.of()).forGetter(SavedState::requests),
                SpawnRequest.CODEC.optionalFieldOf("currentRequest").forGetter(SavedState::currentRequest),
                SPAWN_PHASE_CODEC.optionalFieldOf("phase").forGetter(SavedState::phase),
                Codec.BOOL.optionalFieldOf("forcedTargetChunk", false).forGetter(SavedState::forcedTargetChunk),
                Codec.INT.optionalFieldOf("currentLayer", 0).forGetter(SavedState::currentLayer)
        ).apply(instance, SavedState::new));
    }
}

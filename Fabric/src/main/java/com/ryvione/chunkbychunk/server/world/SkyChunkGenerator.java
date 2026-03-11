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
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
public class SkyChunkGenerator extends ChunkGenerator {
    public static final MapCodec<? extends SkyChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((encoded) ->
            encoded.group(ChunkGenerator.CODEC.withLifecycle(Lifecycle.stable()).fieldOf("parent").forGetter(SkyChunkGenerator::getParent))
                    .apply(encoded, encoded.stable(SkyChunkGenerator::new))
    );
    public static final MapCodec<? extends SkyChunkGenerator> OLD_NETHER_CODEC = RecordCodecBuilder.mapCodec((encoded) ->
            encoded.group(ChunkGenerator.CODEC.withLifecycle(Lifecycle.stable()).fieldOf("parent").forGetter(SkyChunkGenerator::getParent))
                    .apply(encoded, encoded.stable(SkyChunkGenerator::new))
    );
    private final ChunkGenerator parent;
    private ResourceKey<Level> generationLevel;
    private List<ResourceKey<Level>> synchedLevels = new ArrayList<>();
    private int initialChunks;
    private boolean chunkSpawnerAllowed;
    private boolean randomChunkSpawnerAllowed;
    private EmptyGenerationType generationType = EmptyGenerationType.Normal;
    private Block sealBlock;
    @Nullable
    private Block sealCoverBlock;
    @Nullable
    private Holder<Biome> unspawnedBiome;
    public enum EmptyGenerationType {
        Normal,
        Sealed,
        Nether;
        private static final Map<String, EmptyGenerationType> STRING_LOOKUP;
        static {
            ImmutableMap.Builder<String, EmptyGenerationType> builder = new ImmutableMap.Builder<>();
            for (EmptyGenerationType value : EmptyGenerationType.values()) {
                builder.put(value.name().toLowerCase(Locale.ROOT), value);
            }
            STRING_LOOKUP = builder.build();
        }
        public static EmptyGenerationType getFromString(String asString) {
            return STRING_LOOKUP.getOrDefault(asString.toLowerCase(Locale.ROOT), Normal);
        }
    }
    public SkyChunkGenerator(ChunkGenerator parent) {
        super(parent.getBiomeSource());
        this.parent = parent;
    }
    public void configure(ResourceKey<Level> generationLevel, EmptyGenerationType generationType, Block sealBlock, Block sealCoverBlock, int initialChunks, boolean chunkSpawnerAllowed, boolean randomChunkSpawnerAllowed) {
        this.generationLevel = generationLevel;
        this.generationType = generationType;
        this.initialChunks = initialChunks;
        this.chunkSpawnerAllowed = chunkSpawnerAllowed;
        this.randomChunkSpawnerAllowed = randomChunkSpawnerAllowed;
        this.sealBlock = sealBlock;
        this.sealCoverBlock = sealCoverBlock;
    }
    private final Map<String, ResourceKey<Level>> biomeDimensions = new HashMap<>();
    public boolean isChunkSpawnerAllowed() {
        return chunkSpawnerAllowed;
    }
    public boolean isRandomChunkSpawnerAllowed() {
        return randomChunkSpawnerAllowed;
    }
    public void addSynchLevel(ResourceKey<Level> dimension) {
        synchedLevels.add(dimension);
    }
    public List<ResourceKey<Level>> getSynchedLevels() {
        return synchedLevels;
    }
    public EmptyGenerationType getGenerationType() {
        return generationType;
    }
    public Block getSealBlock() {
        return sealBlock;
    }
    @Nullable
    public Holder<Biome> getUnspawnedBiome() {
        return unspawnedBiome;
    }
    public void setUnspawnedBiome(Holder<Biome> unspawnedBiome) {
        this.unspawnedBiome = unspawnedBiome;
    }
    public void addBiomeDimension(String name, ResourceKey<Level> level) {
        biomeDimensions.put(name, level);
    }
    @Nullable
    public ResourceKey<Level> getBiomeDimension(String name) {
        return biomeDimensions.get(name);
    }
    public int getInitialChunks() {
        return initialChunks;
    }
    public ChunkGenerator getParent() {
        return parent;
    }
    public ResourceKey<Level> getGenerationLevel() {
        return generationLevel;
    }
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
    }
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return switch (generationType) {
            case Sealed -> parent.fillFromNoise(blender, randomState, structureManager, chunk).whenCompleteAsync((chunkAccess, throwable) -> {
                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
                for (blockPos.setZ(0); blockPos.getZ() < 16; blockPos.setZ(blockPos.getZ() + 1)) {
                    for (blockPos.setX(0); blockPos.getX() < 16; blockPos.setX(blockPos.getX() + 1)) {
                        blockPos.setY(chunkAccess.getMaxY() - 1);
                        while (blockPos.getY() > chunkAccess.getMinY() && chunkAccess.getBlockState(blockPos).getBlock() instanceof AirBlock) {
                            blockPos.setY(blockPos.getY() - 1);
                        }
                        if (sealCoverBlock != null) {
                            blockPos.setY(blockPos.getY() + 1);
                            chunkAccess.setBlockState(blockPos, sealCoverBlock.defaultBlockState(), 0);
                            blockPos.setY(blockPos.getY() - 1);
                        }
                        while (blockPos.getY() > chunkAccess.getMinY() + 1) {
                            chunkAccess.setBlockState(blockPos, sealBlock.defaultBlockState(), 0);
                            blockPos.setY(blockPos.getY() - 1);
                        }
                        chunkAccess.setBlockState(blockPos, Blocks.BEDROCK.defaultBlockState(), 0);
                        blockPos.setY(blockPos.getY() - 1);
                        chunkAccess.setBlockState(blockPos, Blocks.VOID_AIR.defaultBlockState(), 0);
                    }
                }
            });
            case Nether -> CompletableFuture.completedFuture(chunk).whenCompleteAsync((chunkAccess, throwable) -> {
                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
                for (blockPos.setZ(0); blockPos.getZ() < 16; blockPos.setZ(blockPos.getZ() + 1)) {
                    for (blockPos.setX(0); blockPos.getX() < 16; blockPos.setX(blockPos.getX() + 1)) {
                        blockPos.setY(chunkAccess.getMinY());
                        chunkAccess.setBlockState(blockPos, Blocks.LAVA.defaultBlockState(), 0);
                        blockPos.setY(chunkAccess.getMinY() + 1);
                        chunkAccess.setBlockState(blockPos, Blocks.LAVA.defaultBlockState(), 0);
                        blockPos.setY(127);
                        chunkAccess.setBlockState(blockPos, Blocks.BEDROCK.defaultBlockState(), 0);
                    }
                }
            });
            default -> CompletableFuture.completedFuture(chunk);
        };
    }
    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        if (unspawnedBiome == null) {
            return parent.createBiomes(randomState, blender, structureManager, chunk);
        } else {
            chunk.fillBiomesFromNoise((var1, var2, var3, var4) -> unspawnedBiome, randomState.sampler());
            return CompletableFuture.completedFuture(chunk);
        }
    }
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }
    @Override
    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return parent.getSpawnHeight(heightAccessor);
    }
    @Override
    public BiomeSource getBiomeSource() {
        return parent.getBiomeSource();
    }
    @Override
    public int getGenDepth() {
        return parent.getGenDepth();
    }
    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory mobCategory, BlockPos pos) {
        return parent.getMobsAt(biome, structureManager, mobCategory, pos);
    }
    @Override
    public int getSeaLevel() {
        return parent.getSeaLevel();
    }
    @Override
    public int getMinY() {
        return parent.getMinY();
    }
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return parent.getBaseHeight(x, z, type, heightAccessor, randomState);
    }
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return parent.getBaseColumn(x, z, heightAccessor, randomState);
    }
    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return parent.getBaseHeight(x, z, type, heightAccessor, randomState);
    }
    @Override
    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return parent.getBaseHeight(x, z, type, heightAccessor, randomState) - 1;
    }
    @Override
    public void addDebugScreenInfo(List<String> outDebugInfo, RandomState randomState, BlockPos pos) {
        parent.addDebugScreenInfo(outDebugInfo, randomState, pos);
    }
    protected List<StructurePlacement> getPlacementsForFeatureCompat(Holder<Structure> structure) {
        return ChunkGeneratorAccess.getPlacementsForFeature(parent, structure);
    }
}



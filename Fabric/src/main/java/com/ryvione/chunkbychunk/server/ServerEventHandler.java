/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.server;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.ryvione.chunkbychunk.common.ChunkByChunkConstants;
import com.ryvione.chunkbychunk.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.chunkbychunk.common.data.ScannerData;
import com.ryvione.chunkbychunk.common.data.SkyDimensionData;
import com.ryvione.chunkbychunk.common.util.ChunkUtil;
import com.ryvione.chunkbychunk.common.util.SpiralIterator;
import com.ryvione.chunkbychunk.server.world.*;
import com.ryvione.chunkbychunk.config.ChunkByChunkConfig;
import net.minecraft.core.BlockPos;
import java.util.Set;
import com.ryvione.chunkbychunk.config.system.ConfigSystem;
import com.ryvione.chunkbychunk.mixins.DefrostedRegistry;
import com.ryvione.chunkbychunk.mixins.OverworldBiomeBuilderAccessor;
import com.ryvione.chunkbychunk.mixins.HolderReferenceAccessor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;
public final class ServerEventHandler {
    private static final Logger LOGGER = LogManager.getLogger(ChunkByChunkConstants.MOD_ID);
    private static final int MAX_FIND_CHUNK_ATTEMPTS = 512;
    private static final String SERVERCONFIG = "serverconfig";
    private static final ConfigSystem configSystem = new ConfigSystem();
    private static final Map<Identifier, ScannerData> SCANNER_DATA_DEFS = new LinkedHashMap<>();
    private static RegistryAccess lastRegistryAccess;
    private static final List<List<int[]>> CHUNK_SPAWN_OFFSETS = ImmutableList.<List<int[]>>builder()
            .add(ImmutableList.of(new int[]{0, 0}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{1, 1}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1}, new int[]{1, 1}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1}, new int[]{1, 1}, new int[]{-1, -1}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1}, new int[]{1, 1}, new int[]{-1, -1}, new int[]{1, -1}))
            .add(ImmutableList.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1}, new int[]{1, 1}, new int[]{-1, -1}, new int[]{1, -1}, new int[]{-1, 1}))
            .build();
    private ServerEventHandler() {
    }
    public static void onServerStarting(MinecraftServer server) {
        lastRegistryAccess = server.registryAccess();
        applyScannerData(lastRegistryAccess);
        configSystem.synchConfig(server.getWorldPath(LevelResource.ROOT).resolve(SERVERCONFIG).resolve(ChunkByChunkConstants.CONFIG_FILE), Paths.get(ChunkByChunkConstants.DEFAULT_CONFIG_PATH).resolve(ChunkByChunkConstants.CONFIG_FILE), ChunkByChunkConfig.get());
        if (ChunkByChunkConfig.get().getGeneration().isEnabled()) {
            ChunkByChunkConstants.LOGGER.info("Setting up sky dimensions");
            applySkyDimensionConfig(server.registryAccess());
            applyChunkByChunkWorldGeneration(server);
        }
    }
    private static void applySkyDimensionConfig(RegistryAccess registryAccess) {
        if (ChunkByChunkConfig.get().getGeneration().isSynchNether()) {
            SkyDimensions.getSkyDimensions().values().stream().filter(x -> "minecraft:the_nether".equals(x.dimensionId) || "the_nether".equals(x.dimensionId)).forEach(x -> {
                x.synchToDimensions.add("minecraft:overworld");
            });
        }
        if (ChunkByChunkConfig.get().getGeneration().sealWorld()) {
            SkyDimensions.getSkyDimensions().values().stream().filter(x -> "minecraft:overworld".equals(x.dimensionId) || "overworld".equals(x.dimensionId)).forEach(x -> {
                x.generationType = SkyChunkGenerator.EmptyGenerationType.Sealed;
            });
        }
        if (ChunkByChunkConfig.get().getGeneration().getInitialChunks() != 1) {
            SkyDimensions.getSkyDimensions().values().stream().filter(x -> "minecraft:overworld".equals(x.dimensionId) || "overworld".equals(x.dimensionId)).forEach(x -> {
                x.initialChunks = ChunkByChunkConfig.get().getGeneration().getInitialChunks();
            });
        }
    }
    private static void applyChunkByChunkWorldGeneration(MinecraftServer server) {
        MappedRegistry<LevelStem> dimensions = (MappedRegistry<LevelStem>) server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        MappedRegistry<Biome> biomeRegistry = (MappedRegistry<Biome>) server.registryAccess().lookupOrThrow(Registries.BIOME);
        Registry<DimensionType> dimensionTypeRegistry = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
        Registry<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        DefrostedRegistry dimensionsDefrost = (DefrostedRegistry) dimensions;
        DefrostedRegistry biomeDefrost = (DefrostedRegistry) biomeRegistry;
        dimensionsDefrost.setFrozen(false);
        biomeDefrost.setFrozen(false);
        try {
            for (Map.Entry<Identifier, SkyDimensionData> entry : SkyDimensions.getSkyDimensions().entrySet()) {
                setupDimension(entry.getKey(), entry.getValue(), dimensions, blocks, biomeRegistry, dimensionTypeRegistry);
            }
            configureDimensionSynching(dimensions);
        } finally {
            // Prevent later registry-freeze passes from re-freezing mutable registries with stale tag state.
            dimensionsDefrost.setFrozen(true);
            biomeDefrost.setFrozen(true);
        }
    }
    private static void configureDimensionSynching(MappedRegistry<LevelStem> dimensions) {
        for (SkyDimensionData config : SkyDimensions.getSkyDimensions().values()) {
            if (!config.enabled) {
                continue;
            }
            LevelStem dimension = dimensions.get(Identifier.parse(config.dimensionId)).map(Holder.Reference::value).orElse(null);
            if (dimension == null) {
                continue;
            }
            for (String synchDimId : config.synchToDimensions) {
                LevelStem synchDim = dimensions.get(Identifier.parse(synchDimId)).map(Holder.Reference::value).orElse(null);
                if (synchDim == null) {
                    continue;
                }
                if (DimensionType.getTeleportationScale(synchDim.type().value(), dimension.type().value()) > 1) {
                    ChunkByChunkConstants.LOGGER.warn("Cowardly refusing to synch dimension {} with {}, as the coordinate scale would result in a performance issues", config.dimensionId, synchDimId);
                    continue;
                }
                if (synchDim.generator() instanceof SkyChunkGenerator generator) {
                    generator.addSynchLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(config.dimensionId)));
                } else {
                    ChunkByChunkConstants.LOGGER.warn("Cannot synch dimension {} with {}, as it is not a sky dimension", config.dimensionId, synchDimId);
                }
            }
        }
    }
    private static void setupDimension(Identifier skyDimensionId, SkyDimensionData config, MappedRegistry<LevelStem> dimensions, Registry<Block> blocks, WritableRegistry<Biome> biomeRegistry, Registry<DimensionType> dimensionTypeRegistry) {
        if (!config.validate(skyDimensionId, dimensions)) {
            config.enabled = false;
        }
        if (!config.enabled) {
            return;
        }
        ChunkByChunkConstants.LOGGER.info("Setting up sky dimension for {}", config.dimensionId);
        LevelStem level = dimensions.get(Identifier.parse(config.dimensionId)).map(Holder.Reference::value).orElse(null);
        if (level == null) {
            ChunkByChunkConstants.LOGGER.warn("Could not resolve dimension {}", config.dimensionId);
            return;
        }
        ChunkGenerator rootGenerator;
        if (level.generator() instanceof SkyChunkGenerator skyChunkGenerator) {
            rootGenerator = skyChunkGenerator.getParent();
        } else {
            rootGenerator = level.generator();
        }
        SkyChunkGenerator generator = setupCoreGenerationDimension(config, dimensions, blocks, biomeRegistry, level, rootGenerator);
        Holder<DimensionType> themeDimensionType = level.type();
        if (config.biomeThemeDimensionType != null && !config.biomeThemeDimensionType.isEmpty()) {
            Optional<Holder.Reference<DimensionType>> holder = dimensionTypeRegistry.get(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(config.biomeThemeDimensionType)));
            if (holder.isPresent()) {
                themeDimensionType = holder.get();
            }
        }
        for (Map.Entry<String, List<String>> biomeTheme : config.biomeThemes.entrySet()) {
            ResourceKey<Level> biomeDim = setupThemeDimension(config.dimensionId, biomeTheme.getKey(), biomeTheme.getValue(), level, dimensions, rootGenerator, biomeRegistry, themeDimensionType);
            if (biomeDim != null) {
                generator.addBiomeDimension(biomeTheme.getKey(), biomeDim);
            }
        }
    }
    private static SkyChunkGenerator setupCoreGenerationDimension(SkyDimensionData config, MappedRegistry<LevelStem> dimensions, Registry<Block> blocks, Registry<Biome> biomes, LevelStem level, ChunkGenerator rootGenerator) {
        Identifier genDimensionId = config.getGenDimensionId();
        ResourceKey<LevelStem> genLevelId = ResourceKey.create(Registries.LEVEL_STEM, genDimensionId);
        LevelStem generationLevel = dimensions.get(genDimensionId).map(Holder.Reference::value).orElse(null);
        if (generationLevel == null) {
            generationLevel = new LevelStem(level.type(), rootGenerator);
            dimensions.register(genLevelId, generationLevel, RegistrationInfo.BUILT_IN);
            ChunkByChunkConstants.LOGGER.info("Created generation dimension: {}", genDimensionId);
        }
        SkyChunkGenerator skyGenerator;
        ResourceKey<LevelStem> mainLevelKey = ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse(config.dimensionId));
        LevelStem currentLevel = dimensions.get(mainLevelKey).map(Holder.Reference::value).orElse(null);
        if (currentLevel == null) {
            ChunkByChunkConstants.LOGGER.warn("Could not resolve level stem {}", config.dimensionId);
            return new SkyChunkGenerator(rootGenerator);
        }
        if (currentLevel.generator() instanceof SkyChunkGenerator) {
            skyGenerator = (SkyChunkGenerator) currentLevel.generator();
            ChunkByChunkConstants.LOGGER.info("Sky dimension already configured for {}", config.dimensionId);
        } else {
            skyGenerator = new SkyChunkGenerator(rootGenerator);
            LevelStem newLevelStem = new LevelStem(currentLevel.type(), skyGenerator);
            Holder.Reference<LevelStem> existingHolder = dimensions.get(mainLevelKey).orElse(null);
            if (existingHolder != null) {
                ((HolderReferenceAccessor<LevelStem>) existingHolder).setValue(newLevelStem);
                ChunkByChunkConstants.LOGGER.info("Wrapped generator for {} with SkyChunkGenerator", config.dimensionId);
            } else {
                ChunkByChunkConstants.LOGGER.warn("Could not find existing holder for dimension {}", config.dimensionId);
            }
        }
        Block sealBlock = blocks.getOptional(Identifier.parse(config.sealBlock)).orElse(Blocks.BEDROCK);
        Block coverBlock = blocks.getOptional(Identifier.parse(config.sealCoverBlock)).orElse(null);
        if (config.unspawnedBiome != null && !config.unspawnedBiome.isEmpty()) {
            biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse(config.unspawnedBiome))).ifPresent(skyGenerator::setUnspawnedBiome);
        }
        skyGenerator.configure(ResourceKey.create(Registries.DIMENSION, genLevelId.identifier()), config.generationType, sealBlock, coverBlock, config.initialChunks, config.allowChunkSpawner, config.allowUnstableChunkSpawner);
        return skyGenerator;
    }
    private static ResourceKey<Level> setupThemeDimension(String dimId, String themeName, List<String> biomes, LevelStem sourceLevel, MappedRegistry<LevelStem> dimensions, ChunkGenerator rootGenerator, WritableRegistry<Biome> biomeRegistry, Holder<DimensionType> themeDimensionType) {
        Identifier biomeDimId = Identifier.parse(dimId + "_" + themeName + "_gen");
        ResourceKey<LevelStem> levelKey = ResourceKey.create(Registries.LEVEL_STEM, biomeDimId);
        if (dimensions.containsKey(levelKey)) {
            ChunkByChunkConstants.LOGGER.info("Theme dimension {} already exists, skipping registration", biomeDimId);
            return ResourceKey.create(Registries.DIMENSION, biomeDimId);
        }
        List<ResourceKey<Biome>> biomeKeys = biomes.stream().map(x -> ResourceKey.create(Registries.BIOME, Identifier.parse(x))).filter(key -> {
            boolean valid = biomeRegistry.containsKey(key);
            if (!valid) {
                ChunkByChunkConstants.LOGGER.warn("Could not resolve biome {} for {}", key, dimId);
            }
            return valid;
        }).toList();
        BiomeSource source;
        if (biomeKeys.size() == 0 || !(rootGenerator instanceof NoiseBasedChunkGenerator)) {
            return null;
        } else if (biomeKeys.size() == 1) {
            source = new FixedBiomeSource(biomeRegistry.get(biomeKeys.get(0)).orElseThrow());
        } else {
            ImmutableList.Builder<Pair<Climate.ParameterPoint, Holder<Biome>>> builder = ImmutableList.builder();
            ((OverworldBiomeBuilderAccessor) (Object) new OverworldBiomeBuilder()).callAddBiomes((pair) -> {
                if (biomeKeys.contains(pair.getSecond())) {
                    Holder<Biome> biomeHolder = biomeRegistry.get(pair.getSecond()).orElse(null);
                    if (biomeHolder != null) {
                        builder.add(Pair.of(pair.getFirst(), biomeHolder));
                    }
                }
            });
            Climate.ParameterList<Holder<Biome>> parameterList = new Climate.ParameterList<>(builder.build());
            source = MultiNoiseBiomeSource.createFromList(parameterList);
        }
        LevelStem biomeLevel = new LevelStem(themeDimensionType, new NoiseBasedChunkGenerator(source, ChunkGeneratorAccess.getNoiseGeneratorSettings(rootGenerator)));
        dimensions.register(levelKey, biomeLevel, RegistrationInfo.BUILT_IN);
        return ResourceKey.create(Registries.DIMENSION, biomeDimId);
    }
    public static void onServerStarted(MinecraftServer server) {
        if (ChunkByChunkConfig.get().getGeneration().isEnabled()) {
            checkSpawnInitialChunks(server);
        }
    }
    private static void checkSpawnInitialChunks(MinecraftServer server) {
        ServerLevel overworldLevel = server.getLevel(Level.OVERWORLD);
        BlockPos overworldSpawnPos;
        if (overworldLevel != null && overworldLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator skyGenerator) {
            ServerLevel generationLevel = server.getLevel(skyGenerator.getGenerationLevel());
            overworldSpawnPos = generationLevel.getLevelData().getRespawnData().pos();
            ChunkPos chunkSpawnPos = new ChunkPos(overworldSpawnPos);
            if (SpawnChunkHelper.isEmptyChunk(overworldLevel, chunkSpawnPos)) {
                overworldSpawnPos = findAppropriateSpawnChunk(overworldLevel, generationLevel, server.registryAccess());
                spawnInitialChunks(overworldLevel, skyGenerator.getInitialChunks(), overworldSpawnPos, ChunkByChunkConfig.get().getGeneration().spawnNewChunkChest());
            }
        } else {
            overworldSpawnPos = overworldLevel.getLevelData().getRespawnData().pos();
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level != overworldLevel && level.getChunkSource().getGenerator() instanceof SkyChunkGenerator levelGenerator) {
                if (levelGenerator.getInitialChunks() > 0) {
                    spawnInitialChunks(level, levelGenerator.getInitialChunks(), overworldSpawnPos, false);
                }
            }
        }
    }
    private static BlockPos findAppropriateSpawnChunk(ServerLevel overworldLevel, ServerLevel generationLevel, RegistryAccess registryAccess) {
        if (ChunkByChunkConfig.get().getGeneration().isSpawnChunkStrip()) {
            return overworldLevel.getLevelData().getRespawnData().pos();
        }
        TagKey<Block> logsTag = BlockTags.LOGS;
        TagKey<Block> leavesTag = BlockTags.LEAVES;
        Set<Block> copper = ImmutableSet.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK);
        BlockPos spawnPos = overworldLevel.getLevelData().getRespawnData().pos();
        switch (ChunkByChunkConfig.get().getGameplayConfig().getStartRestriction()) {
            case Village -> {
                spawnPos = findVillage(generationLevel, registryAccess, spawnPos);
            }
            case Biome -> {
                String startingBiome = ChunkByChunkConfig.get().getGameplayConfig().getStartingBiome();
                spawnPos = findBiome(overworldLevel, generationLevel, registryAccess, spawnPos, startingBiome);
            }
        }
        ChunkPos initialChunkPos = new ChunkPos(spawnPos);
        SpiralIterator iterator = new SpiralIterator(initialChunkPos.x, initialChunkPos.z);
        int attempts = 0;
        while (attempts < MAX_FIND_CHUNK_ATTEMPTS) {
            LevelChunk chunk = generationLevel.getChunk(iterator.getX(), iterator.getY());
            if (ChunkUtil.countBlocks(chunk, logsTag) > 2
                    && ChunkUtil.countBlocks(chunk, Blocks.WATER) > 0
                    && ChunkUtil.countBlocks(chunk, leavesTag) > 3
                    && ChunkUtil.countBlocks(chunk, copper) >= 36) {
                spawnPos = new BlockPos(chunk.getPos().getMiddleBlockX(), ChunkUtil.getSafeSpawnHeight(chunk, chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()), chunk.getPos().getMiddleBlockZ());
                break;
            }
            iterator.next();
            attempts++;
        }
        if (attempts < MAX_FIND_CHUNK_ATTEMPTS) {
            LOGGER.info("Found appropriate spawn chunk in {} attempts", attempts);
        } else {
            LOGGER.info("No appropriate spawn chunk found :(");
        }
        ServerLevelData levelData = (ServerLevelData) overworldLevel.getLevelData();
        LevelData.RespawnData respawnData = levelData.getRespawnData();
        levelData.setSpawn(LevelData.RespawnData.of(overworldLevel.dimension(), spawnPos, respawnData.yaw(), respawnData.pitch()));
        return spawnPos;
    }
    private static BlockPos findBiome(ServerLevel overworldLevel, ServerLevel generationLevel, RegistryAccess registryAccess, BlockPos spawnPos, String startingBiome) {
        Registry<Biome> biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
        if (startingBiome.startsWith("#")) {
            Optional<HolderSet.Named<Biome>> tagSet = biomeRegistry.get(TagKey.create(Registries.BIOME, Identifier.parse(startingBiome.substring(1))));
            if (tagSet.isPresent()) {
                Pair<BlockPos, Holder<Biome>> location = generationLevel.findClosestBiome3d(x -> tagSet.get().contains(x), spawnPos, 6400, 32, 64);
                if (location != null) {
                    spawnPos = location.getFirst();
                    ChunkByChunkConstants.LOGGER.info("Spawn shifted to nearest biome of tag " + startingBiome);
                }
            } else {
                ChunkByChunkConstants.LOGGER.warn("No biome matching '" + startingBiome + "' found");
            }
        } else {
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(startingBiome));
            if (biomeRegistry.containsKey(biomeKey)) {
                Pair<BlockPos, Holder<Biome>> location = generationLevel.findClosestBiome3d(x -> x.is(biomeKey), spawnPos, 6400, 32, 64);
                if (location != null) {
                    spawnPos = location.getFirst();
                    ChunkByChunkConstants.LOGGER.info("Spawn shifted to nearest biome: " + startingBiome);
                } else {
                    ChunkByChunkConstants.LOGGER.warn("No biome matching '" + startingBiome + "' found");
                }
            }
        }
        return spawnPos;
    }
    private static BlockPos findVillage(ServerLevel generationLevel, RegistryAccess registryAccess, BlockPos spawnPos) {
        Registry<Structure> structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Optional<HolderSet.Named<Structure>> structuresTag = structures.get(StructureTags.VILLAGE);
        if (structuresTag.isPresent()) {
            HolderSet<Structure> holders = structuresTag.get();
            Pair<BlockPos, Holder<Structure>> nearest = generationLevel.getChunkSource().getGenerator().findNearestMapStructure(generationLevel, holders, spawnPos, 100, false);
            if (nearest != null) {
                spawnPos = nearest.getFirst();
                ChunkByChunkConstants.LOGGER.info("Spawn shifted to nearest village");
            }
        } else {
            ChunkByChunkConstants.LOGGER.warn("Could not find village spawn");
        }
        return spawnPos;
    }
    private static void spawnInitialChunks(ServerLevel level, int initialChunks, BlockPos overworldSpawn, boolean spawnChest) {
        ChunkSpawnController chunkSpawnController = ChunkSpawnController.get(level.getServer());
        BlockPos scaledSpawn = new BlockPos(Mth.floor(overworldSpawn.getX() / level.dimensionType().coordinateScale()), overworldSpawn.getY(), Mth.floor(overworldSpawn.getZ() / level.dimensionType().coordinateScale()));
        ChunkPos centerChunkPos = new ChunkPos(scaledSpawn);
        if (initialChunks > 0 && initialChunks <= CHUNK_SPAWN_OFFSETS.size()) {
            List<int[]> chunkOffsets = CHUNK_SPAWN_OFFSETS.get(initialChunks - 1);
            for (int[] offset : chunkOffsets) {
                ChunkPos targetPos = new ChunkPos(centerChunkPos.x + offset[0], centerChunkPos.z + offset[1]);
                if (chunkSpawnController.request(level, "", false, targetPos.getMiddleBlockPosition(0), offset[0] == 0 && offset[1] == 0)) {
                    if (spawnChest && offset[0] == 0 && offset[1] == 0) {
                        SpawnChunkHelper.createNextSpawner(level, targetPos);
                    }
                }
            }
        } else {
            SpiralIterator spiralIterator = new SpiralIterator(centerChunkPos.x, centerChunkPos.z);
            for (int i = 0; i < initialChunks; i++) {
                ChunkPos targetPos = new ChunkPos(spiralIterator.getX(), spiralIterator.getY());
                if (chunkSpawnController.request(level, "", false, targetPos.getMiddleBlockPosition(0), i == 0)) {
                    if (spawnChest && i == 0) {
                        SpawnChunkHelper.createNextSpawner(level, targetPos);
                    }
                }
                spiralIterator.next();
            }
        }
    }
    public static void onResourceManagerReload(ResourceManager resourceManager) {
        Gson gson = new GsonBuilder().registerTypeAdapter(SkyChunkGenerator.EmptyGenerationType.class, (JsonDeserializer<SkyChunkGenerator.EmptyGenerationType>) (json, typeOfT, context) -> SkyChunkGenerator.EmptyGenerationType.getFromString(json.getAsString())).create();
        loadScannerData(resourceManager, gson);
        if (lastRegistryAccess != null) {
            applyScannerData(lastRegistryAccess);
        }
        SkyDimensions.loadSkyDimensionData(resourceManager, gson);
    }
    private static void loadScannerData(ResourceManager resourceManager, Gson gson) {
        SCANNER_DATA_DEFS.clear();
        int count = 0;
        Map<Identifier, Resource> resources = resourceManager.listResources(ChunkByChunkConstants.SCANNER_DATA_PATH, r -> !r.getPath().isEmpty() && !ChunkByChunkConstants.SCANNER_DATA_PATH.equals(r.getPath()));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier location = entry.getKey();
            Resource resource = entry.getValue();
            try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                ScannerData data = gson.fromJson(reader, ScannerData.class);
                SCANNER_DATA_DEFS.put(location, data);
                count++;
            } catch (IOException | RuntimeException e) {
                ChunkByChunkConstants.LOGGER.error("Failed to read scanner data '{}'", location, e);
            }
        }
        ChunkByChunkConstants.LOGGER.info("Loaded {} scanner data configs", count);
    }
    private static void applyScannerData(RegistryAccess registryAccess) {
        if (registryAccess == null) {
            return;
        }
        WorldScannerBlockEntity.clearItemMappings();
        for (Map.Entry<Identifier, ScannerData> entry : SCANNER_DATA_DEFS.entrySet()) {
            entry.getValue().process(entry.getKey(), registryAccess);
        }
        ChunkByChunkConstants.LOGGER.info("Applied {} scanner data configs", SCANNER_DATA_DEFS.size());
    }
    public static void onLevelTick(MinecraftServer server) {
        ChunkSpawnController chunkSpawnController = ChunkSpawnController.get(server);
        if (chunkSpawnController != null) {
            chunkSpawnController.tick();
        }
        if (server.getTickCount() % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                StarterGuide.giveIfNeeded(player);
            }
        }
        if (server.getTickCount() % 100 == 0) {
            ChestTracker tracker = ChestTracker.get(server);
            Set<BlockPos> chests = new java.util.HashSet<>(tracker.getChestPositions());
            for (BlockPos pos : chests) {
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.isLoaded(pos)) {
                        tracker.checkAndRemoveIfEmpty(pos, level);
                        break;
                    }
                }
            }
        }
    }
}





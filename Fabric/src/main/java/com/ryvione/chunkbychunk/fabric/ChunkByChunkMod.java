/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.fabric;
import com.google.common.collect.ImmutableList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.ryvione.chunkbychunk.common.ChunkByChunkConstants;
import com.ryvione.chunkbychunk.common.CommonEventHandler;
import com.ryvione.chunkbychunk.common.blockEntities.*;
import com.ryvione.chunkbychunk.common.blocks.*;
import com.ryvione.chunkbychunk.server.commands.SpawnChunkCommand;
import com.ryvione.chunkbychunk.common.menus.BedrockChestMenu;
import com.ryvione.chunkbychunk.common.menus.WorldForgeMenu;
import com.ryvione.chunkbychunk.common.menus.WorldMenderMenu;
import com.ryvione.chunkbychunk.common.menus.WorldScannerMenu;
import com.ryvione.chunkbychunk.server.world.SkyChunkGenerator;
import com.ryvione.chunkbychunk.config.ChunkByChunkConfig;
import com.ryvione.chunkbychunk.config.system.ConfigSystem;
import com.ryvione.chunkbychunk.server.ServerEventHandler;
import com.ryvione.chunkbychunk.server.StarterGuide;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
public class ChunkByChunkMod implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger(ChunkByChunkConstants.MOD_ID);
    public static final SpawnChunkBlock SPAWN_CHUNK_BLOCK = new SpawnChunkBlock("", false, blockProps("chunkspawner").mapColor(MapColor.STONE));
    public static final Block UNSTABLE_SPAWN_CHUNK_BLOCK = new SpawnChunkBlock("", true, blockProps("unstablechunkspawner").mapColor(MapColor.STONE));
    public static final Block BEDROCK_CHEST_BLOCK = new BedrockChestBlock(blockProps("bedrockchest").mapColor(MapColor.STONE).strength(-1, 3600000.0F).isValidSpawn(((state, getter, pos, arg) -> false)));
    public static final Block WORLD_CORE_BLOCK = new Block(blockProps("worldcore").mapColor(MapColor.STONE).strength(3.0F).lightLevel((state) -> 7));
    public static final Block WORLD_FORGE_BLOCK = new WorldForgeBlock(blockProps("worldforge").mapColor(MapColor.STONE).strength(3.5F).lightLevel((state) -> 7));
    public static final Block WORLD_SCANNER_BLOCK = new WorldScannerBlock(blockProps("worldscanner").mapColor(MapColor.STONE).strength(3.5F).lightLevel((state) -> 4));
    public static final Block WORLD_MENDER_BLOCK = new WorldMenderBlock(blockProps("worldmender").mapColor(MapColor.STONE).strength(3.5F).lightLevel((state) -> 4));
    public static Item SPAWN_CHUNK_BLOCK_ITEM;
    public static Item UNSTABLE_SPAWN_CHUNK_BLOCK_ITEM;
    public static Item BEDROCK_CHEST_BLOCK_ITEM;
    public static Item WORLD_CORE_BLOCK_ITEM;
    public static Item WORLD_FORGE_BLOCK_ITEM;
    public static Item WORLD_SCANNER_BLOCK_ITEM;
    public static Item WORLD_MENDER_BLOCK_ITEM;
    public static Item WORLD_FRAGMENT_ITEM;
    public static Item WORLD_SHARD_ITEM;
    public static Item WORLD_CRYSTAL_ITEM;
    public static BlockEntityType<BedrockChestBlockEntity> BEDROCK_CHEST_BLOCK_ENTITY;
    public static BlockEntityType<WorldForgeBlockEntity> WORLD_FORGE_BLOCK_ENTITY;
    public static BlockEntityType<WorldScannerBlockEntity> WORLD_SCANNER_BLOCK_ENTITY;
    public static BlockEntityType<WorldMenderBlockEntity> WORLD_MENDER_BLOCK_ENTITY;
    public static MenuType<BedrockChestMenu> BEDROCK_CHEST_MENU;
    public static MenuType<WorldForgeMenu> WORLD_FORGE_MENU;
    public static MenuType<WorldScannerMenu> WORLD_SCANNER_MENU;
    public static MenuType<WorldMenderMenu> WORLD_MENDER_MENU;
    public static SoundEvent SPAWN_CHUNK_SOUND_EVENT;
    public static Identifier CONFIG_PACKET = Identifier.fromNamespaceAndPath(ChunkByChunkConstants.MOD_ID, "config");
    public static List<ItemStack> biomeThemedBlockItems;
    public static final CreativeModeTab CHUNK_BY_CHUNK_TAB = FabricItemGroup.builder()
            .icon(() -> new ItemStack(WORLD_CORE_BLOCK))
            .title(Component.translatable("itemGroup.chunkbychunk.main"))
            .displayItems((parameters, output) -> {
                output.accept(SPAWN_CHUNK_BLOCK_ITEM);
                output.accept(UNSTABLE_SPAWN_CHUNK_BLOCK_ITEM);
                for (ItemStack itemStack : biomeThemedBlockItems) {
                    output.accept(itemStack);
                }
                output.accept(BEDROCK_CHEST_BLOCK_ITEM);
                output.accept(WORLD_CORE_BLOCK_ITEM);
                output.accept(WORLD_FORGE_BLOCK_ITEM);
                output.accept(WORLD_SCANNER_BLOCK_ITEM);
                output.accept(WORLD_MENDER_BLOCK_ITEM);
                output.accept(WORLD_FRAGMENT_ITEM);
                output.accept(WORLD_SHARD_ITEM);
                output.accept(WORLD_CRYSTAL_ITEM);
            })
            .build();
    static {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(ChunkByChunkConstants.MOD_ID, "server_data");
            }
            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                ServerEventHandler.onResourceManagerReload(resourceManager);
            }
        });
    }
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing");
        Registry.register(BuiltInRegistries.BLOCK, createId("chunkspawner"), SPAWN_CHUNK_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, createId("unstablechunkspawner"), UNSTABLE_SPAWN_CHUNK_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, createId("bedrockchest"), BEDROCK_CHEST_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, createId("worldcore"), WORLD_CORE_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, createId("worldforge"), WORLD_FORGE_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, createId("worldscanner"), WORLD_SCANNER_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, createId("worldmender"), WORLD_MENDER_BLOCK);
        List<Block> themeSpawnBlocks = new ArrayList<>();
        List<String> biomeThemesList = new ArrayList<>(ChunkByChunkConstants.BIOME_THEMES);
        for (String biomeTheme : biomeThemesList) {
            String biomeBlockId = biomeTheme + ChunkByChunkConstants.BIOME_CHUNK_BLOCK_SUFFIX;
            Block spawnBlock = new SpawnChunkBlock(biomeTheme, false, blockProps(biomeBlockId).mapColor(MapColor.STONE));
            Registry.register(BuiltInRegistries.BLOCK, createId(biomeTheme + ChunkByChunkConstants.BIOME_CHUNK_BLOCK_SUFFIX), spawnBlock);
            themeSpawnBlocks.add(spawnBlock);
        }
        SPAWN_CHUNK_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("chunkspawner"), new BlockItem(SPAWN_CHUNK_BLOCK, itemProps("chunkspawner").useBlockDescriptionPrefix()));
        UNSTABLE_SPAWN_CHUNK_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("unstablechunkspawner"), new BlockItem(UNSTABLE_SPAWN_CHUNK_BLOCK, itemProps("unstablechunkspawner").useBlockDescriptionPrefix()));
        List<ItemStack> themeSpawnBlockItems = new ArrayList<>();
        for (int i = 0; i < biomeThemesList.size(); i++) {
            String biomeTheme = biomeThemesList.get(i);
            Block spawnBlock = themeSpawnBlocks.get(i);
            String biomeItemId = biomeTheme + ChunkByChunkConstants.BIOME_CHUNK_BLOCK_ITEM_SUFFIX;
            BlockItem item = new BlockItem(spawnBlock, itemProps(biomeItemId).useBlockDescriptionPrefix());
            Registry.register(BuiltInRegistries.ITEM, createId(biomeItemId), item);
            themeSpawnBlockItems.add(item.getDefaultInstance());
        }
        biomeThemedBlockItems = ImmutableList.copyOf(themeSpawnBlockItems);
        BEDROCK_CHEST_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("bedrockchest"), new BlockItem(BEDROCK_CHEST_BLOCK, itemProps("bedrockchest").useBlockDescriptionPrefix()));
        WORLD_CORE_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldcore"), new BlockItem(WORLD_CORE_BLOCK, itemProps("worldcore").useBlockDescriptionPrefix()));
        WORLD_FORGE_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldforge"), new BlockItem(WORLD_FORGE_BLOCK, itemProps("worldforge").useBlockDescriptionPrefix()));
        WORLD_SCANNER_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldscanner"), new BlockItem(WORLD_SCANNER_BLOCK, itemProps("worldscanner").useBlockDescriptionPrefix()));
        WORLD_MENDER_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldmender"), new BlockItem(WORLD_MENDER_BLOCK, itemProps("worldmender").useBlockDescriptionPrefix()));
        WORLD_FRAGMENT_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldfragment"), new Item(itemProps("worldfragment")));
        WORLD_SHARD_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldshard"), new Item(itemProps("worldshard")));
        WORLD_CRYSTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, createId("worldcrystal"), new Item(itemProps("worldcrystal")));
        BEDROCK_CHEST_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, createId("bedrockchestentity"), FabricBlockEntityTypeBuilder.create(BedrockChestBlockEntity::new, BEDROCK_CHEST_BLOCK).build(null));
        WORLD_FORGE_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, createId("worldforgeentity"), FabricBlockEntityTypeBuilder.create(WorldForgeBlockEntity::new, WORLD_FORGE_BLOCK).build(null));
        WORLD_SCANNER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, createId("worldscannerentity"), FabricBlockEntityTypeBuilder.create(WorldScannerBlockEntity::new, WORLD_SCANNER_BLOCK).build(null));
        WORLD_MENDER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, createId("worldmenderentity"), FabricBlockEntityTypeBuilder.create(WorldMenderBlockEntity::new, WORLD_MENDER_BLOCK).build(null));
        BEDROCK_CHEST_MENU = Registry.register(BuiltInRegistries.MENU, createId("bedrockchestmenu"), new MenuType<>(BedrockChestMenu::new, FeatureFlags.VANILLA_SET));
        WORLD_FORGE_MENU = Registry.register(BuiltInRegistries.MENU, createId("worldforgemenu"), new MenuType<>(WorldForgeMenu::new, FeatureFlags.VANILLA_SET));
        WORLD_SCANNER_MENU = Registry.register(BuiltInRegistries.MENU, createId("worldscannermenu"), new MenuType<>(WorldScannerMenu::new, FeatureFlags.VANILLA_SET));
        WORLD_MENDER_MENU = Registry.register(BuiltInRegistries.MENU, createId("worldmendermenu"), new MenuType<>(WorldMenderMenu::new, FeatureFlags.VANILLA_SET));
        SPAWN_CHUNK_SOUND_EVENT = Registry.register(BuiltInRegistries.SOUND_EVENT, createId("spawnchunkevent"), SoundEvent.createVariableRangeEvent(createId("chunk_spawn_sound")));
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, createId("skychunkgenerator"), SkyChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, createId("netherchunkgenerator"), SkyChunkGenerator.OLD_NETHER_CODEC);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, createId("main"), CHUNK_BY_CHUNK_TAB);
        ServerLifecycleEvents.SERVER_STARTED.register(ServerEventHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STARTING.register(ServerEventHandler::onServerStarting);
        ServerTickEvents.END_SERVER_TICK.register(ServerEventHandler::onLevelTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SpawnChunkCommand.register(dispatcher);
            com.ryvione.chunkbychunk.server.commands.ChestsCommand.register(dispatcher);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> StarterGuide.giveIfNeeded(handler.player)));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            Block clickedBlock = world.getBlockState(hitResult.getBlockPos()).getBlock();
            if (clickedBlock == WORLD_FORGE_BLOCK
                    || clickedBlock == WORLD_SCANNER_BLOCK
                    || clickedBlock == WORLD_MENDER_BLOCK) {
                if (world.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
                if (blockEntity instanceof MenuProvider provider) {
                    player.openMenu(provider);
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.PASS;
            }

            ItemStack heldItem = player.getItemInHand(hand);
            if (!(heldItem.getItem() instanceof BlockItem)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            BlockPos placePos = pos.relative(hitResult.getDirection());
            if (!CommonEventHandler.isBlockPlacementAllowed(placePos, player, world)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        setupConfig();
    }
    private void setupConfig() {
        new ConfigSystem().synchConfig(Paths.get("defaultconfigs", ChunkByChunkConstants.MOD_ID + ".toml"), ChunkByChunkConfig.get());
    }
    private static BlockBehaviour.Properties blockProps(String id) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, createId(id)));
    }

    private static Item.Properties itemProps(String id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, createId(id)));
    }

    private static Identifier createId(String id) {
        return Identifier.fromNamespaceAndPath(ChunkByChunkConstants.MOD_ID, id);
    }
}


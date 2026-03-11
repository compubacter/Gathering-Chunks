/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.server.commands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.ryvione.chunkbychunk.server.world.ChestTracker;
import java.util.Set;
public class ChestsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chests")
                .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                .executes(ChestsCommand::listChests)
                .then(Commands.literal("tracker")
                        .then(Commands.literal("enable")
                                .executes(context -> setTrackerState(context, true)))
                        .then(Commands.literal("disable")
                                .executes(context -> setTrackerState(context, false)))));
    }
    private static int listChests(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (level.getServer() == null) {
            return 0;
        }
        ChestTracker tracker = ChestTracker.get(level.getServer());
        Set<BlockPos> chests = tracker.getChestPositions();
        Set<BlockPos> dimensionChests = new java.util.HashSet<>();
        for (BlockPos pos : chests) {
            if (level.isLoaded(pos)) {
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock ||
                        state.getBlock() == com.ryvione.chunkbychunk.interop.Services.PLATFORM.bedrockChestBlock()) {
                    dimensionChests.add(pos);
                } else {
                    tracker.removeChest(pos);
                }
            }
        }
        if (dimensionChests.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No available chests found in this dimension."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Available chests in " + level.dimension().identifier() + " (" + dimensionChests.size() + "):"), false);
        for (BlockPos pos : dimensionChests) {
            Component message = Component.literal("  X=" + pos.getX() + " Y=" + pos.getY() + " Z=" + pos.getZ());
            source.sendSuccess(() -> message, false);
        }
        return dimensionChests.size();
    }
    private static int setTrackerState(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        ChestTracker tracker = ChestTracker.get(source.getServer());
        tracker.setTrackerEnabled(player.getUUID(), enabled);
        if (enabled) {
            player.sendSystemMessage(Component.literal("Chest tracker notifications enabled"));
        } else {
            player.sendSystemMessage(Component.literal("Chest tracker notifications disabled"));
        }
        return 1;
    }
}


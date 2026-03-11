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

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

public final class StarterGuide {
    private static final String RECEIVED_GUIDE_TAG = "chunkbychunk_starter_guide_v2";
    private static final String GUIDE_TITLE = "Chunk By Chunk Guide";

    private StarterGuide() {
    }

    public static void giveIfNeeded(ServerPlayer player) {
        if (player.getTags().contains(RECEIVED_GUIDE_TAG)) {
            return;
        }

        if (playerHasGuide(player)) {
            player.addTag(RECEIVED_GUIDE_TAG);
            return;
        }

        ItemStack guideBook = createGuideBook();
        if (guideBook.isEmpty()) {
            return;
        }

        boolean delivered = player.getInventory().add(guideBook.copy());
        if (!delivered) {
            delivered = player.drop(guideBook, false) != null;
        }

        if (delivered) {
            player.addTag(RECEIVED_GUIDE_TAG);
        }
    }

    private static boolean playerHasGuide(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(Items.WRITTEN_BOOK)) {
                continue;
            }

            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null && GUIDE_TITLE.equals(content.title().raw())) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(
                Filterable.passThrough(Component.literal(
                        "Chunk By Chunk Guide\n\n" +
                        "Welcome! Your world grows one chunk at a time.\n\n" +
                        "Use Chunk Spawners on chunk edges to expand safely."
                )),
                Filterable.passThrough(Component.literal(
                        "Core Loop\n\n" +
                        "1) Explore spawned chunks.\n" +
                        "2) Gather resources.\n" +
                        "3) Craft/find chunk tools.\n" +
                        "4) Spawn new chunks.\n\n" +
                        "World Forge upgrades fragments -> shards -> crystals -> world core."
                )),
                Filterable.passThrough(Component.literal(
                        "Machines\n\n" +
                        "World Scanner: insert target item + fuel to map nearby chunk contents.\n\n" +
                        "World Mender: insert World Core or a Chunk Spawner to auto-fill nearest missing chunks."
                )),
                Filterable.passThrough(Component.literal(
                        "Tips\n\n" +
                        "- Empty chunks are void; expand carefully.\n" +
                        "- Unstable spawners create random chunks.\n" +
                        "- Bedrock chest opens once overhead chunk weight is low enough.\n\n" +
                        "Good luck!"
                ))
        );

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Chunk By Chunk Guide"),
                "Chunk By Chunk",
                0,
                pages,
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }
}

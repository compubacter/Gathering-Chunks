/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Chunk By Chunk (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.chunkbychunk.client.screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import com.ryvione.chunkbychunk.common.ChunkByChunkConstants;
import com.ryvione.chunkbychunk.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.chunkbychunk.common.menus.WorldScannerMenu;
public class WorldScannerScreen extends AbstractContainerScreen<WorldScannerMenu> {
    private static final Identifier CONTAINER_TEXTURE = Identifier.fromNamespaceAndPath(ChunkByChunkConstants.MOD_ID, "textures/gui/container/worldscanner.png");
    private static final int MAIN_TEXTURE_DIM = 512;
    private static final int MAP_DIMENSIONS = 128;
    private static final float TICKS_PER_FRAME = 4f;
    private static final int NUM_FRAMES = 8;
    private float animCounter = 0.f;
    public WorldScannerScreen(WorldScannerMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component);
        imageWidth = 310;
        imageHeight = 166;
    }
    @Override
    protected void init() {
        super.init();
    }
    @Override
    public void onClose() {
        super.onClose();
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int cursorX, int cursorY) {
        super.renderTooltip(guiGraphics, cursorX, cursorY);
        int mapX = cursorX - 174 - leftPos;
        int mapY = cursorY - 18 - topPos;
        if (mapX >= 0 && mapY >= 0 && mapX < MAP_DIMENSIONS && mapY < MAP_DIMENSIONS) {
            mapX = mapX / WorldScannerBlockEntity.SCAN_ZOOM - WorldScannerBlockEntity.SCAN_CENTER;
            mapY = mapY / WorldScannerBlockEntity.SCAN_ZOOM - WorldScannerBlockEntity.SCAN_CENTER;
            StringBuilder builder = new StringBuilder();
            if (mapY < 0) {
                builder.append(-mapY);
                builder.append(" N ");
            } else if (mapY > 0) {
                builder.append(mapY);
                builder.append(" S ");
            }
            if (mapX < 0) {
                builder.append(-mapX);
                builder.append(" W");
            } else if (mapX > 0) {
                builder.append(mapX);
                builder.append(" E");
            }
            if (builder.length() > 0) {
                guiGraphics.setTooltipForNextFrame(this.font, Component.literal(builder.toString()), cursorX, cursorY);
            }
        }
    }
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
        animCounter += delta;
        while (animCounter > TICKS_PER_FRAME * NUM_FRAMES) {
            animCounter -= TICKS_PER_FRAME * NUM_FRAMES;
        }
        int frame = Mth.floor(animCounter / TICKS_PER_FRAME);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight, MAIN_TEXTURE_DIM, MAIN_TEXTURE_DIM);
        if (menu.getEnergy() > 0) {
            int display = Mth.ceil(7.f * menu.getEnergy() / menu.getMaxEnergy());
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos + 54, topPos + 56, 128 + 12 * display, 166 + 12 * frame, 13, 13, MAIN_TEXTURE_DIM, MAIN_TEXTURE_DIM);
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos + 234, topPos + 78, 124, 166 + frame * 4, 4, 4, MAIN_TEXTURE_DIM, MAIN_TEXTURE_DIM);
    }
}

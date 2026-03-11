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
import com.ryvione.chunkbychunk.common.menus.WorldForgeMenu;
public class WorldForgeScreen extends AbstractContainerScreen<WorldForgeMenu> {
    public static final Identifier CONTAINER_TEXTURE = Identifier.fromNamespaceAndPath(ChunkByChunkConstants.MOD_ID, "textures/gui/container/worldforge.png");
    public static final float TICKS_PER_FRAME = 2f;
    public static final int NUM_FRAMES = 8;
    private static final int MAIN_TEXTURE_DIM = 256;
    private float animCounter = 0.f;
    public WorldForgeScreen(WorldForgeMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component);
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
        animCounter += delta;
        while (animCounter > TICKS_PER_FRAME * NUM_FRAMES) {
            animCounter -= TICKS_PER_FRAME * NUM_FRAMES;
        }
        int frame = Mth.floor(animCounter / TICKS_PER_FRAME);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight, MAIN_TEXTURE_DIM, MAIN_TEXTURE_DIM);
        if (menu.getProgress() > 0)
        {
            int completion = 0;
            int goal = menu.getGoal();
            if (goal > 0) {
                int progress = Math.min(goal, menu.getProgress());
                completion = 30 * progress / goal;
            }
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos + 78, topPos + 37, 176, frame * 11, completion, 11, MAIN_TEXTURE_DIM, MAIN_TEXTURE_DIM);
        }
    }
}

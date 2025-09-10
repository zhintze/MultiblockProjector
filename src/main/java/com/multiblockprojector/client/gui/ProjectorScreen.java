package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.network.MessageProjectorSync;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * GUI screen for selecting multiblock structures
 */
public class ProjectorScreen extends Screen {
    private final ItemStack projectorStack;
    private final InteractionHand hand;
    private final Settings settings;
    
    private List<IUniversalMultiblock> availableMultiblocks;
    private int scrollOffset = 0;
    private static final int ENTRIES_PER_PAGE = 8;
    private static final int ENTRY_HEIGHT = 20;
    
    private SimpleMultiblockPreviewRenderer previewRenderer;
    private IUniversalMultiblock selectedMultiblock;
    private boolean isDragging = false;
    private double lastMouseX, lastMouseY;
    
    public ProjectorScreen(ItemStack projectorStack, InteractionHand hand) {
        super(Component.translatable("gui.multiblockprojector.projector"));
        this.projectorStack = projectorStack;
        this.hand = hand;
        this.settings = ProjectorItem.getSettings(projectorStack);
        this.availableMultiblocks = UniversalMultiblockHandler.getMultiblocks();
        this.previewRenderer = new SimpleMultiblockPreviewRenderer();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate layout areas
        int leftPanelWidth = this.width / 3;
        int startY = 50;
        
        // Create buttons for each visible multiblock in left panel
        for (int i = 0; i < Math.min(ENTRIES_PER_PAGE, availableMultiblocks.size() - scrollOffset); i++) {
            int index = scrollOffset + i;
            if (index >= availableMultiblocks.size()) break;
            
            IUniversalMultiblock multiblock = availableMultiblocks.get(index);
            
            Button button = Button.builder(
                multiblock.getDisplayName(),
                (btn) -> selectMultiblockForPreview(multiblock)
            )
            .bounds(10, startY + i * ENTRY_HEIGHT, leftPanelWidth - 30, 18)
            .build();
            
            this.addRenderableWidget(button);
        }
        
        // Add scroll buttons if needed
        if (scrollOffset > 0) {
            this.addRenderableWidget(Button.builder(
                Component.literal("↑"),
                (btn) -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    rebuildWidgets();
                }
            ).bounds(leftPanelWidth - 25, startY, 20, 18).build());
        }
        
        if (scrollOffset + ENTRIES_PER_PAGE < availableMultiblocks.size()) {
            this.addRenderableWidget(Button.builder(
                Component.literal("↓"),
                (btn) -> {
                    scrollOffset = Math.min(availableMultiblocks.size() - ENTRIES_PER_PAGE, scrollOffset + 1);
                    rebuildWidgets();
                }
            ).bounds(leftPanelWidth - 25, startY + (ENTRIES_PER_PAGE - 1) * ENTRY_HEIGHT, 20, 18).build());
        }
        
        // Add control buttons at bottom
        int buttonY = startY + ENTRIES_PER_PAGE * ENTRY_HEIGHT + 20;
        
        // Select button
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.multiblockprojector.select"),
            (btn) -> selectMultiblock(selectedMultiblock)
        ).bounds(10, buttonY, leftPanelWidth / 2 - 15, 20).build());
        
        // Close button
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            (btn) -> this.minecraft.setScreen(null)
        ).bounds(leftPanelWidth / 2 + 5, buttonY, leftPanelWidth / 2 - 15, 20).build());
    }
    
    private void selectMultiblockForPreview(IUniversalMultiblock multiblock) {
        this.selectedMultiblock = multiblock;
        this.previewRenderer.setMultiblock(multiblock);
    }
    
    private void selectMultiblock(IUniversalMultiblock multiblock) {
        if (multiblock == null) return;
        
        settings.setMultiblock(multiblock);
        settings.setMode(Settings.Mode.PROJECTION);
        settings.applyTo(projectorStack);
        
        // Send packet to server
        MessageProjectorSync.sendToServer(settings, hand);
        
        // Close GUI
        this.minecraft.setScreen(null);
        
        // Show confirmation message
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("gui.multiblockprojector.selected", multiblock.getDisplayName()),
                true
            );
        }
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        int leftPanelWidth = this.width / 3;
        
        // Draw left panel background
        guiGraphics.fill(0, 0, leftPanelWidth, this.height, 0x80000000);
        
        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, leftPanelWidth / 2, 20, 0xFFFFFF);
        
        // Draw instructions
        Component instruction = Component.translatable("gui.multiblockprojector.select_multiblock");
        guiGraphics.drawCenteredString(this.font, instruction, leftPanelWidth / 2, 35, 0xAAAAAA);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw vertical separator
        guiGraphics.fill(leftPanelWidth, 0, leftPanelWidth + 2, this.height, 0xFF555555);
        
        // Render preview in right panel
        int previewX = leftPanelWidth + 10;
        int previewY = 50;
        int previewWidth = (int)((this.width - leftPanelWidth - 20) * 0.67);
        int previewHeight = this.height / 2;
        
        // Center the preview in the remaining space
        previewX = leftPanelWidth + (this.width - leftPanelWidth - previewWidth) / 2;
        previewY = (this.height - previewHeight) / 2;
        
        // Draw preview background
        guiGraphics.fill(previewX - 2, previewY - 2, previewX + previewWidth + 2, previewY + previewHeight + 2, 0xFF333333);
        guiGraphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF111111);
        
        // Render the multiblock preview
        previewRenderer.render(guiGraphics, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY, partialTick);
        
        // Draw multiblock info on hover in left panel
        if (mouseX >= 10 && mouseX <= leftPanelWidth - 30) {
            int startY = 50;
            int hoveredIndex = (mouseY - startY) / ENTRY_HEIGHT;
            
            if (hoveredIndex >= 0 && hoveredIndex < ENTRIES_PER_PAGE) {
                int multiblockIndex = scrollOffset + hoveredIndex;
                if (multiblockIndex < availableMultiblocks.size()) {
                    IUniversalMultiblock multiblock = availableMultiblocks.get(multiblockIndex);
                    guiGraphics.renderTooltip(this.font, 
                        Component.translatable("gui.multiblockprojector.tooltip", multiblock.getModId()),
                        mouseX, mouseY);
                }
            }
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC key
            // ESC: Return to nothing selected mode
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            settings.setPlaced(false);
            settings.applyTo(projectorStack);
            
            // Send packet to server
            MessageProjectorSync.sendToServer(settings, hand);
            
            // Close GUI
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int leftPanelWidth = this.width / 3;
        
        // Handle scrolling in left panel
        if (mouseX < leftPanelWidth && availableMultiblocks.size() > ENTRIES_PER_PAGE) {
            int oldScrollOffset = scrollOffset;
            if (scrollY > 0) {
                // Scroll up
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                // Scroll down
                scrollOffset = Math.min(availableMultiblocks.size() - ENTRIES_PER_PAGE, scrollOffset + 1);
            }
            
            if (scrollOffset != oldScrollOffset) {
                rebuildWidgets();
                return true;
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int leftPanelWidth = this.width / 3;
        
        // Handle dragging in preview area for rotation
        if (mouseX > leftPanelWidth && isDragging) {
            previewRenderer.onMouseDragged(mouseX, mouseY, deltaX, deltaY);
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftPanelWidth = this.width / 3;
        
        // Start dragging in preview area
        if (mouseX > leftPanelWidth && button == 0) {
            isDragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
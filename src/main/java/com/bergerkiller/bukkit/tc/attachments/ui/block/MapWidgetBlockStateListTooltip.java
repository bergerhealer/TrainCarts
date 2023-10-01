package com.bergerkiller.bukkit.tc.attachments.ui.block;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.BlockState;

import java.awt.*;
import java.util.Map;

/**
 * Shows a series of tooltips of all the unique states and their values of
 * the currently selected BlockState.
 */
public class MapWidgetBlockStateListTooltip extends MapWidget implements BlockDataSelector {
    private static final int ROW_HEIGHT = 8;
    private static final int NAME_STATE_GAP = 2;
    private BlockData block = null;

    public MapWidgetBlockStateListTooltip() {
        this.setFocusable(false);
        this.setDepthOffset(2);
    }

    /**
     * Note: unused!
     */
    @Override
    public void onSelectedBlockDataChanged(BlockData blockData) {
    }

    @Override
    public BlockData getSelectedBlockData() {
        return block;
    }

    @Override
    public MapWidgetBlockStateListTooltip setSelectedBlockData(BlockData blockData) {
        if (this.block == blockData) {
            return this;
        }

        this.block = blockData;
        this.updateBounds();
        this.invalidate();
        return this;
    }

    @Override
    public void onAttached() {
        updateBounds();
    }

    @Override
    public void onDraw() {
        if (this.block == null) {
            return;
        }
        int y = 0;

        // Block name
        drawText(y, this.block.getBlockName());
        y += ROW_HEIGHT + NAME_STATE_GAP;
        for (Map.Entry<? extends BlockState<?>, Comparable<?>> entry : this.block.getStates().entrySet()) {
            String text = entry.getKey().name() + " = " + entry.getKey().valueName(entry.getValue());
            drawText(y, text);
            y += ROW_HEIGHT;
        }
    }

    private void drawText(int y, String text) {
        Dimension size = view.calcFontSize(MapFont.MINECRAFT, text);
        int x = (this.getWidth() - size.width) / 2;
        view.fillRectangle(x, y, size.width + 1, size.height, MapColorPalette.COLOR_BLACK);
        view.draw(MapFont.MINECRAFT, x + 1, y, MapColorPalette.COLOR_WHITE, text);
        y += ROW_HEIGHT;
    }

    private void updateBounds() {
        if (getParent() == null) {
            return;
        }

        // Center horizontally wrt parent
        int x = (getParent().getWidth() - this.getWidth()) / 2;
        int y = getY();

        if (this.block != null) {
            // Position this tooltip centered in the middle, preserving width and pos y
            this.setBounds(x, y, this.getWidth(),
                    NAME_STATE_GAP + (1 + this.block.getStates().size()) * ROW_HEIGHT);
        } else {
            this.setBounds(x, y, this.getWidth(), 0);
        }
    }
}

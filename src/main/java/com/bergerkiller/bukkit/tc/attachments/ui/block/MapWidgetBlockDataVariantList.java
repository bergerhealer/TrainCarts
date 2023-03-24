package com.bergerkiller.bukkit.tc.attachments.ui.block;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.BlockState;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget that once given a BlockData, populates a List of all other block states
 * of that same block material. This is like the item variant list, but for blocks.
 * The menu can be scrolled left/right using a left/right navigation button.
 */
public abstract class MapWidgetBlockDataVariantList extends MapWidget implements SetValueTarget, BlockDataSelector {
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);
    private final MapTexture background;
    private List<BlockData> variants;
    private final BlockDataTextureCache iconCache = BlockDataTextureCache.get(16, 16);
    private int variantIndex = 0;

    public MapWidgetBlockDataVariantList() {
        this.background = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_selector_bg.png");
        this.setSize(100, 18);
        this.setFocusable(true);
        this.variants = new ArrayList<BlockData>(0);

        this.nav_left.setPosition(0, 4);
        this.nav_right.setPosition(this.getWidth() - nav_right.getWidth(), 4);
        this.nav_left.setVisible(false);
        this.nav_right.setVisible(false);
        this.addWidget(this.nav_left);
        this.addWidget(this.nav_right);
        this.setRetainChildWidgets(true);
    }

    @Override
    public BlockData getSelectedBlockData() {
        if (this.variantIndex >= 0 && this.variantIndex < this.variants.size()) {
            return this.variants.get(this.variantIndex);
        } else {
            return null;
        }
    }

    @Override
    public MapWidgetBlockDataVariantList setSelectedBlockData(BlockData blockData) {
        setSelectedBlockData(blockData, false);
        return this;
    }

    private void setSelectedBlockData(BlockData blockData, boolean fireEvent) {
        if (getSelectedBlockData() == blockData) {
            return;
        }

        // Clear
        if (blockData == null) {
            this.variants = new ArrayList<BlockData>(0);
            this.variantIndex = 0;
            this.invalidate();
            if (fireEvent) {
                this.onSelectedBlockDataChanged(null);
            }
            return;
        }

        // Find all block data variants
        this.variants.clear();
        this.variants.add(blockData);
        for (BlockState<?> state : blockData.getStates().keySet()) {
            List<BlockData> tmp = new ArrayList<BlockData>(this.variants);
            this.variants.clear();
            for (Comparable<?> value : state.values()) {
                for (BlockData original : tmp) {
                    try {
                        this.variants.add(original.setState(state, value));
                    } catch (Throwable t) {} // meh!
                }
            }
        }

        // Find the item in the variants to deduce the currently selected index
        this.variantIndex = 0;
        for (int i = 0; i < this.variants.size(); i++) {
            BlockData variant = this.variants.get(i);
            if (variant.equals(blockData)) {
                this.variantIndex = i;
                break; // Final!
            }
        }

        this.invalidate();
        if (fireEvent) {
            this.onSelectedBlockDataChanged(blockData);
        }
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Block Information";
    }

    @Override
    public boolean acceptTextValue(String value) {
        // Try parsing the item name from the value
        value = value.trim();
        int nameEnd = 0;
        while (nameEnd < value.length()) {
            if (value.charAt(nameEnd) == '{' || value.charAt(nameEnd) == ' ') {
                break;
            } else {
                nameEnd++;
            }
        }
        String itemName = value.substring(0, nameEnd);
        if (nameEnd >= value.length()) {
            value = "";
        } else {
            value = value.substring(nameEnd).trim();
        }
        if (!ParseUtil.isNumeric(itemName)) {
            // Item name
            Material newItemMaterial = ParseUtil.parseMaterial(itemName, null);
            if (newItemMaterial == null) {
                return false;
            }
            BlockData newBlock = BlockData.fromMaterial(newItemMaterial);

            // Update
            this.setSelectedBlockData(newBlock, true);
        } else {
            // Variant index (no item name specified)
            try {
                this.setVariantIndex(Integer.parseInt(itemName));
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void onFocus() {
        nav_left.setVisible(true);
        nav_right.setVisible(true);
    }

    @Override
    public void onBlur() {
        nav_left.setVisible(false);
        nav_right.setVisible(false);
    }

    @Override
    public void onDraw() {
        // Subregion where things are drawn
        // To the left and right are navigation buttons
        int selector_edge = this.nav_left.getWidth()+1;
        MapCanvas itemView = this.view.getView(selector_edge, 0, this.getWidth() - 2*selector_edge, this.getHeight());

        // Background
        itemView.draw(this.background, 0, 0);

        // Draw the same item with -2 to +2 variant indices
        int x = 1;
        int y = 1;
        for (int index = this.variantIndex - 2; index <= this.variantIndex + 2; index++) {
            // Check index valid
            if (index >= 0 && index < this.variants.size()) {
                itemView.draw(this.iconCache.get(this.variants.get(index)), x, y);
            }
            x += 17;
        }

        // If focused, show something to indicate that
        if (this.isFocused()) {
            int fx = 1 + 2 * 17;
            int fy = 1;
            itemView.drawRectangle(fx, fy, 16, 16, MapColorPalette.COLOR_RED);
        }
    }

    private void changeVariantIndex(int offset) {
        this.setVariantIndex(this.variantIndex + offset);
    }

    private void setVariantIndex(int newVariantIndex) {
        if (newVariantIndex < 0) {
            newVariantIndex = 0;
        } else if (newVariantIndex >= this.variants.size()) {
            newVariantIndex = this.variants.size()-1;
        }
        if (this.variantIndex == newVariantIndex) {
            return;
        }
        this.variantIndex = newVariantIndex;
        this.invalidate();
        this.onSelectedBlockDataChanged(this.getSelectedBlockData());
        this.display.playSound(SoundEffect.CLICK);
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        super.onKeyReleased(event);
        if (event.getKey() == Key.LEFT) {
            nav_left.stopFocus();
        } else if (event.getKey() == Key.RIGHT) {
            nav_right.stopFocus();
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.LEFT) {
            changeVariantIndex(-1 - (event.getRepeat() / 40));
            nav_left.sendFocus();
        } else if (event.getKey() == Key.RIGHT) {
            changeVariantIndex(1 + (event.getRepeat() / 40));
            nav_right.sendFocus();
        } else {
            super.onKeyPressed(event);
        }
    }
}

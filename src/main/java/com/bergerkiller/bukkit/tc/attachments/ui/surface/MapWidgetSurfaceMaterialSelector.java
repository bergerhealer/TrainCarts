package com.bergerkiller.bukkit.tc.attachments.ui.surface;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.block.BlockDataSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.block.BlockDataTextureCache;
import com.bergerkiller.bukkit.tc.attachments.ui.block.MapWidgetBlockDataSelector;

/**
 * Shows a little button that when clicked, opens a menu to configure what block material
 * the surface material should be.
 */
public abstract class MapWidgetSurfaceMaterialSelector extends MapWidgetBlinkyButton implements BlockDataSelector {
    private static final int WIDTH = 13;
    private static final int HEIGHT = 13;
    private BlockData surfaceBlock = BlockData.fromMaterial(MaterialUtil.getFirst("STONE", "LEGACY_STONE"));
    private MapTexture blockIcon = MapTexture.createEmpty(11, 11);
    private MapWidgetBlockDataSelector activeSelector = null;
    /** Remembers whether the user last focused the block selection grid (true) or the variant list (false) */
    private boolean wasBlockGridFocused = true;

    public MapWidgetSurfaceMaterialSelector() {
        updateBlockIcon();
        setSize(WIDTH, HEIGHT);
        setIcon(MapTexture.loadResource(MapWidgetSurfacePlatformModeSelector.class,
                        "/com/bergerkiller/bukkit/tc/textures/surface/surface_menu_icons.png")
                .getView(2 * WIDTH, 0, WIDTH, HEIGHT)
                .clone());
    }

    @Override
    public BlockData getSelectedBlockData() {
        return surfaceBlock;
    }

    public MapWidgetSurfaceMaterialSelector setSelectedBlockData(BlockData blockData) {
        if (this.surfaceBlock != blockData) {
            this.surfaceBlock = blockData;
            this.updateBlockIcon();
            if (activeSelector != null) {
                activeSelector.setSelectedBlockData(blockData);
            }
            if (display != null) {
                ResourceKey<SoundEffect> sound = blockData.getStepSound();
                if (sound != null) {
                    display.playSound(sound, 0.7f, 1.0f);
                }
            }
            this.onSelectedBlockDataChanged(blockData);
        }
        return this;
    }

    @Override
    public void onClick() {
        root.getActivatedWidget().addWidget(new BlockSelectorMenu().setBounds(8, 10, 128 - 2 * 8, 109));
    }

    @Override
    public void onDraw() {
        super.onDraw();
        if (isEnabled()) {
            view.draw(blockIcon, 1, 1);
        } else {
            drawGreyscale(view, blockIcon, 1, 1);
        }
    }

    private void updateBlockIcon() {
        blockIcon = BlockDataTextureCache.get(WIDTH - 2, HEIGHT - 2).get(surfaceBlock);
        this.setTooltip("Surface Material:\n" + surfaceBlock.getBlockName());
        this.invalidate();
    }

    private class BlockSelectorMenu extends MapWidgetMenu {
        private final MapTexture stepSoundIcon = MapTexture.loadResource(MapWidgetSurfaceMaterialSelector.class,
                "/com/bergerkiller/bukkit/tc/textures/surface/step_sound.png");

        public BlockSelectorMenu() {
            setPositionAbsolute(true);
            setDepthOffset(8);
            getTitle().setText("Surface Material");
            setBackgroundColor(MapColorPalette.getColor(120, 120, 190));
        }

        @Override
        public void onAttached() {
            addWidget(new MapWidgetBlockDataSelector() {
                @Override
                public void onAttached() {
                    setPosition((BlockSelectorMenu.this.getWidth() - this.getWidth()) / 2, 16);
                    activeSelector = this;
                    super.onAttached();
                    if (wasBlockGridFocused) {
                        activateBlockGrid();
                    }
                }

                @Override
                public void onDetached() {
                    if (activeSelector == this) {
                        activeSelector = null;
                    }
                    super.onDetached();
                }

                @Override
                public void onSelectedBlockDataChanged(BlockData blockData) {
                    MapWidgetSurfaceMaterialSelector.this.setSelectedBlockData(blockData);
                }

                @Override
                public void onSelectionConfirmed() {
                    BlockSelectorMenu.this.close();
                }
            }.setSelectedBlockData(surfaceBlock));

            super.onAttached();
        }

        @Override
        public void close() {
            if (activeSelector != null) {
                wasBlockGridFocused = activeSelector.isBlockGridActivated();
            }
            super.close();
        }

        @Override
        public void onDraw() {
            super.onDraw();
            view.draw(stepSoundIcon, 92, 4);
        }
    }
}

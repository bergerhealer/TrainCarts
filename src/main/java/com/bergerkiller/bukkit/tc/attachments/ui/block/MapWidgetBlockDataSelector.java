package com.bergerkiller.bukkit.tc.attachments.ui.block;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetVerticalNavigableList;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This widget allows a player to select BlockData in a menu showing all
 * block types, and for every selected block types what block states exist
 * for them. Provides a callback called when the user changes the selected
 * BlockData using any number of methods.<br>
 * <br>
 * Optionally also enabled setting the brightness of the block display.
 * Disabled by default.
 */
public abstract class MapWidgetBlockDataSelector extends MapWidget implements BlockDataSelector {
    private final MapWidgetVerticalNavigableList blockOptions;
    private final MapWidgetBlockStateListTooltip blockStateListTooltip;
    private final MapWidgetBlockDataVariantList variantList;
    private final MapWidgetBlockGrid blockSelector;

    public MapWidgetBlockDataSelector() {
        this.setBounds(0, 0, 100, 103);
        this.setRetainChildWidgets(true);

        this.blockOptions = this.addWidget(new MapWidgetVerticalNavigableList() {
            @Override
            public void onLastItemDown(MapKeyEvent event) {
                activateBlockGrid();
            }

            @Override
            public void onNavigated(MapKeyEvent event, Tab tab) {
                if (tab.getIndex() == 0) {
                    blockStateListTooltip.setVisible(true);
                } else {
                    blockStateListTooltip.setVisible(false);
                }
            }
        });
        this.blockOptions.setBounds(0, 0, 100, 18);

        this.blockStateListTooltip = this.addWidget(new MapWidgetBlockStateListTooltip());
        this.blockStateListTooltip.setBounds(0, 18, 128, 0);

        this.variantList = new MapWidgetBlockDataVariantList() {
            @Override
            public void onSelectedBlockDataChanged(BlockData blockData) {
                blockStateListTooltip.setSelectedBlockData(blockData);
                variantList.setSelectedBlockData(blockData);
                MapWidgetBlockDataSelector.this.onSelectedBlockDataChanged(blockData);
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == MapPlayerInput.Key.ENTER) {
                    activateBlockGrid();
                } else {
                    super.onKeyPressed(event);
                }
            }

            @Override
            public boolean onItemDrop(Player player, ItemStack item) {
                return MapWidgetBlockDataSelector.this.onItemDrop(player, item);
            }
        };
        this.variantList.setPosition(0, 0);
        this.blockOptions.addTab().addWidget(this.variantList);
        this.blockOptions.setSelectedIndex(0);

        // Tab with additional block display options
        {
            MapWidgetTabView.Tab tab = blockOptions.addTab(new MapWidgetTabView.Tab() {
                private final MapTexture bg_texture = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_options_bg.png");

                @Override
                public void onDraw() {
                    this.view.draw(bg_texture, 0, 0);
                }
            });
        }

        this.blockSelector = this.addWidget(new MapWidgetBlockGrid() {
            @Override
            public void onSelectedBlockDataChanged(BlockData blockData) {
                variantList.setSelectedBlockData(blockData);
                blockStateListTooltip.setSelectedBlockData(blockData);
                MapWidgetBlockDataSelector.this.onSelectedBlockDataChanged(blockData);
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == MapPlayerInput.Key.BACK || event.getKey() == MapPlayerInput.Key.ENTER) {
                    blockStateListTooltip.setVisible(true);
                    variantList.focus();
                } else {
                    super.onKeyPressed(event);
                }
            }

            @Override
            public boolean onItemDrop(Player player, ItemStack item) {
                return MapWidgetBlockDataSelector.this.onItemDrop(player, item);
            }

            @Override
            public void onBlockInteract(PlayerInteractEvent event) {
                MapWidgetBlockDataSelector.this.onBlockInteract(event);
            }
        }).setDimensions(6, 4);
        this.blockSelector.addAllBlocks();
        this.blockSelector.setPosition(0, 20);
    }

    /**
     * Makes the brightness adjustment button visible
     */
    public void showBrightnessButton() {
        MapWidgetBlinkyButton brightnessButton; brightnessButton = new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onBrightnessClicked();
            }
        };
        brightnessButton.setSize(14, 14);
        brightnessButton.setIcon("attachments/item_brightness.png");
        brightnessButton.setTooltip("Block Brightness");
        brightnessButton.setPosition(42, 2);
        blockOptions.getTab(1).addWidget(brightnessButton);
    }

    private void activateBlockGrid() {
        blockStateListTooltip.setVisible(false);
        blockOptions.setSelectedIndex(0);
        blockSelector.activate();
    }

    @Override
    public BlockData getSelectedBlockData() {
        return this.variantList.getSelectedBlockData();
    }

    @Override
    public MapWidgetBlockDataSelector setSelectedBlockData(BlockData blockData) {
        this.blockSelector.setSelectedBlockData(blockData);
        this.blockStateListTooltip.setSelectedBlockData(blockData);
        this.variantList.setSelectedBlockData(blockData);
        return this;
    }

    @Override
    public boolean onItemDrop(Player player, ItemStack item) {
        BlockData data = BlockData.fromItemStack(item);
        if (data != null && data != BlockData.AIR) {
            this.setSelectedBlockData(data);
            display.playSound(SoundEffect.CLICK_WOOD);
            this.onSelectedBlockDataChanged(data);
            return true;
        }
        return false;
    }

    @Override
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null) {
                BlockData data = WorldUtil.getBlockData(block);
                this.setSelectedBlockData(data);
                display.playSound(SoundEffect.CLICK_WOOD);
                this.onSelectedBlockDataChanged(data);
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    /**
     * Optional: is called when the brightness adjustment button is clicked.
     * Should open some dialog where the display entity brightness levels can
     * be adjusted.
     */
    public void onBrightnessClicked() {
    }
}

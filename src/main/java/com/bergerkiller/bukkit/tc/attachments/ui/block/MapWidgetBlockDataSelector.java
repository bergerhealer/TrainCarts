package com.bergerkiller.bukkit.tc.attachments.ui.block;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
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
 * BlockData using any number of methods.
 */
public abstract class MapWidgetBlockDataSelector extends MapWidget implements BlockDataSelector {
    private final MapWidgetBlockStateListTooltip blockStateListTooltip;
    private final MapWidgetBlockDataVariantList variantList;
    private final MapWidgetBlockGrid blockSelector;

    public MapWidgetBlockDataSelector() {
        this.setBounds(0, 0, 100, 103);
        this.setRetainChildWidgets(true);

        this.blockStateListTooltip = this.addWidget(new MapWidgetBlockStateListTooltip());
        this.blockStateListTooltip.setBounds(0, 18, 128, 0);

        this.variantList = this.addWidget(new MapWidgetBlockDataVariantList() {
            @Override
            public void onSelectedBlockDataChanged(BlockData blockData) {
                blockStateListTooltip.setSelectedBlockData(blockData);
                variantList.setSelectedBlockData(blockData);
                MapWidgetBlockDataSelector.this.onSelectedBlockDataChanged(blockData);
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == MapPlayerInput.Key.DOWN ||
                    event.getKey() == MapPlayerInput.Key.ENTER)
                {
                    blockStateListTooltip.setVisible(false);
                    blockSelector.activate();
                } else {
                    super.onKeyPressed(event);
                }
            }

            @Override
            public boolean onItemDrop(Player player, ItemStack item) {
                return MapWidgetBlockDataSelector.this.onItemDrop(player, item);
            }
        });
        this.variantList.setPosition(0, 0);

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
}

package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.internal.CommonBootstrap;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapDisplayDebugWindow;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.block.MapWidgetBlockDataSelector;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests TrainCarts widgets in an interactive debug window.
 */
public class MapDisplayTest {
    /**
     * Tests correct navigation logic of the block data selector. Also includes a simple button above it
     * to test that the user can smoothly navigate up/down between the selector and other widgets above it.
     */
    @Test
    @Ignore
    public void testBlockDataSelector() {
        CommonBootstrap.initServer();
        MapResourcePack.VANILLA.load();

        final MapDisplay display = new MapDisplay() {
            @Override
            public void onAttached() {
                getLayer().fill(MapColorPalette.getColor(120, 120, 120));

                addWidget(new MapWidgetMenu() {
                    @Override
                    public void close() {
                        System.out.println("Close requested");
                    }

                    @Override
                    public void onAttached() {
                        super.onAttached();

                        addWidget(new MapWidgetButton() {
                            @Override
                            public void onActivate() {
                                System.out.println("Dummy button clicked");
                            }
                        }.setText("Dummy").setBounds(30, 5, 60, 13));

                        MapWidgetBlockDataSelector selector = new MapWidgetBlockDataSelector() {
                            @Override
                            public void onSelectedBlockDataChanged(BlockData blockData) {
                                System.out.println("Selected block data changed to: " + blockData);
                            }

                            @Override
                            public void onBrightnessClicked() {
                                System.out.println("Brightness adjustment requested");
                            }
                        };
                        selector.showBrightnessButton();
                        selector.setPosition(10, 20);
                        addWidget(selector);
                    }
                }.setBounds(5, 5, 118, 110));
            }
        };

        MapDisplayDebugWindow.showForeverAutoScale(display);
    }
}

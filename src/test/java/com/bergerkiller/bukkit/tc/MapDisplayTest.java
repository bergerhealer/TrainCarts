package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.internal.CommonBootstrap;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapDisplayDebugWindow;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.util.MapDebugWindow;
import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentPlatform;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.block.MapWidgetBlockDataSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceAnimation;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceMaterialSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfacePlatformModeSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceShulkerColorSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceViewLockSelector;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests TrainCarts widgets in an interactive debug window.
 */
public class MapDisplayTest {

    @Test
    @Ignore
    public void testWidgetSurfaceAnimation() {
        CommonBootstrap.initServer();
        MapResourcePack.VANILLA.load();

        final MapDisplay display = new MapDisplay() {
            @Override
            public void onAttached() {
                getLayer().fill(MapColorPalette.getColor(120, 120, 120));

                final MapWidgetSurfaceAnimation surfaceAnimation = new MapWidgetSurfaceAnimation();
                surfaceAnimation.setBounds(0, 28, 128, 100);
                surfaceAnimation.setSurfaceBlockData(BlockData.fromMaterial(MaterialUtil.getFirst("ICE", "LEGACY_ICE")));
                addWidget(surfaceAnimation);

                addWidget(new MapWidgetSurfacePlatformModeSelector() {
                    @Override
                    public void onSelectedItemChanged() {
                        surfaceAnimation.setPlatformMode(getSelectedMode());
                    }
                }.setSelectedMode(CartAttachmentPlatform.PlatformMode.SIMULATED).setPosition(24, 5));

                addWidget(new MapWidgetSurfaceShulkerColorSelector() {
                    @Override
                    public void onSelectedItemChanged() {
                        surfaceAnimation.setShulkerColor(getSelectedColor());
                    }
                }.setPosition(45, 5));

                addWidget(new MapWidgetSurfaceViewLockSelector() {
                    @Override
                    public void onViewLockedChanged() {
                        surfaceAnimation.setViewLock(isViewLocked());
                    }
                }.setPosition(67, 5));

                addWidget(new MapWidgetSurfaceMaterialSelector() {
                    @Override
                    public void onSelectedBlockDataChanged(BlockData blockData) {
                        surfaceAnimation.setSurfaceBlockData(blockData);
                    }
                }.setSelectedBlockData(surfaceAnimation.getSurfaceBlockData()).setPosition(89, 5));
            }
        };

        MapDisplayDebugWindow.showForeverAutoScale(display);
    }

    @Test
    @Ignore
    public void testPlayerRender() {
        Model model = MapWidgetSurfaceAnimation.PlayerModel.STEVE;

        Matrix4x4 transform = new Matrix4x4();
        transform.translate(50.0, 0.0, 50.0);
        transform.rotateYawPitchRoll(-20.0, 45.0, 0.0);
        transform.scale(1.0);

        MapTexture texture = MapTexture.createEmpty(128, 128);
        texture.setLightOptions(0.5f, 0.5f, new Vector3(-1.0, 0.0, 0.0));
        texture.drawModel(MapResourcePack.VANILLA.getBlockModel(MaterialUtil.getFirst("STONE")), transform);
        texture.drawModel(model, transform);

        MapDebugWindow.showMapForeverAutoScale(texture);
    }

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

package com.bergerkiller.bukkit.tc.attachments.ui.surface;

import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentPlatform;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetIconDropDown;

public abstract class MapWidgetSurfaceShulkerColorSelector extends MapWidgetIconDropDown {
    private static final int WIDTH = 13;
    private static final int HEIGHT = 13;
    private static final int COLOR_EDGE = 3;
    private final MapTexture baseIcon = MapTexture.loadResource(MapWidgetSurfacePlatformModeSelector.class,
                    "/com/bergerkiller/bukkit/tc/textures/surface/surface_menu_icons.png")
            .getView(2 * WIDTH, 0, WIDTH, HEIGHT)
            .clone();

    public MapWidgetSurfaceShulkerColorSelector() {
        setMaxVisibleItems(5);
        setDropDownWidth(80);
        setSize(WIDTH, HEIGHT);
        this.setDepthOffset(2); // Ensure above preview screen

        // Add the items here, not onAttached(), so that setSelectedColor works correctly
        for (CartAttachmentPlatform.Color color : CartAttachmentPlatform.Color.values()) {
            addItem(color.name());
        }

        setSelectedColor(CartAttachmentPlatform.Color.DEFAULT);
    }

    public CartAttachmentPlatform.Color getSelectedColor() {
        int index = getSelectedIndex();
        return index == -1 ? null : CartAttachmentPlatform.Color.values()[index];
    }

    public MapWidgetSurfaceShulkerColorSelector setSelectedColor(CartAttachmentPlatform.Color color) {
        setSelectedIndex(color.ordinal());
        return this;
    }

    @Override
    protected MapTexture createItemIcon(int itemIndex, String item) {
        CartAttachmentPlatform.Color color = CartAttachmentPlatform.Color.values()[itemIndex];
        MapTexture icon = baseIcon.clone();
        fillIconColor(icon, color.mapColor());
        return icon;
    }

    @Override
    protected String createItemTooltip(int itemIndex, String item) {
        return "Shulker Box Color:\n" + item;
    }

    @Override
    public void onDraw() {
        super.onDraw();

        // Draw the actual color on top, unaffected by the blinky animation state
        // Except for disabled, as disabled doesn't show clearly
        fillIconColor(view, isEnabled() ? getSelectedColor().mapColor() : MapColorPalette.getColor(64, 64, 64));
    }

    private static void fillIconColor(MapCanvas view, byte infillColor) {
        int edgeX = COLOR_EDGE;
        int edgeY = COLOR_EDGE;
        int edgeW = WIDTH - 2 * COLOR_EDGE;
        int edgeH = HEIGHT - 2 * COLOR_EDGE;
        view.fillRectangle(edgeX, edgeY, edgeW, edgeH, infillColor);
    }
}

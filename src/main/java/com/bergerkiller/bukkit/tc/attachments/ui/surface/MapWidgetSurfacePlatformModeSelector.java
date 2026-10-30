package com.bergerkiller.bukkit.tc.attachments.ui.surface;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentPlatform;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetIconDropDown;

public abstract class MapWidgetSurfacePlatformModeSelector extends MapWidgetIconDropDown {
    private static final int WIDTH = 13;
    private static final int HEIGHT = 13;
    private final MapTexture platformModeIconAtlas = MapTexture.loadResource(MapWidgetSurfacePlatformModeSelector.class,
            "/com/bergerkiller/bukkit/tc/textures/surface/surface_menu_icons.png")
            .getView(0, 0, WIDTH, HEIGHT * CartAttachmentPlatform.PlatformMode.values().length)
            .clone();

    public MapWidgetSurfacePlatformModeSelector() {
        this.setMaxVisibleItems(CartAttachmentPlatform.PlatformMode.values().length);
        this.setDropDownWidth(90);
        this.setSize(WIDTH, HEIGHT);
        this.setDepthOffset(2); // Ensure above preview screen

        // Add the items here, not onAttached(), so that setSelectedMode works correctly
        for (CartAttachmentPlatform.PlatformMode mode : CartAttachmentPlatform.PlatformMode.values()) {
            addItem(mode.getDisplayName());
        }
    }

    public CartAttachmentPlatform.PlatformMode getSelectedMode() {
        int index = getSelectedIndex();
        return index == -1 ? null : CartAttachmentPlatform.PlatformMode.values()[index];
    }

    public MapWidgetSurfacePlatformModeSelector setSelectedMode(CartAttachmentPlatform.PlatformMode mode) {
        setSelectedIndex(mode.ordinal());
        return this;
    }

    @Override
    protected MapTexture createItemIcon(int itemIndex, String item) {
        return platformModeIconAtlas.getView(0, itemIndex * HEIGHT, WIDTH, HEIGHT).clone();
    }

    @Override
    protected String createItemTooltip(int itemIndex, String item) {
        return "Platform Mode:\n" + item;
    }
}

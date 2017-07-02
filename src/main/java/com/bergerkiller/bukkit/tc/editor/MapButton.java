package com.bergerkiller.bukkit.tc.editor;

import com.bergerkiller.bukkit.common.map.MapDisplay;

public class MapButton {
    private final MapDisplay.Layer layer;
    private final int x, y;

    public MapButton(MapDisplay display, int x, int y, int z) {
        this.layer = display.getLayer(z);
        this.x = x;
        this.y = y;
    }

}

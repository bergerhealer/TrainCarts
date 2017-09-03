package com.bergerkiller.bukkit.tc.editor;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapTexture;

public class MapControl {
    protected MapDisplay display = null;
    private MapTexture _bg = null;
    private boolean _selected = false;
    protected int x = 0;
    protected int y = 0;

    public void bind(MapDisplay display) {
        this.display = display;
        this.onInit();
        this.draw();
    }

    public void setBackground(MapTexture background) {
        this._bg = background;
        this.draw();
    }

    public void setSelected(boolean selected) {
        if (this._selected != selected) {
            this._selected = selected;
            this.draw();
        }
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isSelected() {
        return this._selected;
    }

    public final void draw() {
        if (this.display != null) {
            if (this._bg != null) {
                MapTexture texture = this._bg;
                if (this._selected) {
                    texture = texture.clone();
                    texture.setBlendMode(MapBlendMode.AVERAGE);
                    texture.fill(MapColorPalette.getColor(128, 128, 255));
                }
                this.display.getLayer(1).setBlendMode(MapBlendMode.NONE);
                this.display.getLayer(1).draw(texture, x, y);
            }
            this.onDraw();
        }
    }

    public void onInit() {
    }
    
    public void onKeyPressed(MapKeyEvent event) {
    }

    protected void onDraw() {
    }

    public void onTick() {
    }
}

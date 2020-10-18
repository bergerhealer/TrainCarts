package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;

/**
 * Displays a simplified summary of a single animation node's properties.
 * Can be selected, but does not handle activation.
 */
public class MapWidgetAnimationNode extends MapWidget {
    private AnimationNode _node = null;
    private double _maxPosition = 1.0;
    private boolean _selected = false;
    private boolean _multiSelectRoot = false;

    public MapWidgetAnimationNode() {
        this.setSize(100, 5);
    }

    /**
     * Sets the scale applied to the x/y/z position values when drawn as bars.
     * This should be the maximum relative position coordinate value used
     * in the entire animation.
     * 
     * @param maximum
     * @return this animation node widget
     */
    public MapWidgetAnimationNode setMaximumPosition(double maximum) {
        if (this._maxPosition != maximum) {
            this._maxPosition = maximum;
            this.invalidate();
        }
        return this;
    }

    /**
     * Sets the animation node whose value is displayed here
     * 
     * @param node
     * @return this animation node widget
     */
    public MapWidgetAnimationNode setValue(AnimationNode node) {
        this._node = node;
        this.invalidate();
        return this;
    }

    /**
     * Gets the animation node whose value is displayed here
     * 
     * @return node value
     */
    public AnimationNode getValue() {
        return this._node;
    }

    /**
     * Sets whether this animation node is selected
     * 
     * @param selected
     */
    public void setSelected(boolean selected) {
        if (this._selected != selected) {
            this._selected = selected;
            this.invalidate();
        }
    }

    /**
     * Gets whether this animation node is selected
     * 
     * @return True if selected
     */
    public boolean isSelected() {
        return this._selected;
    }

    /**
     * Sets whether this animation node is a multi-select root.
     * These get special colors when drawing.
     * 
     * @param root
     */
    public void setIsMultiSelectRoot(boolean root) {
        if (this._multiSelectRoot != root) {
            this._multiSelectRoot = root;
            this.invalidate();
        }
    }

    @Override
    public void onDraw() {
        Column[] columns = calculateColumns(this.getWidth());

        // Fill entire widget with lines of color values, based on selected or not
        byte top_color, mid_color, btm_color;
        if (this._multiSelectRoot && this.isSelected()) {
            top_color = MapColorPalette.getColor(219, 145, 92);
            mid_color = MapColorPalette.getColor(188, 124, 79);
            btm_color = MapColorPalette.getColor(154, 101, 64);
        } else if (this.isSelected()) {
            top_color = MapColorPalette.getColor(213, 219, 92);
            mid_color = MapColorPalette.getColor(183, 188, 79);
            btm_color = MapColorPalette.getColor(150, 154, 64);
        } else {
            top_color = MapColorPalette.getColor(51, 127, 216);
            mid_color = MapColorPalette.getColor(44, 109, 186);
            btm_color = MapColorPalette.getColor(36, 82, 159);
        }
        this.view.drawLine(0, 0, getWidth()-1, 0, top_color);
        this.view.fillRectangle(0, 1, getWidth(), getHeight()-2, mid_color);
        this.view.drawLine(0, getHeight()-1, getWidth()-1, getHeight()-1, btm_color);

        // Draw dashed vertical lines at the column boundaries
        for (int y = 1; y < this.getHeight(); y += 2) {
            this.view.drawPixel(columns[1].x-1, y, MapColorPalette.COLOR_BLACK);
            this.view.drawPixel(columns[2].x-1, y, MapColorPalette.COLOR_BLACK);
        }

        // Display node information (if set)
        if (this._node != null) {
            double time = this._node.getDuration();
            Vector pos = this._node.getPosition();
            Vector rot = this._node.getRotationVector();

            // Delta time: we can only show 4 digits, and an optional dot
            // Below the limit we show '0.000'
            // Above the limit we show '9999'
            String timeStr = Util.stringifyAnimationNodeTime(time);
            byte light_green_color = MapColorPalette.getColor(56, 178, 127);
            int drawTimeOffset = 1;
            int numDigits = 0;
            for (int ch_idx = 0; ch_idx < timeStr.length(); ch_idx++) {
                char c = timeStr.charAt(ch_idx);
                if (c != '.' && c != ',') {
                    numDigits++;

                    // Draw single digit
                    MapTexture sprite = MapFont.TINY.getSprite(c);
                    this.view.draw(sprite, drawTimeOffset, 0, light_green_color);
                    drawTimeOffset += sprite.getWidth();

                    // Last digit
                    if (numDigits == 4) {
                        break;
                    }
                } else if (numDigits <= 3) {
                    // Draw a dot, if a digit will follow
                    this.view.drawPixel(drawTimeOffset, 4, light_green_color);
                    drawTimeOffset += 2;
                }
            }

            // When inactive, use gray-scale colors to indicate that
            byte color_x, color_y, color_z;
            if (this._node.isActive()) {
                color_x = MapColorPalette.COLOR_RED;
                color_y = light_green_color;
                color_z = MapColorPalette.COLOR_BLUE;
            } else {
                color_x = MapColorPalette.getColor(199, 199, 199);
                color_y = MapColorPalette.getColor(180, 180, 180);
                color_z = MapColorPalette.getColor(158, 144, 141);
            }

            // Position x/y/z
            this.view.drawLine(columns[1].mid, 1, columns[1].getPos(pos.getX(), this._maxPosition), 1, color_x);
            this.view.drawLine(columns[1].mid, 2, columns[1].getPos(pos.getY(), this._maxPosition), 2, color_y);
            this.view.drawLine(columns[1].mid, 3, columns[1].getPos(pos.getZ(), this._maxPosition), 3, color_z);

            // Rotation x/y/z
            this.view.drawLine(columns[2].mid, 1, columns[2].getRot(rot.getX()), 1, color_x);
            this.view.drawLine(columns[2].mid, 2, columns[2].getRot(rot.getY()), 2, color_y);
            this.view.drawLine(columns[2].mid, 3, columns[2].getRot(rot.getZ()), 3, color_z);
        }
    }

    /**
     * Computes the three column coordinates for a given width
     * 
     * @param width
     * @return column coordinates
     */
    protected static Column[] calculateColumns(int width) {
        // Exclude the pixel boundaries between the columns
        width -= 2;

        // Width of the time column is fixed, but may grow to guarantee
        // the position / rotation columns have an equal and odd number of pixels
        int time_width = 20;
        width -= time_width;
        while (width > 0) {
            // Width must be dividable by 2
            if ((width & 0x1) == 0x1) {
                width--;
                time_width++;
                continue;
            }

            // Width divided by 2 must be an odd number
            if (((width >> 1) & 0x1) != 0x1) {
                width--;
                time_width++;
                continue;
            }

            // Found it.
            break;
        }

        // Compile final column values
        return new Column[] {
            new Column(0, time_width),
            new Column(time_width + 1, width >> 1),
            new Column(time_width + (width>>1) + 2, width >> 1)
        };
    }

    protected static final class Column {
        public final int x;
        public final int width;
        public final int mid;

        public Column(int x, int width) {
            this.x = x;
            this.width = width;
            this.mid = (x + ((width-1)>>1));
        }

        public int getPos(double value, double maximum) {
            int pixels = this.mid;
            if (value != 0.0 && maximum > 0.0) {
                pixels += (int) ((value * (this.width >> 1)) / maximum);
            }
            return pixels;
        }

        public int getRot(double angle) {
            // Wrap angle
            while (angle > 180.0) angle -= 360.0;
            while (angle < -180.0) angle += 360.0;

            // Standard getPos
            return getPos(angle, 180.0);
        }
    }
}

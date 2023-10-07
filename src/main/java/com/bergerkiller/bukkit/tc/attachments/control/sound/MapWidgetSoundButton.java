package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;

/**
 * A button that can be pressed, with a click handler. Shows a pressed-down
 * state, the same as if the widget is activated.
 */
public abstract class MapWidgetSoundButton extends MapWidgetSoundElement {
    protected boolean pressed = false;
    protected int pressedTicks = 0;

    public abstract void onClick();

    public void onClickHold(int pressedTicks) {
    }

    @Override
    public void onKey(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            if (!pressed) {
                pressed = true;
                pressedTicks = 0;
                invalidate();
                onClick();
            } else {
                pressedTicks++;
                onClickHold(pressedTicks);
            }
        } else {
            super.onKey(event);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            // Handled in onKey
        } else {
            super.onKeyPressed(event);
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            pressedTicks = 0;
            if (pressed) {
                pressed = false;
                invalidate();
            }
        } else {
            super.onKeyReleased(event);
        }
    }

    @Override
    public void onBlur() {
        pressedTicks = 0;
        pressed = false;
    }

    @Override
    public void onDraw() {
        if (pressed) {
            drawBackground(MapColorPalette.COLOR_BLACK,
                           MapColorPalette.getColor(36, 89, 152),
                           MapColorPalette.getColor(44, 109, 186));
        } else {
            super.onDraw();
        }
    }
}

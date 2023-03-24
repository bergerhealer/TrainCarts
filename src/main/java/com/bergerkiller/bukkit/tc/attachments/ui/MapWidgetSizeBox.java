package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/**
 * Configures the size of an attachment. By navigating down once
 * on the bottom-most selection box, uniform size can be configured.
 */
public abstract class MapWidgetSizeBox extends MapWidget {
    public final MapWidgetNumberBox x = addWidget(new SizeNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size X-Axis";
        }
    });
    public final MapWidgetNumberBox y = addWidget(new SizeNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size Y-Axis";
        }
    });
    public final MapWidgetNumberBox z = addWidget(new SizeNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size Z-Axis";
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.DOWN) {
                setUniformFocused(true);
            } else {
                super.onKeyPressed(event);
            }
        }
    });

    private Vector uniformFocusStart = new Vector();
    private double uniformScale = 1.0;
    private boolean suppressSizeChanges = false;
    private boolean uniformFocusActive = false;

    public MapWidgetSizeBox() {
        this.setRetainChildWidgets(true);
        x.setRange(0.01, 1000.0);
        y.setRange(0.01, 1000.0);
        z.setRange(0.01, 1000.0);
    }

    public boolean isUniformFocused() {
        return uniformFocusActive;
    }

    /**
     * Sets whether the uniform scaling mode is focused.
     *
     * @param focused Whether scaling mode is focused
     */
    public void setUniformFocused(boolean focused) {
        if (uniformFocusActive == focused) {
            return;
        }

        uniformFocusActive = focused;
        if (focused) {
            setFocusable(true);
            focus();
            forAllAxis(a -> a.setAlwaysFocused(true));
            uniformFocusStart = new Vector(x.getValue(), y.getValue(), z.getValue());
            uniformScale = 1.0;
        } else {
            forAllAxis(a -> {
                a.setAlwaysFocused(false);
                a.updateArrowFocus(false, false);
            });
            setFocusable(false);
        }
    }

    /**
     * Called when one of the size axis are changed
     */
    public abstract void onSizeChanged();

    /**
     * Sets the (initial) size displayed
     *
     * @param sx Size X-Axis
     * @param sy Size Y-Axis
     * @param sz Size Z-Axis
     */
    public void setSize(double sx, double sy, double sz) {
        x.setValue(sx);
        y.setValue(sy);
        z.setValue(sz);
    }

    @Override
    public void onBoundsChanged() {
        int selHeight = (this.getHeight() - 2) / 3;
        x.setBounds(0, 0, getWidth(), selHeight);
        y.setBounds(0, (getHeight() - selHeight) / 2, getWidth(), selHeight);
        z.setBounds(0, getHeight() - selHeight, getWidth(), selHeight);
    }

    private void increaseUniform(double increase, int repeat) {
        uniformScale = Math.max(0.0, MapWidgetNumberBox.scaledIncrease(uniformScale, increase, repeat));
        suppressSizeChanges = true;
        x.setValue(calcUniformScale(uniformFocusStart.getX(), increase));
        y.setValue(calcUniformScale(uniformFocusStart.getY(), increase));
        z.setValue(calcUniformScale(uniformFocusStart.getZ(), increase));
        suppressSizeChanges = false;
        onSizeChanged();
    }

    private double calcUniformScale(double value, double incr) {
        return incr * Math.round(uniformScale * value / incr);
    }

    @Override
    public void onKey(MapKeyEvent event) {
        if (!this.isUniformFocused() || event.getKey() != MapPlayerInput.Key.ENTER) {
            super.onKey(event);
            return;
        }

        // Send onKey to all x/y/z to signal resetting
        forAllAxis(a -> a.onKey(event));
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (!this.isUniformFocused()) {
            super.onKeyReleased(event);
            return;
        }

        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            forAllAxis(a -> a.stopArrowFocus(false));
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            forAllAxis(a -> a.stopArrowFocus(true));
        } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
            forAllAxis(a -> a.onKeyReleased(event));
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!this.isUniformFocused()) {
            super.onKeyPressed(event);
            return;
        }

        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            forAllAxis(a -> a.updateArrowFocus(true, false));
            increaseUniform(-0.01, event.getRepeat());
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            forAllAxis(a -> a.updateArrowFocus(false, true));
            increaseUniform(0.01, event.getRepeat());
        } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
            forAllAxis(a -> a.onKeyPressed(event));
            return;
        }

        // Navigate back up to Z
        // If disabled, it'll just focus the widgets above instead
        /*
        if (event.getKey() == MapPlayerInput.Key.UP) {
            setUniformFocused(false);
            z.focus();
            return;
        }
        */

        // Try to navigate. If focus is lost, make this widget no
        // longer focusable.
        super.onKeyPressed(event);
        if (!isFocused()) {
            setUniformFocused(false);
        } else if (event.getKey() == MapPlayerInput.Key.UP) {
            setUniformFocused(false);
            x.focus();
        }
    }

    private void forAllAxis(Consumer<MapWidgetNumberBox> action) {
        action.accept(x);
        action.accept(y);
        action.accept(z);
    }

    private class SizeNumberBox extends MapWidgetNumberBox {
        @Override
        public void onValueChanged() {
            if (!suppressSizeChanges) {
                onSizeChanged();
            }
        }

        @Override
        public void onResetValue() {
            setValue(1.0);
        }

        @Override
        protected void onDraw(boolean focused) {
            super.onDraw(focused || MapWidgetSizeBox.this.isFocused());
        }
    }
}

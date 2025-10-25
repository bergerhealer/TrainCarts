package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
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

        @Override
        protected void onResetClickSound() {
            if (!isUniformFocused()) {
                super.onResetClickSound();
            }
        }
    });
    public final MapWidgetNumberBox y = addWidget(new SizeNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size Y-Axis";
        }

        @Override
        protected void onResetClickSound() {
            if (!isUniformFocused()) {
                super.onResetClickSound();
            }
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
    private boolean suppressSizeChanges = false;
    private boolean uniformFocusActive = false;
    private double defaultValue = 1.0;
    private boolean enableY = true;
    private int lastUniformResetTick = -1;

    public MapWidgetSizeBox() {
        this.setRetainChildWidgets(true);
        this.setRangeAndDefault(false, 1.0);
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
        } else {
            forAllAxis(a -> {
                a.setAlwaysFocused(false);
                a.updateArrowFocus(false, false);
            });
            setFocusable(false);
        }
    }

    /**
     * Sets whether the Y-size can be configured, or if its kept hidden and unchanged
     * for a 2D plane. When disabled, the user cannot change the size y value,
     * but values set programmatically do persist.
     *
     * @param enableY True to show the Y-axis (default true)
     * @return this
     */
    public MapWidgetSizeBox setYAxisEnabled(boolean enableY) {
        if (this.enableY != enableY) {
            this.enableY = enableY;
            if (enableY) {
                this.addWidget(y);
            } else {
                this.removeWidget(y);
            }
            this.onBoundsChanged();
        }
        return this;
    }

    /**
     * Sets the x/y/z coordinate range bounds and the default value to set when ENTER is kept
     * pressed for a short time.
     * @param canBeNegative Whether the number can go below 0
     * @param defaultValue Default value
     * @return this
     */
    public MapWidgetSizeBox setRangeAndDefault(boolean canBeNegative, double defaultValue) {
        double min = canBeNegative ? -1000.0 : 0.01;
        double max = 1000.0;
        this.x.setRange(min, max);
        this.y.setRange(min, max);
        this.z.setRange(min, max);
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * Called when one of the size axis are changed. Is <b>not</b> called when
     * {@link #setInitialSize(double, double, double)} is used.
     */
    public abstract void onSizeChanged();

    /**
     * Called when all the axis are reset simultaneously
     */
    public void onUniformResetValue() {
        forAllAxis(a -> a.setValue(defaultValue));
    }

    /**
     * Sets the size displayed. Will fire {@link #onSizeChanged()}
     * if changed.
     *
     * @param sx Size X-Axis
     * @param sy Size Y-Axis
     * @param sz Size Z-Axis
     */
    public void setSize(double sx, double sy, double sz) {
        if (sx != x.getValue() || sy != y.getValue() || sz != z.getValue()) {
            setInitialSize(sx, sy, sz);
            onSizeChanged();
        }
    }

    /**
     * Sets the initial size displayed. Does <b>not</b> call
     * {@link #onSizeChanged()}
     *
     * @param sx Size X-Axis
     * @param sy Size Y-Axis
     * @param sz Size Z-Axis
     */
    public void setInitialSize(double sx, double sy, double sz) {
        x.setInitialValue(sx);
        y.setInitialValue(sy);
        z.setInitialValue(sz);
    }

    /**
     * Displays text in the number boxes instead of the current value
     *
     * @param text Text override, null to hide again and show the number
     * @return this
     */
    public MapWidgetSizeBox setTextOverride(String text) {
        forAllAxis(a -> a.setTextOverride(text));
        return this;
    }

    @Override
    public void onBoundsChanged() {
        int selHeight = (this.getHeight() - 2) / (enableY ? 3 : 2);
        x.setBounds(0, 0, getWidth(), selHeight);
        if (enableY) {
            y.setBounds(0, (getHeight() - selHeight) / 2, getWidth(), selHeight);
        }
        z.setBounds(0, getHeight() - selHeight, getWidth(), selHeight);
    }

    private void increaseUniform(double increase, int repeat) {
        // Scale the highest value axis by the increase (faster with repeat)
        // If the highest value axis is 0, assume we want to uniform scale by the increase
        double absX = Math.abs(uniformFocusStart.getX());
        double absY = Math.abs(uniformFocusStart.getY());
        double absZ = Math.abs(uniformFocusStart.getZ());

        suppressSizeChanges = true;
        if (absX == 0.0 && absZ == 0.0 && (!enableY || absY == 0.0)) {
            // All axis values are 0. Uniform scale them with the increase
            double value = MapWidgetNumberBox.scaledIncrease(x.getValue(), increase, repeat);
            x.setValue(value);
            if (enableY) {
                y.setValue(value);
            }
            z.setValue(value);
        } else if (absX > absZ && (!enableY || absX > absY)) {
            // X is highest, increase X and adjust the other axis in same proportions
            scaleAxisByIncreasing(x, uniformFocusStart.getX(), increase, repeat);
        } else if (enableY && absY > absX && absY > absZ) {
            // Y is highest, increase Y and adjust the other axis in same proportions
            scaleAxisByIncreasing(y, uniformFocusStart.getY(), increase, repeat);
        } else {
            // Z is highest, increase Z and adjust the other axis in same proportions
            scaleAxisByIncreasing(z, uniformFocusStart.getZ(), increase, repeat);
        }
        suppressSizeChanges = false;
        onSizeChanged();
    }

    private void scaleAxisByIncreasing(MapWidgetNumberBox axis, double uniformStart, double increase, int repeat) {
        axis.setValue(MapWidgetNumberBox.scaledIncrease(axis.getValue(), increase, repeat));
        double scale = axis.getValue() / uniformStart;
        if (axis != x) x.setValue(roundByIncrease(uniformFocusStart.getX() * scale, increase));
        if (axis != y && enableY) y.setValue(roundByIncrease(uniformFocusStart.getY() * scale, increase));
        if (axis != z) z.setValue(roundByIncrease(uniformFocusStart.getZ() * scale, increase));
    }

    private double roundByIncrease(double value, double incr) {
        return incr * Math.round(value / incr);
    }

    @Override
    public void onKey(MapKeyEvent event) {
        if (!this.isUniformFocused() || event.getKey() != MapPlayerInput.Key.ENTER) {
            super.onKey(event);
            return;
        }

        // Send onKey to all x/y/z to signal resetting
        forAllAxis(a -> a.onKey(event));
        if (x.isHoldEnterResetComplete()) {
            // Also reset the uniform state
            this.uniformFocusStart = new Vector(x.getValue(), y.getValue(), z.getValue());
        }
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
        if (enableY) {
            action.accept(y);
        }
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
            if (isUniformFocused()) {
                if (lastUniformResetTick != CommonUtil.getServerTicks()) {
                    lastUniformResetTick = CommonUtil.getServerTicks();
                    onUniformResetValue();
                }
            } else {
                setValue(defaultValue);
            }
        }

        @Override
        protected void onDraw(boolean focused) {
            super.onDraw(focused || MapWidgetSizeBox.this.isFocused());
        }
    }
}

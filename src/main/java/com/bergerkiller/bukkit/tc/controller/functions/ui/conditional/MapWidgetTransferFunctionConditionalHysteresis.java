package com.bergerkiller.bukkit.tc.controller.functions.ui.conditional;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;
import org.bukkit.block.BlockFace;

import java.text.NumberFormat;

/**
 * Configures the hysteresis of the conditional function. If non-zero, then
 * the left/right hand side must change significantly enough before a state
 * change occurs. This is useful for noisy inputs to avoid rapid toggling.
 */
public abstract class MapWidgetTransferFunctionConditionalHysteresis extends MapWidget implements SetValueTarget {
    private static final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);
    private static final byte COLOR_BG_ACTIVATED = MapColorPalette.getColor(247, 233, 163);
    private static final NumberFormat NUMBER_FORMAT = Util.createNumberFormat(1, 4);
    private static final MapTexture HYSTERESIS_ICON = MapTexture.loadPluginResource(TrainCarts.plugin,
            "com/bergerkiller/bukkit/tc/textures/attachments/hysteresis.png");

    private final MapWidgetArrow leftArrow = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow rightArrow = new MapWidgetArrow(BlockFace.EAST);
    private double hysteresis;

    public MapWidgetTransferFunctionConditionalHysteresis(double hysteresis) {
        this.hysteresis = hysteresis;
        this.setFocusable(true);
    }

    public abstract void onHysteresisChanged(double hysteresis);

    public void setHysteresis(double hysteresis) {
        if (this.hysteresis != hysteresis) {
            this.hysteresis = hysteresis;
            this.invalidate();
            this.onHysteresisChanged(hysteresis);
        }
    }

    private void increment(double incr, int repeat) {
        setHysteresis(MapWidgetNumberBox.scaledIncrease(hysteresis, incr, repeat));
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Hysteresis";
    }

    @Override
    public boolean acceptTextValue(String value) {
        return acceptTextValue(Operation.SET, value);
    }

    @Override
    public boolean acceptTextValue(Operation operation, String value) {
        return operation.perform(() -> hysteresis, this::setHysteresis, value);
    }

    @Override
    public void onActivate() {
        addWidget(leftArrow.setPosition(-leftArrow.getWidth() - 1, (getHeight() - leftArrow.getHeight()) / 2));
        addWidget(rightArrow.setPosition(getWidth() + 1, (getHeight() - rightArrow.getHeight()) / 2));
        super.onActivate();
    }

    @Override
    public void onDeactivate() {
        removeWidget(leftArrow);
        removeWidget(rightArrow);
        super.onDeactivate();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!isActivated()) {
            super.onKeyPressed(event);
            return;
        }

        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            // Previous mode
            increment(-0.001, event.getRepeat());
            leftArrow.sendFocus();
            rightArrow.stopFocus();
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            // Next mode
            increment(0.001, event.getRepeat());
            rightArrow.sendFocus();
            leftArrow.stopFocus();
        } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
            setHysteresis(0.0);
            display.playSound(SoundEffect.EXTINGUISH);
        } else {
            this.focus();
            if (event.getKey() == MapPlayerInput.Key.UP || event.getKey() == MapPlayerInput.Key.DOWN) {
                super.onKeyPressed(event);
            }
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (isActivated()) {
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                leftArrow.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                rightArrow.stopFocus();
            }
        }
        super.onKeyReleased(event);
    }

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                isActivated() ? COLOR_BG_ACTIVATED : (isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT));

        // Hysteresis icon
        byte color = isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK;
        view.draw(HYSTERESIS_ICON, 2, 2, color);
        view.draw(MapFont.MINECRAFT, 14, 3, color, NUMBER_FORMAT.format(hysteresis));
    }
}

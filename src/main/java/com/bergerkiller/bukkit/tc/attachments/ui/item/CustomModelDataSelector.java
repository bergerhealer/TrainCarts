package com.bergerkiller.bukkit.tc.attachments.ui.item;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;

public abstract class CustomModelDataSelector extends MapWidget implements SetValueTarget {
    private int numDigits;
    private int selectedDigit = 0;
    private int value = 0;
    private boolean isKeyUp = false;
    private boolean isKeyDown = false;
    public final MapWidgetTooltip tooltip = new MapWidgetTooltip();

    public CustomModelDataSelector() {
        this.numDigits = 0;
        this.setNumDigits(8);
        this.setFocusable(true);
        this.tooltip.setText("Custom Model Data");
    }

    /**
     * Sets the number of digits displayed
     * 
     * @param num
     * @return this widget
     */
    public CustomModelDataSelector setNumDigits(int num) {
        if (this.numDigits != num) {
            this.numDigits = num;
            this.setSize(num * 4 + 3, 13);
        }
        return this;
    }

    /**
     * Gets the current custom model data value
     * 
     * @return value
     */
    public int getValue() {
        return this.value;
    }

    /**
     * Sets the current custom model data value
     * 
     * @param value
     */
    public void setValue(int value) {
        if (this.value != value) {
            this.value = value;
            this.invalidate();
        }
    }

    @Override
    public void onDraw() {
        // Draw the number itself (or ------- when not selected), padded with 0's at the start
        // Note: is drawn right to left
        int text_x = this.getWidth()-1;
        int text_y = (this.getHeight()-5)/2;
        int tmp = this.value;
        for (int digit = 0; digit < this.numDigits; digit++) {
            text_x -= 4;
            char ch = '-';
            if (this.value > 0) {
                ch = Character.forDigit(tmp%10, 10);
                tmp /= 10;
            }
            byte color = MapColorPalette.COLOR_WHITE;
            if (this.isActivated() && digit == selectedDigit) {
                color = MapColorPalette.COLOR_YELLOW;
            }
            this.view.draw(MapFont.TINY.getSprite(ch), text_x, text_y, color);
        }

        if (this.isActivated()) {
            // Show a small rectangle/arrow above and below the digit to indicate the selector
            int selX = this.getWidth() - 4 * (this.selectedDigit + 1) - 1;
            int upY = 0;
            int downY = this.getHeight() - 2;
            byte upColor = this.isKeyUp ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_YELLOW;
            byte downColor = this.isKeyDown ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_YELLOW;
            this.view.drawPixel(selX, upY+1, upColor);
            this.view.drawPixel(selX+1, upY, upColor);
            this.view.drawPixel(selX+2, upY+1, upColor);
            this.view.drawPixel(selX, downY, downColor);
            this.view.drawPixel(selX+1, downY+1, downColor);
            this.view.drawPixel(selX+2, downY, downColor);
        } else if (this.isFocused()) {
            // Focusing before activating it, show a rectangle around it
            this.view.drawRectangle(0, (this.getHeight()-5)/2-2, this.getWidth(), 9, MapColorPalette.COLOR_RED);
        }
    }

    // 1 -> 2 -> 5 -> 10 -> 20 -> 50 -> 100 etc.
    private static double getExp(int repeat) {
        int a = (repeat / 3);
        int b = (repeat % 3);
        double f = (b==0) ? 1.0 : (b==1) ? 2.0 : 5.0;
        return f * Math.pow(10.0, a);
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (this.isKeyUp && event.getKey() == MapPlayerInput.Key.UP) {
            this.isKeyUp = false;
            this.invalidate();
        } else if (this.isKeyDown && event.getKey() == MapPlayerInput.Key.DOWN) {
            this.isKeyDown = false;
            this.invalidate();
        }
        super.onKeyReleased(event);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            if (this.isActivated()) {
                this.deactivate();
            } else {
                this.selectedDigit = 0;
                this.activate();
            }
            return;
        }
        if (!this.isActivated()) {
            super.onKeyPressed(event);
            return;
        }
        if (event.getKey() == MapPlayerInput.Key.BACK) {
            this.deactivate();
        } else if (event.getKey() == MapPlayerInput.Key.LEFT) {
            this.selectedDigit++;
            if (this.selectedDigit >= this.numDigits) {
                this.selectedDigit = this.numDigits - 1;
            }
            this.invalidate();
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            this.selectedDigit--;
            if (this.selectedDigit < 0) {
                this.selectedDigit = 0;
            }
            this.invalidate();
        } else {
            int incr = 0;
            if (event.getKey() == MapPlayerInput.Key.UP) {
                incr = 1;
                this.isKeyUp = true;
                this.isKeyDown = false;
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                incr = -1;
                this.isKeyUp = false;
                this.isKeyDown = true;
            }
            incr *= getExp(event.getRepeat() / 50);
            incr *= (int) Math.pow(10.0, this.selectedDigit);
            this.value += incr;

            int max_value = ((int) Math.pow(10.0, this.numDigits) - 1);
            if (this.value < 0) {
                this.value = 0;
            } else if (this.value > max_value) {
                this.value = max_value;
            }
            this.onValueChanged();
            this.invalidate();
        }
    }

    @Override
    public void onFocus() {
        super.onFocus();
        this.addWidget(this.tooltip);

        // Click navigation sounds
        display.playSound(SoundEffect.CLICK_WOOD);
    }

    @Override
    public void onBlur() {
        super.onBlur();
        this.removeWidget(this.tooltip);
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Custom Model Name";
    }

    @Override
    public boolean acceptTextValue(String value) {
        try {
            this.setValue(Integer.parseInt(value));
            this.onValueChanged();
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public abstract void onValueChanged();
}

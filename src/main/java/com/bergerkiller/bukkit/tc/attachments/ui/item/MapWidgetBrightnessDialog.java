package com.bergerkiller.bukkit.tc.attachments.ui.item;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

public abstract class MapWidgetBrightnessDialog extends MapWidgetMenu {
    private boolean disabled = true;
    private final MapWidgetNumberBox blockLight = new MapWidgetNumberBox() {
        @Override
        public void onValueChanged() {
            updateDisabled(false);
            onBrightnessChanged();
        }
    };
    private final MapWidgetNumberBox skyLight = new MapWidgetNumberBox() {
        @Override
        public void onValueChanged() {
            updateDisabled(false);
            onBrightnessChanged();
        }
    };
    private final MapWidgetButton disabledButton = new MapWidgetButton() {
        @Override
        public void onActivate() {
            updateDisabled(!disabled);
            onBrightnessChanged();
            blockLight.focus();
        }
    };

    public MapWidgetBrightnessDialog() {
        this.setRetainChildWidgets(true);
        this.setSize(74, 73);

        blockLight.setRange(0, 15);
        blockLight.setIncrement(1.0);
        blockLight.setTextOverride("Default");
        addLabel(16, 5, "Block Light");
        addWidget(blockLight.setBounds(7, 12, 60, 13));

        skyLight.setRange(0, 15);
        skyLight.setIncrement(1.0);
        skyLight.setTextOverride("Default");
        addLabel(19, 28, "Sky Light");
        addWidget(skyLight.setBounds(7, 35, 60, 13));

        disabledButton.setText("Default");
        disabledButton.setEnabled(false);
        disabledButton.setBounds(14, 53, 46, 12);
        addWidget(disabledButton);
    }

    /**
     * Sets the initial brightness shown in the dialog
     *
     * @param blockLight Block Light. -1 for automatic/natural
     * @param skyLight Sky Light. -1 for automatic/natural
     */
    public void setBrightness(int blockLight, int skyLight) {
        if (blockLight == -1 || skyLight == -1) {
            if (!this.disabled) {
                updateDisabled(true);
                this.blockLight.setInitialValue(0);
                this.skyLight.setInitialValue(0);
            }
        } else {
            updateDisabled(false);
            this.blockLight.setInitialValue(blockLight);
            this.skyLight.setInitialValue(skyLight);
        }
    }

    private void updateDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            this.disabled = disabled;
            this.disabledButton.setEnabled(!disabled);
            if (disabled) {
                blockLight.setTextOverride("Default");
                skyLight.setTextOverride("Default");
            } else {
                blockLight.setTextOverride(null);
                skyLight.setTextOverride(null);
            }
        }
    }

    public int getBlockLight() {
        return disabled ? -1 : (int) blockLight.getValue();
    }

    public int getSkyLight() {
        return disabled ? -1 : (int) skyLight.getValue();
    }

    /**
     * Called after the brightness configuration is changed
     */
    public abstract void onBrightnessChanged();
}

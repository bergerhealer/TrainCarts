package com.bergerkiller.bukkit.tc.attachments.ui.item;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
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

    /**
     * Handles the configuration of an attachment, storing the configured brightness
     * in a 'brightness' configuration field.
     */
    public static class AttachmentBrightnessDialog extends MapWidgetBrightnessDialog {

        public AttachmentBrightnessDialog(MapWidgetAttachmentNode attachment) {
            this.setAttachment(attachment);
        }

        @Override
        public void onAttached() {
            if (attachment.getConfig().contains("brightness")) {
                setBrightness(attachment.getConfig().get("brightness.block", 0),
                              attachment.getConfig().get("brightness.sky", 0));
            } else {
                setBrightness(-1, -1);
            }
        }

        @Override
        public void onBrightnessChanged() {
            if (getSkyLight() == -1 || getBlockLight() == -1) {
                attachment.getConfig().remove("brightness");
            } else {
                ConfigurationNode b_node = attachment.getConfig().getNode("brightness");
                b_node.set("block", getBlockLight());
                b_node.set("sky", getSkyLight());
            }
        }
    }
}

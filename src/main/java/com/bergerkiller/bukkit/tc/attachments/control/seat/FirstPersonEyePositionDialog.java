package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;

/**
 * Menu window dialog where the eye position can be configured
 */
public class FirstPersonEyePositionDialog extends MapWidgetMenu {
    private final MapWidgetAttachmentNode attachment;
    private boolean isLoadingWidgets;

    public FirstPersonEyePositionDialog(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(0, -10, 103, 95);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        isLoadingWidgets = true;

        int slider_width = 75;
        int y_offset = 5;
        int y_step = 12;

        this.addWidget(new SeatEyeNumberBox("posX", "Position X-Coordinate", 0.01))
            .setBounds(26, y_offset, slider_width, 11);
        addLabel(4, y_offset + 3, "Pos.X");
        y_offset += y_step;

        this.addWidget(new SeatEyeNumberBox("posY", "Position Y-Coordinate", 0.01))
            .setBounds(26, y_offset, slider_width, 11);
        addLabel(4, y_offset + 3, "Pos.Y");
        y_offset += y_step;

        this.addWidget(new SeatEyeNumberBox("posZ", "Position Z-Coordinate", 0.01))
            .setBounds(26, y_offset, slider_width, 11);
        addLabel(4, y_offset + 3, "Pos.Z");
        y_offset += y_step;

        this.addWidget(new SeatEyeNumberBox("rotX", "Rotation Pitch", 0.1))
            .setBounds(26, y_offset, slider_width, 11);
        addLabel(4, y_offset + 3, "Pitch");
        y_offset += y_step;

        this.addWidget(new SeatEyeNumberBox("rotY", "Rotation Yaw", 0.1))
            .setBounds(26, y_offset, slider_width, 11);
        addLabel(4, y_offset + 3, "Yaw");
        y_offset += y_step;

        this.addWidget(new SeatEyeNumberBox("rotZ", "Rotation Roll", 0.1))
            .setBounds(26, y_offset, slider_width, 11);
        addLabel(4, y_offset + 3, "Roll");
        y_offset += y_step;

        this.addWidget(new MapWidgetButton() {
            private final MapWidgetTooltip tooltip = new MapWidgetTooltip();

            @Override
            public void onAttached() {
                super.onAttached();
                tooltip.setText("Sets eye position based\non seat display mode");
            }

            @Override
            public void onFocus() {
                addWidget(tooltip);
            }

            @Override
            public void onBlur() {
                removeWidget(tooltip);
            }

            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);

                // Remove configuration node, if any
                {
                    ConfigurationNode config = attachment.getConfig();
                    if (config.isNode("firstPersonViewPosition")) {
                        config.remove("firstPersonViewPosition");
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }

                // Set all number box widgets to auto
                setAutomaticDisplayed(true);

                // Show preview arrow
                showArrowPreview(true);
            }
        }).setText("Automatic")
          .setBounds(33, y_offset, 61, 13);

        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                previewEye(20); // Preview for a second
            }

            @Override
            public void onRepeatClick() {
                previewEye(2); // Preview for a very short time
            }

            @Override
            public void onClickHoldRelease() {
                previewEye(0); // Stop
            }
        }).setRepeatClickEnabled(true)
          .setTooltip("Preview")
          .setIcon("attachments/view_camera_preview.png")
          .setPosition(17, y_offset);

        isLoadingWidgets = false;
    }

    @Override
    public void onDetached() {
        super.onDetached();

        // Stop previewing the eye
        previewEye(0);
        showArrowPreview(false);
    }

    private void previewEye(int numTicks) {
        Attachment liveAttachment = this.attachment.getAttachment();
        if (liveAttachment instanceof CartAttachmentSeat) {
            for (Player player : display.getOwners()) {
                if (display.isControlling(player)) {
                    ((CartAttachmentSeat) liveAttachment).previewEye(player, numTicks);
                }
            }
        }
    }

    public boolean isAutomatic() {
        return !attachment.getConfig().isNode("firstPersonViewPosition");
    }

    private void setAutomaticDisplayed(boolean automatic) {
        isLoadingWidgets = true;
        for (MapWidget widget : this.getWidgets()) {
            if (widget instanceof SeatEyeNumberBox) {
                ((SeatEyeNumberBox) widget).setAutomatic(automatic);
            }
        }
        isLoadingWidgets = false;
    }

    public <T> T getConfigValue(String key, T def) {
        ConfigurationNode config = attachment.getConfig();
        if (config.isNode("firstPersonViewPosition")) {
            config = config.getNode("firstPersonViewPosition");

            if (config.contains(key)) {
                return config.get(key, def);
            }
        }
        return def;
    }

    public void updateConfigValue(String key, Object value) {
        if (isLoadingWidgets) {
            return;
        }

        ConfigurationNode config = attachment.getConfig();
        if (config.isNode("firstPersonViewPosition")) {
            config = config.getNode("firstPersonViewPosition");
            config.set(key, value);
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
        } else {
            // Setup the node with initial defaults
            config = config.getNode("firstPersonViewPosition");
            config.set("posX", 0.0);
            config.set("posY", 0.0);
            config.set("posZ", 0.0);
            config.set("rotX", 0.0);
            config.set("rotY", 0.0);
            config.set("rotZ", 0.0);
            config.set(key, value);

            // Make all number widgets no longer show 'auto'
            setAutomaticDisplayed(false);
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        }

        // Preview with an arrow
        showArrowPreview(true);
    }

    private void showArrowPreview(boolean show) {
        final int numTicks = show ? (5 * 20) : 0; // 5s
        Attachment liveAttachment = this.attachment.getAttachment();
        if (liveAttachment instanceof CartAttachmentSeat) {
            for (Player player : display.getOwners()) {
                if (display.isControlling(player)) {
                    ((CartAttachmentSeat) liveAttachment).showEyeArrow(player, numTicks);
                }
            }
        }
    }

    private class SeatEyeNumberBox extends MapWidgetNumberBox {
        private final String configField;
        private final String acceptedPropertyName;

        public SeatEyeNumberBox(String configField, String acceptedPropertyName, double increment) {
            this.configField = configField;
            this.acceptedPropertyName = acceptedPropertyName;
            this.setIncrement(increment);
        }

        @Override
        public String getAcceptedPropertyName() {
            return acceptedPropertyName;
        }

        @Override
        public void onAttached() {
            super.onAttached();
            setAutomatic(isAutomatic());
        }

        @Override
        public void onValueChanged() {
            updateConfigValue(configField, getValue());
        }

        public void setAutomatic(boolean automatic) {
            setTextOverride(automatic ? "Auto" : null);
            if (automatic) {
                setValue(0.0);
            } else {
                setValue(getConfigValue(configField, 0.0));
            }
        }
    }
}

package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class SeatExitPositionMenu extends MapWidgetMenu {
    private SeatMapWidgetNumberBox _positionX, _positionY, _positionZ;
    private SeatMapWidgetNumberBox _rotationX, _rotationY, _rotationZ;

    public SeatExitPositionMenu() {
        this.setBounds(5, 3, 108, 98);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
        this.getTitle().setText("Seat Exit Position");
        this.getTitle().setColor(MapColorPalette.getSpecular(MapColorPalette.COLOR_GREEN, 1.7f));
    }

    @Override
    public void onAttached() {
        super.onAttached();

        int slider_width = 74;
        int y_offset = 12;
        int y_step = 12;

        this.addWidget(new MapWidgetSelectionBox() { // anchor
            @Override
            public void onAttached() {
                super.onAttached();

                for (AttachmentAnchor type : AttachmentAnchor.values()) {
                    this.addItem(type.toString());
                }
                this.setSelectedItem(getConfig().get("anchor", AttachmentAnchor.DEFAULT.getName()));
            }

            @Override
            public void onSelectedItemChanged() {
                getConfig().set("anchor", getSelectedItem());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
                previewEjectPosition();
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Anchor");
        y_offset += y_step;

        //this.transformType
        this._positionX = this.addWidget(new SeatMapWidgetNumberBox(this, "posX")); // Position X
        this._positionX.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.X");
        y_offset += y_step;

        this._positionY = this.addWidget(new SeatMapWidgetNumberBox(this, "posY")); // Position Y
        this._positionY.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Y");
        y_offset += y_step;

        this._positionZ = this.addWidget(new SeatMapWidgetNumberBox(this, "posZ")); // Position Z
        this._positionZ.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Z");
        y_offset += y_step;

        _rotationX = this.addWidget(new SeatMapWidgetNumberBox(this, "rotX") { // Rotation X
            @Override
            public void onActivate() {
                setRotationLocked(!isRotationLocked());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
            }

            @Override
            public void onValueChangeStart() {
                setRotationLocked(true);
            }
        });
        _rotationX.setIncrement(0.1);
        _rotationX.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pitch");
        y_offset += y_step;

        _rotationY = this.addWidget(new SeatMapWidgetNumberBox(this, "rotY") { // Rotation Y
            @Override
            public void onActivate() {
                setRotationLocked(!isRotationLocked());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
            }

            @Override
            public void onValueChangeStart() {
                setRotationLocked(true);
            }
        });
        _rotationY.setIncrement(0.1);
        _rotationY.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Yaw");
        y_offset += y_step;

        _rotationZ = this.addWidget(new SeatMapWidgetNumberBox(this, "rotZ") { // Rotation Z
            @Override
            public void onActivate() {
                setRotationLocked(!isRotationLocked());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
            }

            @Override
            public void onValueChangeStart() {
                setRotationLocked(true);
            }
        });
        _rotationZ.setIncrement(0.1);
        _rotationZ.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Roll");
        y_offset += y_step;

        refreshRotationLocked();
    }

    public boolean isRotationLocked() {
        return getConfig().get("lockRotation", false);
    }

    public void setRotationLocked(boolean locked) {
        getConfig().set("lockRotation", locked);
        refreshRotationLocked();
    }

    public void refreshRotationLocked() {
        String overrideStr = isRotationLocked() ? null : "FREE";
        _rotationX.setTextOverride(overrideStr);
        _rotationY.setTextOverride(overrideStr);
        _rotationZ.setTextOverride(overrideStr);
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("ejectPosition");
    }

    /**
     * Spawns dust particles where the exit position is of all seats using this configuration
     */
    private void previewEjectPosition() {
        for (Attachment attachment : this.attachment.getAttachments()) {
            if (attachment instanceof CartAttachmentSeat) {
                CartAttachmentSeat seat = (CartAttachmentSeat) attachment;

                for (Player viewer : ((AttachmentEditor) this.display).getViewers()) {
                    //TODO: This probably bugs out cross-world!
                    Location ejectPos = ((CartAttachmentSeat) attachment).getEjectPosition(viewer);
                    PlayerUtil.spawnDustParticles(viewer, ejectPos.toVector(), Color.BLUE);
                }
            }
        }
    }

    private class SeatMapWidgetNumberBox extends MapWidgetNumberBox {
        private final String field;
        private boolean ignoreValueChange = true;

        public SeatMapWidgetNumberBox(SeatExitPositionMenu menu, String field) {
            this.field = field;
        }

        @Override
        public void onAttached() {
            super.onAttached();
            this.setValue(getConfig().get(this.field, 0.0));
            this.ignoreValueChange = false;
        }

        @Override
        public void onValueChanged() {
            if (this.ignoreValueChange) {
                return;
            }

            getConfig().set(this.field, getValue());
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
            if (this.getChangeRepeat() <= 1) {
                onValueChangeStart();
            }

            previewEjectPosition();
        }

        public void onValueChangeStart() {
        }

        @Override
        public void onValueChangeEnd() {
        }
    }
}

package com.bergerkiller.bukkit.tc.attachments.ui.menus.appearance;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

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
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                previewViewer();
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
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
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
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
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
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
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

    // Teleports the player that is editing to where the current exit position is at
    // If the player is seated, only changes the look-angle
    public void previewViewer() {
        AttachmentEditor editor = ((AttachmentEditor) this.display);
        MinecartMember<?> member = editor.editedCart.getHolder();
        if (member == null) {
            return;
        }

        Attachment attachment = member.getAttachments().getRootAttachment().findChild(this.attachment.getTargetPath());
        if (!(attachment instanceof CartAttachmentSeat)) {
            return;
        }

        Player player = editor.getViewers().get(0);
        Location ejectPos = ((CartAttachmentSeat) attachment).getEjectPosition(player);
        if (player.getWorld() != member.getEntity().getWorld()) {
            return; // Why would you do this?
        }
        if (player.getVehicle() != null) {
            // Preserve player position, only change look direction
            Location playerPos = player.getLocation();
            ejectPos.setX(playerPos.getX());
            ejectPos.setY(playerPos.getY());
            ejectPos.setZ(playerPos.getZ());
        }
        Util.correctTeleportPosition(ejectPos);
        player.teleport(ejectPos, TeleportCause.PLUGIN);
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("ejectPosition");
    }
}

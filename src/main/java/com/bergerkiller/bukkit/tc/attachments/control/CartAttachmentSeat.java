package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity.DisplayMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityElytra;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityNormal;
import com.bergerkiller.bukkit.tc.attachments.control.seat.ThirdPersonDefault;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonDefault;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewLockMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetToggleButton;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.appearance.SeatExitPositionMenu;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.bukkit.tc.properties.standard.type.ExitOffset;

public class CartAttachmentSeat extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "SEAT";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/seat.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentSeat();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            // First person view mode and whether FPV is locked (spectator mode)
            // TODO: In future, a toggle for smoothcoasters could be added. Is now always-on.
            {
                tab.addWidget(new MapWidgetText())
                   .setText("FIRST PERSON VIEW")
                   .setFont(MapFont.TINY)
                   .setColor(MapColorPalette.COLOR_RED)
                   .setPosition(20, 2);

                 tab.addWidget(new MapWidgetToggleButton<FirstPersonViewMode>() {
                     @Override
                     public void onSelectionChanged() {
                         attachment.getConfig().set("firstPersonViewMode", this.getSelectedOption());
                         sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                         attachment.resetIcon();
                         display.playSound(SoundEffect.CLICK);
                     }
                 }).addOptions(FirstPersonViewMode::name, FirstPersonViewMode.class)
                   .setSelectedOption(attachment.getConfig().get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC))
                   .setBounds(0, 9, 84, 14);

                 tab.addWidget(new MapWidgetBlinkyButton() {
                     @Override
                     public void onAttached() {
                         updateIcon();
                     }

                     @Override
                     public void onClick() {
                         FirstPersonViewLockMode lockMode = attachment.getConfig().get("firstPersonViewLockMode", FirstPersonViewLockMode.OFF);
                         lockMode = FirstPersonViewLockMode.values()[(lockMode.ordinal()+1) % FirstPersonViewLockMode.values().length];
                         attachment.getConfig().set("firstPersonViewLockMode", lockMode);
                         sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                         updateIcon();
                         display.playSound(SoundEffect.CLICK);
                     }

                     public void updateIcon() {
                         FirstPersonViewLockMode lockMode = attachment.getConfig().get("firstPersonViewLockMode", FirstPersonViewLockMode.OFF);
                         setIcon(lockMode.getIconPath());
                         setTooltip(lockMode.getTooltip());
                     }
                 }).setPosition(86, 9);
            }

            // Passenger display configuration and whether body rotation is locked
            {
                tab.addWidget(new MapWidgetText())
                   .setText("PASSENGER DISPLAY")
                   .setFont(MapFont.TINY)
                   .setColor(MapColorPalette.COLOR_RED)
                   .setPosition(20, 26);

                 tab.addWidget(new MapWidgetToggleButton<DisplayMode>() {
                     @Override
                     public void onSelectionChanged() {
                         attachment.getConfig().set("displayMode", this.getSelectedOption());
                         sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                         attachment.resetIcon();
                         display.playSound(SoundEffect.CLICK);
                     }
                 }).addOptions(DisplayMode::name, DisplayMode.class)
                   .setSelectedOption(attachment.getConfig().get("displayMode", DisplayMode.DEFAULT))
                   .setBounds(0, 33, 84, 14);

                 tab.addWidget(new MapWidgetBlinkyButton() {
                     @Override
                     public void onAttached() {
                         updateIcon();
                     }

                     @Override
                     public void onClick() {
                         attachment.getConfig().set("lockRotation", !attachment.getConfig().get("lockRotation", false));
                         sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                         updateIcon();
                         display.playSound(SoundEffect.CLICK);
                     }

                     public void updateIcon() {
                         boolean isPassengerLocked = attachment.getConfig().get("lockRotation", false);
                         if (isPassengerLocked) {
                             setIcon("attachments/seat_body_locked.png");
                             setTooltip("No body rotation");
                         } else {
                             setIcon("attachments/seat_body_unlocked.png");
                             setTooltip("Body can rotate");
                         }
                     }
                 }).setPosition(86, 33);
            }

            tab.addWidget(new MapWidgetButton() { // Change exit position button
                @Override
                public void onActivate() {
                    //TODO: Cleaner way to open a sub dialog
                    tab.getParent().getParent().addWidget(new SeatExitPositionMenu()).setAttachment(attachment);
                }
            }).setText("Change Exit").setBounds(0, 52, 100, 14);

            final MapWidgetSubmitText permissionTextBox = tab.addWidget(new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    this.setDescription("Enter permission node");
                }

                @Override
                public void onAccept(String text) {
                    attachment.getConfig().set("enterPermission", text);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            });
            tab.addWidget(new MapWidgetButton() { // Change exit position button
                @Override
                public void onActivate() {
                    permissionTextBox.activate();
                }
            }).setText("Permission").setBounds(0, 68, 100, 14);
        }
    };

    // Houses the logic for synchronizing this seat to players viewing the entity in first person
    // That is, the viewer is the one inside this seat
    public FirstPersonDefault firstPerson = new FirstPersonDefault(this);
    // Houses the logic for synchronizing this seat to players viewing the entity in third person
    // That is, the viewer is not the one inside this seat
    public ThirdPersonDefault thirdPerson = new ThirdPersonDefault(this);

    /**
     * Information about the entity that is seated inside this seat
     */
    public SeatedEntity seated = null;

    // During makeVisible(viewer) this is set to that viewer, to ignore it when refreshing
    private Player _makeVisibleCurrent = null;

    // Seat configuration
    private ObjectPosition _ejectPosition = new ObjectPosition();
    private boolean _ejectLockRotation = false;
    private String _enterPermission = null;

    /**
     * Gets the viewers of this seat that have already had makeVisible processed.
     * The entity passed to makeVisible() is removed from the list during
     * makeVisible().
     * 
     * @return synced viewers
     */
    public Collection<Player> getViewersSynced() {
        if (_makeVisibleCurrent == null) {
            return this.getViewers();
        } else {
            ArrayList<Player> tmp = new ArrayList<Player>(this.getViewers());
            tmp.remove(_makeVisibleCurrent);
            return tmp;
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();

        DisplayMode displayMode = this.getConfig().get("displayMode", DisplayMode.DEFAULT);
        switch (displayMode) {
        case ELYTRA_SIT:
            this.seated = new SeatedEntityElytra(this);
            break;
        case NO_NAMETAG:
            this.seated = new SeatedEntityNormal(this, true);
            break;
        default:
            this.seated = new SeatedEntityNormal(this, false);
            break;
        }
        this.seated.orientation.setLocked(this.getConfig().get("lockRotation", false));

        FirstPersonViewMode viewMode = this.getConfig().get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC);
        FirstPersonViewLockMode viewLockMode = this.getConfig().get("firstPersonViewLockMode", FirstPersonViewLockMode.OFF);
        if (viewLockMode.isSpectator()) {
            this.firstPerson = new FirstPersonSpectator(this);
        } else {
            this.firstPerson = new FirstPersonDefault(this);
        }
        this.firstPerson.setMode(viewMode);
        this.firstPerson.setLockMode(viewLockMode);

        this._enterPermission = this.getConfig().get("enterPermission", String.class, null);
        this.seated.setDisplayMode(this.getConfig().get("displayMode", DisplayMode.DEFAULT));

        ConfigurationNode ejectPosition = this.getConfig().getNode("ejectPosition");
        this._ejectPosition.load(this.getManager().getClass(), TYPE, ejectPosition);
        this._ejectLockRotation = ejectPosition.get("lockRotation", false);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.setEntity(null);
    }

    @Override
    public void makeVisible(Player viewer) {
        try {
            this._makeVisibleCurrent = viewer;
            this.seated.updateMode(false);
            makeVisibleImpl(viewer);
        } finally {
            this._makeVisibleCurrent = null;
        }
    }

    public void makeVisibleImpl(Player viewer) {
        if (seated.isEmpty()) {
            return;
        }

        if (viewer == this.seated.getEntity()) {
            this.firstPerson.makeVisible(viewer);
        } else {
            this.thirdPerson.makeVisible(viewer);
        }

        // If rotation locked, send the rotation of the passenger if available
        this.seated.orientation.makeVisible(viewer, this.seated);
    }

    @Override
    public void makeHidden(Player viewer) {
        if (this.seated.getEntity() == viewer) {
            this.firstPerson.makeHidden(viewer);
        } else {
            this.thirdPerson.makeHidden(viewer);
        }
    }

    public Vector calcMotion() {
        AttachmentInternalState state = this.getInternalState();
        Vector pos_old = state.last_transform.toVector();
        Vector pos_new = state.curr_transform.toVector();
        return pos_new.subtract(pos_old);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        if (this.seated.fakeMount != null &&
            this.getConfiguredPosition().isDefault() &&
            this.getParent() != null)
        {
            this.getParent().applyDefaultSeatTransform(transform);
        }
    }

    /**
     * Transforms the transformation matrix so that the eyes are at the center.
     * Used by the 'eyes' anchor.
     * 
     * @param transform
     */
    public void transformToEyes(Matrix4x4 transform) {
        FirstPersonViewMode mode = this.firstPerson.getMode();
        if (mode == FirstPersonViewMode.DYNAMIC) {
            mode = FirstPersonViewMode.THIRD_P;
        }
        if (mode.isVirtual()) {
            transform.translate(0.0, -mode.getVirtualOffset(), 0.0);
        } else {
            transform.translate(0.0, -1.0, 0.0);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        // Move the first-person view, if needed
        if (seated.isPlayer() && this.getViewers().contains(seated.getEntity())) {
            firstPerson.onMove(absolute);
        }

        // If not parented to a parent attachment, move the fake mount to move the seat
        if (this.seated.fakeMount != null) {
            this.seated.fakeMount.syncPosition(absolute);
        }
    }

    /**
     * Gets the Entity that is displayed and controlled in this seat
     * 
     * @return seated entity
     */
    public Entity getEntity() {
        return this.seated.getEntity();
    }

    /**
     * Sets the Entity that is displayed and controlled.
     * Any previously set entity is reset to the defaults.
     * The new entity has seated entity specific settings applied to it.
     * 
     * @param entity to set to
     */
    public void setEntity(Entity entity) {
        if (seated.getEntity() == entity) {
            return;
        }

        if (!this.seated.isEmpty()) {
            // If a previous entity was set, unseat it
            for (Player viewer : this.getViewers()) {
                PlayerUtil.getVehicleMountController(viewer).unmount(this.seated.parentMountId, this.seated.getEntity().getEntityId());
                this.makeHidden(viewer);
            }
            TrainCarts.plugin.getSeatAttachmentMap().remove(this.seated.getEntity().getEntityId(), this);
        }

        // Switch entity
        this.seated.setEntity(entity);

        // Initialize mode with this new Entity
        this.seated.updateMode(true);

        // Re-seat new entity
        if (!this.seated.isEmpty()) {
            TrainCarts.plugin.getSeatAttachmentMap().set(this.seated.getEntity().getEntityId(), this);
            for (Player viewer : this.getViewers()) {
                this.makeVisibleImpl(viewer);
            }
        }
    }

    @Override
    public void onTick() {
        // Synchronize orientation of the entity inside this seat
        this.seated.orientation.synchronize(this, this.getTransform(), this.seated);

        // Only needed when there is a passenger
        this.seated.updateMode(false);

        // Move player view relatively
        this.firstPerson.onTick();
    }

    /**
     * Whether the passengers inside have their rotation locked based on the orientation of this seat
     * 
     * @return True if rotation is locked
     */
    public boolean isRotationLocked() {
        return this.seated.orientation.isLocked();
    }

    public float getPassengerYaw() {
        return this.seated.orientation.getPassengerYaw();
    }

    public float getPassengerPitch() {
        return this.seated.orientation.getPassengerPitch();
    }

    public float getPassengerHeadYaw() {
        return this.seated.orientation.getPassengerHeadYaw();
    }

    /**
     * Calculates the eject position of the seat
     * 
     * @param passenger to check eject position for
     * @return eject position
     */
    public Location getEjectPosition(Entity passenger) {
        Matrix4x4 tmp = this.getTransform().clone();
        this._ejectPosition.anchor.apply(this, tmp);

        // Rotate based on the relative exit set for the seat
        tmp.multiply(this._ejectPosition.transform);

        org.bukkit.World w = this.getManager().getWorld();
        Vector pos = tmp.toVector();
        Vector ypr = tmp.getYawPitchRoll();
        float yaw = (float) ypr.getY();
        float pitch = (float) ypr.getX();

        // When rotation is not locked, preserve original orientation of passenger
        if (!this._ejectLockRotation && passenger != null) {
            Location curr_loc;
            if (passenger instanceof LivingEntity) {
                curr_loc = ((LivingEntity) passenger).getEyeLocation();
            } else {
                curr_loc = passenger.getLocation();
            }
            yaw = curr_loc.getYaw();
            pitch = curr_loc.getPitch();
        }

        // Apply exit offset property on top of this result, purely based on the
        // movement direction of the root cart
        if (this.getManager() instanceof AttachmentControllerMember) {
            MinecartMember<?> member = ((AttachmentControllerMember) this.getManager()).getMember();
            ExitOffset cprop_offset = member.getProperties().getExitOffset();

            if (cprop_offset.isAbsolute()) {
                // Ignore seat/cart position, teleport to these coordinates
                MathUtil.setVector(pos, cprop_offset.getPosition());
                if (cprop_offset.hasLockedYaw()) {
                    yaw = cprop_offset.getYaw();
                }
                if (cprop_offset.hasLockedPitch()) {
                    pitch = cprop_offset.getPitch();
                }
            } else {
                // Relative to the seat/cart
                Quaternion orientation = member.getOrientation();
                if (member.isOrientationInverted()) {
                    orientation.rotateY(180.0);
                }

                Vector exitpos = cprop_offset.getPosition();
                exitpos.setX(-exitpos.getX()); // Weird?
                orientation.transformPoint(exitpos);
                pos.add(exitpos);

                if (cprop_offset.hasLockedYaw()) {
                    yaw = (float) (orientation.getYaw() + cprop_offset.getYaw());
                }
                if (cprop_offset.hasLockedPitch()) {
                    pitch = (float) (orientation.getPitch() + cprop_offset.getPitch());
                }
            }
        }

        return new Location(w, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch);
    }

    @Override
    public boolean isHiddenWhenInactive() {
        return false;
    }

    /**
     * Checks whether the given passenger entity is allowed to sit inside this seat.
     * If this seat is already occupied, False is returned.
     * 
     * @param passenger
     * @return True if the entity can sit inside this seat
     */
    public boolean canEnter(Entity passenger) {
        if (!this.seated.isEmpty()) {
            return false;
        }
        if (passenger instanceof Player && this._enterPermission != null && !this._enterPermission.isEmpty()) {
            Player p = (Player) passenger;
            if (!p.hasPermission(this._enterPermission)) {
                return false;
            }
        }
        return true;
    }
}

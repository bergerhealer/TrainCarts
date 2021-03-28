package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
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
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetToggleButton;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.appearance.SeatExitPositionMenu;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.ExitOffset;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPositionHandle;

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
            tab.addWidget(new MapWidgetToggleButton<Boolean>() {
                @Override
                public void onSelectionChanged() {
                    attachment.getConfig().set("lockRotation", this.getSelectedOption());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                    display.playSound(SoundEffect.CLICK);
                }
            }).addOptions(b -> "Lock Rotation: " + (b ? "ON" : "OFF"), Boolean.TRUE, Boolean.FALSE)
              .setSelectedOption(attachment.getConfig().get("lockRotation", false))
              .setBounds(0, 4, 100, 14);

            tab.addWidget(new MapWidgetToggleButton<FirstPersonViewMode>() {
                @Override
                public void onSelectionChanged() {
                    attachment.getConfig().set("firstPersonViewMode", this.getSelectedOption());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                    display.playSound(SoundEffect.CLICK);
                }
            }).addOptions(o -> "FPV: " + o.name(), FirstPersonViewMode.class)
              .setSelectedOption(attachment.getConfig().get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC))
              .setBounds(0, 20, 100, 14);

            tab.addWidget(new MapWidgetToggleButton<DisplayMode>() {
                @Override
                public void onSelectionChanged() {
                    attachment.getConfig().set("displayMode", this.getSelectedOption());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                    display.playSound(SoundEffect.CLICK);
                }
            }).addOptions(o -> "Display: " + o.name(), DisplayMode.class)
              .setSelectedOption(attachment.getConfig().get("displayMode", DisplayMode.DEFAULT))
              .setBounds(0, 36, 100, 14);

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

    // Remainder yaw and pitch when moving player view orientation along with the seat
    // This remainder is here because Minecraft has only limited yaw/pitch granularity
    private double _playerYawRemainder = 0.0;
    private double _playerPitchRemainder = 0.0;

    // Seat configuration
    private ViewLockMode _viewLockMode = ViewLockMode.OFF;
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
        default:
            this.seated = new SeatedEntityNormal(this);
            break;
        }

        this.seated.orientation.setLocked(this.getConfig().get("lockRotation", false));
        this.firstPerson.setMode(this.getConfig().get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC));
        this._viewLockMode = ViewLockMode.OFF; // Disabled, is broken
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
        if (this._viewLockMode == ViewLockMode.MOVE && this.seated.isPlayer()) {
            Vector old_pyr;
            {
                Location eye_loc = ((Player) this.seated.getEntity()).getEyeLocation();
                old_pyr = new Vector(eye_loc.getPitch(),
                                     eye_loc.getYaw(),
                                     0.0);
                old_pyr.setX(-old_pyr.getX());
            }

            // Find the rotation transformation to go from the previous transformation to pyr
            // Multiplying getPreviousTransform() with this rotation should result in old_pyr exactly
            Quaternion diff = Quaternion.diff(this.getPreviousTransform().getRotation(), Quaternion.fromYawPitchRoll(old_pyr));

            // Transform the new seat transform with this diff to obtain the expected rotation after moving
            Quaternion new_rotation = this.getTransform().getRotation();
            new_rotation.multiply(diff);
            Vector new_pyr = new_rotation.getYawPitchRoll();

            // Compute difference, also include a remainder we haven't synchronized yet
            Vector pyr = new_pyr.clone().subtract(old_pyr);
            pyr.setX(pyr.getX() + this._playerPitchRemainder);
            pyr.setY(pyr.getY() + this._playerYawRemainder);

            // Refresh this change in pitch/yaw/roll to the player
            if (Math.abs(pyr.getX()) > 1e-5 || Math.abs(pyr.getY()) > 1e-5) {
                PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, (float) pyr.getY(), (float) pyr.getX());
                this._playerPitchRemainder = (pyr.getX() - p.getPitch());
                this._playerYawRemainder = (pyr.getY() - p.getYaw());
                PacketUtil.sendPacket((Player) this.seated.getEntity(), p);
            } else {
                this._playerPitchRemainder = pyr.getX();
                this._playerYawRemainder = pyr.getY();
            }
        }
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

        // If this is inside a Minecart, check the exit offset / rotation properties
        if (this.getManager() instanceof AttachmentControllerMember) {
            CartProperties cprop = ((AttachmentControllerMember) this.getManager()).getMember().getProperties();
            ExitOffset cprop_offset = cprop.getExitOffset();

            // Translate eject offset specified in the cart's properties
            tmp.translate(cprop_offset.getRelativePosition());

            // Apply transformation of eject position (translation, then rotation)
            tmp.multiply(this._ejectPosition.transform);

            // Apply eject rotation specified in the cart's properties on top
            tmp.rotateYawPitchRoll(cprop_offset.getPitch(), cprop_offset.getYaw(), 0.0f);
        } else {
            // Only use the eject position transform
            tmp.multiply(this._ejectPosition.transform);
        }

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

    public static enum ViewLockMode {
        OFF, /* Player view orientation is not changed */
        MOVE, /* Player view orientation moves along as the seat moves */
        //LOCK /* Player view is locked to look forwards in the seat direction at all times */
    }
}

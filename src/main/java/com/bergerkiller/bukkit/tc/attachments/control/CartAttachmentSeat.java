package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.config.transform.ItemTransformType;
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
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity.DisplayMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.ThirdPersonDefault;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonView;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewDefault;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonEyePositionDialog;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewStanding;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatDebugUI;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatDisplayedItemDialog;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatExitPositionMenu;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewLockMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewNone;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetToggleButton;
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
        public void migrateConfiguration(ConfigurationNode config) {
            // FLOATING was removed now that the eye position configuration made it redundant
            if ("FLOATING".equals(config.get("firstPersonViewMode", String.class))) {
                config.set("firstPersonViewMode", "DEFAULT");

                // Position the eye at a y-offset similar to what would be used in floating mode
                config.remove("firstPersonViewPosition");
                ConfigurationNode eye = config.getNode("firstPersonViewPosition");
                eye.set("posX", 0.0);
                eye.set("posY", 1.0);
                eye.set("posZ", 0.0);
                eye.set("rotX", 0.0);
                eye.set("rotY", 0.0);
                eye.set("rotZ", 0.0);
            }
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
                   .setPosition(17, 2);

                 tab.addWidget(new MapWidgetToggleButton<FirstPersonViewMode>() {
                     @Override
                     public void onSelectionChanged() {
                         attachment.getConfig().set("firstPersonViewMode", this.getSelectedOption());
                         sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                         attachment.resetIcon();
                         display.playSound(SoundEffect.CLICK);
                     }
                 }).addOptions(FirstPersonViewMode::name,
                         Stream.of(FirstPersonViewMode.values())
                               .filter(FirstPersonViewMode::isSelectable)
                               .toArray(FirstPersonViewMode[]::new))
                   .setSelectedOption(attachment.getConfig().get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC))
                   .setBounds(0, 9, 68, 14);

                 tab.addWidget(new MapWidgetBlinkyButton() {
                     @Override
                     public void onAttached() {
                         updateIcon();
                     }

                     @Override
                     public void onClick() {
                         tab.addWidget(new FirstPersonEyePositionDialog(attachment) {
                             @Override
                             public void close() {
                                 super.close();
                                 updateIcon();
                             }
                         });
                     }

                     public void updateIcon() {
                         boolean configured = attachment.getConfig().isNode("firstPersonViewPosition");
                         setIcon(configured ? "attachments/view_camera_configured.png" : "attachments/view_camera_auto.png");
                         setTooltip(configured ? "Set eye position\n  (Configured)" : "Set eye position\n   (Automatic)");
                     }
                 }).setPosition(70, 9);

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
                   .setPosition(17, 26);

                 tab.addWidget(new MapWidgetToggleButton<DisplayMode>() { // Switch display mode button
                     @Override
                     public void onSelectionChanged() {
                         attachment.getConfig().set("displayMode", this.getSelectedOption());
                         sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                         attachment.resetIcon();
                         display.playSound(SoundEffect.CLICK);
                     }
                 }).addOptions(DisplayMode::name, DisplayMode.class)
                   .setSelectedOption(attachment.getConfig().get("displayMode", DisplayMode.DEFAULT))
                   .setBounds(0, 33, 68, 14);

                 tab.addWidget(new MapWidgetBlinkyButton() { // Configures displaying an item at the seat
                     @Override
                     public void onAttached() {
                         updateIcon();
                     }

                     @Override
                     public void onClick() {
                         //TODO: Cleaner way to open a sub dialog
                         tab.getParent().getParent().addWidget(new SeatDisplayedItemDialog() {
                             @Override
                             public void onDetached() {
                                 super.onDetached();
                                 updateIcon();
                             }
                         }).setAttachment(attachment);
                     }

                     public void updateIcon() {
                         boolean hasItemSet = false;
                         if (attachment.getConfig().isNode("displayItem")) {
                             ConfigurationNode displayItem = attachment.getConfig().getNode("displayItem");
                             hasItemSet = displayItem.get("enabled", false);
                         }

                         //boolean hasItemSet = attachment.getConfig().get("lockRotation", false);
                         if (hasItemSet) {
                             setIcon("attachments/seat_item_on.png");
                             setTooltip("Display an item\n   (Enabled)");
                         } else {
                             setIcon("attachments/seat_item_off.png");
                             setTooltip("Display an item\n   (Disabled)");
                         }
                     }
                 }).setPosition(70, 33);

                 tab.addWidget(new MapWidgetBlinkyButton() { // Configures whether the body can rotate
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
    public FirstPersonView firstPerson = new FirstPersonViewNone(this);
    // Houses the logic for synchronizing this seat to players viewing the entity in third person
    // That is, the viewer is not the one inside this seat
    public ThirdPersonDefault thirdPerson = new ThirdPersonDefault(this);

    /**
     * Information about the entity that is seated inside this seat
     */
    public SeatedEntity seated = null;

    // During makeVisible(viewer) this is set to that viewer, to ignore it when refreshing
    private AttachmentViewer _makeVisibleCurrent = null;

    // Seat configuration
    private ObjectPosition _ejectPosition = new ObjectPosition();
    private boolean _ejectLockRotation = false;
    private String _enterPermission = null;
    private boolean _locked = false;
    private FirstPersonViewMode fpvViewMode;
    private FirstPersonViewLockMode fpvViewLockMode;
    private final ObjectPosition fpvEyePosition = new ObjectPosition();

    // When onFocus() or other changes happen, a dummy player is displayed sitting in the seat
    // if no player is currently displayed. This normally blinks in some menus, which looks bad.
    // We debounce that here, leaving the player displayed for a little longer while the player
    // is in the menu.
    // If 0, the attachment is not focused and no dummy player should be displayed.
    private static final int FOCUS_DEBOUNCE_TICKS = 40; //2s
    private int _focusDebounceTimer = 0;

    // Displays an item where the seat is at, but only to people in third-person
    // Also shown to first-person mode viewers, if their mode is THIRD_P
    private VirtualSpawnableObject _displayedItemEntity = null;
    private ObjectPosition _displayedItemPosition = null;
    private boolean _displayedItemShowFirstPersonEnabled = false;
    private boolean _displayedItemShownInFirstPerson = false;

    // Debug displays used by the attachment editor
    public final SeatDebugUI debug = new SeatDebugUI(this);

    // Whether the smooth coasters mod is used in first-person for a particular Player
    private boolean _useSmoothCoasters = false;

    /**
     * Gets the viewers of this seat that have already had makeVisible processed.
     * The entity passed to makeVisible() is removed from the list during
     * makeVisible().
     * 
     * @return synced viewers
     */
    public Collection<AttachmentViewer> getAttachmentViewersSynced() {
        if (_makeVisibleCurrent == null) {
            return this.getAttachmentViewers();
        } else {
            ArrayList<AttachmentViewer> tmp = new ArrayList<>(this.getAttachmentViewers());
            tmp.remove(_makeVisibleCurrent);
            return tmp;
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.seated = this.getConfig().get("displayMode", DisplayMode.DEFAULT).create(this);
        this._locked = this.getConfig().get("lockRotation", false);
        this.fpvViewMode = this.getConfig().get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC);
        this.fpvViewLockMode = this.getConfig().get("firstPersonViewLockMode", FirstPersonViewLockMode.OFF);

        // If enabled, initialize a displayed item
        this._displayedItemPosition = null;
        this._displayedItemEntity = null;
        this._displayedItemShowFirstPersonEnabled = false;
        if (this.getConfig().get("displayItem.enabled", false)) {
            ItemTransformType type = ItemTransformType.deserialize(getConfig(), "displayItem.position.transform");

            // Rest is loaded in during onLoad()
            this._displayedItemPosition = new ObjectPosition();
            this._displayedItemEntity = type.create(this.getManager(), null);
            this._displayedItemShowFirstPersonEnabled = this.getConfig().get("displayItem.showFirstPerson", false);
        }
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        // Verify that the new configuration is not incompatible with what we load in onAttached()
        // Some stuff just can't be live modified.
        // TODO: Try and make some of these changeable without re-creating the attachment
        if (config.get("displayItem.enabled", false) != (this._displayedItemEntity != null)) {
            return false;
        }
        if (this._displayedItemEntity != null) {
            if (config.get("displayItem.showFirstPerson", false) != this._displayedItemShowFirstPersonEnabled) {
                return false;
            }
            ItemTransformType type = ItemTransformType.deserialize(config, "displayItem.position.transform");
            if (!type.canUpdate(this._displayedItemEntity)) {
                return false;
            }
        }
        if (this.seated.getDisplayMode() != config.get("displayMode", DisplayMode.DEFAULT)) {
            return false;
        }
        if (this._locked != config.get("lockRotation", false)) {
            return false;
        }
        if (this.fpvViewMode != config.get("firstPersonViewMode", FirstPersonViewMode.DYNAMIC)) {
            return false;
        }
        if (this.fpvViewLockMode != config.get("firstPersonViewLockMode", FirstPersonViewLockMode.OFF)) {
            return false;
        }

        return true;
    }

    // Note: Only load things here that can be live-modified in the editor, such as positions
    //       Things that require a re-initialization shouldn't be done here.
    @Override
    public void onLoad(ConfigurationNode config) {
        super.onLoad(config);

        this._enterPermission = this.getConfig().get("enterPermission", String.class, null);

        // If the position is default, change the anchor to seat_parent so that logic works correctly
        // This is technically legacy behavior, but we're stuck with it now...
        {
            AttachmentInternalState state = getInternalState();
            if (state.position.isDefault() && state.position.anchor == AttachmentAnchor.DEFAULT) {
                state.position.anchor = AttachmentAnchor.SEAT_PARENT;
            }
        }

        // Eye position
        if (config.contains("firstPersonViewPosition")) {
            this.fpvEyePosition.load(this.getManager().getClass(), TYPE,
                    config.getNode("firstPersonViewPosition"));
        } else {
            this.fpvEyePosition.reset();
        }
        this.firstPerson.getEyePosition().load(this.fpvEyePosition);

        // Eject position
        {
            ConfigurationNode ejectPosition = this.getConfig().getNode("ejectPosition");
            this._ejectPosition.load(this.getManager().getClass(), TYPE, ejectPosition);
            this._ejectLockRotation = ejectPosition.get("lockRotation", false);
        }

        // Displayed item and position
        if (this._displayedItemEntity != null) {
            ConfigurationNode displayItemConfig = config.getNode("displayItem");

            this._displayedItemPosition.load(this.getManager().getClass(), CartAttachmentItem.TYPE,
                    displayItemConfig.getNode("position"));

            ItemTransformType.deserialize(displayItemConfig, "position.transform")
                    .load(this._displayedItemEntity, displayItemConfig, this._displayedItemPosition);
        }

        // Reset (player modifying attachment position or other stuff)
        if (_focusDebounceTimer > 0) {
            _focusDebounceTimer = FOCUS_DEBOUNCE_TICKS;
        }
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.debug.stopEyePreviews();
        this.setEntityImpl(null, true);
        this._displayedItemEntity = null;
        this._displayedItemPosition = null;
    }

    @Override
    @Deprecated
    public void makeVisible(Player player) {
        makeVisible(getManager().asAttachmentViewer(player));
    }

    @Override
    @Deprecated
    public void makeHidden(Player player) {
        makeHidden(getManager().asAttachmentViewer(player));
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        try {
            this._makeVisibleCurrent = viewer;
            this.seated.updateMode(false);
            makeVisibleImpl(viewer, false);
        } finally {
            this._makeVisibleCurrent = null;
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        makeHiddenImpl(viewer, false);
    }

    public void makeVisibleImpl(AttachmentViewer viewer, boolean isReload) {
        if (!seated.isDisplayed()) {
            return;
        }

        if (viewer.getPlayer() == this.seated.getEntity()) {
            // Initialize the first-person mode for this viewer
            if (!isReload || !viewer.equals(this.firstPerson.getViewer())) {
                FirstPersonViewMode liveMode = this.firstPerson.getLiveMode();
                if (this.fpvViewLockMode.isSpectator() && !this.useSmoothCoasters()) {
                    this.firstPerson = new FirstPersonViewSpectator(this, viewer);
                } else if (this.fpvViewMode == FirstPersonViewMode.STANDING) {
                    this.firstPerson = new FirstPersonViewStanding(this, viewer);
                } else {
                    this.firstPerson = new FirstPersonViewDefault(this, viewer);
                }
                this.firstPerson.setMode(this.fpvViewMode);
                this.firstPerson.setLiveMode(liveMode);
                this.firstPerson.setLockMode(this.fpvViewLockMode);
                this.firstPerson.getEyePosition().load(this.fpvEyePosition);
                this.seated.updateMode(true);
            }

            this.firstPerson.makeVisible(viewer, isReload);

            if (this._displayedItemEntity != null && !this._displayedItemShownInFirstPerson && showDisplayedItemInFirstPerson()) {
                this._displayedItemShownInFirstPerson = true;
                this.makeDisplayedItemVisible(viewer);
            }
        } else {
            this.thirdPerson.makeVisible(viewer);
            if (!isReload && this._displayedItemEntity != null) {
                this.makeDisplayedItemVisible(viewer);
            }
        }
    }

    private void makeDisplayedItemVisible(AttachmentViewer viewer) {
        if (!this._displayedItemEntity.hasViewers()) {
            // Set interpolation mode to match whatever parent vehicle is used
            this._displayedItemEntity.setUseMinecartInterpolation(this.isMinecartInterpolation());
            // Ensure position is updated
            updateDisplayedItemPosition(this.getTransform());
            this._displayedItemEntity.syncPosition(true);
        }
        this._displayedItemEntity.spawn(viewer, this.calcMotion());
    }

    private boolean showDisplayedItemInFirstPerson() {
        return this._displayedItemShowFirstPersonEnabled ||
               this.firstPerson.getLiveMode() == FirstPersonViewMode.THIRD_P;
    }

    private void updateDisplayedItemPosition(Matrix4x4 transform) {
        transform = transform.clone();
        transform.multiply(this._displayedItemPosition.transform);
        this._displayedItemEntity.updatePosition(transform);
        this._displayedItemEntity.syncPosition(false);
    }

    public void makeHiddenImpl(AttachmentViewer viewer, boolean isReload) {
        if (this.seated.getEntity() == viewer.getPlayer()) {
            if (!isReload && this._displayedItemEntity != null && _displayedItemShownInFirstPerson) {
                this._displayedItemShownInFirstPerson = false;
                this._displayedItemEntity.destroy(viewer);
            }
            this.firstPerson.makeHidden(viewer, isReload);
            if (!isReload) {
                this.firstPerson = new FirstPersonViewNone(this); // Reset
            }
        } else {
            this.thirdPerson.makeHidden(viewer);
            if (!isReload && this._displayedItemEntity != null) {
                this._displayedItemEntity.destroy(viewer);
            }
        }
    }

    public Vector calcMotion() {
        AttachmentInternalState state = this.getInternalState();
        Vector pos_old = state.last_transform.toVector();
        Vector pos_new = state.curr_transform.toVector();
        return pos_new.subtract(pos_old);
    }

    /**
     * Gets whether movement updates use minecart interpolation, which unlike other entities, update over
     * 5 ticks instead of 3.
     * This happens when the parent is also using minecart interpolation, and SEAT_PARENT anchor is used.
     *
     * @return True if Minecart interpolation is used
     */
    public boolean isMinecartInterpolation() {
        return getConfiguredPosition().anchor == AttachmentAnchor.SEAT_PARENT &&
               getParent() instanceof CartAttachmentEntity &&
               ((CartAttachmentEntity) getParent()).isMinecartInterpolation();
    }

    /**
     * Whether the smooth coasters client mod is used to control the first-person view camera.
     * During plugin disabling this changes to false to avoid sending plugin messages.
     *
     * @return True if used
     */
    public boolean useSmoothCoasters() {
        return this._useSmoothCoasters && getPlugin().isEnabled();
    }

    /**
     * Sends a rotation update to the player
     *
     * @param orientation
     * @param instant Whether to instantly set the rotation, without interpolation
     */
    public void sendSmoothCoastersRelativeRotation(Quaternion orientation, boolean instant) {
        if (this._useSmoothCoasters) {
            this.getPlugin().getSmoothCoastersAPI().setRotation(
                    this.firstPerson.getViewer().getSmoothCoastersNetwork(),
                    this.firstPerson.getViewer().getPlayer(),
                    (float) orientation.getX(),
                    (float) orientation.getY(),
                    (float) orientation.getZ(),
                    (float) orientation.getW(),
                    instant ? (byte) 0 : (isMinecartInterpolation() ? (byte) 5 : (byte) 3)
            );
        }
    }

    /**
     * Transforms the transformation matrix so that the eyes are at the center.
     * Used by the 'eyes' anchor.
     * 
     * @param transform
     */
    public void transformToEyes(Matrix4x4 transform) {
        // If an eye position is set, use that
        if (!this.firstPerson.getEyePosition().isDefault()) {
            Matrix4x4 tmp = this.firstPerson.getEyePosition().transform.clone();
            tmp.invert();
            transform.multiply(tmp);
            return;
        }

        FirstPersonViewMode mode = this.firstPerson.getMode();
        if (mode == FirstPersonViewMode.DYNAMIC) {
            mode = FirstPersonViewMode.THIRD_P; // Oh god...
        }

        if (mode == FirstPersonViewMode.THIRD_P) {
            transform.translate(seated.getThirdPersonCameraOffset().clone().multiply(-1.0));
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

        // Sync eye previews
        debug.syncEyePreviews(absolute);

        // If not parented to a parent attachment, move the fake mount to move the seat
        seated.syncPosition(absolute);
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
     * Gets the number of ticks this current Entity has been inside the seat.
     * Returns 0 if this seat has no passenger.
     *
     * @return Number of ticks the current passenger entity has been inside the seat
     */
    public int getTicksInSeat() {
        return this.seated.getTicksInSeat();
    }

    /**
     * Sets the Entity that is displayed and controlled.
     * Any previously set entity is reset to the defaults.
     * The new entity has seated entity specific settings applied to it.
     * 
     * @param entity to set to
     */
    public void setEntity(Entity entity) {
        setEntityImpl(entity, false);
    }

    private void setEntityImpl(Entity entity, boolean isDetaching) {
        if (seated.getEntity() == entity) {
            return;
        }

        if (this.seated.isDisplayed()) {
            // If a previous entity was set, unseat it
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                this.makeHiddenImpl(viewer, false);
            }

            // Reset these
            this._useSmoothCoasters = false;
        }
        if (!this.seated.isEmpty()) {
            TrainCarts.plugin.getSeatAttachmentMap().remove(this.seated.getEntity().getEntityId(), this);
        }

        // Detect whether the new entity is a Player with the smooth coasters mod installed
        this._useSmoothCoasters = (entity instanceof Player) &&
                getPlugin().getSmoothCoastersAPI().isEnabled((Player) entity);

        // Switch entity
        this.seated.setEntity(entity);

        // Re-seat new entity
        if (!this.seated.isEmpty()) {
            TrainCarts.plugin.getSeatAttachmentMap().set(this.seated.getEntity().getEntityId(), this);
        }
        if (!isDetaching && this.seated.isDisplayed()) {
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                this.makeVisibleImpl(viewer, false);
            }
        }
    }

    @Override
    public TrainCarts getPlugin() {
        return (TrainCarts) super.getPlugin();
    }

    @Override
    public void onTick() {
        // When focus timer expires, hide the dummy player again
        if (this._focusDebounceTimer > 0 && --this._focusDebounceTimer == 0) {
            hideDummyPlayer();
        }

        // Synchronize orientation of the entity inside this seat
        this.seated.updatePosition(this.getTransform());

        // Update displayed item position as well
        if (this._displayedItemEntity != null) {
            this.updateDisplayedItemPosition(this.getTransform());
        }

        // Only needed when there is a passenger
        this.seated.updateMode(false);

        // Move player view relatively
        this.firstPerson.onTick();
        this.debug.updateEyePreview();

        // Smooth coasters mod might be turned on or off
        if (this.seated.isPlayer()) {
            Player player = (Player) this.seated.getEntity();
            boolean enabled = getPlugin().getSmoothCoastersAPI().isEnabled(player);
            if (enabled != this._useSmoothCoasters) {
                AttachmentViewer viewer = getManager().asAttachmentViewer(player);
                this.makeHiddenImpl(viewer, false);
                this._useSmoothCoasters = enabled;
                this.seated.updateMode(false);
                this.makeVisibleImpl(viewer, false);
            }
        }
    }

    @Override
    public void onFocus() {
        if (this._focusDebounceTimer == 0) {
            showDummyPlayer();
        }
        this._focusDebounceTimer = FOCUS_DEBOUNCE_TICKS;
        this.seated.updateFocus(true);
    }

    @Override
    public void onBlur() {
        if (this._focusDebounceTimer > 0) {
            this._focusDebounceTimer = FOCUS_DEBOUNCE_TICKS;
        }
        this.seated.updateFocus(false);
    }

    /**
     * Shows the dummy player, if no entity is in the seat yet
     */
    private void showDummyPlayer() {
        if (!seated.isDummyPlayer()) {
            seated.setShowDummyPlayer(true);
            if (seated.isEmpty()) {
                for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                    this.makeVisibleImpl(viewer, false);
                }
            }
        }
    }

    /**
     * Hides the dummy player again
     */
    private void hideDummyPlayer() {
        // Make dummy player hidden, if it was displayed
        if (seated.isDummyPlayer()) {
            // Hide them all, don't show again
            if (seated.isEmpty()) {
                for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                    this.makeHiddenImpl(viewer, false);
                }
            }
            seated.setShowDummyPlayer(false);
        }
    }

    /**
     * Whether the passengers inside have their rotation locked based on the orientation of this seat
     * 
     * @return True if rotation is locked
     */
    public boolean isRotationLocked() {
        return this._locked;
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
     * Gets the exact position the passenger would have if it
     * was inside this seat.
     *
     * @param passenger Passenger to check
     * @return Location position the passenger would or will have
     */
    public Location getPosition(Entity passenger) {
        org.bukkit.World w = this.getManager().getWorld();
        Matrix4x4 transform = this.getTransform();
        Vector pyr = transform.getYawPitchRoll();
        return transform.toVector().toLocation(w, (float) pyr.getY(), (float) pyr.getX());
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

    /**
     * Calculates the eye position and direction of a Player passenger inside this seat.
     * If there is no entity inside (that has a first-person view) then this method
     * returns null.
     *
     * @return Eye Location, or null if this seat has no (player) passenger
     */
    public Location getFirstPersonEyeLocation() {
        return firstPerson.getPlayerEyeLocation();
    }

    @Override
    public boolean isHiddenWhenInactive() {
        return false;
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (this._displayedItemEntity != null && this._displayedItemEntity.containsEntityId(entityId)) {
            return true;
        }
        return seated.containsEntityId(entityId);
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

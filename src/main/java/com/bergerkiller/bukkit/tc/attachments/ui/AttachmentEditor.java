package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapDisplayProperties;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode.MenuItem;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AttachmentEditor extends MapDisplay {
    private static final int SNEAK_DEBOUNCE_TICKS = 5;
    public CartProperties editedCart;
    public AttachmentModel model;
    private boolean _hasPermission;
    private int blinkCounter = 0;
    private int sneakCounter = 0;
    private List<Attachment> _lastSelectedAttachments = new ArrayList<>();

    private MapWidgetWindow window = new MapWidgetWindow();
    private MapWidgetAttachmentTree tree = new MapWidgetAttachmentTree() {
        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (!updateSneakWalking(event)) {
                super.onKeyPressed(event);
            }
        }

        @Override
        public void onMenuOpen(MapWidgetAttachmentNode node, MenuItem menu) {
            if (node.checkModifyPermissions()) {
                AttachmentEditor.this.addWidget(menu.createMenu(node));
            }
        }
    };

    public MapDisplayProperties getProperties() {
        return this.properties;
    }

    @Override
    public void onTick() {
        Player player = this.getViewers().get(0);

        // Allow walking around when sneaking
        if (this.sneakCounter > 0) {
            if (player.isSneaking()) {
                this.sneakCounter = SNEAK_DEBOUNCE_TICKS;
            } else if (--this.sneakCounter == 0) {
                this.setReceiveInputWhenHolding(true);
            }
        }

        // If permission changes, reload
        if (this._hasPermission != Permission.COMMAND_GIVE_EDITOR.has(getOwners().get(0))) {
            this.setRunning(false);
            this.setRunning(true);
            return;
        }

        // If edited cart can no longer be edited, reload
        if (this.editedCart != null && editedCart.isRemoved()) {
            this.setRunning(false);
            this.setRunning(true);
            return;
        }

        // Refresh which live attachments are selected & blinking
        this.syncSelectedLiveAttachments();

        // Update blink counter, toggle between showing focused and not
        if (!this._lastSelectedAttachments.isEmpty()) {
            this.blinkCounter++;
            FocusMode nextMode = FocusMode.fromPhase(this.blinkCounter);
            if (nextMode != null) {
                updateFocus(nextMode);
                if (nextMode == FocusMode.NONE) {
                    this.blinkCounter = 0; // Loop
                }
            }
        }
    }

    public boolean updateSneakWalking(MapKeyEvent event) {
        if (event.getKey() == Key.BACK) {
            MapWidget activated = this.getActivatedWidget();

            // Ignore this action while changing order of an item
            if (activated instanceof MapWidgetAttachmentNode && ((MapWidgetAttachmentNode) activated).isChangingOrder()) {
                return false;
            }

            if ((activated == this.getRootWidget()) ||
                (activated == this.tree) ||
                (activated instanceof MapWidgetAttachmentNode))
            {
                if (TCConfig.enableSneakingInAttachmentEditor) {
                    this.sneakCounter = SNEAK_DEBOUNCE_TICKS;
                    this.setReceiveInputWhenHolding(false);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        this.updateSneakWalking(event);
    }

    @Override
    public void onStatusChanged(MapStatusEvent event) {
        if (this.tree.getDisplay() == null) {
            return; // Detached, do nothing
        }
        if (event.isName("changed") || event.isName("sync")) {
            this.tree.sync();

            // Focus the attachment. This makes sure invisible attachments,
            // like hitbox, are highlighted while moving.
            this.pauseBlinking(FocusMode.SELECTED, 5);
        } else if (event.isName("reset")) {
            // Completely re-initialize the model
            this.tree.updateView();
            this.tree.sync();

            // Do not blink for a little while, with focused=false
            this.pauseBlinking(FocusMode.NONE, 30);
        }
    }

    private void syncSelectedLiveAttachments() {
        LogicUtil.synchronizeList(
                this._lastSelectedAttachments,
                this.tree.getSelectedNode().getAttachments(),
                new LogicUtil.ItemSynchronizer<Attachment, Attachment>() {
                    @Override
                    public boolean isItem(Attachment o, Attachment o2) {
                        return o == o2;
                    }

                    @Override
                    public Attachment onAdded(Attachment added) {
                        FocusMode.fromCounter(blinkCounter).applyTo(added);
                        return added;
                    }

                    @Override
                    public void onRemoved(Attachment removed) {
                        removed.setFocused(false);
                        setChildrenFocused(removed, false);
                    }
                });
    }

    public void onSelectedNodeChanged() {
        if (this.getFocusedWidget() instanceof MapWidgetAttachmentNode) {
            this.pauseBlinking(FocusMode.SELECTED, 2);
        } else {
            this.pauseBlinking(FocusMode.NONE, 30);
        }
    }

    @Override
    public void onAttached() {
        this.setGlobal(false);
        this.setUpdateWithoutViewers(false);
        this.setSessionMode(MapSessionMode.HOLDING);
        this.setMasterVolume(0.3f);
        this.reload();
    }

    @Override
    public void onDetached() {
        // Deactivate whatever menu is active right now before detaching all child widgets
        // This makes sure status events can be handled by this editor without running into NPEs
        this.getRootWidget().deactivate();

        // Make sure previously selected attachments are not focused
        this.updateFocus(FocusMode.NONE);
        this._lastSelectedAttachments.clear();
    }

    /**
     * Gets the properties being edited. Null if no cart is being edited,
     * or a model configuration is being edited
     *
     * @return edited cart properties
     */
    public CartProperties getEditedCartProperties() {
        return this.editedCart;
    }

    /**
     * Gets the cart being edited. Null if no cart is being edited, or the
     * cart is not loaded, or a model configuration is being edited
     *
     * @return edited cart
     */
    public MinecartMember<?> getEditedCart() {
        return this.editedCart == null ? null : this.editedCart.getHolder();
    }

    /**
     * Gets whether the editor is editing a saved model configuration
     *
     * @return True if editing a saved model
     */
    public boolean isEditingSavedModel() {
        return model instanceof SavedAttachmentModel;
    }

    /**
     * Checks if there's any attachment editors active for a particular Player, and if so,
     * reloads it.
     *
     * @param playerUUID UUID of the player
     */
    public static void reloadAttachmentEditorFor(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            // Refresh attachment editor, if open
            AttachmentEditor editor = MapDisplay.getHeldDisplay(player, AttachmentEditor.class);
            if (editor != null) {
                editor.reload();
            }
        }
    }

    /**
     * Reloads the editor. Happens when switching between carts being edited.
     */
    public void reload() {
        this.clearWidgets();

        this.window = new MapWidgetWindow();
        this.window.setBounds(0, 0, getWidth(), getHeight());
        this.window.getTitle().setText("Attachment Editor");
        this.addWidget(this.window);

        this._hasPermission = Permission.COMMAND_GIVE_EDITOR.has(getOwners().get(0));
        if (!this._hasPermission) {
            this.setReceiveInputWhenHolding(false);
            this.editedCart = null;
            this.model = AttachmentModel.getDefaultModel(EntityType.MINECART);
            this.window.addWidget(new MapWidgetText())
                .setText("You do not have\npermission!")
                .setColor(MapColorPalette.COLOR_RED)
                .setShadowColor(MapColorPalette.getSpecular(MapColorPalette.COLOR_RED, 0.5f))
                .setPosition(20, 60);
        } else {
            TrainCarts traincarts = TrainCarts.plugin;
            Player owner = this.getOwners().get(0);
            TrainCartsPlayer tcOwner = traincarts.getPlayer(owner);

            SavedAttachmentModel editedModel = tcOwner.getEditedModelInit();
            if (editedModel != null) {
                this.editedCart = null;
                this.sneakCounter = owner.isSneaking() ? SNEAK_DEBOUNCE_TICKS : 0;
                this.setReceiveInputWhenHolding(this.sneakCounter == 0);
                this.model = editedModel;
                this.tree.setModel(this.model);
                this.tree.setBounds(5, 13, 7 * 17, 6 * 17);
                this.window.getTitle().setText("Attachment Model Editor");
                this.window.setBackgroundColor(MapColorPalette.getColor(54, 168, 176));
                this.window.addWidget(this.tree);
            } else if ((this.editedCart = tcOwner.getEditedCart()) != null) {
                this.sneakCounter = owner.isSneaking() ? SNEAK_DEBOUNCE_TICKS : 0;
                this.setReceiveInputWhenHolding(this.sneakCounter == 0);
                this.model = this.editedCart.getModel();
                this.tree.setModel(this.model);
                this.tree.setBounds(5, 13, 7 * 17, 6 * 17);
                this.window.addWidget(this.tree);
            } else {
                this.setReceiveInputWhenHolding(false);
                this.model = AttachmentModel.getDefaultModel(EntityType.MINECART);

                this.window.addWidget(new MapWidgetText())
                    .setText("Please select the\nMinecart to edit!")
                    .setColor(MapColorPalette.COLOR_RED)
                    .setShadowColor(MapColorPalette.getSpecular(MapColorPalette.COLOR_RED, 0.5f))
                    .setPosition(20, 60);
            }
        }
    }

    private static void setChildrenFocused(Attachment attachment, boolean focused) {
        if (attachment == null) {
            return;
        }
        for (Attachment child : attachment.getChildren()) {
            child.setFocused(focused);
            setChildrenFocused(child, focused);
        }
    }

    @Override
    public boolean onItemDrop(Player player, ItemStack item) {
        if (item == null) {
            return false;
        }

        MapWidget activated = this.getActivatedWidget();
        return (activated instanceof ItemDropTarget) ?
                ((ItemDropTarget) activated).acceptItem(item) : false;
    }

    private void pauseBlinking(FocusMode mode, int time) {
        this.updateFocus(mode);
        this.blinkCounter = -time;
    }

    private void updateFocus(FocusMode mode) {
        this._lastSelectedAttachments.forEach(mode::applyTo);
    }

    private static enum FocusMode {
        SELECTED(10),
        SELECTED_AND_CHILDREN(12),
        NONE(20);

        public final int phase;

        private FocusMode(int phase) {
            this.phase = phase;
        }

        public void applyTo(Attachment attachment) {
            switch (this) {
                case NONE:
                    HelperMethods.setFocusedRecursive(attachment, false);
                    break;
                case SELECTED:
                    attachment.setFocused(true);
                    break;
                case SELECTED_AND_CHILDREN:
                    HelperMethods.setFocusedRecursive(attachment, true);
                    break;
            }
        }

        public static FocusMode fromPhase(int phase) {
            for (FocusMode mode : values()) {
                if (mode.phase == phase) {
                    return mode;
                }
            }
            return null;
        }

        public static FocusMode fromCounter(int counter) {
            FocusMode result = SELECTED;
            for (FocusMode mode : values()) {
                if (mode.phase > counter) {
                    break;
                } else {
                    result = mode;
                }
            }
            return result;
        }
    }
}

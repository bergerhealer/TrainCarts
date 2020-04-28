package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
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
import com.bergerkiller.bukkit.tc.properties.CartProperties;

public class AttachmentEditor extends MapDisplay {
    private static final int SNEAK_DEBOUNCE_TICKS = 5;
    public CartProperties editedCart;
    public AttachmentModel model;
    private boolean _hasPermission;
    private int blinkCounter = 0;
    private int sneakCounter = 0;
    private Attachment _lastSelectedAttachment = null;

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
            AttachmentEditor.this.addWidget(menu.createMenu(node));
        }
    };

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
        }

        // Update blink counter, toggle between showing focused and not
        if (this._lastSelectedAttachment != null) {
            this.blinkCounter++;
            if (this.blinkCounter == 10) {
                updateFocus(FocusMode.SELECTED);
            } else if (this.blinkCounter == 12) {
                updateFocus(FocusMode.SELECTED_AND_CHILDREN);
            } else if (this.blinkCounter == 20) {
                updateFocus(FocusMode.NONE);
                this.blinkCounter = 0;
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
        if (event.isName("changed") || event.isName("changed_silent")) {
            MapWidgetAttachmentNode node = event.getArgument(MapWidgetAttachmentNode.class);
            if (node != null) {
                this.tree.updateModelNode(node, !event.isName("changed_silent"));
            } else {
                this.tree.updateModel(!event.isName("changed_silent"));
            }
        } else if (event.isName("reset")) {
            // Completely re-initialize the model
            this.tree.updateView();
            this.tree.updateModel(true);

            // Do not blink for a little while, with focused=false
            this.pauseBlinking(FocusMode.NONE, 30);
        }
    }

    public void onSelectedNodeChanged() {
        Attachment attachment = this.tree.getSelectedNode().getAttachment();
        if (attachment != this._lastSelectedAttachment && this._lastSelectedAttachment != null) {
            this._lastSelectedAttachment.setFocused(false);
            setChildrenFocused(this._lastSelectedAttachment, false);
        }
        this._lastSelectedAttachment = attachment;

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
            this.editedCart = CartProperties.getEditing(this.getOwners().get(0));
            if (this.editedCart != null) {
                this.sneakCounter = this.getOwners().get(0).isSneaking() ? SNEAK_DEBOUNCE_TICKS : 0;
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

    public boolean acceptItem(ItemStack item) {
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
        if (this._lastSelectedAttachment != null) {
            switch (mode) {
            case NONE:
                HelperMethods.setFocusedRecursive(this._lastSelectedAttachment, false);
                break;
            case SELECTED:
                this._lastSelectedAttachment.setFocused(true);
                break;
            case SELECTED_AND_CHILDREN:
                HelperMethods.setFocusedRecursive(this._lastSelectedAttachment, true);
                break;
            }
        }
    }

    private static enum FocusMode {
        NONE, SELECTED, SELECTED_AND_CHILDREN
    }
}

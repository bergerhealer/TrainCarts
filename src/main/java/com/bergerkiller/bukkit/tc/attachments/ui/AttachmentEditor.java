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
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode.MenuItem;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

public class AttachmentEditor extends MapDisplay {
    public CartProperties editedCart;
    public AttachmentModel model;
    private boolean sneakWalking = false;
    private boolean _hasPermission;
    private int blinkCounter = 0;
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
        if (this.sneakWalking && !player.isSneaking()) {
            this.sneakWalking = false;
            this.setReceiveInputWhenHolding(true);
        }

        // If permission changes, reload
        if (this._hasPermission != Permission.COMMAND_GIVE_EDITOR.has(getOwners().get(0))) {
            this.setRunning(false);
            this.setRunning(true);
        }

        // Update blink counter, toggle between showing focused and not
        if (this._lastSelectedAttachment != null) {
            if (++this.blinkCounter > 6) {
                this._lastSelectedAttachment.setFocused(!this._lastSelectedAttachment.isFocused());
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
                this.setReceiveInputWhenHolding(false);
                getOwners().get(0).setSneaking(true);
                this.sneakWalking = true;
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
        if (event.isName("changed")) {
            MapWidgetAttachmentNode node = event.getArgument(MapWidgetAttachmentNode.class);
            if (node != null) {
                this.tree.updateModelNode(node);
            } else {
                this.tree.updateModel();
            }
        } else if (event.isName("reset")) {
            // Completely re-initialize the model
            this.tree.updateView();
            this.tree.updateModel();

            // Do not blink for a little while, with focused=false
            this.pauseBlinking(false);
        }
    }

    public void onSelectedNodeChanged() {
        Attachment attachment = this.tree.getSelectedNode().getAttachment();
        if (attachment != this._lastSelectedAttachment && this._lastSelectedAttachment != null) {
            this._lastSelectedAttachment.setFocused(false);
        }
        this._lastSelectedAttachment = attachment;
        this.pauseBlinking(this.getFocusedWidget() instanceof MapWidgetAttachmentNode);
    }

    private void pauseBlinking(boolean focused) {
        if (this._lastSelectedAttachment != null) {
            this._lastSelectedAttachment.setFocused(focused);
            this.blinkCounter = -30;
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
        if (this._lastSelectedAttachment != null) {
            this._lastSelectedAttachment.setFocused(false);
            this._lastSelectedAttachment = null;
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
            this.editedCart = CartProperties.getEditing(this.getOwners().get(0));
            if (this.editedCart != null) {
                this.sneakWalking = this.getOwners().get(0).isSneaking();
                this.setReceiveInputWhenHolding(!this.sneakWalking);
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

    public boolean acceptItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        MapWidget activated = this.getActivatedWidget();
        return (activated instanceof ItemDropTarget) ?
                ((ItemDropTarget) activated).acceptItem(item) : false;
    }
}

package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode.MenuItem;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.AppearanceMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.GeneralMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PhysicalMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

public class AttachmentEditor extends MapDisplay {
    public AttachmentModel model;

    private MapWidgetAttachmentTree tree = new MapWidgetAttachmentTree() {
        @Override
        public void onMenuOpen(MapWidgetAttachmentNode node, MenuItem menu) {
            if (menu == MenuItem.APPEARANCE) {
                AttachmentEditor.this.addWidget(new AppearanceMenu(node));
            } else if (menu == MenuItem.POSITION) {
                AttachmentEditor.this.addWidget(new PositionMenu(node));
            } else if (menu == MenuItem.GENERAL) {
                AttachmentEditor.this.addWidget(new GeneralMenu(node));
            } else if (menu == MenuItem.PHYSICAL) {
                AttachmentEditor.this.addWidget(new PhysicalMenu(node));
            }
        }
    };

    @Override
    public void onTick() {
        //this.getWidgets().get(new Random().nextInt(this.getWidgets().size())).focus();
        CartProperties.getEditing(this.getViewers().get(0));
    }

    @Override
    public void onStatusChanged(MapStatusEvent event) {
        if (event.isName("changed")) {
            this.tree.updateModel();
        } else if (event.isName("reset")) {
            // Completely re-initialize the model
            this.tree.updateView();
            this.tree.updateModel();
        }
    }

    @Override
    public void onAttached() {
        this.setGlobal(false);
        this.setUpdateWithoutViewers(false);
        this.setSessionMode(MapSessionMode.HOLDING);
        this.setMasterVolume(0.3f);

        MapWidgetWindow window = new MapWidgetWindow();
        window.setBounds(0, 0, getWidth(), getHeight());
        window.getTitle().setText("Attachment Editor");
        this.addWidget(window);

        CartProperties prop = CartProperties.getEditing(this.getOwners().get(0));
        if (prop != null) {
            this.model = prop.getModel();
            this.tree.setModel(this.model);
            window.addWidget(this.tree.setBounds(5, 13, 7 * 17, 6 * 17));

            this.setReceiveInputWhenHolding(true);
        } else {
            this.model = AttachmentModel.getDefaultModel(EntityType.MINECART);

            window.addWidget(new MapWidgetText())
                .setText("Please select the\nMinecart to edit!")
                .setColor(MapColorPalette.COLOR_RED)
                .setShadowColor(MapColorPalette.getSpecular(MapColorPalette.COLOR_RED, 0.5f))
                .setPosition(20, 60);
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

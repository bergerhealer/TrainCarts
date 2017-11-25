package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;

public class GeneralMenu extends MapWidgetWindow {
    private final MapWidgetAttachmentNode attachment;

    public GeneralMenu(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 15, 118, 104);
        this.setDepthOffset(4);
        this.setFocusable(true);
        this.setBackgroundColor(MapColorPalette.COLOR_YELLOW);
    }

    @Override
    public void onAttached() {
        this.activate();

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                MapWidgetAttachmentNode created = attachment.addAttachment();
                created.setType(CartAttachmentType.ITEM);
                created.getConfig().set("item", new ItemStack(Material.WOOD));
                GeneralMenu.this.deactivate();
            }
        }).setText("Add Attachment").setBounds(10, 10, 90, 18);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                GeneralMenu.this.deactivate();
            }
        }).setText("Change order").setBounds(10, 40, 90, 18);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                attachment.remove();
                GeneralMenu.this.deactivate();
            }
        }).setText("Delete").setBounds(10, 70, 90, 18).setEnabled(attachment.getParentAttachment() != null);
    }

    @Override
    public void onDeactivate() {
        this.removeWidget();
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("position");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}

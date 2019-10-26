package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import org.bukkit.inventory.ItemStack;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.ConfirmAnimationDeleteDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.general.ConfirmAttachmentDeleteDialog;

public class GeneralMenu extends MapWidgetMenu {

    public GeneralMenu() {
        this.setBounds(5, 15, 118, 104);
        this.setBackgroundColor(MapColorPalette.COLOR_YELLOW);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                ConfigurationNode config = new ConfigurationNode();
                config.set("type", CartAttachmentType.ITEM);
                config.set("item", new ItemStack(getMaterial("LEGACY_WOOD")));
                MapWidgetAttachmentNode added = attachment.addAttachment(config);
                GeneralMenu.this.close();
                attachment.getTree().setSelectedNode(added);
            }
        }).setText("Add Attachment").setBounds(10, 10, 98, 18);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                attachment.setChangingOrder(true);
                GeneralMenu.this.close();
            }
        }).setText("Change order").setBounds(10, 30, 98, 18);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                GeneralMenu.this.addWidget(new ConfirmAttachmentDeleteDialog() {
                    @Override
                    public void onConfirmDelete() {
                        GeneralMenu.this.attachment.remove();
                        GeneralMenu.this.close();
                    }
                });
            }
        }).setText("Delete").setBounds(10, 50, 98, 18).setEnabled(attachment.getParentAttachment() != null);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                int index = attachment.getParentAttachment().getAttachments().indexOf(attachment);
                MapWidgetAttachmentNode addedNode;
                addedNode = attachment.getParentAttachment().addAttachment(index+1, attachment.getFullConfig());
                attachment.getTree().setSelectedNode(addedNode);
                GeneralMenu.this.close();
            }
        }).setText("Duplicate").setBounds(10, 70, 98, 18).setEnabled(attachment.getParentAttachment() != null);
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("position");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}

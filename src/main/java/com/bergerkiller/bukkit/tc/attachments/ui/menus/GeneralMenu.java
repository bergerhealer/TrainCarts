package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.general.ModelStorageTypeSelectionDialog;
import org.bukkit.inventory.ItemStack;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentItem;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.general.ConfirmAttachmentDeleteDialog;

public class GeneralMenu extends MapWidgetMenu {

    public GeneralMenu() {
        this.setBounds(5, 15, 118, 104);
        this.setBackgroundColor(MapColorPalette.COLOR_YELLOW);
    }

    private void addAndSelectAttachment(ConfigurationNode newAttachmentConfig) {
        MapWidgetAttachmentNode added = attachment.addAttachment(newAttachmentConfig);
        GeneralMenu.this.close();
        attachment.getTree().setSelectedNode(added);
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "reset");
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                ConfigurationNode config = new ConfigurationNode();
                AttachmentTypeRegistry.instance().toConfig(config, CartAttachmentItem.TYPE);
                config.set("item", new ItemStack(getMaterial("LEGACY_WOOD")));
                addAndSelectAttachment(config);
            }
        }).setText("Add Attachment").setBounds(10, 10, 85, 14);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                GeneralMenu.this.addWidget(new ModelStorageTypeSelectionDialog.LoadDialog() {
                    @Override
                    public void onConfigLoaded(ConfigurationNode attachmentConfig) {
                        addAndSelectAttachment(attachmentConfig);
                    }
                });
            }
        }).setText("V").setBounds(96, 10, 12, 14);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                attachment.setChangingOrder(true);
                GeneralMenu.this.close();
            }
        }).setText("Change order").setBounds(10, 27, 98, 14);

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
        }).setText("Delete").setBounds(10, 44, 98, 14).setEnabled(attachment.getParentAttachment() != null);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                int index = attachment.getParentAttachment().getChildAttachmentNodes().indexOf(attachment);
                MapWidgetAttachmentNode addedNode;
                addedNode = attachment.getParentAttachment().addAttachment(index+1, attachment.getConfig().clone());
                attachment.getTree().setSelectedNode(addedNode);
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "reset");
                GeneralMenu.this.close();
            }
        }).setText("Duplicate").setBounds(10, 61, 98, 14).setEnabled(attachment.getParentAttachment() != null);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                GeneralMenu.this.addWidget(new ModelStorageTypeSelectionDialog.SaveDialog(attachment.getConfig()) {
                    @Override
                    public void onExported() {
                        GeneralMenu.this.close();
                    }
                });
            }
        }).setText("Save").setBounds(10, 78, 48, 14);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                GeneralMenu.this.addWidget(new ModelStorageTypeSelectionDialog.LoadDialog() {
                    @Override
                    public void onConfigLoaded(ConfigurationNode attachmentConfig) {
                        GeneralMenu.this.attachment.getConfig().setTo(attachmentConfig);
                        GeneralMenu.this.close();
                    }
                });
            }
        }).setText("Load").setBounds(60, 78, 48, 14);
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}

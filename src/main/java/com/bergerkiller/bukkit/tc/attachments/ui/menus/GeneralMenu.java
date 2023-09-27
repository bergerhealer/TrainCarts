package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.general.ModelStorageTypeSelectionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.general.NameAttachmentDialog;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.BasicModularConfiguration;
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

import java.util.Collections;

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
        }).setText("Add Attachment").setBounds(10, 8, 85, 13);

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
        }).setText("V").setBounds(96, 8, 12, 13);

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
        }).setText("Duplicate").setBounds(10, 23, 98, 13).setEnabled(attachment.getParentAttachment() != null);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                GeneralMenu.this.addWidget(new NameAttachmentDialog(attachment));
            }
        }).setText("Name").setBounds(10, 38, 98, 13);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                attachment.setChangingOrder(true);
                GeneralMenu.this.close();
            }
        }).setText("Change Order").setBounds(10, 53, 98, 13);

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
        }).setText("Delete").setBounds(10, 68, 98, 13).setEnabled(attachment.getParentAttachment() != null);

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
        }).setText("Save").setBounds(10, 83, 48, 13);

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                GeneralMenu.this.addWidget(new ModelStorageTypeSelectionDialog.LoadDialog() {
                    @Override
                    public void onConfigLoaded(ConfigurationNode attachmentConfig) {
                        GeneralMenu.this.attachment.getConfig().setToExcept(attachmentConfig,
                                Collections.singleton(BasicModularConfiguration.KEY_SAVED_NAME));
                        GeneralMenu.this.close();
                    }
                });
            }
        }).setText("Load").setBounds(60, 83, 48, 13);
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}

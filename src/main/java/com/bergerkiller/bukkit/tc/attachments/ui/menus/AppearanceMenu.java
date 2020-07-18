package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.map.widgets.*;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.ui.ItemDropTarget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class AppearanceMenu extends MapWidgetMenu implements ItemDropTarget {
    private final MapWidgetTabView tabView = new MapWidgetTabView();
    private AttachmentTypeRegistry typeRegistry;
    private List<AttachmentType> attachmentTypeList;

    public AppearanceMenu() {
        this.setBounds(5, 15, 118, 104);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.typeRegistry = AttachmentTypeRegistry.instance();
        this.attachmentTypeList = this.typeRegistry.all();

        // Tab view widget to switch between different appearance editing modes
        // The order of these tabs is important, and must match the order in attachmentTypeList!
        for (AttachmentType type : this.attachmentTypeList) {
            type.createAppearanceTab(this.tabView.addTab(), this.attachment);
        }

        tabView.setPosition(9, 16);
        this.addWidget(tabView);

        // This widget is always visible: type selector
        MapWidgetSelectionBox typeSelectionBox = this.addWidget(new MapWidgetSelectionBox() {
            @Override
            public void onSelectedItemChanged() {
                int index = this.getSelectedIndex();
                if (index >= 0 && index < attachmentTypeList.size()) {
                    setType(attachmentTypeList.get(index));
                }
            }
        });

        AttachmentType selected = this.typeRegistry.fromConfig(getAttachment().getConfig());
        for (AttachmentType type : this.attachmentTypeList) {
            typeSelectionBox.addItem(type.getName());
            if (selected != null && selected.getID().equalsIgnoreCase(type.getID())) {
                typeSelectionBox.setSelectedIndex(typeSelectionBox.getItemCount() - 1);
            }
        }
        typeSelectionBox.setBounds(9, 3, 100, 11);

        // Set to display currently selected type

        setType(selected);
        typeSelectionBox.focus();
    }

    public void setType(AttachmentType type) {
        if (this.typeRegistry.fromConfig(getAttachment().getConfig()) != type) {
            this.typeRegistry.toConfig(getAttachment().getConfig(), type);
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            getAttachment().resetIcon();
        }

        // Switch tab
        for (int i = 0; i < this.attachmentTypeList.size(); i++) {
            if (this.attachmentTypeList.get(i).getID().equalsIgnoreCase(type.getID())) {
                this.tabView.setSelectedIndex(i);
                break;
            }
        }
    }

    @Override
    public boolean acceptItem(ItemStack item) {
        for (MapWidget widget : this.tabView.getSelectedTab().getWidgets()) {
            if (widget instanceof ItemDropTarget && ((ItemDropTarget) widget).acceptItem(item)) {
                return true;
            }
        }
        return false;
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig();
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }
}

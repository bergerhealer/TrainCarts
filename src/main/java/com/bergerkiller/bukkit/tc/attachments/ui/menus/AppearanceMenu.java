package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.map.widgets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.ui.ItemDropTarget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class AppearanceMenu extends MapWidgetMenu implements ItemDropTarget {
    private final MapWidgetTabView tabView = new MapWidgetTabView();
    private AttachmentTypeRegistry typeRegistry;
    private List<TypePage> pages;

    public AppearanceMenu() {
        this.setBounds(5, 15, 118, 104);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.typeRegistry = AttachmentTypeRegistry.instance();

        // Initialize pages
        {
            List<AttachmentType> types = this.typeRegistry.all();
            pages = new ArrayList<TypePage>(types.size());
            for (AttachmentType type : types) {
                boolean listed = true;
                for (Player player : this.display.getOwners()) {
                    if (!type.isListed(player)) {
                        listed = false;
                        break;
                    }
                }
                if (listed) {
                    pages.add(new TypePage(type, this.tabView.addTab()));
                }
            }
        }

        tabView.setPosition(9, 16);
        this.addWidget(tabView);

        // This widget is always visible: type selector
        MapWidgetSelectionBox typeSelectionBox = this.addWidget(new MapWidgetSelectionBox() {
            @Override
            public void onSelectedItemChanged() {
                int index = this.getSelectedIndex();
                if (index >= 0 && index < pages.size()) {
                    setPage(pages.get(index));
                }
            }
        });

        AttachmentType selected = this.typeRegistry.fromConfig(getAttachment().getConfig());
        for (TypePage page : pages) {
            typeSelectionBox.addItem(page.type.getName());
            if (selected != null && selected.getID().equalsIgnoreCase(page.type.getID())) {
                typeSelectionBox.setSelectedIndex(typeSelectionBox.getItemCount() - 1);
            }
        }
        typeSelectionBox.setBounds(9, 3, 100, 11);

        // Set to display currently selected type (if index = 0)
        setType(selected);
        typeSelectionBox.focus();
    }

    public void setType(AttachmentType type) {
        for (TypePage page : pages) {
            if (page.type == type) {
                setPage(page);
                return;
            }
        }

        // Not found! Weird.
        setPage(pages.get(0));
    }

    private void setPage(TypePage page) {
        // Sync the type to the configuration of the attachment
        if (this.typeRegistry.fromConfig(getAttachment().getConfig()) != page.type) {
            this.typeRegistry.toConfig(getAttachment().getConfig(), page.type);
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            getAttachment().resetIcon();
        }

        // Render appearance tab for the first time, if needed
        if (!page.appearanceCreated) {
            page.appearanceCreated = true;
            try {
                page.type.migrateConfiguration(attachment.getConfig());
            } catch (Throwable t) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE,
                        "Failed to migrate attachment configuration of " + page.type.getName(), t);
            }
            try {
                page.type.createAppearanceTab(page.tab, attachment);
            } catch (Throwable t) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE,
                        "Failed to display appearance tab for " + page.type.getName(), t);
                page.tab.clear();
                page.tab.addWidget(new MapWidgetText())
                    .setText("An error occurred!")
                    .setColor(MapColorPalette.COLOR_RED)
                    .setPosition(5, 5);
            }
        }

        // Switch tab
        page.tab.select();
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

    private static class TypePage {
        public final AttachmentType type;
        public final MapWidgetTabView.Tab tab;
        public boolean appearanceCreated;

        public TypePage(AttachmentType type, MapWidgetTabView.Tab tab) {
            this.type = type;
            this.tab = tab;
            this.appearanceCreated = false;
        }
    }
}

package com.bergerkiller.bukkit.tc.attachments.ui.entity;

import java.util.ArrayList;
import java.util.Collections;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentEntity;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

/**
 * Switch between different supported attachment entity types
 */
public class MapWidgetEntityTypeList extends MapWidget {
    private final MapWidgetSelectionBox selector = new MapWidgetSelectionBox() {
        @Override
        public void onSelectedItemChanged() {
            onEntityTypeChanged();
        }
    };

    @Override
    public void onAttached() {
        selector.clearItems();

        ArrayList<String> items = new ArrayList<String>();
        for (EntityType type : EntityType.values()) {
            if (CartAttachmentEntity.isEntityTypeSupported(type)) {
                items.add(type.toString());
            }
        }
        Collections.sort(items);
        for (String item : items) {
            selector.addItem(item);
        }

        this.addWidget(selector);
    }

    @Override
    public void onBoundsChanged() {
        selector.setBounds(0, 0, getWidth(), getHeight());
    }

    public EntityType getEntityType() {
        return ParseUtil.parseEnum(this.selector.getSelectedItem(), EntityType.MINECART);
    }

    public void setEntityType(EntityType entityType) {
        this.selector.setSelectedItem(entityType.toString());
    }

    public void onEntityTypeChanged() {
    }
}

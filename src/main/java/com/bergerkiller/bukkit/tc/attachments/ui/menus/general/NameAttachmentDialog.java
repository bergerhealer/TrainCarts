package com.bergerkiller.bukkit.tc.attachments.ui.menus.general;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNameSet;

import java.util.List;

/**
 * Simple menu dialog popup that allows the player to set one or more unique names to
 * identify this attachment by. These names can then later be used to target a specific
 * attachment using commands, signs or other systems.
 */
public class NameAttachmentDialog extends MapWidgetMenu {

    public NameAttachmentDialog(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 8, 108, 89);
        this.setBackgroundColor(MapColorPalette.getColor(53, 33, 167));
    }

    @Override
    public void onAttached() {
        List<String> setNames = attachment.getConfig().getList("names", String.class);

        this.addWidget(new MapWidgetNameSet() {
            @Override
            public void onItemAdded(String item) {
                attachment.getConfig().getList("names", String.class).add(item);
            }

            @Override
            public void onItemRemoved(String item) {
                List<String> names = attachment.getConfig().getList("names", String.class);
                if (names.remove(item) && names.isEmpty()) {
                    attachment.getConfig().remove("names");
                }
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == MapPlayerInput.Key.BACK && this.isActivated()) {
                    NameAttachmentDialog.this.close();
                } else {
                    super.onKeyPressed(event);
                }
            }
        }).setNewItemText("+++ New Name +++")
          .setNewItemDescription("Add a new name")
          .setItems(attachment.getConfig().getList("names", String.class))
          .setBounds(5, 5, getWidth() - 10, getHeight() - 10)
          .activate();
    }
}

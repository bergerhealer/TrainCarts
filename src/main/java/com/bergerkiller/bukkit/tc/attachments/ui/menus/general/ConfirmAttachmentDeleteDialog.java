package com.bergerkiller.bukkit.tc.attachments.ui.menus.general;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;

/**
 * Dialog asking whether or not it is OK to delete an animation
 */
public class ConfirmAttachmentDeleteDialog extends MapWidgetMenu {

    public ConfirmAttachmentDeleteDialog() {
        this.setBounds(10, 22, 98, 58);
        this.setBackgroundColor(MapColorPalette.getColor(135, 33, 33));
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // Label
        this.addWidget(new MapWidgetText()
                .setText("Are you sure you\nwant to delete\nthis attachment?")
                .setBounds(5, 5, 80, 30));

        // Cancel
        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                ConfirmAttachmentDeleteDialog.this.close();
            }
        }.setText("No").setBounds(10, 40, 36, 13));

        // Yes!
        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                ConfirmAttachmentDeleteDialog.this.close();
                ConfirmAttachmentDeleteDialog.this.onConfirmDelete();
            }
        }.setText("Yes").setBounds(52, 40, 36, 13));
    }

    /**
     * Called when the player specifically said 'yes' to deleting
     */
    public void onConfirmDelete() {
    }
}

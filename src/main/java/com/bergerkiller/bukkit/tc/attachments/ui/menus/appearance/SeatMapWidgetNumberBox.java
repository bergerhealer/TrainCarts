package com.bergerkiller.bukkit.tc.attachments.ui.menus.appearance;

import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

public class SeatMapWidgetNumberBox extends MapWidgetNumberBox {
    private final SeatExitPositionMenu menu;
    private final String field;
    private boolean ignoreValueChange = true;

    public SeatMapWidgetNumberBox(SeatExitPositionMenu menu, String field) {
        this.menu = menu;
        this.field = field;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.setValue(menu.getConfig().get(this.field, 0.0));
        this.ignoreValueChange = false;
    }

    @Override
    public void onValueChanged() {
        if (this.ignoreValueChange) {
            return;
        }

        menu.getConfig().set(this.field, getValue());
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        if (this.getChangeRepeat() <= 1) {
            onValueChangeStart();
        }
    }

    public void onValueChangeStart() {
    }

    @Override
    public void onValueChangeEnd() {
        menu.previewViewer();
    }
}

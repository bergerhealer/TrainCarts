package com.bergerkiller.bukkit.tc.attachments.ui.menus.appearance;

import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

public class SeatMapWidgetNumberBox extends MapWidgetNumberBox {
    private static final int PREVIEW_RATE = 20;
    private final SeatExitPositionMenu menu;
    private final String field;
    private int editCtr = PREVIEW_RATE;

    public SeatMapWidgetNumberBox(SeatExitPositionMenu menu, String field) {
        this.menu = menu;
        this.field = field;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.setValue(menu.getConfig().get(this.field, 0.0));
    }

    @Override
    public void onValueChanged() {
        menu.getConfig().set(this.field, getValue());
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        if (this.getChangeRepeat() <= 1) {
            onValueChangeStart();
            editCtr = PREVIEW_RATE;
        }
        if (++editCtr >= PREVIEW_RATE) {
            editCtr = 0;
            menu.previewViewer();
        }
    }

    public void onValueChangeStart() {
    }

    @Override
    public void onValueChangeEnd() {
        menu.previewViewer();
    }
}

package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;

/**
 * Scroller widget that stores the three different {@link MapWidgetSequencerEffectGroup}
 * widgets.
 */
public class MapWidgetSequencerScroller extends MapWidgetScroller {
    public MapWidgetSequencerEffectGroup startGroup, loopGroup, stopGroup;
    protected int effectSelButtonIndex = 0; // Used by MapWidgetSequencerEffect

    public MapWidgetSequencerScroller() {
        this.setScrollPadding(15);
    }

    @Override
    public void onAttached() {
        startGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(this, MapWidgetSequencerEffectGroup.Mode.START));
        loopGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(this, MapWidgetSequencerEffectGroup.Mode.LOOP));
        stopGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(this, MapWidgetSequencerEffectGroup.Mode.STOP));
        recalculateContainerSize();
    }

    /**
     * Updates the positions of the {@link MapWidgetSequencerEffectGroup} widgets
     * added to this scroller element.
     */
    public void recalculateContainerSize() {
        int y = 0;
        for (MapWidget w : this.getContainer().getWidgets()) {
            w.setPosition(0, y);
            y += w.getHeight() + 2;
        }
        super.recalculateContainerSize();
    }
}

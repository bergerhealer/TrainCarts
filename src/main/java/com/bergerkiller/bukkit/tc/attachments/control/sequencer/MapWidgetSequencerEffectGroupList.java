package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.List;

/**
 * Scroller widget that stores the three different {@link MapWidgetSequencerEffectGroup}
 * widgets. Also contains the main abstract methods for information required.
 */
public abstract class MapWidgetSequencerEffectGroupList extends MapWidgetScroller {
    public MapWidgetSequencerEffectGroup startGroup, loopGroup, stopGroup;
    protected int effectSelButtonIndex = 0; // Used by MapWidgetSequencerEffect

    public MapWidgetSequencerEffectGroupList() {
        this.setScrollPadding(15);
    }

    public abstract ConfigurationNode getConfig();

    /**
     * Gets the names of effect attachments that can be targeted from the sequencer
     * attachment. No need to filter duplicates.
     *
     * @return Sequencer targetable effect attachment names
     */
    public abstract List<String> getEffectNames();

    /**
     * Gets the transfer function host. This is the source of information of
     * transfer function metadata such as available inputs.
     *
     * @return Transfer Function Host
     */
    public abstract TransferFunctionHost getTransferFunctionHost();

    /**
     * Creates an Effect Sink of the effect attachments that have the name specified
     *
     * @param name Name assigned to the effect attachments
     * @return Effect Sink for the group of effect attachments matching this name
     */
    public abstract Attachment.EffectSink createEffectSink(String name);

    @Override
    public void onAttached() {
        ConfigurationNode config = getConfig();
        startGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(
                this, MapWidgetSequencerEffectGroup.Mode.START, config.getNode("start")));
        loopGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(
                this, MapWidgetSequencerEffectGroup.Mode.LOOP, config.getNode("loop")));
        stopGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(
                this, MapWidgetSequencerEffectGroup.Mode.STOP, config.getNode("stop")));
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

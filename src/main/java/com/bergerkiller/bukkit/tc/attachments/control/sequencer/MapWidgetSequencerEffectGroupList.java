package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.List;

/**
 * Scroller widget that stores the three different {@link MapWidgetSequencerEffectGroup}
 * widgets. Also contains the main abstract methods for information required.
 */
public abstract class MapWidgetSequencerEffectGroupList extends MapWidgetScroller {
    public MapWidgetSequencerEffectGroup startGroup, loopGroup, stopGroup;
    protected int effectSelButtonIndex = 1; // Used by MapWidgetSequencerEffect
    private final EffectLoop.Player previewEffectLoopPlayer;

    public MapWidgetSequencerEffectGroupList() {
        this.setScrollPadding(15);
        previewEffectLoopPlayer = TrainCarts.plugin.getEffectLoopPlayerController().createPlayer(20);
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

    /**
     * Gets the effect loop player that can be used to preview effect loops in the UI
     *
     * @return EffectLoop preview player
     */
    public EffectLoop.Player getPreviewEffectLoopPlayer() {
        return previewEffectLoopPlayer;
    }

    @Override
    public void onAttached() {
        ConfigurationNode config = getConfig();
        startGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(this, SequencerMode.START));
        loopGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(this, SequencerMode.LOOP));
        stopGroup = addContainerWidget(new MapWidgetSequencerEffectGroup(this, SequencerMode.STOP));
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

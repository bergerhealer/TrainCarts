package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.List;

/**
 * Scroller widget that stores the three different {@link MapWidgetSequencerEffectGroup}
 * widgets. Also contains the main abstract methods for information required.
 */
public abstract class MapWidgetSequencerConfigurationMenu extends MapWidgetScroller {
    public MapWidgetSequencerEffectGroup startGroup, loopGroup, stopGroup;
    protected int effectSelButtonIndex = 1; // Used by MapWidgetSequencerEffect
    private final EffectLoop.Player previewEffectLoopPlayer;

    public MapWidgetSequencerConfigurationMenu() {
        this.setScrollPadding(15);
        previewEffectLoopPlayer = TrainCarts.plugin.getEffectLoopPlayerController().createPlayer(20);
    }

    public abstract ConfigurationNode getConfig();

    /**
     * Gets the names of effect attachments that can be targeted from the sequencer
     * attachment.
     *
     * @param allSelector Selector with search strategy to find all the effects
     * @return Sequencer targetable effect attachment names
     */
    public abstract List<String> getEffectNames(AttachmentSelector<Attachment.EffectAttachment> allSelector);

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
     * @param effectSelector Selector used to find the effect attachments to target
     * @return Effect Sink for the group of effect attachments matching this name
     */
    public abstract Attachment.EffectSink createEffectSink(AttachmentSelector<Attachment.EffectAttachment> effectSelector);

    /**
     * Gets the current playing status of the sequencer being configured
     *
     * @return Playing status
     */
    public abstract SequencerPlayStatus getPlayStatus();

    /**
     * Sends an instruction to start the sequencer
     */
    public abstract void startPlaying();

    /**
     * Sends an instruction to stop the sequencer
     */
    public abstract void stopPlaying();

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
        addContainerWidget(new MapWidgetSequencerTopHeader()).setSize(getWidth(), 7);
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
            if (w instanceof MapWidgetSequencerTopHeader) {
                y++;
            }
        }
        super.recalculateContainerSize();
    }
}

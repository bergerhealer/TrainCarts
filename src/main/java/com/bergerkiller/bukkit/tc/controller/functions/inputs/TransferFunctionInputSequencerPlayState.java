package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSequencer;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.function.Function;

public class TransferFunctionInputSequencerPlayState extends TransferFunctionInput {
    public static final Serializer<TransferFunctionInputSequencerPlayState> SERIALIZER = new Serializer<TransferFunctionInputSequencerPlayState>() {
        @Override
        public String typeId() {
            return "INPUT-SEQUENCER-PLAY-STATE";
        }

        @Override
        public String title() {
            return "In: Play State";
        }

        @Override
        public boolean isInput() {
            return true;
        }

        @Override
        public boolean isListed(TransferFunctionHost host) {
            return host.isSequencer();
        }

        @Override
        public TransferFunctionInputSequencerPlayState createNew(TransferFunctionHost host) {
            TransferFunctionInputSequencerPlayState function = new TransferFunctionInputSequencerPlayState(Mode.SPEED);
            function.updateSource(host);
            return function;
        }

        @Override
        public TransferFunctionInputSequencerPlayState load(TransferFunctionHost host, ConfigurationNode config) {
            Mode mode = config.getOrDefault("mode", Mode.SPEED);
            TransferFunctionInputSequencerPlayState function = new TransferFunctionInputSequencerPlayState(mode);
            function.updateSource(host);
            return function;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInputSequencerPlayState function) {
            config.set("mode", function.getMode());
        }
    };

    private Mode mode;

    public TransferFunctionInputSequencerPlayState(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        Attachment attachment = host.getAttachment();
        if (attachment instanceof CartAttachmentSequencer) {
            return mode.createReferencedSource((CartAttachmentSequencer) attachment);
        } else {
            return ReferencedSource.NONE;
        }
    }

    @Override
    protected TransferFunctionInput cloneInput() {
        return new TransferFunctionInputSpeed();
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN, mode.previewTitle());
    }

    @Override
    public void openDialog(Dialog dialog) {
        super.openDialog(dialog);

        dialog.addWidget(new MapWidgetSelectionBox() {
            private boolean loading = false;

            @Override
            public void onAttached() {
                loading = true;

                for (Mode mode : Mode.values()) {
                    addItem(mode.title());
                    if (mode == TransferFunctionInputSequencerPlayState.this.mode) {
                        setSelectedIndex(getItemCount() - 1);
                    }
                }

                super.onAttached();
                loading = false;
            }

            @Override
            public void onSelectedItemChanged() {
                if (!loading && getSelectedIndex() >= 0 && getSelectedIndex() < Mode.values().length) {
                    setMode(Mode.values()[getSelectedIndex()]);
                    dialog.markChanged();
                }
            }
        }).setBounds(4, 18, dialog.getWidth() - 8, 11);
    }

    private static class EffectOptionsVolumeReferencedSource extends ReferencedSource {
        private final CartAttachmentSequencer sequencer;

        public EffectOptionsVolumeReferencedSource(CartAttachmentSequencer sequencer) {
            this.sequencer = sequencer;
        }

        @Override
        public void onTick() {
            value = sequencer.getCurrentPlayOptions().volume();
        }

        @Override
        public boolean isTickedDuringPlay() {
            return true;
        }

        @Override
        public boolean isBool() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EffectOptionsVolumeReferencedSource;
        }
    }

    private static class EffectOptionsSpeedReferencedSource extends ReferencedSource {
        private final CartAttachmentSequencer sequencer;

        public EffectOptionsSpeedReferencedSource(CartAttachmentSequencer sequencer) {
            this.sequencer = sequencer;
        }

        @Override
        public void onTick() {
            value = sequencer.getCurrentPlayOptions().speed();
        }

        @Override
        public boolean isTickedDuringPlay() {
            return true;
        }

        @Override
        public boolean isBool() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EffectOptionsSpeedReferencedSource;
        }
    }

    private static class ProgressionReferencedSource extends ReferencedSource {
        private final CartAttachmentSequencer sequencer;

        public ProgressionReferencedSource(CartAttachmentSequencer sequencer) {
            this.sequencer = sequencer;
        }

        @Override
        public void onTick() {
            value = sequencer.getProgression();
        }

        @Override
        public boolean isTickedDuringPlay() {
            return true;
        }

        @Override
        public boolean isBool() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ProgressionReferencedSource;
        }
    }

    public enum Mode {
        VOLUME("Volume", "<Play Volume>", EffectOptionsVolumeReferencedSource::new),
        SPEED("Speed", "<Play Speed>", EffectOptionsSpeedReferencedSource::new),
        PROGRESSION("Progression", "<Play Progress>", ProgressionReferencedSource::new);

        private final String title;
        private final String previewTitle;
        private final Function<CartAttachmentSequencer, ReferencedSource> sourceFactory;

        Mode(String title, String previewTitle, Function<CartAttachmentSequencer, ReferencedSource> sourceFactory) {
            this.title = title;
            this.previewTitle = previewTitle;
            this.sourceFactory = sourceFactory;
        }

        public String title() {
            return title;
        }

        public String previewTitle() {
            return previewTitle;
        }

        public ReferencedSource createReferencedSource(CartAttachmentSequencer sequencer) {
            return sourceFactory.apply(sequencer);
        }
    }
}

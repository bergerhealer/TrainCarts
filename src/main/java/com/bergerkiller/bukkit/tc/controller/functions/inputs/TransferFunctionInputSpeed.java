package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import org.bukkit.util.Vector;

public class TransferFunctionInputSpeed extends TransferFunctionInput {
    public static final Serializer<TransferFunctionInputSpeed> SERIALIZER = new Serializer<TransferFunctionInputSpeed>() {
        @Override
        public String typeId() {
            return "INPUT-SPEED";
        }

        @Override
        public String title() {
            return "In: Move Speed";
        }

        @Override
        public boolean isInput() {
            return true;
        }

        @Override
        public TransferFunctionInputSpeed createNew(TransferFunctionHost host) {
            TransferFunctionInputSpeed speed = new TransferFunctionInputSpeed();
            speed.updateSource(host);
            return speed;
        }

        @Override
        public TransferFunctionInputSpeed load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionInputSpeed speed = new TransferFunctionInputSpeed();
            speed.setSourceMode(config.getOrDefault("mode", SourceMode.TRAIN));
            speed.setOutputMode(config.getOrDefault("output", OutputMode.SPEED));
            speed.updateSource(host);
            return speed;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInputSpeed function) {
            config.set("mode", function.sourceMode);
            config.set("output", function.outputMode);
        }
    };

    private SourceMode sourceMode = SourceMode.TRAIN;
    private OutputMode outputMode = OutputMode.SPEED;

    public void setSourceMode(SourceMode mode) {
        this.sourceMode = mode;
    }

    public void setOutputMode(OutputMode mode) {
        this.outputMode = mode;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        // Source for speed
        ReferencedSource result;
        if (sourceMode == SourceMode.TRAIN) {
            MinecartMember<?> member = host.getMember();
            if (member != null) {
                result = new TrainSpeedReferencedSource(member);
            } else {
                result = ReferencedSource.NONE;
            }
        } else {
            result = new AttachmentSpeedReferencedSource();
        }

        // Derivative to track acceleration
        if (outputMode == OutputMode.ACCELERATION) {
            result = new AccelerationReferencedSource(result);
        }

        return result;
    }

    @Override
    protected TransferFunctionInput cloneInput() {
        return new TransferFunctionInputSpeed();
    }

    @Override
    public boolean isBooleanOutput() {
        return false;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN,
                outputMode == OutputMode.SPEED ? "<Move Speed>" : "<Move Accel.>");
    }

    @Override
    public void openDialog(Dialog dialog) {
        super.openDialog(dialog);

        dialog.addLabel(5, 20, MapColorPalette.COLOR_RED, "Speed of:");
        dialog.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                updateText();
                super.onAttached();
            }

            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                sourceMode = SourceMode.values()[(sourceMode.ordinal() + 1) % SourceMode.values().length];
                updateSource(dialog.getHost());
                updateText();
                dialog.markChanged();
            }

            private void updateText() {
                setText(sourceMode.name());
            }
        }).setBounds(5, 27, 70, 13);

        dialog.addLabel(5, 43, MapColorPalette.COLOR_RED, "Output:");
        dialog.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                updateText();
                super.onAttached();
            }

            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                outputMode = OutputMode.values()[(outputMode.ordinal() + 1) % SourceMode.values().length];
                updateSource(dialog.getHost());
                updateText();
                dialog.markChanged();
            }

            private void updateText() {
                setText(outputMode.name());
            }
        }).setBounds(5, 50, 70, 13);
    }

    private static class AccelerationReferencedSource extends ReferencedSource {
        private final ReferencedSource base;
        private double prevValue = Double.NaN;

        public AccelerationReferencedSource(ReferencedSource base) {
            this.base = base;
        }

        @Override
        public void onTick() {
            base.onTick();

            double prevValue = this.prevValue;
            double newValue = base.value();
            this.value = Double.isNaN(prevValue) ? 0.0 : (newValue - prevValue);
            this.prevValue = newValue;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof AccelerationReferencedSource) {
                return ((AccelerationReferencedSource) o).base.equals(this.base);
            } else {
                return false;
            }
        }
    }

    private static class TrainSpeedReferencedSource extends ReferencedSource {
        private final MinecartMember<?> member;

        public TrainSpeedReferencedSource(MinecartMember<?> member) {
            this.member = member;
        }

        @Override
        public void onTick() {
            this.value = member.isUnloaded() ? 0.0 : member.getRealSpeedLimited();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TrainSpeedReferencedSource;
        }
    }

    private static class AttachmentSpeedReferencedSource extends ReferencedSource {
        private final Vector prevPosition;
        //private final Vector prevForward;
        private boolean first = true;

        public AttachmentSpeedReferencedSource() {
            this.prevPosition = new Vector();
            //this.prevForward = new Vector();
        }

        @Override
        public void onTransform(Matrix4x4 transform) {
            if (first) {
                first = false;
                MathUtil.setVector(this.prevPosition, transform.toVector());
                //MathUtil.setVector(this.prevForward, transform.getRotation().forwardVector());
                // Initially 0
                this.value = 0.0;
            } else {
                Vector newPosition = transform.toVector();

                this.value = newPosition.distance(this.prevPosition);
                MathUtil.setVector(this.prevPosition, newPosition);

                // This is the old method, which can also detect the direction, with
                // a negative speed in one direction vs the other. Is not useful for
                // functions where we want ABS speed.
                /*
                // Compute difference in position
                Vector diff = newPosition.clone().subtract(this.prevPosition);
                // Dot by forward vector of original transform
                double d = diff.dot(prevForward);
                // Update
                MathUtil.setVector(this.prevPosition, newPosition);
                MathUtil.setVector(this.prevForward, transform.getRotation().forwardVector());
                // Result
                this.value = d;
                 */
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof AttachmentSpeedReferencedSource;
        }
    }

    public enum SourceMode {
        TRAIN,
        ATTACHMENT
    }

    public enum OutputMode {
        SPEED,
        ACCELERATION
    }
}

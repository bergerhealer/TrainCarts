package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import org.bukkit.util.Vector;

public class TransferFunctionInputSpeed extends TransferFunctionInput {
    public static final Serializer<TransferFunctionInput> SERIALIZER = new Serializer<TransferFunctionInput>() {
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
        public TransferFunctionInput createNew(TransferFunctionHost host) {
            TransferFunctionInputSpeed speed = new TransferFunctionInputSpeed();
            speed.updateSource(host);
            return speed;
        }

        @Override
        public TransferFunctionInput load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionInputSpeed speed = new TransferFunctionInputSpeed();
            speed.updateSource(host);
            return speed;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInput function) {
        }
    };

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        return new SpeedReferencedSource();
    }

    @Override
    protected TransferFunctionInput cloneInput() {
        return new TransferFunctionInputSpeed();
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN, "<Move Speed>");
    }

    @Override
    public void openDialog(Dialog dialog) {
        super.openDialog(dialog);
    }

    private static class SpeedReferencedSource extends ReferencedSource {
        private final Vector prevPosition;
        //private final Vector prevForward;
        private boolean first = true;

        public SpeedReferencedSource() {
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
        public boolean isBool() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SpeedReferencedSource;
        }
    }
}

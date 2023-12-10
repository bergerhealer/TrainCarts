package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

import java.util.function.BooleanSupplier;

/**
 * A boolean constant. Always returns either true (=1) or false (=0).
 */
public class TransferFunctionBoolean implements TransferFunction {
    public static final TransferFunctionBoolean TRUE = new TransferFunctionBoolean(true);
    public static final TransferFunctionBoolean FALSE = new TransferFunctionBoolean(false);

    public static final Serializer<TransferFunctionBoolean> SERIALIZER = new Serializer<TransferFunctionBoolean>() {
        @Override
        public String typeId() {
            return "BOOLEAN";
        }

        @Override
        public String title() {
            return "Yes/No";
        }

        @Override
        public TransferFunctionBoolean createNew(TransferFunctionHost host) {
            return TRUE;
        }

        @Override
        public TransferFunctionBoolean load(TransferFunctionHost host, ConfigurationNode config) {
            return config.getOrDefault("output", false) ? TRUE : FALSE;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionBoolean function) {
            config.set("output", function.output);
        }
    };

    private final boolean boolOutput;
    private final double output;

    private TransferFunctionBoolean(boolean output) {
        this.boolOutput = output;
        this.output = boolOutput ? 1.0 : 0.0;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    public boolean getOutput() {
        return boolOutput;
    }

    public TransferFunctionBoolean opposite() {
        return boolOutput ? FALSE : TRUE;
    }

    @Override
    public double map(double input) {
        return output;
    }

    @Override
    public boolean isBooleanOutput(BooleanSupplier isBooleanInput) {
        return true;
    }

    @Override
    public boolean isPure() {
        return true;
    }

    @Override
    public TransferFunction clone() {
        return this;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3,
                widget.defaultColor(boolOutput ? MapColorPalette.COLOR_GREEN : MapColorPalette.COLOR_RED),
                boolOutput ? "Yes [ 1 ]" : "No [ 0 ]");
    }

    @Override
    public void openDialog(Dialog dialog) {
        dialog.addWidget(new TrueFalseToggleWidget(boolOutput) {
            @Override
            public void onChanged(boolean state) {
                dialog.setFunction(state ? TRUE : FALSE);
            }

            @Override
            public void onClosed() {
                dialog.finish();
            }
        }).setBounds(8, 1, dialog.getWidth() - 16, dialog.getHeight() - 2);
    }

    @Override
    public DialogMode openDialogMode() {
        return DialogMode.INLINE;
    }

    private static abstract class TrueFalseToggleWidget extends MapWidget {
        private boolean state;

        public TrueFalseToggleWidget(boolean initial) {
            this.state = initial;
            this.setFocusable(true);
        }

        public abstract void onChanged(boolean state);

        public abstract void onClosed();

        @Override
        public void onDraw() {
            view.draw(MapFont.MINECRAFT, 11, 2,
                    state ? MapColorPalette.getColor(0, 217, 58)
                          : MapColorPalette.getColor(0, 65, 0),
                    "Yes");

            view.draw(MapFont.MINECRAFT, 33, 2, MapColorPalette.COLOR_WHITE, "/");

            view.draw(MapFont.MINECRAFT, 43, 2,
                    state ? MapColorPalette.getColor(100, 25, 25)
                          : MapColorPalette.COLOR_RED,
                    "No");
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                if (!state) {
                    state = true;
                    invalidate();
                    onChanged(true);
                }
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                if (state) {
                    state = false;
                    invalidate();
                    onChanged(false);
                }
            } else if (event.getKey() == MapPlayerInput.Key.ENTER || event.getKey() == MapPlayerInput.Key.BACK) {
                onClosed();
            }
        }
    }
}

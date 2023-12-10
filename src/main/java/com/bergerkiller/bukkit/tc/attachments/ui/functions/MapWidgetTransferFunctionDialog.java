package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Displays the configuration of a {@link TransferFunction}
 */
public abstract class MapWidgetTransferFunctionDialog extends MapWidgetMenu implements TransferFunction.Dialog {
    private final TransferFunctionHost host;
    private TransferFunction.Holder<TransferFunction> root;
    private TransferFunctionNav nav;

    public MapWidgetTransferFunctionDialog(TransferFunctionHost host, TransferFunction rootFunction, BooleanSupplier isBooleanInput) {
        this.setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        this.setBounds(7, 17, 128 - 14, 105);
        this.setPositionAbsolute(true);

        this.host = host;
        this.root = TransferFunction.Holder.of(rootFunction);
        this.nav = new TransferFunctionNav(null, this.root, isBooleanInput);
    }

    /**
     * Called when a change is made to the transfer function tree
     *
     * @param function Updated root transfer function
     */
    public abstract void onChanged(TransferFunction function);

    /**
     * Should be called by menu dialogs when changes are made to a transfer function
     */
    @Override
    public void markChanged() {
        onChanged(root.getFunction());
    }

    /**
     * Gets the host this dialog is shown for. This provides information about available
     * inputs and such.
     *
     * @return TransferFunctionHost
     */
    @Override
    public TransferFunctionHost getHost() {
        return host;
    }

    @Override
    public void finish() {
        if (nav.parent != null) {
            navigate(nav.parent);
        } else {
            close();
        }
    }

    @Override
    public MapWidget getWidget() {
        return this; // To satisfy TransferFunction.Display
    }

    /**
     * Replaces the current function being configured in this dialog with a new one.
     * The menu is updated to reflect the new function.
     *
     * @param newFunction Function to replace the current function in this dialog with
     */
    @Override
    public void setFunction(TransferFunction newFunction) {
        if (nav != null) {
            if (nav.function.getFunction() == newFunction) {
                markChanged();
                return;
            }
            nav.function.setFunction(newFunction);
            navigate(nav); // Navigate to same dialog
            markChanged();
        }
    }

    @Override
    public boolean isBooleanInput() {
        return nav != null && nav.isBooleanInput.getAsBoolean();
    }

    /**
     * Navigates to a new transfer function. The back button will return back to this
     * current function.
     *
     * @param function Function Holder to navigate to
     * @param isBooleanInput Supplies whether the input to this function is a boolean
     */
    public void navigate(TransferFunction.Holder<TransferFunction> function, BooleanSupplier isBooleanInput) {
        // Some checks
        if (nav != null) {
            if (nav.function == function) {
                return;
            } else if (nav.parent != null && nav.parent.function == function) {
                navigate(nav.parent);
                return;
            }
        }

        // Normal navigation deeper into the function tree
        navigate(new TransferFunctionNav(nav, function, isBooleanInput));
    }

    private void navigate(TransferFunctionNav nav) {
        if (nav.function.getFunction().openDialogMode() != TransferFunction.DialogMode.WINDOW) {
            throw new IllegalArgumentException("Cannot navigate: function dialog mode is " +
                    nav.function.getFunction().openDialogMode());
        }

        this.nav = nav;
        this.clearWidgets();
        if (this.nav != null && getDisplay() != null) {
            this.deactivate();
            try {
                this.nav.function.getFunction().openDialog(this);
            } catch (Throwable t) {
                display.getPlugin().getLogger().log(Level.SEVERE, "Failed to open function dialog", t);
                close();
                return;
            }

            // If no widgets were added, automatically close the dialog again and go back
            if (nav.parent != null && this.getWidgetCount() == 0) {
                display.playSound(SoundEffect.EXTINGUISH);
                navigate(nav.parent);
            } else {
                this.activate();
            }
        }
    }

    /**
     * Shows a dialog to select what type of transfer function to create, then
     * creates a new one in its default state. If not cancelled, calls the consumer
     * with the newly created transfer function
     *
     * @param action Consumer for the new transfer function
     */
    public void createNew(Consumer<TransferFunction> action) {
        this.addWidget(new MapWidgetTransferFunctionTypeSelectorDialog(host) {
            @Override
            public void onSelected(TransferFunction function) {
                action.accept(function);
            }
        });
    }

    @Override
    public void onAttached() {
        navigate(this.nav);
        super.onAttached();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (exitOnBack && event.getKey() == MapPlayerInput.Key.BACK && this.isActivated()) {
            if (nav.parent != null) {
                navigate(nav.parent);
                display.playSound(SoundEffect.CLICK, 1.0f, 0.6f);
                return;
            }
        }

        super.onKeyPressed(event);
    }

    private static class TransferFunctionNav {
        public final TransferFunctionNav parent;
        public final TransferFunction.Holder<TransferFunction> function;
        public final BooleanSupplier isBooleanInput;
        public final int depth;

        public TransferFunctionNav(
                final TransferFunctionNav parent,
                final TransferFunction.Holder<TransferFunction> function,
                final BooleanSupplier isBooleanInput
        ) {
            this.parent = parent;
            this.function = function;
            this.isBooleanInput = isBooleanInput;
            this.depth = (parent != null) ? (parent.depth + 1) : 0;
        }
    }
}

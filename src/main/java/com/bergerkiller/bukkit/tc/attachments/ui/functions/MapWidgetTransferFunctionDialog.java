package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionConstant;

import java.util.function.Consumer;

/**
 * Displays the configuration of a {@link TransferFunction}
 */
public abstract class MapWidgetTransferFunctionDialog extends MapWidgetMenu {
    private TransferFunction root;
    private TransferFunctionNav nav;

    public MapWidgetTransferFunctionDialog(TransferFunction rootFunction) {
        this.setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        this.setBounds(7, 17, 128 - 14, 105);
        this.setPositionAbsolute(true);

        this.root = rootFunction;
        this.nav = new TransferFunctionNav(null, rootFunction);
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
    public void markChanged() {
        onChanged(root);
    }

    /**
     * Navigates to a new transfer function. The back button will return back to this
     * current function.
     *
     * @param function Function to navigate to
     */
    public void navigate(TransferFunction function) {
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
        navigate(new TransferFunctionNav(nav, function));
    }

    private void navigate(TransferFunctionNav nav) {
        this.nav = nav;
        this.clearWidgets();
        if (getDisplay() != null) {
            this.nav.function.makeDialog(this);
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
        //TODO: Implement
        action.accept(new TransferFunctionConstant(0.0));
    }

    @Override
    public void onAttached() {
        if (this.nav != null) {
            this.nav.function.makeDialog(this);
        }
        super.onAttached();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.BACK && this.isActivated()) {
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
        public final TransferFunction function;
        public final int depth;

        public TransferFunctionNav(TransferFunctionNav parent, TransferFunction function) {
            this.parent = parent;
            this.function = function;
            this.depth = (parent != null) ? (parent.depth + 1) : 0;
        }
    }
}

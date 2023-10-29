package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Displays the preview of a function, above which optional buttons can be drawn (if focused)
 * to make changes to the item.
 */
public class MapWidgetTransferFunctionItem extends MapWidget {
    private static final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);
    private static final byte COLOR_BG_MOVING = MapColorPalette.getColor(247, 233, 163);
    private final MapWidgetTransferFunctionDialog dialog;
    private final TransferFunction function;
    private final List<Button> buttons = new ArrayList<>();
    private boolean moving;
    private int selButtonIdx = 0;

    public MapWidgetTransferFunctionItem(MapWidgetTransferFunctionDialog dialog, TransferFunction function) {
        this.dialog = dialog;
        this.function = function;
        this.setFocusable(true);
    }

    public void onMoveUp() {
    }

    public void onMoveDown() {
    }

    public TransferFunction getFunction() {
        return function;
    }

    public MapWidgetTransferFunctionItem addButton(ButtonIcon icon, Consumer<MapWidgetTransferFunctionItem> action) {
        buttons.add(new Button(icon, action));
        return this;
    }

    public void configure() {
        dialog.navigate(function);
    }

    public void startMove() {
        moving = true;
        dialog.setExitOnBack(false);
        this.invalidate();
    }

    public boolean isMoving() {
        return moving;
    }

    @Override
    public void onFocus() {
        selButtonIdx = 0;
    }

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);

        MapCanvas previewView = view.getView(1, 1, getWidth() - 2, getHeight() - 2);
        previewView.fill(moving ? COLOR_BG_MOVING : (isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT));
        function.drawPreview(this, previewView);

        if (!moving && isFocused() && !buttons.isEmpty()) {
            int x_icon_step = buttons.get(0).icon.width() + 1;
            int x = getWidth() - buttons.size() * x_icon_step - 1;
            int i = 0;
            for (Button b : buttons) {
                view.draw(b.icon.icon(selButtonIdx == i), x, 2);
                ++i;
                x += x_icon_step;
            }
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (moving) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                onMoveUp();
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                onMoveDown();
            } else if (event.getKey() == MapPlayerInput.Key.BACK || event.getKey() == MapPlayerInput.Key.ENTER) {
                moving = false;
                dialog.setExitOnBack(true);
                invalidate();
            }
        } else if (event.getKey() == MapPlayerInput.Key.LEFT && isFocused()) {
            if (selButtonIdx > 0) {
                selButtonIdx--;
                invalidate();
            }
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT && isFocused()) {
            if (selButtonIdx < (buttons.size() - 1)) {
                selButtonIdx++;
                invalidate();
            }
        } else if (event.getKey() == MapPlayerInput.Key.ENTER && !buttons.isEmpty() && isFocused()) {
            buttons.get(selButtonIdx).action.accept(this);
        } else {
            super.onKeyPressed(event);
        }
    }

    private static class Button {
        public final ButtonIcon icon;
        public Consumer<MapWidgetTransferFunctionItem> action;

        public Button(ButtonIcon icon, Consumer<MapWidgetTransferFunctionItem> action) {
            this.icon = icon;
            this.action = action;
        }
    }

    /**
     * A type of button icon that can be displayed for this item
     */
    public enum ButtonIcon {
        /**
         * Configures the configuration of the current item widget
         */
        CONFIGURE("Configure"),
        /**
         * Moves this item to a different index (in the list)
         */
        MOVE("Change order"),
        /**
         * Removes this item, or if this is not a list, clears/resets the item to one that passes
         * the input forward (default behavior)
         */
        REMOVE("Remove operation"),
        /**
         * Adds a new function below this one
         */
        ADD("Add new operation"),
        /**
         * Shown when an element can only contain one function, but none is set yet, to initialize one.
         * Behaves the same as {@link #ADD} except it doesn't add one below, but instead replaces the
         * current item.
         */
        INITIALIZE("Set operation");

        private final MapTexture icon_selected;
        private final MapTexture icon_default;
        private final String tooltip;

        ButtonIcon(String tooltip) {
            MapTexture atlas = MapTexture.loadPluginResource(TrainCarts.plugin,
                    "com/bergerkiller/bukkit/tc/textures/attachments/transfer_function_item_buttons.png");
            this.icon_selected = atlas.getView(ordinal() * atlas.getHeight(), 0, atlas.getHeight(), atlas.getHeight()).clone();
            this.icon_default = this.icon_selected.clone();
            this.icon_default.setBlendMode(MapBlendMode.SUBTRACT);
            this.icon_default.fill(MapColorPalette.getColor(20, 20, 64));
            this.tooltip = tooltip;
        }

        public int width() {
            return icon_selected.getWidth();
        }

        public MapTexture icon(boolean selected) {
            return selected ? icon_selected : icon_default;
        }

        public String tooltip() {
            return tooltip;
        }
    }
}

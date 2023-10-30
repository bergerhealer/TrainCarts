package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionList;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Displays the preview of a function, above which optional buttons can be drawn (if focused)
 * to make changes to the item.
 */
public class MapWidgetTransferFunctionItem extends MapWidget {
    private static final int MODE_WIDTH = 9;
    private static final byte COLOR_MODE_TEXT_DEFAULT = MapColorPalette.getColor(64, 64, 64);
    private static final byte COLOR_MODE_TEXT_SELECTED = MapColorPalette.COLOR_WHITE;
    private static final byte COLOR_MODE_BG_DEFAULT = MapColorPalette.getColor(180, 152, 138);
    private static final byte COLOR_MODE_BG_FOCUSED = MapColorPalette.getColor(209, 177, 161);
    private static final byte COLOR_MODE_BG_SELECTED = MapColorPalette.getColor(64, 64, 255);
    private static final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);
    private static final byte COLOR_BG_MOVING = MapColorPalette.getColor(247, 233, 163);

    private final MapWidgetTransferFunctionDialog dialog;
    private final MapWidgetArrow modeUpArrow = new MapWidgetArrow(BlockFace.SOUTH);
    private final MapWidgetArrow modeDownArrow = new MapWidgetArrow(BlockFace.NORTH);
    private TransferFunctionList.Item item;
    private final List<Button> buttons = new ArrayList<>();
    private boolean moving;
    private boolean showFunctionMode = true;
    private int selButtonIdx = 0; // -1 = function mode

    public MapWidgetTransferFunctionItem(MapWidgetTransferFunctionDialog dialog, TransferFunctionList.Item item) {
        this.dialog = dialog;
        this.item = item;
        this.setFocusable(true);
    }

    public void onMoveUp() {
    }

    public void onMoveDown() {
    }

    /**
     * Called when the function mode is changed. Only called when {@link #setShowFunctionMode(boolean)}
     * is set to true.
     *
     * @param oldItem Old item before change
     * @param newItem New item after change
     */
    public void onFunctionModeChanged(TransferFunctionList.Item oldItem, TransferFunctionList.Item newItem) {
    }

    public TransferFunctionList.Item getItem() {
        return item;
    }

    public TransferFunction getFunction() {
        return item.function();
    }

    public MapWidgetTransferFunctionItem setShowFunctionMode(boolean show) {
        if (this.showFunctionMode != show) {
            this.showFunctionMode = show;
            this.invalidate();
        }
        return this;
    }

    public MapWidgetTransferFunctionItem addButton(ButtonIcon icon, Consumer<MapWidgetTransferFunctionItem> action) {
        buttons.add(new Button(icon, action));
        return this;
    }

    public void configure() {
        dialog.navigate(getFunction());
    }

    public void startMove() {
        moving = true;
        dialog.setExitOnBack(false);
        this.invalidate();
    }

    public boolean isMoving() {
        return moving;
    }

    private void setSelectedButton(int index) {
        if (selButtonIdx == index) {
            return;
        }
        if (index == -1) {
            // Enable function changing mode
            addWidget(modeUpArrow.setEnabled(functionModeValid(-1))
                    .setPosition(0, -modeUpArrow.getHeight() + 1));
            addWidget(modeDownArrow.setEnabled(functionModeValid(1))
                    .setPosition(0, getHeight() - 1));
        } else if (selButtonIdx == -1) {
            // Disable function changing mode
            removeWidget(modeUpArrow);
            removeWidget(modeDownArrow);
        }

        selButtonIdx = index;
        invalidate();
    }

    private void updateFunctionMode(int incr) {
        TransferFunctionList.FunctionMode[] values = TransferFunctionList.FunctionMode.values();
        int newIndex = item.mode().ordinal() + incr;
        if (newIndex >= 0 && newIndex < values.length) {
            TransferFunctionList.Item oldItem = item;
            item = new TransferFunctionList.Item(values[newIndex], item.function());
            onFunctionModeChanged(oldItem, item);
            invalidate();
        }
    }

    private boolean functionModeValid(int incr) {
        TransferFunctionList.FunctionMode[] values = TransferFunctionList.FunctionMode.values();
        int newIndex = item.mode().ordinal() + incr;
        return newIndex >= 0 && newIndex < values.length;
    }

    @Override
    public void onFocus() {
        selButtonIdx = 0;
    }

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);

        MapCanvas previewView;
        if (showFunctionMode) {
            view.fillRectangle(1, 1, MODE_WIDTH, getHeight() - 2,
                    moving ? COLOR_BG_MOVING : (isFocused()
                            ? ((selButtonIdx == -1) ? COLOR_MODE_BG_SELECTED : COLOR_MODE_BG_FOCUSED)
                            : COLOR_MODE_BG_DEFAULT));
            view.drawLine(MODE_WIDTH + 1, 1, MODE_WIDTH + 1, getHeight() - 2, MapColorPalette.COLOR_BLACK);
            view.fillRectangle(MODE_WIDTH + 2, 1, getWidth() - 3 - MODE_WIDTH, getHeight() - 2,
                    moving ? COLOR_BG_MOVING : (isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT));

            byte textColor = (isFocused() && selButtonIdx == -1) ? COLOR_MODE_TEXT_SELECTED : COLOR_MODE_TEXT_DEFAULT;

            // Draw two lines for the =. Dont use font = because its wider
            int baseY = (getHeight() - 1) / 2;
            view.drawLine(MODE_WIDTH - 3, baseY - 1, MODE_WIDTH - 1, baseY - 1, textColor);
            view.drawLine(MODE_WIDTH - 3, baseY + 1, MODE_WIDTH - 1, baseY + 1, textColor);

            // Draw a mode symbol, if not simply assignment
            if (item.mode() != TransferFunctionList.FunctionMode.ASSIGN) {
                MapCanvas iconView = view.getView(MODE_WIDTH - 7, baseY - 3, 4, 6);
                switch (item.mode()) {
                    case ADD:
                        iconView.drawLine(0, 3, 2, 3, textColor);
                        iconView.drawPixel(1, 2, textColor);
                        iconView.drawPixel(1, 4, textColor);
                        break;
                    case SUBTRACT:
                        iconView.drawLine(0, 3, 2, 3, textColor);
                        break;
                    case MULTIPLY:
                        iconView.drawLine(0, 1, 0, 2, textColor);
                        iconView.drawLine(2, 1, 2, 2, textColor);
                        iconView.drawPixel(1, 3, textColor);
                        iconView.drawLine(0, 4, 0, 5, textColor);
                        iconView.drawLine(2, 4, 2, 5, textColor);
                        break;
                    case DIVIDE:
                        iconView.drawPixel(3, 0, textColor);
                        iconView.drawLine(2, 1, 2, 2, textColor);
                        iconView.drawLine(1, 3, 1, 4, textColor);
                        iconView.drawPixel(0, 5, textColor);
                        break;
                }
            }

            previewView = view.getView(MODE_WIDTH + 3, 1, getWidth() - 2, getHeight() - 2);
        } else {
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    moving ? COLOR_BG_MOVING : (isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT));

            previewView = view.getView(2, 1, getWidth() - 2, getHeight() - 2);
        }

        getFunction().drawPreview(this, previewView);

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
    public void onKeyReleased(MapKeyEvent event) {
        if (!moving && selButtonIdx == -1) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                modeUpArrow.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                modeDownArrow.stopFocus();
            }
        }
        super.onKeyReleased(event);
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
        } else if (selButtonIdx == -1) {
            if (event.getKey() == MapPlayerInput.Key.ENTER || event.getKey() == MapPlayerInput.Key.RIGHT) {
                // Go back
                setSelectedButton(0);
            } else if (event.getKey() == MapPlayerInput.Key.UP) {
                // Previous mode
                updateFunctionMode(-1);
                modeUpArrow.setEnabled(functionModeValid(-1));
                modeDownArrow.setEnabled(functionModeValid(1));
                modeUpArrow.sendFocus();
                modeDownArrow.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                // Next mode
                updateFunctionMode(1);
                modeUpArrow.setEnabled(functionModeValid(-1));
                modeDownArrow.setEnabled(functionModeValid(1));
                modeDownArrow.sendFocus();
                modeUpArrow.stopFocus();
            }
        } else if (event.getKey() == MapPlayerInput.Key.LEFT && isFocused()) {
            if (selButtonIdx > (showFunctionMode ? -1 : 0)) {
                setSelectedButton(selButtonIdx - 1);
            }
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT && isFocused()) {
            if (selButtonIdx < (buttons.size() - 1)) {
                setSelectedButton(selButtonIdx + 1);
            }
        } else if (event.getKey() == MapPlayerInput.Key.ENTER && showFunctionMode && selButtonIdx == -1 && isFocused()) {
            setSelectedButton(0);
        } else if (event.getKey() == MapPlayerInput.Key.ENTER && selButtonIdx >= 0 && !buttons.isEmpty() && isFocused()) {
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

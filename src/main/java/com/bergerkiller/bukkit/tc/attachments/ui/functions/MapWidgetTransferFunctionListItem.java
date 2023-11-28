package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionList;
import org.bukkit.block.BlockFace;

/**
 * Expands the item widget with a 'mode' option, to change the operator modifier of
 * the item in the list. This is unique to items in a list.
 */
public class MapWidgetTransferFunctionListItem extends MapWidgetTransferFunctionItem {
    private static final int MODE_WIDTH = 9;
    private static final byte COLOR_MODE_TEXT_DEFAULT = MapColorPalette.getColor(64, 64, 64);
    private static final byte COLOR_MODE_TEXT_SELECTED = MapColorPalette.COLOR_WHITE;
    private static final byte COLOR_MODE_BG_DEFAULT = MapColorPalette.getColor(180, 152, 138);
    private static final byte COLOR_MODE_BG_FOCUSED = MapColorPalette.getColor(209, 177, 161);
    private static final byte COLOR_MODE_BG_SELECTED = MapColorPalette.getColor(64, 64, 255);

    private TransferFunctionList.Item item;
    private final MapWidgetArrow modeUpArrow = new MapWidgetArrow(BlockFace.SOUTH);
    private final MapWidgetArrow modeDownArrow = new MapWidgetArrow(BlockFace.NORTH);

    public MapWidgetTransferFunctionListItem(TransferFunctionHost host, TransferFunctionList.Item item) {
        super(host, item);
        this.item = item;
    }

    /**
     * Called when the function mode is changed
     *
     * @param oldItem Old item before change
     * @param newItem New item after change
     */
    public void onFunctionModeChanged(TransferFunctionList.Item oldItem, TransferFunctionList.Item newItem) {
    }

    public TransferFunctionList.Item getItem() {
        return item;
    }

    public void startMove() {
        MapWidgetTransferFunctionDialog dialog = getCurrentDialog();
        if (dialog != null) {
            dialog.setExitOnBack(false);
        }

        moving = true;
        this.invalidate();
    }

    private void updateFunctionMode(int incr) {
        TransferFunctionList.FunctionMode[] values = TransferFunctionList.FunctionMode.values();
        int newIndex = item.mode().ordinal() + incr;
        if (newIndex >= 0 && newIndex < values.length) {
            TransferFunctionList.Item oldItem = item;
            item = new TransferFunctionList.Item(values[newIndex], item.getFunction());
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
    public MapWidgetTransferFunctionItem addButton(ButtonIcon icon, Runnable action) {
        super.addButton(icon, action);
        return this;
    }

    @Override
    protected void setSelectedButton(int index) {
        if (index < -1) {
            index = -1;
        } else if (index >= buttons.size()) {
            index = buttons.size() - 1;
        }

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

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);

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

        MapCanvas previewView = view.getView(MODE_WIDTH + 3, 1, getWidth() - 2, getHeight() - 2);
        getFunction().drawPreview(this, previewView);

        drawUI();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!moving && event.getKey() == MapPlayerInput.Key.ENTER && selButtonIdx == -1 && isFocused()) {
            setSelectedButton(0);
        } else if (!moving && selButtonIdx == -1) {
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
        } else {
            super.onKeyPressed(event);
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
}

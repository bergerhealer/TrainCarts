package com.bergerkiller.bukkit.tc.controller.functions.ui.conditional;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionConditional;
import org.bukkit.block.BlockFace;

/**
 * Shows the conditional operator. When activated, can be changed with up/down
 * navigation buttons.
 */
public abstract class MapWidgetTransferFunctionConditionalOperator extends MapWidget {
    private static final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);
    private static final byte COLOR_BG_ACTIVATED = MapColorPalette.getColor(247, 233, 163);

    private final MapWidgetArrow opUpArrow = new MapWidgetArrow(BlockFace.SOUTH);
    private final MapWidgetArrow opDownArrow = new MapWidgetArrow(BlockFace.NORTH);
    private final Connector aboveConnector = new Connector();
    private final Connector belowConnector = new Connector();
    private TransferFunctionConditional.Operator operator;

    public MapWidgetTransferFunctionConditionalOperator(TransferFunctionConditional.Operator operator) {
        this.operator = operator;
        this.setFocusable(true);
        this.setRetainChildWidgets(true);
        this.addWidget(aboveConnector);
        this.addWidget(belowConnector);
        belowConnector.setVisible(operator != TransferFunctionConditional.Operator.BOOL);
    }

    public abstract void onOperatorChanged(TransferFunctionConditional.Operator operator);

    public void setOperator(TransferFunctionConditional.Operator operator) {
        if (this.operator != operator) {
            this.operator = operator;
            belowConnector.setVisible(operator != TransferFunctionConditional.Operator.BOOL);
            onOperatorChanged(operator);
            this.invalidate();
        }
    }

    private void updateOperator(int incr) {
        TransferFunctionConditional.Operator[] values = TransferFunctionConditional.Operator.values();
        int newIndex = operator.ordinal() + incr;
        if (newIndex >= 0 && newIndex < values.length) {
            setOperator(values[newIndex]);
        }
    }

    private boolean opValid(int incr) {
        TransferFunctionConditional.Operator[] values = TransferFunctionConditional.Operator.values();
        int newIndex = operator.ordinal() + incr;
        return newIndex >= 0 && newIndex < values.length;
    }

    @Override
    public void onBoundsChanged() {
        aboveConnector.setPosition((getWidth() - aboveConnector.getWidth()) / 2,
                                      -aboveConnector.getHeight());
        belowConnector.setPosition((getWidth() - belowConnector.getWidth()) / 2,
                                      getHeight());
    }

    @Override
    public void onActivate() {
        addWidget(opUpArrow.setEnabled(opValid(-1))
                .setPosition((getWidth() - opUpArrow.getWidth()) / 2, -opUpArrow.getHeight() - 1));
        addWidget(opDownArrow.setEnabled(opValid(1))
                .setPosition((getWidth() - opDownArrow.getWidth()) / 2, getHeight() + 1));
        super.onActivate();
    }

    @Override
    public void onDeactivate() {
        removeWidget(opUpArrow);
        removeWidget(opDownArrow);
        super.onDeactivate();
    }

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                isActivated() ? COLOR_BG_ACTIVATED : (isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT));

        byte color = isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK;
        int textWidth = (int) view.calcFontSize(MapFont.MINECRAFT, operator.title()).getWidth();
        view.draw(MapFont.MINECRAFT, (getWidth() - textWidth + 1) / 2, 3, color, operator.title());
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!isActivated()) {
            super.onKeyPressed(event);
            return;
        }

        if (event.getKey() == MapPlayerInput.Key.UP) {
            // Previous mode
            updateOperator(-1);
            opUpArrow.setEnabled(opValid(-1));
            opDownArrow.setEnabled(opValid(1));
            opUpArrow.sendFocus();
            opDownArrow.stopFocus();
        } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
            // Next mode
            updateOperator(1);
            opUpArrow.setEnabled(opValid(-1));
            opDownArrow.setEnabled(opValid(1));
            opDownArrow.sendFocus();
            opUpArrow.stopFocus();
        } else {
            this.focus();
            if (event.getKey() == MapPlayerInput.Key.LEFT || event.getKey() == MapPlayerInput.Key.RIGHT) {
                super.onKeyPressed(event);
            }
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (isActivated()) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                opUpArrow.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                opDownArrow.stopFocus();
            }
        }
        super.onKeyReleased(event);
    }

    /**
     * Shows a little connector line between the operator and the left/right hand sides
     */
    private static class Connector extends MapWidget {

        public Connector() {
            this.setSize(3, 2);
            this.setDepthOffset(-1); // Avoid arrows interfering
        }

        @Override
        public void onDraw() {
            view.drawLine(0, 0, 0, getHeight() - 1, MapColorPalette.COLOR_BLACK);
            view.drawLine(2, 0, 2, getHeight() - 1, MapColorPalette.COLOR_BLACK);
        }
    }
}
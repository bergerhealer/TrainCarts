package com.bergerkiller.bukkit.tc.controller.functions.ui.inputs;

import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;

/**
 * Shows a filtering expression for tags/owners/etc.
 */
public abstract class MapWidgetInputFilterExpression extends MapWidget {
    private static final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);
    private static final byte COLOR_INVERTED = MapColorPalette.getColor(94, 40, 114);
    private MapWidgetSubmitText submitWidget;
    private String expression = "";

    public MapWidgetInputFilterExpression() {
        this.setFocusable(true);
    }

    public abstract void onChanged(String expression);

    public MapWidgetInputFilterExpression setExpression(String expression) {
        if (!this.expression.equals(expression)) {
            this.expression = expression;
            this.invalidate();
        }
        return this;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public void onAttached() {
        submitWidget = getParent().addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAccept(String text) {
                text = text.trim();
                setExpression(text);
                onChanged(text);
            }
        }.setDescription("Set Expression"));
    }

    @Override
    public void onActivate() {
        submitWidget.activate();
    }

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT);

        MapCanvas textView = view.getView(1, 1, getWidth() - 1, getHeight() - 1);
        if (expression.isEmpty()) {
            textView.draw(MapFont.MINECRAFT, (getWidth() - 50) / 2, 2,
                    MapColorPalette.COLOR_RED, "<Not Set>");
        } else if (expression.startsWith("!")) {
            textView.draw(MapFont.MINECRAFT, 1, 2, COLOR_INVERTED, "!");
            textView.draw(MapFont.MINECRAFT, 3, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    expression.substring(1));
        } else {
            textView.draw(MapFont.MINECRAFT, 1, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    expression);
        }
    }
}

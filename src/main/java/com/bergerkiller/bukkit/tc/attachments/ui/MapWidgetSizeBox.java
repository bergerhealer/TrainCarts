package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * Configures the size of an attachment. By navigating down once
 * on the bottom-most selection box, uniform size can be configured.
 */
public abstract class MapWidgetSizeBox extends MapWidget {
    public final MapWidgetNumberBox x = addWidget(new MapWidgetNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size X-Axis";
        }

        @Override
        public void onValueChanged() {
            onSizeChanged();
        }
    });
    public final MapWidgetNumberBox y = addWidget(new MapWidgetNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size Y-Axis";
        }

        @Override
        public void onValueChanged() {
            onSizeChanged();
        }
    });
    public final MapWidgetNumberBox z = addWidget(new MapWidgetNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Size Z-Axis";
        }

        @Override
        public void onValueChanged() {
            onSizeChanged();
        }
    });

    public MapWidgetSizeBox() {
        this.setRetainChildWidgets(true);
    }

    /**
     * Called when one of the size axis are changed
     */
    public abstract void onSizeChanged();

    /**
     * Sets the (initial) size displayed
     *
     * @param sx Size X-Axis
     * @param sy Size Y-Axis
     * @param sz Size Z-Axis
     */
    public void setSize(double sx, double sy, double sz) {
        x.setValue(sx);
        y.setValue(sy);
        z.setValue(sz);
    }

    @Override
    public void onBoundsChanged() {
        int selHeight = (this.getHeight() - 2) / 3;
        x.setBounds(0, 0, getWidth(), selHeight);
        y.setBounds(0, (getHeight() - selHeight) / 2, getWidth(), selHeight);
        z.setBounds(0, getHeight() - selHeight, getWidth(), selHeight);
    }
}

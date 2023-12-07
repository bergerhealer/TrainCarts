package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

/**
 * Configures the options of a simple effect loop
 */
public class SimpleEffectLoopDialog extends MapWidgetMenu {
    private final ConfigurationNode config;

    public SimpleEffectLoopDialog(ConfigurationNode config) {
        this.config = config;
        setPositionAbsolute(true);
        setBounds(39, 40, 70, 30);
        setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        labelColor = MapColorPalette.COLOR_BLACK;
    }

    @Override
    public void onAttached() {
        addLabel(5, 6, "Delay (s):");
        addWidget(new MapWidgetNumberBox() {
            @Override
            public void onAttached() {
                setIncrement(0.01);
                setRange(0.0, 10000.0);
                setInitialValue(config.getOrDefault("delay", 0.0));
                super.onAttached();
            }

            @Override
            public void onValueChanged() {
                config.set("delay", getValue() == 0.0 ? null : getValue());
            }
        }).setBounds(5, 13, getWidth() - 10, 11);

        super.onAttached();
    }
}

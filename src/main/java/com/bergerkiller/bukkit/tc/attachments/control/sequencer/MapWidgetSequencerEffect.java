package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A single effect shown in a {@link MapWidgetSequencerEffectGroup}
 */
public class MapWidgetSequencerEffect extends MapWidget {
    private static final byte BG_COLOR_DEFAULT = MapColorPalette.getColor(86, 88, 97);
    private static final byte BG_COLOR_FOCUSED = MapColorPalette.getColor(180, 177, 172);
    private static final byte EFFECT_COLOR_DEFAULT = MapColorPalette.getColor(220, 220, 220);
    private static final byte EFFECT_COLOR_FOCUSED = MapColorPalette.getColor(247, 233, 163);
    public static final MapTexture TEXTURE_ATLAS = MapTexture.loadPluginResource(TrainCarts.plugin,
            "com/bergerkiller/bukkit/tc/textures/attachments/sequencer_icons.png");
    public static final int HEIGHT = 11;

    private final ConfigurationNode config;
    private final Type type;
    private final List<Button> buttons = new ArrayList<>();

    public MapWidgetSequencerEffect() {
        this(Type.MIDI, "Effect");
    }

    public MapWidgetSequencerEffect(Type type, String name) {
        this(type.createConfig(name));
    }

    public MapWidgetSequencerEffect(ConfigurationNode config) {
        this.setFocusable(true);
        this.setClipParent(true);

        this.config = config;
        this.type = Type.fromConfig(config);
        this.buttons.add(new Button(type.icon(), "Configure " + type.name().toLowerCase(Locale.ENGLISH), () -> {
            // Configure the effect loop type
        }));
        this.buttons.add(new Button(Icon.EFFECT_NAME, "Effect", () -> {
            // Open a dialog to select a different effect name to target
        }));
        this.buttons.add(new Button(Icon.SETTINGS, "Settings", () -> {
            // Open a dialog to configure the general settings (active / volume / pitch)

        }));
        this.buttons.add(new Button(Icon.DELETE, "Delete", () -> {
            // Open a dialog to confirm deletion
            getScroller().addWidget(new ConfirmEffectDeleteDialog() {
                @Override
                public void onConfirmDelete() {
                    // Actually delete this effect
                    MapWidgetSequencerEffect.this.remove();
                }

                @Override
                public void close() {
                    super.close();
                    MapWidgetSequencerEffect.this.focus();
                }
            });
        }));
    }

    public ConfigurationNode getConfig() {
        return config;
    }

    public void remove() {
        if (getParent() instanceof MapWidgetSequencerEffectGroup) {
            ((MapWidgetSequencerEffectGroup) getParent()).removeEffect(this);
        }
    }

    private MapWidgetSequencerScroller getScroller() {
        for (MapWidget w = getParent(); w != null; w = w.getParent()) {
            if (w instanceof MapWidgetSequencerScroller) {
                return (MapWidgetSequencerScroller) w;
            }
        }
        throw new IllegalStateException("Effect not added to a scroller widget");
    }

    private int getSelButtonIndex() {
        return Math.min(buttons.size() - 1, getScroller().effectSelButtonIndex);
    }

    private void setSelButtonIndex(int newIndex) {
        if (newIndex >= 0 && newIndex < buttons.size()) {
            getScroller().effectSelButtonIndex = newIndex;
            invalidate();
        }
    }

    @Override
    public void onDraw() {
        boolean focused = isFocused();

        // Background
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                focused ? BG_COLOR_FOCUSED : BG_COLOR_DEFAULT);

        // Effect name
        view.getView(1, 1, getWidth() - 2, getHeight() - 2)
                .draw(MapFont.MINECRAFT, 1, 1,
                        focused ? EFFECT_COLOR_FOCUSED : EFFECT_COLOR_DEFAULT,
                        config.getOrDefault("effectName", ""));

        // Buttons
        if (focused) {
            int selButtonIndex = getSelButtonIndex();
            int x = getWidth() - 1;
            for (int i = buttons.size() - 1; i >= 0; --i) {
                Button b = buttons.get(i);
                x -= b.icon.width() + 1;
                view.draw(b.icon.image(i == selButtonIndex), x, 2);
            }
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (isFocused()) {
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                setSelButtonIndex(getSelButtonIndex() - 1);
                return;
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                setSelButtonIndex(getSelButtonIndex() + 1);
                return;
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                buttons.get(getSelButtonIndex()).action.run();
                return;
            }
        }

        super.onKeyPressed(event);
    }

    private static class ConfirmEffectDeleteDialog extends MapWidgetMenu {

        public ConfirmEffectDeleteDialog() {
            this.setPositionAbsolute(true);
            this.setBounds(15, 36, 98, 58);
            this.setBackgroundColor(MapColorPalette.getColor(135, 33, 33));
        }

        @Override
        public void onAttached() {
            super.onAttached();

            // Label
            this.addWidget(new MapWidgetText()
                    .setText("Are you sure you\nwant to delete\nthis effect?")
                    .setBounds(5, 5, 80, 30));

            // Cancel
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ConfirmEffectDeleteDialog.this.close();
                }
            }.setText("No").setBounds(10, 40, 36, 13));

            // Yes!
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ConfirmEffectDeleteDialog.this.close();
                    ConfirmEffectDeleteDialog.this.onConfirmDelete();
                }
            }.setText("Yes").setBounds(52, 40, 36, 13));
        }

        /**
         * Called when the player specifically said 'yes' to deleting
         */
        public void onConfirmDelete() {
        }
    }

    private static class Button {
        public final Icon icon;
        public final String title;
        public final Runnable action;

        public Button(Icon icon, String title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }

    public enum Icon {
        EFFECT_NAME,
        SETTINGS,
        DELETE,
        MIDI;

        private final MapTexture unfocusedImage;
        private final MapTexture focusedImage;

        Icon() {
            unfocusedImage = TEXTURE_ATLAS.getView(ordinal() * 7, 0, 7, 7).clone();
            focusedImage = TEXTURE_ATLAS.getView(ordinal() * 7, 7, 7, 7).clone();
        }

        public int width() {
            return unfocusedImage.getWidth();
        }

        public int height() {
            return unfocusedImage.getHeight();
        }

        public MapTexture image(boolean focused) {
            return focused ? focusedImage : unfocusedImage;
        }
    }

    /**
     * Type of effect loop
     */
    public enum Type {
        MIDI(Icon.MIDI);

        private final Icon icon;

        Type(Icon icon) {
            this.icon = icon;
        }

        public Icon icon() {
            return icon;
        }

        public ConfigurationNode createConfig(String name) {
            ConfigurationNode config = new ConfigurationNode();
            config.set("type", name());
            config.set("effectName", name);
            return config;
        }

        public static Type fromConfig(ConfigurationNode config) {
            String typeName = config.getOrDefault("type", String.class, null);
            if (typeName != null) {
                typeName = typeName.toUpperCase(Locale.ENGLISH);
                for (Type type : values()) {
                    if (typeName.equals(type.name())) {
                        return type;
                    }
                }
            }

            return MIDI; // Fallback
        }
    }
}

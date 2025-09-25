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
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.control.effect.ScheduledEffectLoop;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionSingleConfigItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionBoolean;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionConstant;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

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
    private final SequencerType type;
    private final List<Button> buttons = new ArrayList<>();
    private boolean focusOnActivate = false;

    public MapWidgetSequencerEffect(SequencerType type, AttachmentSelector<Attachment.EffectAttachment> effectSelector) {
        this(type.createConfig(effectSelector));
    }

    public MapWidgetSequencerEffect(ConfigurationNode config) {
        this.setFocusable(true);
        this.setClipParent(true);

        this.config = config;
        this.type = SequencerType.fromConfig(config);
        this.buttons.add(new Button(Icon.PREVIEW, "Preview", () -> {
            ScheduledEffectLoop effectLoop = type.createEffectLoop(
                    getConfig().getNode("config"),
                    getMenu().createEffectSink(getEffectSelector()));
            getMenu().getPreviewEffectLoopPlayer().play(
                    effectLoop.asEffectLoop(getGroup().getDuration()));
        }));
        this.buttons.add(new Button(type.icon(false), type.icon(true), "Configure " + type.name().toLowerCase(Locale.ENGLISH), () -> {
            // Configure the effect loop type
            display.playSound(SoundEffect.PISTON_EXTEND);
            type.openConfigurationDialog(new SequencerType.OpenDialogArguments(
                    getMenu(),
                    getConfig().getNode("config"),
                    getGroup().getDuration(),
                    getMenu().createEffectSink(
                            MapWidgetSequencerEffect.this.getEffectSelector())
            ));
        }));
        this.buttons.add(new Button(Icon.EFFECT_NAME, "Effect", () -> {
            // Open a dialog to select a different effect name to target
            display.playSound(SoundEffect.PISTON_EXTEND);
            getMenu().addWidget(new MapWidgetAttachmentSelector<Attachment.EffectAttachment>(
                    getEffectSelector()
            ) {
                @Override
                public List<String> getAttachmentNames(AttachmentSelector<Attachment.EffectAttachment> allSelector) {
                    return getMenu().getEffectNames(allSelector);
                }

                @Override
                public void onSelected(AttachmentSelector<Attachment.EffectAttachment> selection) {
                    selection.writeToConfig(config, "effect");
                    MapWidgetSequencerEffect.this.invalidate();
                }
            }.setTitle("Set Effect to play"));
        }));
        this.buttons.add(new Button(Icon.SETTINGS, "Settings", () -> {
            // Open a dialog to configure the general settings (active / volume / pitch)
            display.playSound(SoundEffect.PISTON_EXTEND);
            getMenu().addWidget(new ConfigureDialog());
        }));
        this.buttons.add(new Button(Icon.DELETE, "Delete", () -> {
            // Open a dialog to confirm deletion
            display.playSound(SoundEffect.PISTON_EXTEND);
            getMenu().addWidget(new ConfirmEffectDeleteDialog() {
                @Override
                public void onConfirmDelete() {
                    // Actually delete this effect
                    if (MapWidgetSequencerEffect.this.display != null) {
                        MapWidgetSequencerEffect.this.display.playSound(SoundEffect.EXTINGUISH);
                    }
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

    public MapWidgetSequencerEffect focusOnActivate() {
        this.focusOnActivate = true;
        return this;
    }

    public void remove() {
        if (getParent() instanceof MapWidgetSequencerEffectGroup) {
            ((MapWidgetSequencerEffectGroup) getParent()).removeEffect(this);
        }
    }

    public AttachmentSelector<Attachment.EffectAttachment> getEffectSelector() {
        return AttachmentSelector.readFromConfig(config, "effect")
                .withType(Attachment.EffectAttachment.class)
                .excludingSelf();
    }

    private MapWidgetSequencerEffectGroup getGroup() {
        for (MapWidget w = getParent(); w != null; w = w.getParent()) {
            if (w instanceof MapWidgetSequencerEffectGroup) {
                return (MapWidgetSequencerEffectGroup) w;
            }
        }
        throw new IllegalStateException("Effect not added to a effect group widget");
    }

    private MapWidgetSequencerConfigurationMenu getMenu() {
        for (MapWidget w = getParent(); w != null; w = w.getParent()) {
            if (w instanceof MapWidgetSequencerConfigurationMenu) {
                return (MapWidgetSequencerConfigurationMenu) w;
            }
        }
        throw new IllegalStateException("Effect not added to a effect group list widget");
    }

    private int getSelButtonIndex() {
        return Math.min(buttons.size() - 1, getMenu().effectSelButtonIndex);
    }

    private void setSelButtonIndex(int newIndex) {
        if (newIndex >= 0 && newIndex < buttons.size()) {
            getMenu().effectSelButtonIndex = newIndex;
            invalidate();
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();

        if (focusOnActivate) {
            focusOnActivate = false;
            this.focus();
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
        AttachmentSelector<Attachment.EffectAttachment> selector = getEffectSelector();
        String name = selector.nameFilter().orElseGet(
                () -> selector.strategy() == AttachmentSelector.SearchStrategy.NONE
                        ? "<None>" : "<Any>");

        view.getView(1, 1, getWidth() - 2, getHeight() - 2)
                .draw(MapFont.MINECRAFT, 1, 1,
                        focused ? EFFECT_COLOR_FOCUSED : EFFECT_COLOR_DEFAULT,
                        name);

        // Buttons
        if (focused) {
            int selButtonIndex = getSelButtonIndex();
            int x = getWidth() - 1;
            for (int i = buttons.size() - 1; i >= 0; --i) {
                Button b = buttons.get(i);
                x -= b.width() + 1;
                view.draw(b.icon(i == selButtonIndex), x, 2);
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

    private class ConfigureDialog extends MapWidgetMenu {

        public ConfigureDialog() {
            setPositionAbsolute(true);
            setBounds(14, 21, 100, 95);
            setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
            labelColor = MapColorPalette.COLOR_BLACK;
        }

        @Override
        public void onAttached() {
            final TransferFunctionHost host = getMenu().getTransferFunctionHost();

            addLabel(5, 5, "Active");
            addWidget(new MapWidgetTransferFunctionSingleConfigItem(host, config, "active", () -> false) {
                @Override
                public TransferFunction createDefault() {
                    return TransferFunctionBoolean.TRUE;
                }
            }).setBounds(5, 11, getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            addLabel(5, 28, "Volume");
            addWidget(new MapWidgetTransferFunctionSingleConfigItem(host, config, "volume", () -> false) {
                @Override
                public TransferFunction createDefault() {
                    return TransferFunctionConstant.of(1.0);
                }
            }).setBounds(5, 34, getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            addLabel(5, 51, "Pitch");
            addWidget(new MapWidgetTransferFunctionSingleConfigItem(host, config, "pitch", () -> false) {
                @Override
                public TransferFunction createDefault() {
                    return TransferFunctionConstant.of(1.0);
                }
            }).setBounds(5, 57, getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            addLabel(5, 74, "Stop After (s)");
            addWidget(new AutoStopDelayNumberBox()).setBounds(5, 80, getWidth() - 10, 11);

            super.onAttached();
        }
    }

    private class AutoStopDelayNumberBox extends MapWidgetNumberBox {
        public AutoStopDelayNumberBox() {
            this.setRange(0.0, 3600.0);
        }

        @Override
        public void onAttached() {
            Double value = config.getOrDefault("stopAfter", Double.class, null);
            if (value != null) {
                setInitialValue(value);
                setTextOverride(null);
            } else {
                setInitialValue(0.0);
                setTextOverride("Not Set");
            }

            super.onAttached();
        }

        @Override
        public void onResetValue() {
            config.remove("stopAfter");
            setTextOverride("Not Set");
        }

        @Override
        public void onValueChanged() {
            config.set("stopAfter", getValue());
            setTextOverride(null);
        }
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
        public final MapTexture iconDefault, iconFocused;
        public final String title;
        public final Runnable action;

        public Button(Icon icon, String title, Runnable action) {
            this(icon.image(false), icon.image(true), title, action);
        }

        public Button(MapTexture iconDefault, MapTexture iconFocused, String title, Runnable action) {
            this.iconDefault = iconDefault;
            this.iconFocused = iconFocused;
            this.title = title;
            this.action = action;
        }

        public int width() {
            return iconDefault.getWidth();
        }

        public int height() {
            return iconDefault.getHeight();
        }

        public MapTexture icon(boolean focused) {
            return focused ? iconFocused : iconDefault;
        }
    }

    public enum Icon {
        PREVIEW,
        EFFECT_NAME,
        SETTINGS,
        DELETE,
        MIDI,
        SIMPLE;

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
     * Icons used for headers. Includes a 'disabled' icon if the feature is not available.
     */
    public enum HeaderIcon {
        CONFIGURE(0, 35),
        ADD(35, 7),
        SYNC(42, 31),
        ASYNC(73, 31),
        AUTOPLAY(104, 40),
        PLAY(144, 23),
        STOP(167, 23);

        private final MapTexture defaultImage, focusedImage, disabledImage;

        HeaderIcon(int x, int w) {
            defaultImage = TEXTURE_ATLAS.getView(x, 14, w, 7).clone();
            focusedImage = TEXTURE_ATLAS.getView(x, 21, w, 7).clone();
            disabledImage = TEXTURE_ATLAS.getView(x, 28, w, 7).clone();
        }

        public int getWidth() {
            return defaultImage.getWidth();
        }

        public int getHeight() {
            return defaultImage.getHeight();
        }

        public MapTexture getIcon(boolean enabled, boolean focused) {
            return enabled ? (focused ? focusedImage : defaultImage) : disabledImage;
        }
    }
}

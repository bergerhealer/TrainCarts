package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionBoolean;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionSingleConfigItem;

/**
 * The top header of the menu with a few feature buttons such as playback preview
 */
public class MapWidgetSequencerTopHeader extends MapWidget {
    private static final byte PLAY_STATUS_BG_COLOR_PLAYING = MapColorPalette.getColor(180, 177, 172);
    private static final byte PLAY_STATUS_BG_COLOR_STOPPED = MapColorPalette.getColor(86, 88, 97);
    private static final byte PLAY_STATUS_TEXT_COLOR_STOPPED = MapColorPalette.getColor(220, 220, 220);
    private static final byte PLAY_STATUS_TEXT_COLOR_PLAYING = MapColorPalette.getColor(247, 233, 163);
    private static final MapTexture ICON_PLAYING = MapWidgetSequencerEffect.TEXTURE_ATLAS.getView(19, 35, 7, 7).clone();
    private static final MapTexture ICON_STOPPED = MapWidgetSequencerEffect.TEXTURE_ATLAS.getView(26, 35, 7, 7).clone();
    private static final MapTexture ICON_AUTOMATIC = MapWidgetSequencerEffect.TEXTURE_ATLAS.getView(33, 35, 7, 7).clone();

    public MapWidgetSequencerTopHeader() {
        this.setClipParent(true);
    }

    private MapWidgetSequencerEffectGroupList getGroupList() {
        for (MapWidget w = getParent(); w != null; w = w.getParent()) {
            if (w instanceof MapWidgetSequencerEffectGroupList) {
                return (MapWidgetSequencerEffectGroupList) w;
            }
        }
        throw new IllegalStateException("Effect not added to a effect group list widget");
    }

    @Override
    public void onAttached() {
        final MapWidgetSequencerEffectGroupList groupList = getGroupList();

        this.addWidget(new Button() {
            private boolean wasPlaying = false;

            @Override
            public void onAttached() {
                SequencerPlayStatus playStatus = getGroupList().getPlayStatus();
                updateIcon(playStatus);
                wasPlaying = playStatus.isPlaying();
            }

            @Override
            public void onActivate() {
                MapWidgetSequencerEffectGroupList groupList = getGroupList();
                if (groupList.getPlayStatus().isPlaying()) {
                    groupList.stopPlaying();
                } else {
                    groupList.startPlaying();
                }
            }

            @Override
            public void onTick() {
                SequencerPlayStatus playStatus = getGroupList().getPlayStatus();
                if (playStatus.isPlaying() != wasPlaying) {
                    wasPlaying = playStatus.isPlaying();
                    updateIcon(playStatus);
                }
            }

            public void updateIcon(SequencerPlayStatus playStatus) {
                setIcon(playStatus.isPlaying() ? MapWidgetSequencerEffect.HeaderIcon.STOP
                        : MapWidgetSequencerEffect.HeaderIcon.PLAY);
            }
        }).setPosition(83, 0);

        this.addWidget(new Button() {
            @Override
            public void onAttached() {
                updateIcon(getCurrentMode());
            }

            private EffectLoop.RunMode getCurrentMode() {
                return groupList.getConfig().getOrDefault("runMode", EffectLoop.RunMode.ASYNCHRONOUS);
            }

            @Override
            public void onActivate() {
                EffectLoop.RunMode mode = getCurrentMode();
                mode = EffectLoop.RunMode.values()[(mode.ordinal() + 1) % EffectLoop.RunMode.values().length];
                groupList.getConfig().set("runMode", mode);
                updateIcon(mode);
                display.playSound(SoundEffect.CLICK);
            }

            public void updateIcon(EffectLoop.RunMode mode) {
                if (mode == EffectLoop.RunMode.SYNCHRONOUS) {
                    setIcon(MapWidgetSequencerEffect.HeaderIcon.SYNC);
                } else {
                    setIcon(MapWidgetSequencerEffect.HeaderIcon.ASYNC);
                }
            }
        }).setPosition(4, 0);

        this.addWidget(new Button() {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.PISTON_EXTEND);
                groupList.addWidget(new ConfigureAutoPlayDialog());
            }
        }).setIcon(MapWidgetSequencerEffect.HeaderIcon.AUTOPLAY)
          .setPosition(39, 0);
    }

    @Override
    public void onDraw() {
        view.fillRectangle(1, 0, getWidth() - 2, getHeight(), MapWidgetSequencerEffectGroup.BACKGROUND_COLOR);
        view.drawLine(0, 1, 0, getHeight() - 2, MapWidgetSequencerEffectGroup.BACKGROUND_COLOR);
        view.drawLine(getWidth() - 1, 1, getWidth() - 1, getHeight() - 2, MapWidgetSequencerEffectGroup.BACKGROUND_COLOR);
    }

    private class ConfigureAutoPlayDialog extends MapWidgetMenu {

        public ConfigureAutoPlayDialog() {
            setPositionAbsolute(true);
            setBounds(14, 40, 100, 54);
            setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
            labelColor = MapColorPalette.COLOR_BLACK;
        }

        @Override
        public void onAttached() {
            final MapWidgetSequencerEffectGroupList groupList = getGroupList();

            addLabel(5, 5, "Play Automatically:");
            addWidget(new MapWidgetTransferFunctionSingleConfigItem(
                    groupList.getTransferFunctionHost(),
                    groupList.getConfig(),
                    "autoplay",
                    () -> false
            ) {
                @Override
                public TransferFunction createDefault() {
                    return TransferFunctionBoolean.FALSE;
                }
            }).setBounds(5, 12, getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            addLabel(5, 31, "Current Status:");
            addWidget(new PlayStatusWidget()).setBounds(5, 38, getWidth() - 10, 11);

            super.onAttached();
        }
    }

    private class PlayStatusWidget extends MapWidget {
        private SequencerPlayStatus lastPlayStatus = SequencerPlayStatus.STOPPED_AUTOMATIC;

        @Override
        public void onAttached() {
            updatePlayStatus();
        }

        @Override
        public void onTick() {
            updatePlayStatus();
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    lastPlayStatus.isPlaying() ? PLAY_STATUS_BG_COLOR_PLAYING : PLAY_STATUS_BG_COLOR_STOPPED);

            int textX = 11;
            view.draw(lastPlayStatus.isPlaying() ? ICON_PLAYING : ICON_STOPPED, 2, 2);
            if (lastPlayStatus.isAutomatic()) {
                view.draw(ICON_AUTOMATIC, textX - 1, 2);
                textX += 8;
            }

            String text = lastPlayStatus.isAutomatic() ? "automatically" : "manually";
            byte textColor = lastPlayStatus.isPlaying() ? PLAY_STATUS_TEXT_COLOR_PLAYING
                                                        : PLAY_STATUS_TEXT_COLOR_STOPPED;

            view.getView(textX, 2, getWidth() - textX - 1, getHeight() - 3)
                    .draw(MapFont.MINECRAFT, 0, 0, textColor, text);
        }

        private void updatePlayStatus() {
            SequencerPlayStatus playStatus = getGroupList().getPlayStatus();
            if (playStatus != lastPlayStatus) {
                lastPlayStatus = playStatus;
                invalidate();
            }
        }
    }

    private static abstract class Button extends MapWidget {
        private MapWidgetSequencerEffect.HeaderIcon icon;

        public Button() {
            this.setFocusable(true);
            this.setClipParent(true);
            this.icon = icon;
        }

        public Button setIcon(MapWidgetSequencerEffect.HeaderIcon icon) {
            this.icon = icon;
            this.setSize(icon.getWidth(), icon.getHeight());
            this.invalidate();
            return this;
        }

        public abstract void onActivate();

        @Override
        public void onDraw() {
            view.draw(icon.getIcon(isEnabled(), isFocused()), 0, 0);
        }
    }
}

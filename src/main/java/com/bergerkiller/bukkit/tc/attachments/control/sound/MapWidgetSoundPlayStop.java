package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget that features a stop and play button. When the play button is held,
 * it can be set to loop and play at a rate defined by tapping play while active.
 * The {@link #onPlay()} and {@link #onStop()} can be implemented to, well,
 * play the sound or stop the sound from playing.
 */
public abstract class MapWidgetSoundPlayStop extends MapWidget {
    private final PlayButton play = new PlayButton();
    private final StopButton stop = new StopButton();

    public abstract void onPlay();
    public abstract void onStop();

    @Override
    public void onAttached() {
        addWidget(play.setBounds(0, 0, 11, 11));
        addWidget(stop.setBounds(play.getWidth() + 1, 0, 11, 11));
    }

    private class PlayButton extends PressableButton {
        private static final int AUTOPLAY_START_DELAY = 5;
        private static final int AUTOPLAY_ACTIVATION_TIME = 20;
        private int autoPlayCtr = 0;
        private int autoPlayInterval = AUTOPLAY_ACTIVATION_TIME;
        private boolean autoPlayActive = false;
        private int ticksSinceLastPress = 0;
        private List<HighlightPixel> highlightPixels = new ArrayList<>();
        private HighlightPixel lastDrawn = null;

        public void disableAutoPlay() {
            if (autoPlayActive) {
                autoPlayActive = false;
                resetPlayCounters();
                invalidate();
            }
        }

        private void resetPlayCounters() {
            autoPlayCtr = 0;
            ticksSinceLastPress = 0;
            lastDrawn = null;
        }

        @Override
        public void onAttached() {
            // Generate all highlight pixels needed
            // These are pixels offset 1 pixel from the edge, excluding corners
            // Pixels start at the middle-top (like a clock)
            lastDrawn = null;
            highlightPixels.clear();
            for (int x = (getWidth()/2); x <= (getWidth()-3); x++) {
                addHighlightPixel(x, 1);
            }
            for (int y = 2; y <= (getHeight()-3); y++) {
                addHighlightPixel(getWidth()-2, y);
            }
            for (int x = (getWidth()-3); x >= 2; x--) {
                addHighlightPixel(x, getHeight()-2);
            }
            for (int y = (getHeight()-3); y >= 2; y--) {
                addHighlightPixel(1, y);
            }
            for (int x = 2; x < (getWidth()/2); x++) {
                addHighlightPixel(x, 1);
            }
            highlightPixels.get(highlightPixels.size() - 1).next = highlightPixels.get(0);
        }

        @Override
        public void onFirstTimeActivation() {
            if (autoPlayActive) {
                autoPlayInterval = ticksSinceLastPress;
                resetPlayCounters();
            }
            onPlay();
        }

        @Override
        public void onSuccessiveActivation(int ticksHeld) {
            if (!autoPlayActive && ticksHeld >= AUTOPLAY_START_DELAY) {
                if (ticksHeld >= (AUTOPLAY_START_DELAY + AUTOPLAY_ACTIVATION_TIME)) {
                    autoPlayActive = true;
                    resetPlayCounters();
                    onPlay();
                }
                invalidate();
            } else if (ticksHeld == 1) {
                // Need to undo the 'play' highlight after the press tick
                invalidate();
            }
        }

        @Override
        public void onFocus() {
            ticksSinceLastPress = autoPlayCtr;
        }

        @Override
        public void onTick() {
            if (autoPlayActive) {
                ++ticksSinceLastPress;
                if (++autoPlayCtr >= autoPlayInterval) {
                    autoPlayCtr = 0;
                    onPlay();
                }
                invalidate();
            }
        }

        @Override
        public void onDraw() {
            // When focused draw a lighter animation pixel than not
            // It's a little distracting when it's super bright while elsewhere in the menu
            byte highlightColor = isFocused() ? MapColorPalette.getColor(200, 200, 150)
                                              : MapColorPalette.getColor(140, 140, 0);

            // Whenever a new sound is played, trk
            // Show a normal 'button pressed down' background when pressed otherwise.
            // Show normal button background when focused/unfocused
            boolean isPlayPressed = false;
            if ((autoPlayActive && autoPlayCtr == 0) || (pressed && pressedTicks == 0)) {
                drawBackground(highlightColor,
                               MapColorPalette.getColor(36, 89, 152),
                               MapColorPalette.getColor(44, 109, 186));
                isPlayPressed = true;
            } else if (pressed) {
                drawBackground(MapColorPalette.COLOR_BLACK,
                               MapColorPalette.getColor(36, 89, 152),
                               MapColorPalette.getColor(44, 109, 186));
                isPlayPressed = true;
            } else {
                super.onDraw();
            }

            if (autoPlayActive) {
                // In this mode we show a pixel going around the button
                // Every time the sound is played (ctr = 0) we light up the button
                HighlightPixel curr = getHighlightProgress(autoPlayCtr, autoPlayInterval);
                if (lastDrawn != null && lastDrawn != curr) {
                    for (HighlightPixel p = lastDrawn.next; p != curr; p = p.next) {
                        view.writePixel(p.x, p.y, highlightColor);
                    }
                }
                lastDrawn = curr;
                view.writePixel(curr.x, curr.y, highlightColor);
            } else if (pressedTicks >= AUTOPLAY_START_DELAY) {
                // Show an animation indicating autoplay is in the process of being activated
                HighlightPixel end = getHighlightProgress(pressedTicks - AUTOPLAY_START_DELAY, AUTOPLAY_ACTIVATION_TIME);
                for (HighlightPixel p = highlightPixels.get(0);; p = p.next) {
                    view.writePixel(p.x, p.y, highlightColor);
                    if (p == end) {
                        break;
                    }
                }
            }

            // Draw a play icon
            {
                byte color = isPlayPressed ? MapColorPalette.COLOR_GREEN
                                           : MapColorPalette.getColor(0, 180, 0);
                int play_height = getHeight() - 4;
                int play_width = (play_height + 1) / 2;
                int play_x = (getWidth() - play_width + 1) / 2;
                int play_y = 2;
                for (int dx = 0; dx < play_width && play_height > 0; dx++) {
                    for (int dy = 0; dy < play_height; dy++) {
                        view.writePixel(play_x + dx, play_y + dy, color);
                    }
                    play_height -= 2;
                    play_y++;
                }
            }
        }

        private HighlightPixel getHighlightProgress(int mul, int div) {
            if (div <= 0) {
                return highlightPixels.get(0);
            } else {
                int index = ((highlightPixels.size() * Math.floorMod(mul, div)) / div);
                index = Math.min(index, highlightPixels.size() - 1); // Just to be safe
                return highlightPixels.get(index);
            }
        }

        private void addHighlightPixel(int x, int y) {
            HighlightPixel curr = new HighlightPixel(highlightPixels.size(), x, y);
            highlightPixels.add(curr);
            if (highlightPixels.size() > 1) {
                highlightPixels.get(highlightPixels.size() - 2).next = curr;
            }
        }

        private class HighlightPixel {
            public final int x, y;
            public final int index;
            public HighlightPixel next;

            public HighlightPixel(int index, int x, int y) {
                this.index = index;
                this.x = x;
                this.y = y;
            }
        }
    }

    private class StopButton extends PressableButton {
        @Override
        public void onFirstTimeActivation() {
            play.disableAutoPlay();
            onStop();
        }

        @Override
        public void onSuccessiveActivation(int ticksHeld) {
        }

        @Override
        public void onDraw() {
            if (pressed) {
                drawBackground(MapColorPalette.COLOR_BLACK,
                               MapColorPalette.getColor(36, 89, 152),
                               MapColorPalette.getColor(44, 109, 186));
            } else {
                super.onDraw();
            }

            view.fillRectangle(3, 3, getWidth() - 6, getHeight() - 6,
                    pressed ? MapColorPalette.getColor(180, 0, 0)
                            : MapColorPalette.COLOR_RED);
        }
    }

    private static abstract class PressableButton extends MapWidgetSoundElement {
        public boolean pressed = false;
        public int pressedTicks = 0;

        public abstract void onFirstTimeActivation();

        public abstract void onSuccessiveActivation(int ticksHeld);

        @Override
        public void onKey(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.ENTER) {
                if (!pressed) {
                    pressed = true;
                    pressedTicks = 0;
                    invalidate();
                    onFirstTimeActivation();
                } else {
                    pressedTicks++;
                    onSuccessiveActivation(pressedTicks);
                }
            } else {
                super.onKey(event);
            }
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.ENTER) {
                // Handled in onKey
            } else {
                super.onKeyPressed(event);
            }
        }

        @Override
        public void onKeyReleased(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.ENTER) {
                pressedTicks = 0;
                if (pressed) {
                    pressed = false;
                    invalidate();
                }
            } else {
                super.onKeyReleased(event);
            }
        }

        @Override
        public void onBlur() {
            pressedTicks = 0;
            pressed = false;
        }
    }
}

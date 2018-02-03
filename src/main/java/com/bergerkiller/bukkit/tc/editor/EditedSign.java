package com.bergerkiller.bukkit.tc.editor;

import java.util.Locale;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.SignRedstoneMode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;

public class EditedSign {
    private Sign _sign;
    private SignActionHeader _header;

    public void load(Sign sign) {
        this._sign = sign;
        this._header = SignActionHeader.parseFromSign(sign);
    }

    public boolean isValid() {
        return this._sign != null && this._header != null;
    }

    public void save() {
        this._sign.setLine(0, this._header.toString());
        this._sign.update();

        // Update trains above
        Block rails = Util.getRailsFromSign(this._sign.getBlock());
        if (rails != null) {
            MinecartMember<?> member = MinecartMemberStore.getAt(rails);
            if (member != null) {
                member.getSignTracker().update();
            }
        }
    }

    public String getName() {
        return "Unknown Sign";
    }

    public void setMode(SignActionMode mode) {
        this._header.setMode(mode);
        this.save();
    }

    public SignActionMode getMode() {
        return this._header.getMode();
    }

    public Direction[] getDirections() {
        return this._header.getDirections();
    }

    public void setDirections(Direction[] directions) {
        this._header.setDirections(directions);
        this.save();
    }

    public void setRedstoneMode(SignRedstoneMode mode) {
        this._header.setRedstoneMode(mode);
        this.save();
    }

    public SignRedstoneMode getRedstoneMode() {
        return this._header.getRedstoneMode();
    }

    public void initEditor(final TCMapEditor editor) {
        editor.addControl(new MapControl() {
            private final SignRedstoneMode[] modes = {SignRedstoneMode.ON, SignRedstoneMode.OFF, SignRedstoneMode.ALWAYS,
                    SignRedstoneMode.PULSE_ON, SignRedstoneMode.PULSE_OFF, SignRedstoneMode.PULSE_ALWAYS};

            @Override
            public void onInit() {
                this.setLocation(5, 20);
                this.setBackground(display.loadTexture("com/bergerkiller/bukkit/tc/textures/redstone/bg.png"));
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == Key.DOWN || event.getKey() == Key.ENTER) {
                    setRedstoneMode(nextElement(modes, getRedstoneMode(), 1));
                    this.draw();
                } else if (event.getKey() == Key.UP) {
                    setRedstoneMode(nextElement(modes, getRedstoneMode(), -1));
                    this.draw();
                }
            }

            @Override
            public void onDraw() {
                MapTexture texture = editor.loadTexture("com/bergerkiller/bukkit/tc/textures/redstone/" + getRedstoneMode().name().toLowerCase(Locale.ENGLISH) + ".png");
                display.getLayer(2).setBlendMode(MapBlendMode.NONE);
                display.getLayer(2).draw(texture, x, y);
            }
        });

        editor.addControl(new MapControl() {
            private final SignActionMode[] modes = {SignActionMode.CART, SignActionMode.TRAIN, SignActionMode.RCTRAIN};

            @Override
            public void onInit() {
                this.setLocation(40, 20);
                this.setBackground(display.loadTexture("com/bergerkiller/bukkit/tc/textures/modes/bg.png"));
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == Key.DOWN || event.getKey() == Key.ENTER) {
                    setMode(nextElement(modes, getMode(), 1));
                    this.draw();
                } else if (event.getKey() == Key.UP) {
                    setMode(nextElement(modes, getMode(), -1));
                    this.draw();
                }
            }

            @Override
            public void onDraw() {
                MapTexture texture = editor.loadTexture("com/bergerkiller/bukkit/tc/textures/modes/" + getMode().name().toLowerCase(Locale.ENGLISH) + ".png");
                display.getLayer(2).setBlendMode(MapBlendMode.NONE);
                display.getLayer(2).draw(texture, x, y);
            }
        });

        editor.addControl(new MapRailsControl() {
            @Override
            public void onInit() {
                setLocation(80, 20);
                if (editor.getRailsBlock() != null) {
                    for (RailType type : RailType.values()) {
                        if (type.isRail(editor.getRailsBlock())) {
                            setRails(type, editor.getRailsBlock());
                            break;
                        }
                    }
                }
                super.onInit();
            }
        });
    }

    private static <T> T nextElement(T[] elements, T value, int n) {
        int i = 0;
        while (i < elements.length) {
            if (elements[i] == value) {
                break;
            } else {
                i++;
            }
        }
        i += n;
        while (i >= elements.length) i -= elements.length;
        while (i < 0) i += elements.length;
        return elements[i];
    }
}

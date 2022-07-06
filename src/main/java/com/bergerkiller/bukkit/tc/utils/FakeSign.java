package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;

import com.bergerkiller.bukkit.common.bases.BlockStateBase;

/**
 * A fake Sign implementation that allows someone to implement the sign properties
 * (facing, lines of text, attached block). No actual sign has to exist at the
 * sign's position for the fake sign to be used by Traincarts.<br>
 * <br>
 * As it fakes all the behavior of a Sign, it should not be used in other Bukkit
 * API's to avoid problems.
 */
public abstract class FakeSign extends BlockStateBase implements Sign {

    public FakeSign(Block block) {
        super(block);
    }

    @Override
    public String[] getLines() {
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = this.getLine(i);
        }
        return lines;
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        BlockState state = this.getBlock().getState();
        if (state instanceof PersistentDataHolder) {
            return ((PersistentDataHolder) state).getPersistentDataContainer();
        } else {
            return null;
        }
    }

    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public void setEditable(boolean editable) {
    }

    @Override
    public DyeColor getColor() {
        return null;
    }

    @Override
    public void setColor(DyeColor arg0) {
    }

    @Override
    public boolean isGlowingText() {
        return false;
    }

    @Override
    public void setGlowingText(boolean arg0) {
    }

    /**
     * Tracks the sign line information from an ongoing SignChangeEvent
     *
     * @param event
     * @return Fake sign
     */
    public static FakeSign changing(final SignChangeEvent event) {
        return new FakeSign(event.getBlock()) {
            @Override
            public String getLine(int index) throws IndexOutOfBoundsException {
                return event.getLine(index);
            }

            @Override
            public void setLine(int index, String line) throws IndexOutOfBoundsException {
                event.setLine(index, line);
            }
        };
    }
}

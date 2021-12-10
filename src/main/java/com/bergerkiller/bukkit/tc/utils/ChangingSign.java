package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.bases.BlockStateBase;

import org.bukkit.DyeColor;
import org.bukkit.UndefinedNullability;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * A sign implementation that ensured proper access to a sign while it is being placed
 */
public class ChangingSign extends BlockStateBase implements Sign {
    private final SignChangeEvent event;

    public ChangingSign(SignChangeEvent event) {
        super(event.getBlock());
        this.event = event;
    }

    @Override
    public String getLine(int index) throws IndexOutOfBoundsException {
        return this.event.getLine(index);
    }

    @Override
    public void setLine(int index, String line) throws IndexOutOfBoundsException {
        this.event.setLine(index, line);
    }

    @Override
    public String[] getLines() {
        return this.event.getLines();
    }

    @Override
    public boolean isEditable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setEditable(boolean editable) {
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DyeColor getColor() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setColor(@UndefinedNullability("defined by subclass") DyeColor arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean isGlowingText() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setGlowingText(boolean arg0) {
        // TODO Auto-generated method stub
        
    }
}

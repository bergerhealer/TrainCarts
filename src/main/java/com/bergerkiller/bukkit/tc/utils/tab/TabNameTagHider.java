package com.bergerkiller.bukkit.tc.utils.tab;

/**
 * Temporarily hides and then restores the player's nametag displayed by the TAB plugin
 */
public interface TabNameTagHider {

    void hide();

    void show();
}

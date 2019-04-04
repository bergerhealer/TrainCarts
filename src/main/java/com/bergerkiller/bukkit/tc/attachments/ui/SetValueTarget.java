package com.bergerkiller.bukkit.tc.attachments.ui;

/**
 * For widgets that support setting the value using the command /train menu set [value]
 */
public interface SetValueTarget {

    public boolean acceptTextValue(String value);

}

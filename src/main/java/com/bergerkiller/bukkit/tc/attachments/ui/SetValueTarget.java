package com.bergerkiller.bukkit.tc.attachments.ui;

/**
 * For widgets that support setting the value using the command /train menu set [value]
 */
public interface SetValueTarget {

    /**
     * Gets the name of the property changed when a text value is accepted
     * 
     * @return accepted property name
     */
    public String getAcceptedPropertyName();

    /**
     * Accepts a text value updating a property of this target
     * 
     * @param value
     * @return True if the value was actually updated, False if invalid or not accepted
     */
    public boolean acceptTextValue(String value);

}

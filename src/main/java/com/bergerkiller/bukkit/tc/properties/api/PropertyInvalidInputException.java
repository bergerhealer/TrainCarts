package com.bergerkiller.bukkit.tc.properties.api;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;

/**
 * Exception that can be thrown from inside {@link PropertyParser} methods
 * to indicate the provided input is invalid.
 */
public class PropertyInvalidInputException extends RuntimeException {
    private static final long serialVersionUID = -8618056967820261214L;

    /**
     * Initializes a new invalid input exception with the specified message
     * 
     * @param message The message shown to the player
     */
    public PropertyInvalidInputException(String message) {
        super(message);
    }

    /**
     * Initializes a new invalid input exception with the specified message
     * from a localization enum class.
     * 
     * @param localization Localization to read messages from
     * @param arguments Arguments for the localized message
     */
    public PropertyInvalidInputException(LocalizationEnum localization, String... arguments) {
        super(localization.get(arguments));
    }
}

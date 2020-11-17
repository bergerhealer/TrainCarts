package com.bergerkiller.bukkit.tc.exception.command;

/**
 * Exception thrown when the player specifies an invalid player name or uuid
 * as an argument to adding/removing claims.
 */
public class InvalidClaimPlayerNameException extends RuntimeException {
    private final String arg;
    private static final long serialVersionUID = -1462602705268773036L;

    public InvalidClaimPlayerNameException(String arg) {
        this.arg = arg;
    }

    public String getArgument() {
        return arg;
    }
}

package com.bergerkiller.bukkit.tc.exception;

/**
 * The name of a train is illegal and can not be used
 */
public class IllegalNameException extends Exception {
    private static final long serialVersionUID = -3918159273322822945L;

    public IllegalNameException(String reason) {
        super(reason);
    }
}

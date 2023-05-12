package com.bergerkiller.bukkit.tc.exception.command;

/**
 * Can be thrown in command handlers to indicate the command is only for players, and cannot
 * be used from command blocks or the server terminal.
 */
public class CommandOnlyForPlayersException extends RuntimeException {
}

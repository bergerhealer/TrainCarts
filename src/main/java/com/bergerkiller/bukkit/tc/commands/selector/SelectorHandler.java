package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.Collection;
import java.util.Map;

import org.bukkit.command.CommandSender;

/**
 * A single handler, which replaces the selector with suitable replacements.
 * If more than one value is returned, the command is re-dispatched with the
 * other arguments. If no results are returned, then the command is cancelled.
 */
public interface SelectorHandler {

    /**
     * Handles selectors in a command
     *
     * @param sender The command sender
     * @param selector The selector name that was hit, that was previously registered
     * @param arguments The argument for the selector, empty map if none are provided
     * @return collection of replacements for the selector
     * @throws SelectorException If something about the provided selector or arguments is wrong
     */
    Collection<String> handle(CommandSender sender, String selector, Map<String, String> arguments) throws SelectorException;
}

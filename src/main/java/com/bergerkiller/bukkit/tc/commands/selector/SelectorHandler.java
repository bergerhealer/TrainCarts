package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.Collection;
import java.util.List;

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
     * @param conditions The conditions for the selector, empty list if none were provided
     * @return collection of replacements for the selector
     * @throws SelectorException If something about the provided selector or arguments is wrong
     */
    Collection<String> handle(CommandSender sender, String selector, List<SelectorCondition> conditions) throws SelectorException;

    /**
     * Gets a list of potential selector conditions that are possible to specify for the
     * provided selector information.
     *
     * @param sender The command sender
     * @param selector The selector name that was hit, that was previously registered
     * @param conditions If the player already typed some conditions, this list stores those.
     *                   Can be used to omit options that are incompatible.
     * @return list of supported options
     */
    List<SelectorHandlerConditionOption> options(CommandSender sender, String selector, List<SelectorCondition> conditions);

    /**
     * Gets whether a particular command is handled by this selector, or not.
     * Only gets called if this selector is used in the command. The selector text
     * itself is excluded in the input command, only what precedes is kept.
     *
     * @param command Command text before the selector was encountered
     * @return True if this command should be handled
     */
    default boolean isCommandHandled(String command) {
        return true;
    }
}

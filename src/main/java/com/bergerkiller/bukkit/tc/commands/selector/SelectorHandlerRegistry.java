package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import com.bergerkiller.generated.net.minecraft.server.PlayerConnectionHandle;

import net.md_5.bungee.api.ChatColor;

/**
 * Registry for {@link SelectorHandler} objects. Can perform the selector
 * replacement logic, which expands a command based on the replacement
 * values provided.<br>
 * <br>
 * For full initialization, plugins should register an instance of this Class
 * as a Listener in the Bukkit API.
 */
public class SelectorHandlerRegistry implements Listener {
    // (?:[=]|(?<!\S))@([a-zA-Z0-9]+)(\[([\w\d\-\+=,\*]+)\])?(\s|$)
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("(?:[=]|(?<!\\S))@([a-zA-Z0-9]+)(\\[([\\w\\d\\-\\+=,\\*]+)\\])?(\\s|$)");
    private final Map<String, SelectorHandler> handlers = new HashMap<>();

    /**
     * Registers a selector handler by multiple selector names
     *
     * @param selectorNames List of names that register the selector
     * @param handler Handler to execute when the selector is detected in commands
     */
    public synchronized void registerMultiple(List<String> selectorNames, SelectorHandler handler) {
        selectorNames.forEach(selectorName -> register(selectorName, handler));
    }

    /**
     * Registers a selector handle used for the selector name
     *
     * @param selectorName The selector name to match in executed commands
     * @param handler Handler to execute when the selector is detected in commands
     */
    public synchronized void register(String selectorName, SelectorHandler handler) {
        handlers.put(selectorName.toLowerCase(Locale.ENGLISH), handler);
    }

    /**
     * Expands the commands to execute by querying the registered handlers.
     * This operation is recursive, meaning that if multiple selectors are specified,
     * they will all be expanded and a total list of all permutations is generated.
     *
     * @param sender Sender of the command, passed to the handlers
     * @param command The command that was executed
     * @return List of commands to execute <b>instead</b>.
     *         Is a list with a single item, if the command is unchanged.
     * @throws SelectorException Exception that could be thrown by a registered SelectorHandler
     */
    public synchronized List<String> expandCommands(CommandSender sender, String command) throws SelectorException {
        final int commandLength = command.length();

        // These will be filled up as the command is expanded
        List<StringBuilder> resultBuilders = null;
        int builderStartPosition = 0;

        for (Matcher matcher = SELECTOR_PATTERN.matcher(command); matcher.find();) {
            // Retrieve selector from match and try to find a handler for it
            final String selector = matcher.group(1);
            final SelectorHandler handler = handlers.get(selector.toLowerCase(Locale.ENGLISH));
            if (handler == null) {
                continue;
            }

            // Decode the arguments
            final Map<String, String> arguments;
            final String argumentsString = matcher.group(3);
            if (argumentsString == null) {
                arguments = Collections.emptyMap();
            } else {
                int separator = argumentsString.indexOf(',');
                final int length = argumentsString.length();
                if (separator == -1) {
                    // A single argument provided
                    // Parse as a singleton map, with an expected key=value syntax
                    // Reject invalid matches such as value, =value and value=
                    int equals = argumentsString.indexOf('=');
                    if (equals == -1 || equals == 0 || equals == (length-1)) {
                        continue;
                    }
                    arguments = Collections.singletonMap(argumentsString.substring(0, equals),
                                                         argumentsString.substring(equals+1));
                } else {
                    // Multiple arguments provided, build a hashmap with them
                    arguments = new LinkedHashMap<String, String>();
                    int argStart = 0;
                    int argEnd = separator;
                    boolean valid = true;
                    while (true) {
                        int equals = argumentsString.indexOf('=', argStart);
                        if (equals == -1 || equals == argStart || equals >= (argEnd-1)) {
                            valid = false;
                            break;
                        }

                        arguments.put(argumentsString.substring(argStart, equals),
                                      argumentsString.substring(equals+1, argEnd));

                        // End of String
                        if (argEnd == length) {
                            break;
                        }

                        // Find next separator. If none found, argument is until end of String.
                        argStart = argEnd + 1;
                        argEnd = argumentsString.indexOf(',', argEnd + 1);
                        if (argEnd == -1) {
                            argEnd = length;
                        }
                    }
                    if (!valid) {
                        continue;
                    }
                }
            }

            // With this information, ask the handler to provide replacement values
            // If empty, it ends the chain and an empty result is returned
            final Collection<String> values = handler.handle(sender, selector, arguments);
            final int valuesCount = values.size();
            if (valuesCount == 0) {
                return Collections.emptyList();
            }

            if (resultBuilders == null) {
                // First time, initialize resultBuilders
                StringBuilder builder = new StringBuilder(command.length());
                builder.append(command, 0, matcher.start(1)-1);
                resultBuilders = new ArrayList<>(values.size());
                resultBuilders.add(builder);
            } else {
                // Second time, include the text in-between two matches
                String inbetween = command.substring(builderStartPosition, matcher.start(1)-1);
                for (StringBuilder builder : resultBuilders) {
                    builder.append(inbetween);
                }
            }

            if (valuesCount > 1) {
                // Duplicate result builders to make room for every value
                int numResults = resultBuilders.size();
                for (int num = 1; num < valuesCount; num++) {
                    for (int i = 0; i < numResults; i++) {
                        resultBuilders.add(new StringBuilder(resultBuilders.get(i)));
                    }
                }

                // Store the values in the same order the builders were created
                Iterator<StringBuilder> builderIter = resultBuilders.iterator();
                for (String value : values) {
                    for (int i = 0; i < numResults; i++) {
                        builderIter.next().append(value);
                    }
                }
            } else {
                // Only one value, append to all builders (easy)
                final String value = values.iterator().next();
                for (StringBuilder builder : resultBuilders) {
                    builder.append(value);
                }
            }

            // Label this portion as processed. Ignore trailing space.
            builderStartPosition = matcher.end((argumentsString != null) ? 2 : 1);
        }

        // If there are results, finalize all result builders
        if (resultBuilders != null) {
            List<String> results = new ArrayList<String>(resultBuilders.size());
            for (StringBuilder builder : resultBuilders) {
                builder.append(command, builderStartPosition, commandLength);
                results.add(builder.toString());
            }
            return results;
        }

        // None matched
        return Collections.singletonList(command);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().startsWith("/") && event.getMessage().length() > 1) {
            // Wrap in ServerCommandEvent and handle
            final String command = event.getMessage().substring(1);
            ServerCommandEvent wrapped = new ServerCommandEvent(event.getPlayer(), command);
            this.onServerCommand(wrapped);
            if (wrapped.isCancelled() || wrapped.getCommand().isEmpty()) {
                event.setCancelled(true);
            } else if (!command.equals(wrapped.getCommand())) {
                event.setMessage("/" + wrapped.getCommand());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        final CommandSender sender = event.getSender();
        final String inputCommand = event.getCommand();
        final List<String> commands;

        try {
            commands = this.expandCommands(sender, inputCommand);
        } catch (SelectorException ex) {
            sender.sendMessage(ChatColor.RED + "[TrainCarts] " + ex.getMessage());
            cancelCommand(event);
            return;
        }

        if (commands.size() == 1) {
            // Either is the same command, or a replaced command.
            // Set a new command if needed
            String replacement = commands.iterator().next();
            if (!replacement.equals(inputCommand)) {
                event.setCommand(replacement);
            }
        } else if (commands.isEmpty()) {
            // No results from expansion, cancel the command
            cancelCommand(event);
        } else {
            // More than one command. Set the event command itself
            // to use the first command result. All following commands
            // need to be dispatched.
            Iterator<String> iter = commands.iterator();
            event.setCommand(iter.next());
            while (iter.hasNext()) {
                try {
                    Bukkit.getServer().dispatchCommand(sender, iter.next());
                } catch (CommandException ex) {
                    //TODO: Ew! But that's how the server does it, I guess.
                    sender.sendMessage(org.bukkit.ChatColor.RED + "An internal error occurred while attempting to perform this command");
                    java.util.logging.Logger.getLogger(PlayerConnectionHandle.T.getType().getName()).log(java.util.logging.Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void cancelCommand(ServerCommandEvent event) {
        // Note: cancellable since Server version 1.8.8
        //       as fallback for the few people that still use it,
        //       set the command to something harmless.
        if (event instanceof Cancellable) {
            ((Cancellable) event).setCancelled(true);
        } else {
            event.setCommand("");
        }
    }
}

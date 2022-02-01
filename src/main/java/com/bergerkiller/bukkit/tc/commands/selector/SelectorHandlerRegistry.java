package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.generated.net.minecraft.server.network.PlayerConnectionHandle;

/**
 * Registry for {@link SelectorHandler} objects. Can perform the selector
 * replacement logic, which expands a command based on the replacement
 * values provided.<br>
 * <br>
 * For full initialization, plugins should register an instance of this Class
 * as a Listener in the Bukkit API.
 */
public class SelectorHandlerRegistry implements Listener {
    // ^\[([\w\d\-\+=,\*\.\!]+)\](?:\s|$)
    private static final Pattern CONDITIONS_PATTERN = Pattern.compile("^\\[([\\w\\d\\-\\+=,\\*\\.\\!]+)\\](?:\\s|$)");

    private final Map<String, SelectorHandler> handlers = new HashMap<>();
    private final JavaPlugin plugin;

    public SelectorHandlerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables this selector handler registry so that it will start pre-processing
     * commands.
     */
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

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
     * Registers a selector handler used for the selector name
     *
     * @param selectorName The selector name to match in executed commands
     * @param handler Handler to execute when the selector is detected in commands
     */
    public synchronized void register(String selectorName, SelectorHandler handler) {
        handlers.put(selectorName.toLowerCase(Locale.ENGLISH), handler);
    }

    /**
     * Finds a previously registered selector handler by selector name
     *
     * @param selectorName
     * @return handler, or null if none by this name is registered
     */
    public synchronized SelectorHandler find(String selectorName) {
        return handlers.get(selectorName.toLowerCase(Locale.ENGLISH));
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

        // Find all instances of @, and check that the character before is permitted
        // for use of a selector expression.
        char lastChar = ' ';
        Matcher conditionsMatcher = null; // re-used for performance

        selectorSearch:
        for (int searchIndex = 0; searchIndex < commandLength;) {
            // Check potential start of a selector
            {
                char ch = command.charAt(searchIndex);
                if (ch != '@' || (!Character.isWhitespace(lastChar) &&
                                  lastChar != '!' &&
                                  lastChar != '=')
                ) {
                    lastChar = ch;
                    searchIndex++;
                    continue;
                }
            }

            // Identify the selector name
            final int selectorStartIndex;
            final String selector;
            final String conditionsString;
            final SelectorHandler handler;
            {
                boolean hasConditions = false;
                int nameEndIndex;
                for (nameEndIndex = searchIndex + 1; nameEndIndex < commandLength; nameEndIndex++) {
                    char ch = command.charAt(nameEndIndex);
                    if (Character.isAlphabetic(ch) || Character.isDigit(ch)) {
                        continue;
                    } else if (ch == '[') {
                        // Allowed - there are conditions for this selector
                        hasConditions = true;
                        break;
                    } else if (Character.isWhitespace(ch)) {
                        // Allowed - there are no conditions for this selector
                        break;
                    } else {
                        // Not allowed - not a valid selector pattern
                        // Skip this selector entirely past this point
                        searchIndex = nameEndIndex;
                        continue selectorSearch;
                    }
                }

                // Found selector start - next time, search from beyond the name end at least
                selectorStartIndex = searchIndex;
                searchIndex = nameEndIndex;
                lastChar = ']'; // If next character is @ do not match!

                // Decode the selector name, efficiently, without using regex
                // See if the found selector has a handler, if not, skip right away
                selector = command.substring(selectorStartIndex + 1, nameEndIndex);
                handler = handlers.get(selector.toLowerCase(Locale.ENGLISH));
                if (handler == null) {
                    continue;
                }

                // If we found conditions before, try to decode them
                // If we cannot find a valid conditions range, skip it
                if (hasConditions) {
                    if (conditionsMatcher == null) {
                        conditionsMatcher = CONDITIONS_PATTERN.matcher(command.subSequence(nameEndIndex, commandLength));
                    } else {
                        conditionsMatcher.reset(command.subSequence(nameEndIndex, commandLength));
                    }
                    if (!conditionsMatcher.lookingAt()) {
                        continue;
                    }
                    conditionsString = conditionsMatcher.group(1);
                    searchIndex += conditionsString.length() + 2; // skip conditions in next search
                } else {
                    conditionsString = null;
                }
            }

            // Decode the conditions
            final List<SelectorCondition> conditions;
            if (conditionsString == null) {
                conditions = Collections.emptyList();
            } else {
                int separator = conditionsString.indexOf(',');
                final int length = conditionsString.length();
                if (separator == -1) {
                    // A single condition provided
                    // Parse as a singleton list, with an expected key=value syntax
                    // Reject invalid matches such as value, =value and value=
                    int equals = conditionsString.indexOf('=');
                    if (equals == -1 || equals == 0 || equals == (length-1)) {
                        continue;
                    }
                    conditions = Collections.singletonList(
                            SelectorCondition.parse(conditionsString.substring(0, equals),
                                                conditionsString.substring(equals+1)));
                } else {
                    // Multiple conditions provided, build a hashmap with them
                    conditions = new ArrayList<SelectorCondition>(10);
                    int argStart = 0;
                    int argEnd = separator;
                    boolean valid = true;
                    while (true) {
                        int equals = conditionsString.indexOf('=', argStart);
                        if (equals == -1 || equals == argStart || equals >= (argEnd-1)) {
                            valid = false;
                            break;
                        }

                        conditions.add(SelectorCondition.parse(conditionsString.substring(argStart, equals),
                                                          conditionsString.substring(equals+1, argEnd)));

                        // End of String
                        if (argEnd == length) {
                            break;
                        }

                        // Find next separator. If none found, condition is until end of String.
                        argStart = argEnd + 1;
                        argEnd = conditionsString.indexOf(',', argEnd + 1);
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
            final Collection<String> values = handler.handle(sender, selector, conditions);
            final int valuesCount = values.size();
            if (valuesCount == 0) {
                return Collections.emptyList();
            }

            if (resultBuilders == null) {
                // First time, initialize resultBuilders
                StringBuilder builder = new StringBuilder(command.length());
                builder.append(command, 0, selectorStartIndex);
                resultBuilders = new ArrayList<>(values.size());
                resultBuilders.add(builder);
            } else {
                // Second time, include the text in-between two matches
                String inbetween = command.substring(builderStartPosition, selectorStartIndex);
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
            builderStartPosition = selectorStartIndex + selector.length() + 1;
            if (conditionsString != null) {
                builderStartPosition += conditionsString.length() + 2;
            }
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
            if (isCancelled(wrapped) || wrapped.getCommand().isEmpty()) {
                event.setCancelled(true);
            } else if (!command.equals(wrapped.getCommand())) {
                event.setMessage("/" + wrapped.getCommand());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        onServerCommandBase(event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!(event instanceof RemoteServerCommandEvent)) {
            onServerCommandBase(event);
        }
    }

    private void onServerCommandBase(ServerCommandEvent event) {
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

    private static boolean isCancelled(ServerCommandEvent event) {
        if (event instanceof Cancellable) {
            return ((Cancellable) event).isCancelled();
        } else {
            return event.getCommand().isEmpty();
        }
    }

    private static void cancelCommand(ServerCommandEvent event) {
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

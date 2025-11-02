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

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.utils.QuoteEscapedString;
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

import com.bergerkiller.bukkit.tc.Localization;
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
    // ^\[([\w\d\s\-\+=,\*\.\!\"\'\\]+)\](?:\s|$)
    private static final Pattern CONDITIONS_PATTERN = Pattern.compile("^\\[([\\w\\d\\s\\-\\+=,\\*\\.\\!\\\"\\'\\\\]+)\\](?:\\s|$)");

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

        // Permission checked on demand
        int maxSelectorValues = 0;

        // These will be filled up as the command is expanded
        List<StringBuilder> resultBuilders = null;

        // Include the other contents if the input command past this index
        // Also tracks it of the previous selector, to include contents 'between'
        int postLastSelectorStart = 0;
        int postSelectorCommandStart = 0;

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
                                  lastChar != '=' &&
                                  lastChar != '"')
                ) {
                    lastChar = ch;
                    searchIndex++;
                    continue;
                }
            }

            // The range of the input command to replace with the selector values
            int replaceStartIndex;

            // Identify the selector name
            final int selectorStartIndex;
            final String selector;
            String conditionsString;
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
                replaceStartIndex = selectorStartIndex;

                // Decode the selector name (@train / @ptrain), efficiently, without using regex
                // See if the found selector has a handler, if not, skip right away
                selector = command.substring(selectorStartIndex + 1, nameEndIndex);
                handler = handlers.get(selector.toLowerCase(Locale.ENGLISH));
                if (handler == null) {
                    continue;
                }

                // Having identified a selector we handle, first check whether this particular command
                // should be excluded from expanding. This is the case with, for example, the
                // train list command.
                {
                    int priorEnd = selectorStartIndex - 1;
                    while (priorEnd > 0 && command.charAt(priorEnd) == ' ') {
                        priorEnd--;
                    }
                    if (!handler.isCommandHandled(command.substring(0, priorEnd + 1))) {
                        continue;
                    }
                }

                // If we found conditions before, try to decode them
                // If we cannot find a valid conditions range, skip it
                postSelectorCommandStart = selectorStartIndex + selector.length() + 1;
                if (hasConditions) {
                    if (conditionsMatcher != null) {
                        conditionsMatcher.reset(command.subSequence(nameEndIndex, commandLength));
                    } else {
                        conditionsMatcher = CONDITIONS_PATTERN.matcher(command.subSequence(nameEndIndex, commandLength));
                    }
                    if (!conditionsMatcher.lookingAt()) {
                        continue;
                    }

                    conditionsString = conditionsMatcher.group(1);
                    searchIndex += conditionsString.length() + 2; // skip conditions in next search
                    postSelectorCommandStart += conditionsString.length() + 2;
                } else {
                    conditionsString = null;
                }
            }

            // Decode the conditions
            final List<SelectorCondition> conditions;
            if (conditionsString == null) {
                conditions = Collections.emptyList();
            } else {
                conditions = SelectorCondition.parseAll(conditionsString);
                if (conditions == null) {
                    // Invalid syntax detected. We can either skip and don't replace the selector, or
                    // cancel the entire command. It's unlikely somebody using @train or @ptrain desires that
                    // a plugin receive the selector itself. So for now, just show an informative message
                    // and cancel the command. It might be this must be changed in the future.

                    if (this.plugin != null) {
                        Localization.COMMAND_INPUT_SELECTOR_INVALID.message(sender, conditionsString);
                    }
                    continue;
                }
            }

            // Before actually executing the handler, verify that the sender has permission to expand selectors at all
            if (maxSelectorValues == 0) {
                if (sender == null || Permission.COMMAND_UNLIMITED_SELECTORS.has(sender)) {
                    maxSelectorValues = Integer.MAX_VALUE;
                } else if (Permission.COMMAND_USE_SELECTORS.has(sender)) {
                    maxSelectorValues = TCConfig.maxCommandSelectorValues;
                }
                if (maxSelectorValues <= 0) {
                    Localization.COMMAND_INPUT_SELECTOR_NOPERM.message(sender);
                    return Collections.emptyList();
                }
            }

            // With this information, ask the handler to provide replacement values
            // If empty, it ends the chain and an empty result is returned
            final Collection<String> values = handler.handle(sender, selector, conditions);
            final int valuesCount = values.size();
            if (valuesCount == 0) {
                return Collections.emptyList();
            }

            // If values will exceed the limit, abort and disallow execution
            if ((valuesCount * (resultBuilders == null ? 1 : resultBuilders.size())) > maxSelectorValues) {
                Localization.COMMAND_INPUT_SELECTOR_EXCEEDEDLIMIT.message(sender);
                return Collections.emptyList();
            }

            if (resultBuilders == null) {
                // First time, initialize resultBuilders
                StringBuilder builder = new StringBuilder(command.length());
                builder.append(command, 0, replaceStartIndex);
                resultBuilders = new ArrayList<>(values.size());
                resultBuilders.add(builder);
            } else {
                // Second time, include the text in-between two matches
                String inbetween = command.substring(postLastSelectorStart, replaceStartIndex);
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
                        builderIter.next().append(QuoteEscapedString.quoteEscape(value).getEscaped());
                    }
                }
            } else {
                // Only one value, append to all builders (easy)
                final String value = values.iterator().next();
                for (StringBuilder builder : resultBuilders) {
                    builder.append(QuoteEscapedString.quoteEscape(value).getEscaped());
                }
            }

            // Label this portion as processed. Ignore trailing space.
            postLastSelectorStart = postSelectorCommandStart;
        }

        // If there are results, finalize all result builders
        if (resultBuilders != null) {
            List<String> results = new ArrayList<String>(resultBuilders.size());
            for (StringBuilder builder : resultBuilders) {
                builder.append(command, postSelectorCommandStart, commandLength);
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

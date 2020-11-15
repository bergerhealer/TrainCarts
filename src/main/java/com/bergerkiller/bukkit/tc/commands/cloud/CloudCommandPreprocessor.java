package com.bergerkiller.bukkit.tc.commands.cloud;

import java.util.LinkedList;

import org.bukkit.command.CommandSender;

import cloud.commandframework.execution.preprocessor.CommandPreprocessingContext;
import cloud.commandframework.execution.preprocessor.CommandPreprocessor;

/**
 * Preprocesses the commands so that quotes can be used to specify words
 * with spaces in them as a single argument. Also stores all input arguments
 * as an "arguments"-keyed value for later injecting.
 */
public class CloudCommandPreprocessor implements CommandPreprocessor<CommandSender> {

    @Override
    public void accept(CommandPreprocessingContext<CommandSender> context) {
        // Tokenize "text" -> text with quote escaping rules
        {
            StringArgTokenizer tokenizer = new StringArgTokenizer();
            for (String arg : context.getInputQueue()) {
                tokenizer.next(arg);
            }
            context.getInputQueue().clear();
            context.getInputQueue().addAll(tokenizer.complete());
        }

        // Store all arguments as ArgumentList type
        context.getCommandContext().store("full_argument_list", ArgumentList.of(context.getInputQueue()));
    }

    // Used by convertArgsList
    // From BKCommonLib StringUtil, can remove when dependency changes
    private static final class StringArgTokenizer {
        private final LinkedList<String> result = new LinkedList<String>();
        private final StringBuilder buffer = new StringBuilder();
        private boolean isEscaped = false;

        public void next(String arg) {
            // If currently escaped, append space character to buffer first
            if (isEscaped) {
                buffer.append(' ');
            }

            int numEscapedAtStart = 0;
            for (;numEscapedAtStart < arg.length() && arg.charAt(numEscapedAtStart)=='\"'; numEscapedAtStart++);
            int remEscapedAtStart = (numEscapedAtStart % 3);

            // Only "-characters are specified. This requires special handling.
            // Replace """ with "-characters, and decide based on remainder what to do.
            if (numEscapedAtStart == arg.length()) {
                if (isEscaped) {
                    if (remEscapedAtStart == 0) {
                        // Doesn't change escape parity
                        appendEscapedQuotes(numEscapedAtStart / 3);
                    } else {
                        // ["some | "] -> [some ]
                        // ["some | ""] -> [some "]
                        // ["some | """"] -> [some "]
                        // ["some | """""] -> [some ""]
                        appendEscapedQuotes((numEscapedAtStart / 3) + remEscapedAtStart - 1);
                        commitBuffer();
                    }
                } else {
                    appendEscapedQuotes(numEscapedAtStart / 3);
                    if (remEscapedAtStart == 1) {
                        isEscaped = true; // Start escape section
                    } else {
                        // When remainder isn't a single quote, commit the buffer
                        commitBuffer();
                    }
                }
                return;
            }

            // Handle start of an escaped section
            if (!isEscaped && remEscapedAtStart > 0) {
                isEscaped = true;
                remEscapedAtStart--;
            }

            // Add all remaining quotes at the start
            appendEscapedQuotes(numEscapedAtStart / 3 + remEscapedAtStart);

            // Process remaining characters of argument in sequence
            int numTrailingQuotes = 0;
            for (int i = numEscapedAtStart; i < arg.length(); i++) {
                char c = arg.charAt(i);
                if (c != '\"') {
                    appendEscapedQuotes(numTrailingQuotes);
                    numTrailingQuotes = 0;
                    buffer.append(c);
                } else if (++numTrailingQuotes == 3) {
                    numTrailingQuotes = 0;
                    buffer.append('\"');
                }
            }

            // Close escaped section
            if (!isEscaped) {
                appendEscapedQuotes(numTrailingQuotes);
                commitBuffer();
            } else if (numTrailingQuotes > 0) {
                appendEscapedQuotes(numTrailingQuotes-1);
                commitBuffer();
            }
        }

        public LinkedList<String> complete() {
            if (isEscaped) {
                commitBuffer();
            }
            return result;
        }

        private void appendEscapedQuotes(int num_quotes) {
            for (int n = 0; n < num_quotes; n++) {
                buffer.append('\"');
            }
        }

        private void commitBuffer() {
            result.add(buffer.toString());
            buffer.setLength(0);
            isEscaped = false;
        }
    }
}

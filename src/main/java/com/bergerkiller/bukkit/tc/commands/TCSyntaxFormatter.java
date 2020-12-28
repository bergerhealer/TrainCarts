package com.bergerkiller.bukkit.tc.commands;

import java.util.Iterator;
import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.bergerkiller.bukkit.tc.commands.parsers.TrainTargetingFlags;

import cloud.commandframework.CommandTree;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.CommandSyntaxFormatter;
import cloud.commandframework.arguments.StaticArgument;
import cloud.commandframework.arguments.compound.CompoundArgument;
import cloud.commandframework.arguments.compound.FlagArgument;
import cloud.commandframework.arguments.flags.CommandFlag;

/**
 * Based upon cloud's standard syntax formatter, but filters certain flags
 * so they don't make the help menu super verbose.
 * 
 * @param <C> CommandSender type
 */
public class TCSyntaxFormatter<C> implements CommandSyntaxFormatter<C> {

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public final @NonNull String apply(
            final @NonNull List<@NonNull CommandArgument<C, ?>> commandArguments,
            final CommandTree.@Nullable Node<@Nullable CommandArgument<C, ?>> node
    ) {
        final FormattingInstance formattingInstance = this.createInstance();
        final Iterator<CommandArgument<C, ?>> iterator = commandArguments.iterator();
        while (iterator.hasNext()) {
            final CommandArgument<?, ?> commandArgument = iterator.next();
            if (commandArgument instanceof StaticArgument) {
                formattingInstance.appendLiteral((StaticArgument<C>) commandArgument);
            } else if (commandArgument instanceof CompoundArgument) {
                formattingInstance.appendCompound((CompoundArgument<?, ?, ?>) commandArgument);
            } else if (commandArgument instanceof FlagArgument) {
                formattingInstance.appendFlag((FlagArgument<?>) commandArgument);
            } else {
                if (commandArgument.isRequired()) {
                    formattingInstance.appendRequired(commandArgument);
                } else {
                    formattingInstance.appendOptional(commandArgument);
                }
            }
            if (iterator.hasNext()) {
                formattingInstance.appendBlankSpace();
            }
        }
        CommandTree.Node<CommandArgument<C, ?>> tail = node;
        while (tail != null && !tail.isLeaf()) {
            if (tail.getChildren().size() > 1) {
                formattingInstance.appendBlankSpace();
                final Iterator<CommandTree.Node<CommandArgument<C, ?>>> childIterator = tail.getChildren().iterator();
                while (childIterator.hasNext()) {
                    final CommandTree.Node<CommandArgument<C, ?>> child = childIterator.next();

                    if (child.getValue() instanceof StaticArgument) {
                        formattingInstance.appendName(child.getValue().getName());
                    } else if (child.getValue().isRequired()) {
                        formattingInstance.appendRequired(child.getValue());
                    } else {
                        formattingInstance.appendOptional(child.getValue());
                    }

                    if (childIterator.hasNext()) {
                        formattingInstance.appendPipe();
                    }
                }
                break;
            }
            final CommandArgument<C, ?> argument = tail.getChildren().get(0).getValue();
            if (argument instanceof CompoundArgument) {
                formattingInstance.appendBlankSpace();
                formattingInstance.appendCompound((CompoundArgument<?, ?, ?>) argument);
            } else if (argument instanceof FlagArgument) {
                formattingInstance.appendBlankSpace();
                formattingInstance.appendFlag((FlagArgument<?>) argument);
            } else {
                formattingInstance.appendBlankSpace();
                if (argument.isRequired()) {
                    formattingInstance.appendRequired(argument);
                } else {
                    formattingInstance.appendOptional(argument);
                }
            }
            tail = tail.getChildren().get(0);
        }
        return formattingInstance.toString();
    }

    /**
     * Create a new formatting instance
     *
     * @return Formatting instance
     */
    protected @NonNull FormattingInstance createInstance() {
        return new FormattingInstance();
    }


    /**
     * Instance that is used when building command syntax
     */
    public static class FormattingInstance {

        private final StringBuilder builder;

        /**
         * Create a new formatting instance
         */
        protected FormattingInstance() {
            this.builder = new StringBuilder();
        }

        @Override
        public final @NonNull String toString() {
            return this.builder.toString();
        }

        /**
         * Append a literal to the syntax string
         *
         * @param literal Literal to append
         */
        public void appendLiteral(final @NonNull StaticArgument<?> literal) {
            this.appendName(literal.getName());
        }

        /**
         * Append a compound argument to the syntax string
         *
         * @param argument Compound argument to append
         */
        public void appendCompound(final @NonNull CompoundArgument<?, ?, ?> argument) {
            final String prefix = argument.isRequired() ? this.getRequiredPrefix() : this.getOptionalPrefix();
            final String suffix = argument.isRequired() ? this.getRequiredSuffix() : this.getOptionalSuffix();
            this.builder.append(prefix);
            final Object[] names = argument.getNames().toArray();
            for (int i = 0; i < names.length; i++) {
                this.builder.append(prefix);
                this.appendName(names[i].toString());
                this.builder.append(suffix);
                if ((i + 1) < names.length) {
                    this.builder.append(' ');
                }
            }
            this.builder.append(suffix);
        }

        /**
         * Append a flag argument
         *
         * @param flagArgument Flag argument
         */
        public void appendFlag(final @NonNull FlagArgument<?> flagArgument) {
            final Iterator<CommandFlag<?>> flagIterator = flagArgument
                    .getFlags()
                    .iterator();

            boolean first = true;
            while (flagIterator.hasNext()) {
                final CommandFlag<?> flag = flagIterator.next();
                if (!isFlagVisible(flag)) {
                    continue;
                }

                if (first) {
                    first = false;
                    this.builder.append(this.getOptionalPrefix());
                } else {
                    this.appendBlankSpace();
                    this.appendPipe();
                    this.appendBlankSpace();
                }

                this.appendName(String.format("--%s", flag.getName()));

                if (flag.getCommandArgument() != null) {
                    this.builder.append(' ');
                    this.builder.append(this.getOptionalPrefix());
                    this.appendName(flag.getCommandArgument().getName());
                    this.builder.append(this.getOptionalSuffix());
                }
            }

            if (!first) {
                this.builder.append(this.getOptionalSuffix());
            }
        }

        /**
         * Checks whether a given command flag is visible in the syntax
         *
         * @param flag
         * @return True if flag is visible
         */
        public boolean isFlagVisible(CommandFlag<?> flag) {
            return !TrainTargetingFlags.INSTANCE.isTrainTargetingFlag(flag);
        }

        /**
         * Append a required argument
         *
         * @param argument Required argument
         */
        public void appendRequired(final @NonNull CommandArgument<?, ?> argument) {
            this.builder.append(this.getRequiredPrefix());
            this.appendName(argument.getName());
            this.builder.append(this.getRequiredSuffix());
        }

        /**
         * Append an optional argument
         *
         * @param argument Optional argument
         */
        public void appendOptional(final @NonNull CommandArgument<?, ?> argument) {
            this.builder.append(this.getOptionalPrefix());
            this.appendName(argument.getName());
            this.builder.append(this.getOptionalSuffix());
        }

        /**
         * Append the pipe (|) character
         */
        public void appendPipe() {
            this.builder.append("|");
        }

        /**
         * Append an argument name
         *
         * @param name Name to append
         */
        public void appendName(final @NonNull String name) {
            this.builder.append(name);
        }

        /**
         * Get the required argument prefix
         *
         * @return Required argument prefix
         */
        public @NonNull String getRequiredPrefix() {
            return "<";
        }

        /**
         * Get the required argument suffix
         *
         * @return Required argument suffix
         */
        public @NonNull String getRequiredSuffix() {
            return ">";
        }

        /**
         * Get the optional argument prefix
         *
         * @return Optional argument prefix
         */
        public @NonNull String getOptionalPrefix() {
            return "[";
        }

        /**
         * Get the optional argument suffix
         *
         * @return Optional argument suffix
         */
        public @NonNull String getOptionalSuffix() {
            return "]";
        }

        /**
         * Append a blank space
         */
        public void appendBlankSpace() {
            this.builder.append(' ');
        }

    }

}

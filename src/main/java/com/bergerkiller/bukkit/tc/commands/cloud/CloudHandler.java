package com.bergerkiller.bukkit.tc.commands.cloud;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.annotations.injection.ParameterInjector;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserParameter;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.arguments.parser.StandardParameters;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.brigadier.CloudBrigadierManager;
import cloud.commandframework.bukkit.BukkitCommandManager;
import cloud.commandframework.bukkit.CloudBukkitCapabilities;
import cloud.commandframework.captions.Caption;
import cloud.commandframework.captions.SimpleCaptionRegistry;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.CommandExecutionException;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.execution.postprocessor.CommandPostprocessor;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.minecraft.extras.MinecraftHelp;
import cloud.commandframework.paper.PaperCommandManager;
import cloud.commandframework.services.PipelineException;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;

/**
 * Configures the Cloud command dispatcher
 */
public class CloudHandler {
    private BukkitCommandManager<CommandSender> manager;
    private AnnotationParser<CommandSender> annotationParser;
    private BukkitAudiences bukkitAudiences;

    public void enable(Plugin plugin) {
        try {
            this.manager = new PaperCommandManager<>(
                    /* Owning plugin */ plugin,
                    /* Coordinator function */ CommandExecutionCoordinator.simpleCoordinator(),
                    /* Command Sender -> C */ Function.identity(),
                    /* C -> Command Sender */ Function.identity()
            );
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to initialize the command manager", e);
        }

        // Register Brigadier mappings
        // Only do this on PaperSpigot. On base Spigot, this breaks command blocks
        if (manager.queryCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            manager.registerBrigadier();

            CloudBrigadierManager<?, ?> brig = manager.brigadierManager();
            brig.setNativeNumberSuggestions(false);
        }

        // Registers a custom command preprocessor that handles quote-escaping
        // Also stores the full argument list input as "arguments" in the context
        this.manager.registerCommandPreProcessor(new CloudCommandPreprocessor());

        // Create the annotation parser. This allows you to define commands using methods annotated with
        // @CommandMethod
        final Function<ParserParameters, CommandMeta> commandMetaFunction = p ->
                CommandMeta.simple()
                        .with(CommandMeta.DESCRIPTION, p.get(StandardParameters.DESCRIPTION, "No description"))
                        .build();
        this.annotationParser = new AnnotationParser<>(
                /* Manager */ this.manager,
                /* Command sender type */ CommandSender.class,
                /* Mapper for command meta instances */ commandMetaFunction
        );

        // Shows the argname as <argname> as a suggestion
        // Fix for numeric arguments on the broken brigadier system
        suggest("argname", (context,b) -> Collections.singletonList("<" + context.getCurrentArgument().getName() + ">"));

        // Makes ArgumentList always available as a field you can put in a method
        injector(ArgumentList.class, (context, annotations) -> {
            return context.get("full_argument_list");
        });

        handle(CommandExecutionException.class, this::handleException);
        handle(PipelineException.class, this::handleException);

        suggest("playername", (context, input) -> {
            // Try online players first
            List<String> names = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(p -> p.startsWith(input))
                .collect(Collectors.toList());

            if (!names.isEmpty()) {
                return names;
            }

            // Try offline players second, to reduce clutter
            // TODO: Doesnt work? Weird.
            return Stream.of(Bukkit.getOfflinePlayers())
                .map(OfflinePlayer::getName)
                .filter(p -> p.startsWith(input))
                .collect(Collectors.toList());
        });

        // Used by the help system
        this.bukkitAudiences = BukkitAudiences.create(plugin);
    }

    private void handleException(CommandSender sender, Throwable exception) {
        Throwable cause = exception.getCause();

        // Find handler for this exception, if registered, execute that handler
        // If the handler throws, handle it as an internal error
        @SuppressWarnings({"unchecked", "rawtypes"})
        BiConsumer<CommandSender, Throwable> handler = manager.getExceptionHandler((Class) cause.getClass());
        if (handler != null) {
            try {
                handler.accept(sender, exception.getCause());
                return;
            } catch (Throwable t2) {
                cause = t2;
            }
        }

        // Default fallback
        this.manager.getOwningPlugin().getLogger().log(Level.SEVERE,
                "Exception executing command handler", cause);
        sender.sendMessage(ChatColor.RED + "An internal error occurred while attempting to perform this command.");
    }
    
    /**
     * Gets the parser instance used to parse annotated commands
     * 
     * @return parser
     */
    public AnnotationParser<CommandSender> getParser() {
        return this.annotationParser;
    }

    /**
     * Gets the command manager
     * 
     * @return manager
     */
    public CommandManager<CommandSender> getManager() {
        return this.manager;
    }

    /**
     * Register a new command postprocessor. The order they are registered in is respected, and they
     * are called in LIFO order. This is called before the command handler is executed.
     *
     * @param processor Processor to register
     * @see #preprocessContext(CommandContext, LinkedList) Preprocess a context
     */
    public void postProcess(final CommandPostprocessor<CommandSender> processor) {
        this.manager.registerCommandPostProcessor(processor);
    }

    /**
     * Register a parser supplier
     *
     * @param type     The type that is parsed by the parser
     * @param supplier The function that generates the parser. The map supplied my contain parameters used
     *                 to configure the parser, many of which are documented in {@link StandardParameters}
     * @param <T>      Generic type specifying what is produced by the parser
     */
    public <T> void parse(
            Class<T> type,
            Function<ParserParameters, ArgumentParser<CommandSender, ?>> supplier
    ) {
        this.manager.getParserRegistry().registerParserSupplier(TypeToken.get(type), supplier);
    }

    /**
     * Register a parser supplier
     *
     * @param type     The type that is parsed by the parser
     * @param supplier The function that generates the parser. The map supplied my contain parameters used
     *                 to configure the parser, many of which are documented in {@link StandardParameters}
     * @param <T>      Generic type specifying what is produced by the parser
     */
    public <T> void parse(
            TypeToken<T> type,
            Function<ParserParameters, ArgumentParser<CommandSender, ?>> supplier
    ) {
        this.manager.getParserRegistry().registerParserSupplier(type, supplier);
    }

    /**
     * Register a named parser supplier
     *
     * @param name     Parser name
     * @param supplier The function that generates the parser. The map supplied my contain parameters used
     *                 to configure the parser, many of which are documented in {@link StandardParameters}
     */
    public void parse(
            String name,
            Function<ParserParameters, ArgumentParser<CommandSender, ?>> supplier
    ) {
        this.manager.getParserRegistry().registerNamedParserSupplier(name, supplier);
    }

    /**
     * Register a new named suggestion provider with a constant list of suggestions.
     * 
     * @param name Name of the suggestions provider. The name is case independent.
     * @param suggestions List of suggestions
     */
    public void suggest(String name, List<String> suggestions) {
        suggest(name, (sender, arg) -> suggestions);
    }

    /**
     * Register a new named suggestion provider with a no-input supplier for a list of suggestions.
     * 
     * @param name Name of the suggestions provider. The name is case independent.
     * @param suggestionsProvider The suggestions provider
     */
    public void suggest(String name, Supplier<List<String>> suggestionsProvider) {
        suggest(name, (sender, arg) -> suggestionsProvider.get());
    }

    /**
     * Register a new named suggestion provider.
     * When an argument suggestion is configured with this name, calls the function
     * to produce suggestions for that argument.
     *
     * @param name Name of the suggestions provider. The name is case independent.
     * @param suggestionsProvider The suggestions provider
     */
    public void suggest(
            String name,
            BiFunction<CommandContext<CommandSender>, String, List<String>> suggestionsProvider
    ) {
        manager.getParserRegistry().registerSuggestionProvider(name, suggestionsProvider);
    }

    /**
     * Register an injector for a particular type.
     * Will automatically inject this type of object when provided in method signatures.
     *
     * @param clazz    Type that the injector should inject for. This type will matched using
     *                 {@link Class#isAssignableFrom(Class)}
     * @param value    The value to inject where clazz is used
     * @param <T>      Injected type
     */
    public <T> void inject(
            final Class<T> clazz,
            final T value
    ) {
        injector(clazz, (context, annotations) -> value);
    }

    /**
     * Register an injector for a particular type.
     * Will automatically inject this type of object when provided in method signatures.
     *
     * @param clazz    Type that the injector should inject for. This type will matched using
     *                 {@link Class#isAssignableFrom(Class)}
     * @param injector The injector that should inject the value into the command method
     * @param <T>      Injected type
     */
    public <T> void injector(
            final Class<T> clazz,
            final ParameterInjector<CommandSender, T> injector
    ) {
        this.annotationParser.getParameterInjectorRegistry().registerInjector(clazz, injector);
    }

    /**
     * Register an annotation mapper with a constant parameter value
     *
     * @param annotation Annotation class
     * @param parameter  Parameter
     * @param value      Parameter value
     * @param <A>        Annotation type
     * @param <T>        Parameter value type
     */
    public <A extends Annotation, T> void annotationParameter(
            final Class<A> annotation,
            final ParserParameter<T> parameter,
            final T value
    ) {
        manager.getParserRegistry().registerAnnotationMapper(annotation, (a, typeToken) -> {
            return ParserParameters.single(parameter, value);
        });
    }

    /**
     * Register an annotation mapper with a function to read the parameter value
     * from an annotation
     *
     * @param annotation   Annotation class
     * @param parameter    Parameter
     * @param valueMapper  Mapper from annotation to parameter value
     * @param <A>          Annotation type
     * @param <T>          Parameter value type
     */
    public <A extends Annotation, T> void annotationParameter(
            final Class<A> annotation,
            final ParserParameter<T> parameter,
            final Function<A, T> valueMapper
    ) {
        manager.getParserRegistry().registerAnnotationMapper(annotation, (a, typeToken) -> {
            return ParserParameters.single(parameter, valueMapper.apply(a));
        });
    }

    /**
     * Register a preprocessor mapper for an annotation.
     * The preprocessor can be created based on properties of the annotation.
     *
     * @param annotation         Annotation class
     * @param preprocessorMapper Preprocessor mapper
     * @param <A>                Annotation type
     */
    public <A extends Annotation> void preprocessAnnotation(
            final Class<A> annotation,
            final Function<A, BiFunction<CommandContext<CommandSender>, Queue<String>,
                    ArgumentParseResult<Boolean>>> preprocessorMapper
    ) {
        this.annotationParser.registerPreprocessorMapper(annotation, preprocessorMapper);
    }

    /**
     * Register a preprocessor mapper for an annotation.
     * This assumes the annotation does not store any properties, and the
     * remapper is always constant.
     *
     * @param annotation         Annotation class
     * @param preprocessorMapper Preprocessor mapper
     * @param <A>                Annotation type
     */
    public <A extends Annotation> void preprocessAnnotation(
            final Class<A> annotation,
            final BiFunction<CommandContext<CommandSender>, Queue<String>,
                    ArgumentParseResult<Boolean>> preprocessorMapper
    ) {
        preprocessAnnotation(annotation, a -> preprocessorMapper);
    }

    /**
     * Registers an exception handler for a given exception class type. This handler will be called
     * when this type of exception is thrown during command handling.
     * 
     * @param <T> Exception class type
     * @param exceptionType Type of exception to handle
     * @param handler Handler for the exception type
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends Throwable> void handle(Class<T> exceptionType, BiConsumer<CommandSender, T> handler) {
        this.manager.registerExceptionHandler((Class) exceptionType, (BiConsumer) handler);
    }

    /**
     * Registers a message-sending exception handler for a given exception class type. When the exception
     * is handled, the message as specified is sent to the player. If the message matches a caption
     * regex, then the message is first translated.
     * 
     * @param <T>
     * @param exceptionType
     * @param message
     */
    public <T extends Throwable> void handleMessage(Class<T> exceptionType, String message) {
        final Caption caption = Caption.of(message);
        handle(exceptionType, (sender, exception) -> {
            String translated = manager.getCaptionRegistry().getCaption(caption, sender);
            sender.sendMessage(translated);
        });
    }

    /**
     * Registers a class instance containing command annotations
     * 
     * @param <T> Type of annotation holding Object
     * @param annotationsClassInstance Object with command annotations
     */
    public <T> void annotations(T annotationsClassInstance) {
        this.annotationParser.parse(annotationsClassInstance);
    }

    /**
     * Registers all the Localization enum constants declared in a Class as captions
     * 
     * @param localizationDefaults Enum or Class with static LocalizationEnum constants
     */
    public void captionFromLocalization(Class<? extends LocalizationEnum> localizationDefaults) {
        for (LocalizationEnum locale : CommonUtil.getClassConstants(localizationDefaults)) {
            caption(locale.getName(), locale.get().replace("%0%", "{input}"));
        }
    }

    /**
     * Registers a caption with a factory for producing the value for matched captions
     * 
     * @param regex The regex to match
     * @param messageFactory Factory for producing the desired value for a caption
     */
    public void caption(String regex, BiFunction<Caption, CommandSender, String> messageFactory) {
        if (manager.getCaptionRegistry() instanceof SimpleCaptionRegistry) {
            final Caption caption = Caption.of(regex);
            ((SimpleCaptionRegistry<CommandSender>) manager.getCaptionRegistry()).registerMessageFactory(
                    caption, messageFactory
            );
        }
    }

    /**
     * Registers a caption with a fixed value String
     * 
     * @param regex The regex to match
     * @param value The String value to use when the regex is matched
     */
    public void caption(String regex, String value) {
        caption(regex, (caption, sender) -> value);
    }

    /**
     * Registers a new help command for all the commands under a filter prefix
     * 
     * @param filterPrefix Command filter prefix, for commands shown in the menu
     * @param helpDescription Description of the help command
     * @param modifier Modifier for the command applied before registering
     * @return minecraft help command
     */
    public Command<CommandSender> helpCommand(
            List<String> filterPrefix,
            String helpDescription
    ) {
        return helpCommand(filterPrefix, helpDescription, builder -> builder);
    }

    /**
     * Registers a new help command for all the commands under a filter prefix
     * 
     * @param filterPrefix Command filter prefix, for commands shown in the menu
     * @param helpDescription Description of the help command
     * @param modifier Modifier for the command applied before registering
     * @return minecraft help command
     */
    public Command<CommandSender> helpCommand(
            List<String> filterPrefix,
            String helpDescription,
            Function<Command.Builder<CommandSender>, Command.Builder<CommandSender>> modifier
    ) {
        String helpCmd = "/" + String.join(" ", filterPrefix) + " help";
        final MinecraftHelp<CommandSender> help = this.help(helpCmd, filterPrefix);

        // Start a builder
        Command.Builder<CommandSender> command = Command.newBuilder(
                filterPrefix.get(0),
                CommandMeta.simple()
                    .with(CommandMeta.DESCRIPTION, helpDescription)
                    .build());

        // Add literals, then 'help'
        for (int i = 1; i < filterPrefix.size(); i++) {
            command = command.literal(filterPrefix.get(i));
        }
        command = command.literal("help");
        command = command.argument(StringArgument.<CommandSender>newBuilder("query")
                .greedy()
                .asOptional());
        command = command.handler(context -> {
            String query = context.getOrDefault("query", "");
            help.queryCommands(query, context.getSender());
        });
        command = modifier.apply(command);

        // Build & return
        Command<CommandSender> builtCommand = command.build();
        this.manager.command(builtCommand);
        return builtCommand;
    }

    /**
     * Creates a help menu
     * 
     * @param commandPrefix Help command prefix
     * @param filterPrefix Command filter prefix, for commands shown in the menu
     * @return minecraft help
     */
    public MinecraftHelp<CommandSender> help(String commandPrefix, final List<String> filterPrefix) {
        MinecraftHelp<CommandSender> help = new MinecraftHelp<>(
                commandPrefix, /* Help Prefix */
                this.bukkitAudiences::sender, /* Audience mapper */
                this.manager /* Manager */
        );

        help.commandFilter(command -> {
            List<CommandArgument<CommandSender, ?>> args = command.getArguments();
            if (args.size() < filterPrefix.size()) {
                return false;
            }
            for (int i = 0; i < filterPrefix.size(); i++) {
                if (!args.get(i).getName().equals(filterPrefix.get(i))) {
                    return false;
                }
            }
            return true;
        });

        return help;
    }
}

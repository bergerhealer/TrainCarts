package com.bergerkiller.bukkit.tc.properties.registry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;

/**
 * Default traincarts property registry implementation
 */
public final class TCPropertyRegistry implements IPropertyRegistry {
    // Used by findPatternLiterals(String)
    private static final Pattern LITERALS_PATTERN = Pattern.compile("([\\w\\s]+)\\|?");

    private final TrainCarts plugin;
    private final Commands commands;

    // All registered properties and their metadata
    private final Map<IProperty<Object>, PropertyDetails<Object>> properties = new HashMap<>();

    // Used during lookup by name
    private final Map<String, PropertyParserElement<?>> parsersByName = new HashMap<>();
    private final Map<String, PropertyParserElement<?>> parsersByPreProcessedName = new HashMap<>();
    private final List<PropertyParserElement<?>> parsersWithComplexRegex = new ArrayList<>();

    public TCPropertyRegistry(TrainCarts plugin, Commands commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    @Override
    public void register(IProperty<?> property) {
        PropertyDetails<Object> details = CommonUtil.unsafeCast(this.createDetails(property));
        PropertyDetails<Object> previous = properties.put(details.property, details);

        // Already registered
        if (details.property == previous) {
            return;
        }

        // Unregister a previous property that is overridden
        if (previous != null) {
            previous.parsers.forEach(this::unregisterParser);
        }

        // Register new parsers
        details.parsers.forEach(this::registerParser);
    }

    @Override
    public void unregister(IProperty<?> property) {
        PropertyDetails<Object> removed = properties.remove(property);
        if (removed != null) {
            removed.parsers.forEach(this::unregisterParser);
        }
    }

    @Override
    public <T> IProperty<T> find(String name) {
        RegistryParseContext context = new RegistryParseContext(plugin, null, name, "");
        PropertyParserElement<?> result = this.findParserElement(context);
        return (result == null) ? null : CommonUtil.unsafeCast(result.property);
    }

    @Override
    public <T> PropertyParseResult<T> parse(IProperties properties, String name, String input) {
        RegistryParseContext context = new RegistryParseContext(plugin, properties, name, input);
        PropertyParserElement<?> result = this.findParserElement(context);
        if (result == null) {
            return PropertyParseResult.failPropertyNotFound(name);
        } else {
            return CommonUtil.unsafeCast(result.parse(context));
        }
    }

    @Override
    public Collection<IProperty<Object>> all() {
        return Collections.unmodifiableCollection(properties.keySet());
    }

    private <T> void registerParser(final PropertyParserElement<T> parser) {
        if (parser.literals.isEmpty()) {
            this.parsersWithComplexRegex.add(parser);
        } else {
            Map<String, PropertyParserElement<?>> literalMap = parser.preProcess ?
                    this.parsersByPreProcessedName : this.parsersByName;
            parser.literals.forEach(literal -> literalMap.put(literal, parser));
        }
    }

    private <T> void unregisterParser(PropertyParserElement<T> parser) {
        if (parser.literals.isEmpty()) {
            this.parsersWithComplexRegex.remove(parser);
        } else {
            Map<String, PropertyParserElement<?>> literalMap = parser.preProcess ?
                    this.parsersByPreProcessedName : this.parsersByName;
            for (String literal : parser.literals) {
                // Remove literal, if mapped to a different parser, cancel
                PropertyParserElement<?> removed = literalMap.remove(literal);
                if (removed != parser && removed != null) {
                    literalMap.put(literal, removed);
                }
            }
        }
    }

    private PropertyParserElement<?> findParserElement(RegistryParseContext context) {
        PropertyParserElement<?> result;

        // By name exactly
        if ((result = this.parsersByName.get(context.name)) != null &&
            result.match(context))
        {
            return result;
        }

        // By pre-processed name
        if ((result = this.parsersByPreProcessedName.get(context.namePreProcessed)) != null &&
            result.match(context))
        {
            return result;
        }

        // Complex
        for (PropertyParserElement<?> parser : this.parsersWithComplexRegex) {
            if (parser.match(context)) {
                return parser;
            }
        }

        // Not found
        return null;
    }

    /**
     * Initializes new metadata information for a given property
     * 
     * @param <T> Type of property
     * @param property Property
     * @return PropertyDetails
     */
    private <T> PropertyDetails<T> createDetails(final IProperty<T> property) {
        List<PropertyParserElement<T>> parsers = ReflectionUtil.getAllMethods(property.getClass())
            .map(method -> {
                PropertyParser parser = method.getAnnotation(PropertyParser.class);
                if (parser == null) {
                    return null;
                }
                try {
                    return new PropertyParserElement<T>(property, parser, method);
                } catch (PatternSyntaxException ex) {
                    plugin.getLogger().log(Level.WARNING, "Invalid syntax of property parser " + method.toGenericString(), ex);
                    return null;
                } catch (ParserIncorrectSignatureException ex) {
                    plugin.getLogger().log(Level.WARNING, "Invalid method signature of property parser", ex);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return new PropertyDetails<T>(property, parsers);
    }

    /**
     * Checks a regex pattern to see if it only specifies literals, not any other
     * kind of pattern syntax. If so, returns a list of literals defined.<br>
     * <br>
     * This parses the literals of:
     * <ul>
     * <li>(name|othername|andanothername)</li>
     * <li>name|othername|andanothername</li>
     * <li>onename</li>
     * </ul>
     * And returns a list of names. If the list returned is empty,
     * then the pattern matches no such exact literals.
     * 
     * @param pattern
     * @return list of literals defined by the pattern, or an empty list
     *         if no(t) (only) literals are defined.
     */
    public static List<String> findPatternLiterals(String pattern) {
        // Omit surrounding (), but if there's other () in there, matching will fail
        if (pattern.startsWith("(") && pattern.endsWith(")")) {
            pattern = pattern.substring(1, pattern.length() - 1);
        }

        // Perform matching
        Matcher matcher = LITERALS_PATTERN.matcher(pattern);

        // Find all the literals being matched
        // Once a group hits the end of the pattern, then it is successful
        // Expect groups to start at the end of the previous group + 1
        // First group should be at index = 0
        int expectedStart = 0;
        int endIndex = pattern.length();
        List<String> literals = new ArrayList<String>();
        while (matcher.find() && matcher.start() == expectedStart) {
            // Add literal, if end of string is reached, we're done
            literals.add(matcher.group(1));
            if (matcher.end() == endIndex) {
                return literals;
            }

            // Next literal should be at previous group end
            expectedStart = matcher.end();
        }

        // There was additional garbage at the end, fail
        return Collections.emptyList();
    }

    /**
     * Stores metadata for a single registered IProperty
     * 
     * @param <T> Type of property
     */
    private static class PropertyDetails<T> {
        public final IProperty<T> property;
        public final List<PropertyParserElement<T>> parsers;

        public PropertyDetails(IProperty<T> property, List<PropertyParserElement<T>> parsers) {
            this.property = property;
            this.parsers = parsers;
        }
    }

    /**
     * A single property parser element that can be executed
     */
    public static class PropertyParserElement<T> {
        public final IProperty<T> property;
        public final FastMethod<T> method;
        public final List<String> literals;
        private final boolean inputIsString; // if true, is String, otherwise is PropertyParseContext
        private final boolean preProcess;
        private final Pattern pattern;

        public PropertyParserElement(IProperty<T> property, PropertyParser parser, Method method)
                throws PatternSyntaxException, ParserIncorrectSignatureException
        {
            // Validate the method is proper
            if (Modifier.isStatic(method.getModifiers())) {
                throw new ParserIncorrectSignatureException(method, "Must not be a static method");
            } else if (method.getParameterCount() != 1) {
                throw new ParserIncorrectSignatureException(method, "Parameter count should be 1");
            } else if (method.getReturnType() == void.class) {
                throw new ParserIncorrectSignatureException(method, "Method should return a value, but return type is void");
            }

            // Check argument of method, must be PropertyParseContext or String
            Class<?> paramType = method.getParameterTypes()[0];
            this.inputIsString = paramType.equals(String.class);
            if (!this.inputIsString && !paramType.isAssignableFrom(PropertyParseContext.class)) {
                throw new ParserIncorrectSignatureException(method, "First argument should be PropertyParseContext or String");
            }

            // Initialize all the fields
            this.property = property;
            this.preProcess = parser.preProcess();
            this.pattern = Pattern.compile(parser.value());
            this.method = new FastMethod<T>();
            this.method.init(method);
            this.literals = findPatternLiterals(parser.value());
        }

        /**
         * Checks whether this parser element matches the input, and if so, stores the
         * match result in the context and returns true. Returns false if it does not match.
         * 
         * @param registryContext Context of the parse operation
         * @return true if this parser element matches, false if not
         */
        public boolean match(RegistryParseContext registryContext) {
            String name = (this.preProcess ? registryContext.name : registryContext.namePreProcessed);
            Matcher matcher = this.pattern.matcher(name);
            if (matcher.find()) {
                registryContext.matchResult = matcher;
                return true;
            }
            return false;
        }

        /**
         * Uses a previous {@link #match(RegistryParseContext)} MatchResult to parse the
         * input value text using a property parser method.
         * 
         * @param registryContext Context of the parse operation
         * @param property parse result
         */
        public PropertyParseResult<T> parse(RegistryParseContext registryContext) {
            try {
                T value;
                if (this.inputIsString) {
                    // Send input value straight to the parser
                    value = this.method.invoke(this.property, registryContext.input);
                } else {
                    // Create a context with the previous value, first

                    // Name as understood by the parser
                    String name = (this.preProcess ? registryContext.name : registryContext.namePreProcessed);

                    // Current value, or default if this fails
                    T currentValue;
                    if (registryContext.properties != null) {
                        try {
                            currentValue = registryContext.properties.get(this.property);
                        } catch (Throwable t) {
                            registryContext.plugin.getLogger().log(Level.SEVERE,
                                    "Failed to read property value of '" + registryContext.name + "'", t);

                            currentValue = this.property.getDefault();
                        }
                    } else {
                        currentValue = this.property.getDefault();
                    }

                    // Context
                    PropertyParseContext<T> context = new PropertyParseContext<T>(
                            registryContext.properties,
                            currentValue,
                            name,
                            registryContext.input,
                            registryContext.matchResult
                    );

                    // Parse
                    value = this.method.invoke(this.property, context);
                }

                return PropertyParseResult.success(this.property, value);
            }
            catch (PropertyInvalidInputException ex)
            {
                return PropertyParseResult.failInvalidInput(this.property, ex.getMessage());
            }
            catch (Throwable t)
            {
                registryContext.plugin.getLogger().log(Level.SEVERE,
                        "Failed to parse property '" + registryContext.name + "'", t);

                return PropertyParseResult.failError(this.property, registryContext.name);
            }
        }
    }

    /**
     * Stores the input to a property parse operation
     */
    public static class RegistryParseContext {
        public final TrainCarts plugin;
        public final IProperties properties;
        public final String name;
        public final String namePreProcessed;
        public final String input;
        public MatchResult matchResult;

        public RegistryParseContext(TrainCarts plugin, IProperties properties, String name, String input) {
            this.plugin = plugin;
            this.properties = properties;
            this.name = name;
            this.namePreProcessed = name.trim().toLowerCase(Locale.ENGLISH);
            this.input = input;
            this.matchResult = null;
        }
    }

    /**
     * Exception thrown when a method with a {@link PropertyParser} annotation
     * has incorrect method arguments or return type.
     */
    public static class ParserIncorrectSignatureException extends Exception {
        private static final long serialVersionUID = -1679698260727072778L;

        public ParserIncorrectSignatureException(Method method, String reason) {
            super("Method " + method.toGenericString() + " has invalid signature for a parser: " + reason);
        }
    }
}

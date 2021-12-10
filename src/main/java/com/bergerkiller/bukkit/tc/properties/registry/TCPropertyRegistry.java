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
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.cloud.CloudSimpleHandler;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.commands.selector.TCSelectorHandlerRegistry;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.IPropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.PropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;

/**
 * Default traincarts property registry implementation
 */
public final class TCPropertyRegistry implements IPropertyRegistry {
    // Used by findPatternLiterals(String)
    private static final Pattern LITERALS_PATTERN = Pattern.compile("([\\w\\s]+)\\|?");

    private final TrainCarts plugin;
    private final CloudSimpleHandler commands;

    // All registered properties and their metadata
    private final Map<IProperty<Object>, PropertyDetails<Object>> properties = new HashMap<>();

    // Used during lookup by name
    private final Map<String, PropertyParserElement<?>> parsersByName = new HashMap<>();
    private final Map<String, PropertyParserElement<?>> parsersByPreProcessedName = new HashMap<>();
    private final List<PropertyParserElement<?>> parsersWithComplexRegex = new ArrayList<>();
    private final List<IProperty<?>> pendingProperties = new ArrayList<IProperty<?>>();

    // Used during parse() to see what property is being parsed currently
    private IProperty<?> currentPropertyBeingParsed = null;

    public TCPropertyRegistry(TrainCarts plugin, CloudSimpleHandler commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    public void enable() {
        // Register the PropertyCheckPermission annotation, which will run
        // a handler to check various property permissions before executing
        // a command handler.
        this.commands.getParser().registerBuilderModifier(PropertyCheckPermission.class, (annot, builder) -> {
            final IProperty<?> property = currentlyParsedProperty();
            final String propertyName = annot.value();
            return builder.prependHandler(context -> {
                property.handlePermission(context.getSender(), propertyName);
            });
        });

        // Register commands of properties that were added before this registry was enabled
        pendingProperties.forEach(this::parsePropertyAnnotations);
        pendingProperties.clear();
    }

    private IProperty<?> currentlyParsedProperty() {
        if (this.currentPropertyBeingParsed == null) {
            throw new IllegalStateException("No property is being parsed right now");
        }
        return this.currentPropertyBeingParsed;
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
            previous.conditions.forEach(this::unregisterCondition);
        }

        // Register new property
        details.parsers.forEach(this::registerParser);
        details.conditions.forEach(this::registerCondition);

        // Register cloud commands declared inside the property
        if (this.commands.isEnabled()) {
            parsePropertyAnnotations(property);
        } else {
            this.pendingProperties.add(property);
        }
    }

    private void parsePropertyAnnotations(IProperty<?> property) {
        // Register cloud commands declared inside the property
        // Note: does not support unregister!
        this.currentPropertyBeingParsed = property;
        try {
            this.commands.annotations(property);
        } finally {
            this.currentPropertyBeingParsed = null;
        }
    }

    @Override
    public void unregister(IProperty<?> property) {
        PropertyDetails<Object> removed = properties.remove(property);
        if (removed != null) {
            removed.parsers.forEach(this::unregisterParser);
        }
    }

    @Override
    public <T> Optional<IPropertyParser<T>> findParser(String name) {
        RegistryPropertyParser<T> search = new RegistryPropertyParser<T>(this.plugin, name);
        return this.findParserElement(search) ? Optional.of(search) : Optional.empty();
    }

    @Override
    public Collection<IProperty<Object>> all() {
        return Collections.unmodifiableCollection(properties.keySet());
    }

    private void registerCondition(PropertySelectorConditionElement<?> condition) {
        TCSelectorHandlerRegistry registry = (TCSelectorHandlerRegistry) this.plugin.getSelectorHandlerRegistry();
        for (String name : condition.names) {
            registry.registerCondition(name, condition);
        }
    }

    private void unregisterCondition(PropertySelectorConditionElement<?> condition) {
        TCSelectorHandlerRegistry registry = (TCSelectorHandlerRegistry) this.plugin.getSelectorHandlerRegistry();
        for (String name : condition.names) {
            registry.unregisterCondition(name);
        }
    }

    private <T> void registerParser(final PropertyParserElement<T> parser) {
        List<String> literals = findPatternLiterals(parser.options.value());
        if (literals.isEmpty()) {
            this.parsersWithComplexRegex.add(parser);
        } else {
            Map<String, PropertyParserElement<?>> literalMap = parser.options.preProcess() ?
                    this.parsersByPreProcessedName : this.parsersByName;
            literals.forEach(literal -> literalMap.put(literal, parser));
        }
    }

    private <T> void unregisterParser(PropertyParserElement<T> parser) {
        List<String> literals = findPatternLiterals(parser.options.value());
        if (literals.isEmpty()) {
            this.parsersWithComplexRegex.remove(parser);
        } else {
            Map<String, PropertyParserElement<?>> literalMap = parser.options.preProcess() ?
                    this.parsersByPreProcessedName : this.parsersByName;
            for (String literal : literals) {
                // Remove literal, if mapped to a different parser, cancel
                PropertyParserElement<?> removed = literalMap.remove(literal);
                if (removed != parser && removed != null) {
                    literalMap.put(literal, removed);
                }
            }
        }
    }

    private <T> boolean findParserElement(RegistryPropertyParser<T> parser) {
        PropertyParserElement<T> result;

        // By name exactly
        if ((result = CommonUtil.unsafeCast(this.parsersByName.get(parser.name))) != null &&
            result.match(parser))
        {
            return true;
        }

        // By pre-processed name
        if ((result = CommonUtil.unsafeCast(this.parsersByPreProcessedName.get(parser.namePreProcessed))) != null &&
            result.match(parser))
        {
            return true;
        }

        // Complex
        for (PropertyParserElement<?> complexParserElementRaw : this.parsersWithComplexRegex) {
            if ((result = CommonUtil.unsafeCast(complexParserElementRaw)).match(parser)) {
                return true;
            }
        }

        // Not found
        return false;
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

        List<PropertySelectorConditionElement<T>> conditions = ReflectionUtil.getAllMethods(property.getClass())
                .map(method -> {
                    PropertySelectorCondition[] options = method.getAnnotationsByType(PropertySelectorCondition.class);
                    if (options.length == 0) {
                        return null;
                    }
                    try {
                        return new PropertySelectorConditionElement<T>(property, options, method);
                    } catch (SelectorConditionIncorrectSignatureException ex) {
                        plugin.getLogger().log(Level.WARNING, "Invalid method signature of property selector condition", ex);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PropertyDetails<T>(property, parsers, conditions);
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
        public final List<PropertySelectorConditionElement<T>> conditions;

        public PropertyDetails(IProperty<T> property, List<PropertyParserElement<T>> parsers,
                List<PropertySelectorConditionElement<T>> conditions
        ) {
            this.property = property;
            this.parsers = parsers;
            this.conditions = conditions;
        }
    }

    /**
     * A single property parser element that can be executed
     */
    public static class PropertyParserElement<T> {
        public final IProperty<T> property;
        public final FastMethod<T> method;
        public final PropertyParser options;
        public final boolean inputIsString; // if true, is String, otherwise is PropertyParseContext
        private final Pattern pattern;

        public PropertyParserElement(IProperty<T> property, PropertyParser options, Method method)
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
            this.options = options;
            this.pattern = Pattern.compile(anchorRegex(options.value()));
            this.method = new FastMethod<T>();
            this.method.init(method);
        }

        /**
         * Checks whether this parser element matches the input, and if so, stores the
         * match result in the context and returns true. Returns false if it does not match.
         * 
         * @param registryContext Context of the parse operation
         * @return true if this parser element matches, false if not
         */
        public boolean match(RegistryPropertyParser<T> parser) {
            Matcher matcher = this.pattern.matcher(options.preProcess() ? parser.namePreProcessed : parser.name);
            if (matcher.find()) {
                parser.parser = this;
                parser.matchResult = matcher;
                return true;
            }
            return false;
        }

        private static String anchorRegex(String expression) {
            if (!expression.startsWith("^")) {
                expression = "^" + expression;
            }
            if (!expression.endsWith("$")) {
                expression = expression + "$";
            }
            return expression;
        }
    }

    /**
     * An initialized selector condition that calls a method in the property instance
     * to match the expression.
     */
    public static class PropertySelectorConditionElement<T> implements IPropertySelectorCondition {
        public final IProperty<T> property;
        public final String[] names;
        private final ArgumentAdapter[] argumentAdapters;
        private final ReturnAdapter returnAdapter;
        private final FastMethod<Object> method;

        public PropertySelectorConditionElement(
                IProperty<T> property,
                PropertySelectorCondition[] options,
                Method method) throws SelectorConditionIncorrectSignatureException
        {
            // Validate the method is proper
            if (Modifier.isStatic(method.getModifiers())) {
                throw new SelectorConditionIncorrectSignatureException(method, "Must not be a static method");
            } else if (method.getReturnType() == void.class) {
                throw new SelectorConditionIncorrectSignatureException(method, "Method should return a value, but return type is void");
            }

            // Adapt all method arguments so they can be filled in
            // Some may be omitted or swapped places, this takes care of that
            Class<?>[] argTypes = method.getParameterTypes();
            this.argumentAdapters = new ArgumentAdapter[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                Class<?> argType = argTypes[i];
                if (argType.isAssignableFrom(TrainProperties.class)) {
                    this.argumentAdapters[i] = (properties, condition) -> properties;
                } else if (argType.isAssignableFrom(SelectorCondition.class)) {
                    this.argumentAdapters[i] = (properties, condition) -> condition;
                } else {
                    throw new SelectorConditionIncorrectSignatureException(method, "Method parameter #" + (i+1)
                            + " has incompatible type " + argType.getName());
                }
            }

            // Adapt the value returned by the method. Numbers we can automatically process.
            Class<?> returnType = BoxedType.getBoxedType(method.getReturnType());
            if (returnType == null) {
                returnType = method.getReturnType();
            }
            if (returnType == Boolean.class) {
                this.returnAdapter = (condition, value) -> ((Boolean) value).booleanValue();
            } else if (returnType == String.class) {
                this.returnAdapter = (condition, value) -> condition.matchesText((String) value);
            } else if (returnType == Float.class || returnType == Double.class) {
                this.returnAdapter = (condition, value) -> condition.matchesNumber(((Number) value).doubleValue());
            } else if (Number.class.isAssignableFrom(returnType)) {
                this.returnAdapter = (condition, value) -> condition.matchesNumber(((Number) value).longValue());
            } else {
                throw new SelectorConditionIncorrectSignatureException(method, "Method has incompatible return type "
                        + returnType.getName());
            }

            this.property = property;
            this.names = Stream.of(options).map(PropertySelectorCondition::value).toArray(String[]::new);
            this.method = new FastMethod<Object>();
            this.method.init(method);
        }

        @Override
        public boolean matches(TrainProperties properties, SelectorCondition condition) {
            Object[] args = new Object[this.argumentAdapters.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = this.argumentAdapters[i].adapt(properties, condition);
            }
            Object result = this.method.invokeVA(this.property, args);
            return this.returnAdapter.adapt(condition, result);
        }

        @FunctionalInterface
        private static interface ArgumentAdapter {
            Object adapt(TrainProperties properties, SelectorCondition condition);
        }

        @FunctionalInterface
        private static interface ReturnAdapter {
            boolean adapt(SelectorCondition condition, Object value);
        }
    }

    /**
     * An initialized property parser that was found by name
     * 
     * @param <T> parser value type
     */
    private static class RegistryPropertyParser<T> implements IPropertyParser<T> {
        public final TrainCarts plugin;
        public final String name;
        public final String namePreProcessed;
        public PropertyParserElement<T> parser;
        public MatchResult matchResult;

        public RegistryPropertyParser(TrainCarts plugin, String name) {
            this.plugin = plugin;
            this.name = name;
            this.namePreProcessed = name.trim().toLowerCase(Locale.ENGLISH);
            this.parser = null;
            this.matchResult = null;
        }

        @Override
        public IProperty<T> getProperty() {
            return parser.property;
        }

        @Override
        public String getName() {
            return parser.options.preProcess() ? namePreProcessed : name;
        }

        @Override
        public boolean isInputPreProcessed() {
            return parser.options.preProcess();
        }

        @Override
        public boolean isProcessedPerCart() {
            return parser.options.processPerCart();
        }

        @Override
        public PropertyParseResult<T> parse(IProperties properties, String input) {
            // Property and name as understood by this parser
            IProperty<T> property = this.getProperty();
            String name = this.getName();

            try {
                T value;
                if (this.parser.inputIsString) {
                    // Send input value straight to the parser
                    value = this.parser.method.invoke(property, input);
                } else {
                    // Current value, or default if this fails
                    T currentValue;
                    if (properties != null) {
                        try {
                            currentValue = properties.get(property);
                        } catch (Throwable t) {
                            this.plugin.getLogger().log(Level.SEVERE,
                                    "Failed to read property value of '" + this.name + "'", t);

                            currentValue = property.getDefault();
                        }
                    } else {
                        currentValue = property.getDefault();
                    }

                    // Context
                    PropertyParseContext<T> context = new PropertyParseContext<T>(
                            properties,
                            currentValue,
                            name,
                            input,
                            this.matchResult
                    );

                    // Parse
                    value = this.parser.method.invoke(property, context);
                }

                return PropertyParseResult.success(property, name, value);
            }
            catch (PropertyInvalidInputException ex)
            {
                return PropertyParseResult.failInvalidInput(property, name,
                        Localization.PROPERTY_INVALID_INPUT.get(
                                name, input, ex.getMessage()));
            }
            catch (Throwable t)
            {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Failed to parse property '" + this.name + "'", t);

                return PropertyParseResult.failError(property, this.name, input);
            }
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

    /**
     * Exception thrown when a method with a {@link PropertySelectorCondition} annotation
     * has incorrect method arguments or return type.
     */
    public static class SelectorConditionIncorrectSignatureException extends Exception {
        private static final long serialVersionUID = 9081453776342069335L;

        public SelectorConditionIncorrectSignatureException(Method method, String reason) {
            super("Method " + method.toGenericString() + " has invalid signature for a selector condition: " + reason);
        }
    }
}

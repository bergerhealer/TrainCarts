package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.controller.functions.inputs.TransferFunctionInput;
import com.bergerkiller.bukkit.tc.controller.functions.inputs.TransferFunctionInputProperty;
import com.bergerkiller.bukkit.tc.controller.functions.inputs.TransferFunctionInputSpeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Tracks all transfer function types that are available. Custom ones can be registered.
 */
public class TransferFunctionRegistry {
    static final TransferFunctionRegistry INSTANCE = new TransferFunctionRegistry();
    private final Map<String, TransferFunction.Serializer<?>> byTypeId = new HashMap<>();
    private List<TransferFunction.Serializer<?>> values = Collections.emptyList();

    public TransferFunctionRegistry() {
        // Register all the default traincarts types
        register(TransferFunctionConstant.SERIALIZER);
        register(TransferFunctionBoolean.SERIALIZER);
        register(TransferFunctionInputSpeed.SERIALIZER);
        register(TransferFunctionInputProperty.SERIALIZER);
        register(TransferFunctionCurve.SERIALIZER);
        register(TransferFunctionList.SERIALIZER);
        register(TransferFunctionIdentity.SERIALIZER);
        register(TransferFunctionConditional.SERIALIZER);
    }

    /**
     * Returns an unmodifiable list of registered serializers known at this time.
     * The list is sorted.
     *
     * @return List of serializers
     */
    public List<TransferFunction.Serializer<?>> all() {
        return values;
    }

    /**
     * Registers a new Transfer Function Serializer. This serializer is responsible for loading,
     * saving and creating this type of transfer function.
     *
     * @param serializer Serializer of the Transfer Function to register
     */
    public void register(TransferFunction.Serializer<?> serializer) {
        if (values.contains(serializer)) {
            return;
        }

        // Listing (by title)
        {
            List<TransferFunction.Serializer<?>> newValues = new ArrayList<>(values);
            newValues.add(serializer);
            newValues.sort(Comparator.comparing(TransferFunction.Serializer::title));
            values = Collections.unmodifiableList(newValues);
        }

        // Loading/saving registry
        {
            String id = serializer.typeId();
            byTypeId.put(id, serializer);
            byTypeId.put(id.toLowerCase(Locale.ENGLISH), serializer);
            byTypeId.put(id.toUpperCase(Locale.ENGLISH), serializer);
        }
    }

    /**
     * Un-registers a transfer function that was previously registered
     *
     * @param serializer Transfer Function Serializer to un-register
     * @see #register(TransferFunction.Serializer)
     */
    public void unregister(TransferFunction.Serializer<?> serializer) {
        int index = values.indexOf(serializer);
        if (index == -1) {
            return;
        }

        // Listing (by title)
        {
            List<TransferFunction.Serializer<?>> newValues = new ArrayList<>(values);
            newValues.remove(index);
            values = Collections.unmodifiableList(newValues);
        }

        // Loading/saving registry
        {
            String id = serializer.typeId();
            byTypeId.remove(id, serializer);
            byTypeId.remove(id.toLowerCase(Locale.ENGLISH), serializer);
            byTypeId.remove(id.toUpperCase(Locale.ENGLISH), serializer);
        }
    }

    public TransferFunction load(TransferFunctionHost host, ConfigurationNode config) {
        String typeId = config.getOrDefault(TransferFunction.Serializer.TYPE_FIELD, "");
        TransferFunction.Serializer<?> serializer = byTypeId.get(typeId);
        if (serializer == null) {
            serializer = byTypeId.get(typeId.toUpperCase(Locale.ENGLISH));
        }
        if (serializer == null) {
            return new TransferFunctionUnknown(typeId, config, false);
        } else {
            try {
                return serializer.load(host, config);
            } catch (Throwable t) {
                host.getTrainCarts().getLogger().log(Level.SEVERE, "Failed to load function of type " + typeId, t);
                return new TransferFunctionUnknown(typeId, config, true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public ConfigurationNode save(TransferFunctionHost host, TransferFunction function) {
        TransferFunction.Serializer<TransferFunction> serializer =
                (TransferFunction.Serializer<TransferFunction>) function.getSerializer();
        String typeId = "GET_TYPE_ID_FAILED";
        try {
            typeId = serializer.typeId();
            ConfigurationNode config = new ConfigurationNode();
            config.set(TransferFunction.Serializer.TYPE_FIELD, typeId);
            serializer.save(host, config, function);
            return config;
        } catch (Throwable t) {
            host.getTrainCarts().getLogger().log(Level.SEVERE, "Failed to save transfer function of type " + typeId, t);
            ConfigurationNode config = new ConfigurationNode();
            config.set(TransferFunction.Serializer.TYPE_FIELD, typeId);
            return config;
        }
    }
}

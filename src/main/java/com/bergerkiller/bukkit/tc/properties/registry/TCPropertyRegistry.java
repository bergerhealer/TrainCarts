package com.bergerkiller.bukkit.tc.properties.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;

/**
 * Default traincarts property registry implementation
 */
public final class TCPropertyRegistry implements IPropertyRegistry {
    private final Commands commands;
    private final List<IProperty<Object>> properties = new ArrayList<IProperty<Object>>();
    private final Map<String, IProperty<Object>> byName = new HashMap<String, IProperty<Object>>();

    public TCPropertyRegistry(Commands commands) {
        this.commands = commands;
    }

    @Override
    public void register(IProperty<?> property) {
        IProperty<Object> propertyObj = CommonUtil.unsafeCast(property);
        properties.add(propertyObj);
        for (String name : property.getNames()) {
            byName.put(createLookupKey(name), propertyObj);
        }
    }

    @Override
    public void unregister(IProperty<?> property) {
        properties.remove(property);
        for (String name : property.getNames()) {
            String key = createLookupKey(name);
            IProperty<Object> removed = byName.remove(key);
            if (removed != null && !removed.equals(property)) {
                byName.put(key, removed);
            }
        }
    }

    @Override
    public IProperty<Object> find(String name) {
        return byName.get(createLookupKey(name));
    }

    @Override
    public List<IProperty<Object>> all() {
        return Collections.unmodifiableList(properties);
    }

    private static String createLookupKey(String name) {
        return name.trim().toLowerCase(Locale.ENGLISH);
    }
}

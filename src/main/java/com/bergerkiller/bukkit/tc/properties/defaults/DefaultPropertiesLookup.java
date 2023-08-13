package com.bergerkiller.bukkit.tc.properties.defaults;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caches and looks up default properties by name. If the registered properties
 * changes, regenerates the default properties instance automatically.<br>
 * <br>
 * Defaults are read from a file configuration.
 */
public class DefaultPropertiesLookup {
    private static final String defaultPropertiesFile = "DefaultTrainProperties.yml";
    private final FileConfiguration config;
    private final Map<String, CachedDefaultProperties> defaultPropertiesByName;
    private final List<CachedDefaultProperties> namedDefaults;
    private final CachedDefaultProperties defaultProperties;

    // This information is regenerated if properties get registered/de-registered
    private Collection<IProperty<Object>> allPropertiesAtTimeOfCaching;

    private DefaultPropertiesLookup(FileConfiguration config) {
        this.config = config;

        // Load all nodes into a CachedDefaultProperties instance
        this.defaultPropertiesByName = new HashMap<>(config.getNodes().size());
        for (ConfigurationNode node : config.getNodes()) {
            this.defaultPropertiesByName.put(node.getName(), new CachedDefaultProperties(node));
        }
        this.defaultProperties = this.defaultPropertiesByName.get("default"); // Always exists! See load()
        if (this.defaultProperties == null) {
            throw new IllegalStateException("No default configuration is included");
        }

        // Fill a List with all the non-builtin properties. These are used for permission checks.
        // Permissions are checked in alphabetical order so it's important to sort them by name.
        this.namedDefaults = new ArrayList<>(defaultPropertiesByName.size());
        for (CachedDefaultProperties props : defaultPropertiesByName.values()) {
            if (!LogicUtil.contains(props.name(), "default", "spawner")) {
                this.namedDefaults.add(props);
            }
        }
        this.namedDefaults.sort(Comparator.comparing(CachedDefaultProperties::name));

        // Invalidates everything if this changes. All() caches internally and we make use of that.
        this.allPropertiesAtTimeOfCaching = IPropertyRegistry.instance().all();
    }

    private void invalidateIfPropertiesChanged() {
        Collection<IProperty<Object>> all = IPropertyRegistry.instance().all();
        if (this.allPropertiesAtTimeOfCaching != all) {
            this.allPropertiesAtTimeOfCaching = all;
            defaultPropertiesByName.values().forEach(CachedDefaultProperties::invalidate);
        }
    }

    /**
     * Gets the default properties mapped to a specified name. If no defaults exist by
     * this name, returns <i>null</i>
     *
     * @param name Default Properties name
     * @return Default Properties, or null if not found
     */
    public DefaultProperties getByName(String name) {
        invalidateIfPropertiesChanged();

        CachedDefaultProperties props = defaultPropertiesByName.get(name);
        return (props == null) ? null : props.get();
    }

    /**
     * Gets the default properties that should be used when trains are spawned by
     * the player specified. Makes use of the train.properties.[name] permission
     * to figure this out. If none of the permissions match returns the "default"
     * properties.
     *
     * @param player Player to find default train properties for
     * @return default properties
     */
    public DefaultProperties getForPlayer(Player player) {
        invalidateIfPropertiesChanged();

        for (CachedDefaultProperties props : namedDefaults) {
            if (props.hasPermission(player)) {
                return props.get();
            }
        }

        return defaultProperties.get();
    }

    /**
     * Loads the default train properties from the DefaultTrainProperties.yml file
     * bundled with the plugin. Generates the file for the first time if parts
     * are missing.
     *
     * @param traincarts TrainCarts plugin instance
     * @return DefaultPropertiesLookup
     */
    public static DefaultPropertiesLookup load(TrainCarts traincarts) {
        FileConfiguration defconfig = new FileConfiguration(traincarts, defaultPropertiesFile);
        defconfig.load();
        boolean changed = false;
        if (!defconfig.contains("default")) {
            ConfigurationNode node = defconfig.getNode("default");

            // Store all default properties, if they exist
            for (IProperty<Object> property : IPropertyRegistry.instance().all()) {
                if (property.isAppliedAsDefault()) {
                    Object value = property.getDefault();
                    if (value != null) {
                        property.writeToConfig(node, Optional.of(value));
                    }
                }
            }

            // These defaults are only read, never written
            node.set("blockTypes", "");
            node.set("blockOffset", "unset");

            changed = true;
        }
        if (!defconfig.contains("admin")) {
            ConfigurationNode node = defconfig.getNode("admin");
            for (Map.Entry<String, Object> entry : defconfig.getNode("default").getValues().entrySet()) {
                node.set(entry.getKey(), entry.getValue());
            }
            changed = true;
        }
        if (!defconfig.contains("spawner")) {
            ConfigurationNode node = defconfig.getNode("spawner");
            for (Map.Entry<String, Object> entry : defconfig.getNode("default").getValues().entrySet()) {
                node.set(entry.getKey(), entry.getValue());
            }
            changed = true;
        }
        if (TrainPropertiesStore.fixDeprecation(defconfig)) {
            changed = true;
        }
        if (changed) {
            defconfig.save();
        }
        return new DefaultPropertiesLookup(defconfig);
    }

    private static class CachedDefaultProperties {
        private final ConfigurationNode config;
        private final String permNode;
        private DefaultProperties cachedProperties;

        public CachedDefaultProperties(ConfigurationNode config) {
            this.config = config;
            this.permNode = "train.properties." + config.getName();
            this.cachedProperties = null;
        }

        public String name() {
            return config.getName();
        }

        public boolean hasPermission(Player player) {
            return CommonUtil.hasPermission(player, permNode);
        }

        public void invalidate() {
            cachedProperties = null;
        }

        public DefaultProperties get() {
            DefaultProperties cached = this.cachedProperties;
            if (cached == null) {
                this.cachedProperties = cached = DefaultProperties.of(config);
            }
            return cached;
        }
    }
}

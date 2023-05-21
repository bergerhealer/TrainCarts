package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.BasicModularConfiguration;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ModularConfigurationEntry;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ModularConfigurationFile;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ModularConfigurationModule;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ReadOnlyModuleException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;

/**
 * Stores the train and cart properties for trains that have been saved using /train save.
 * These properties can also be used on spawner signs, or fused into spawning items.
 */
public abstract class SavedTrainPropertiesStore implements TrainCarts.Provider {
    private static final String KEY_SAVED_NAME = "savedName";

    protected final TrainCarts traincarts;
    protected final ModularConfigurationEntry.Container<SavedTrainProperties> container;

    protected SavedTrainPropertiesStore(TrainCarts traincarts,
                                        ModularConfigurationEntry.Container<SavedTrainProperties> container)
    {
        this.traincarts = traincarts;
        this.container = container;
    }

    /**
     * Creates and initializes a new SavedTrainPropertiesStore
     *
     * @param traincarts Main TrainCarts plugin instance
     * @param filename Main SavedTrainProperties.yml file path
     * @param directoryName SavedTrainModules directory path
     * @return new store
     */
    public static SavedTrainPropertiesStore create(TrainCarts traincarts, String filename, String directoryName) {
        ModularConfig modularConfig = new ModularConfig(traincarts, filename, directoryName);
        return new DefaultStore(traincarts, modularConfig);
    }

    static SavedTrainPropertiesStore createModule(ModularConfigurationModule<SavedTrainProperties> module) {
        ModularConfig modularConfig = (ModularConfig) module.getMain();
        if (module == modularConfig.getDefaultModule()) {
            return modularConfig.traincarts.getSavedTrains();
        } else {
            return new ModuleStore(modularConfig.traincarts, module);
        }
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    /**
     * Gets the name of this saved train store. If this is a module,
     * returns the name of the module, otherwise returns null.
     * 
     * @return module name
     */
    public String getName() {
        return container.getName();
    }

    /**
     * Gets whether this store is the default module
     * 
     * @return True if this is the default module
     */
    public abstract boolean isDefault();

    /**
     * Gets a sorted list of modules that have been created
     * 
     * @return list of module names
     */
    public abstract List<String> getModuleNames();

    /**
     * Gets a module by name
     * 
     * @param moduleName
     * @return module, null if no module by this name exists
     */
    public abstract SavedTrainPropertiesStore getModule(String moduleName);

    /**
     * Checks to see what module a train is saved in.
     * If it is saved in the default place, null is returned.
     * 
     * @param name
     * @return module name, null if not stored in a separate module
     */
    public String getModuleNameOfTrain(String name) {
        ModularConfigurationEntry<SavedTrainProperties> entry = container.getIfExists(name);
        return entry == null ? null : entry.getModule().getName();
    }

    /**
     * Sets the module a train is saved in.
     * If it is currently not saved in a module and a non-null module is provided,
     * the train will be moved to a module of that name. Similarly if a train was
     * saved in a module and a null module is provided, the train is moved out of the
     * module.<br>
     * <br>
     * If no saved train with this name exists, this method does nothing.
     * Make sure to call this method after saving configuration, not before.
     * 
     * @param name
     * @param module name, null for the default store
     */
    public abstract void setModuleNameOfTrain(String name, String module);

    /**
     * Checks whether a player has permission to make changes to a saved train.
     * Returns true if no train by this name exists yet.
     * 
     * @param sender Player or Command Sender to check
     * @param name Permission name
     * @return True if the player has permission
     */
    public boolean hasPermission(CommandSender sender, String name) {
        SavedTrainProperties savedProperties = this.getProperties(name);
        return savedProperties == null || savedProperties.hasPermission(sender);
    }

    /**
     * Gets a list of players that have claimed ownership over a saved train.
     * An empty list indicates nobody has claimed the saved train, or that the saved
     * train does not exist. The returned list is unmodifiable.
     * 
     * @param name
     * @return list of player claims, empty is unclaimed or non-existant train
     */
    public Set<SavedClaim> getClaims(String name) {
        SavedTrainProperties savedProperties = this.getProperties(name);
        return (savedProperties == null) ? Collections.emptySet() : savedProperties.getClaims();
    }

    /**
     * Calls {@link #setClaims(String, Set)} with a single claim of a player.
     * 
     * @param name
     * @param player to add to the claim list
     */
    public void setClaim(String name, Player player) {
        setClaims(name, Collections.singleton(new SavedClaim(player)));
    }

    /**
     * Sets a list of players that have claimed ownership over a saved train.
     * An empty list indicates nobody has claimed the saved train.
     * Fails silently if the train does not exist.
     * 
     * @param name
     * @param claims list to set to
     */
    public void setClaims(String name, Set<SavedClaim> claims) {
        SavedTrainProperties savedProperties = this.getProperties(name);
        if (savedProperties != null) {
            savedProperties.setClaims(claims);
        }
    }

    /**
     * Checks whether a particular saved train name exists inside this store
     * 
     * @param name
     * @return True if the train is contained
     */
    public boolean containsTrain(String name) {
        return container.getIfExists(name) != null;
    }

    public abstract void save(boolean autosave);

    /**
     * Reloads saved train properties from disk
     */
    public abstract void reload();

    /**
     * Saves the train information under a name.
     *
     * @param group to save
     * @param name to save as
     * @param module to save in. null for the default store.
     * @deprecated Saving has been split into two methods, use those instead:
     *             <ul>
     *             <li>{@link #saveGroup(String, MinecartGroup)}
     *             <li>{@link #setModuleNameOfTrain(String, String)}
     *             </ul>
     */
    @Deprecated
    public void save(MinecartGroup group, String name, String module) throws IllegalNameException {
        this.saveGroup(name, group);
        this.setModuleNameOfTrain(name, module);
    }

    /**
     * Saves the live train information in a Minecart Group as a saved train.
     * If the train was already stored in a module, it will be saved in that module.
     * 
     * @param name
     * @param group
     * @throws IllegalNameException Thrown if the train name is of an invalid format
     */
    public void saveGroup(String name, MinecartGroup group) throws IllegalNameException {
        setConfig(name, group.saveConfig());
    }

    /**
     * Sets the configuration for a saved train
     * 
     * @param name of the saved train
     * @param config to set to, is cloned before storing
     * @throws IllegalNameException Thrown if the train name is of an invalid format
     * @return Existing or created saved train properties
     */
    public SavedTrainProperties setConfig(String name, ConfigurationNode config) throws IllegalNameException {
        // Name validation
        if (name == null || name.isEmpty()) {
            throw new IllegalNameException("Name is empty");
        }
        if (Character.isDigit(name.charAt(0))) {
            throw new IllegalNameException("Name starts with a digit");
        }

        // Back up previous claims information
        List<String> claims = Collections.emptyList();
        {
            ModularConfigurationEntry<SavedTrainProperties> entry = container.getIfExists(name);
            if (entry != null && entry.getConfig().contains("claims")) {
                claims = new ArrayList<>(entry.getConfig().getList("claims", String.class));
            }
        }

        // Store the entry
        ModularConfigurationEntry<SavedTrainProperties> entry = container.add(name, config);
        entry.getWritableConfig().set("claims", claims);
        return entry.get();
    }

    /**
     * Gets the properties of a saved train
     * 
     * @param name Name of the saved train
     * @return properties, null if not found
     */
    public SavedTrainProperties getProperties(String name) {
        ModularConfigurationEntry<SavedTrainProperties> entry = container.getIfExists(name);
        return entry == null ? null : entry.get();
    }

    /**
     * Gets the properties of a saved train. If one by this name does not
     * exist, returns a default fallback. Use {@link SavedTrainProperties#isNone()}
     * to check for this.
     *
     * @param name Name of the saved train
     * @return properties
     */
    public abstract SavedTrainProperties getPropertiesOrNone(String name);

    /**
     * Gets the configuration for a saved train
     * 
     * @param name of the saved train
     * @return configuration, null if the train is not stored
     */
    public ConfigurationNode getConfig(String name) {
        ModularConfigurationEntry<SavedTrainProperties> entry = container.getIfExists(name);
        return entry == null ? null : entry.getConfig();
    }

    /**
     * Attempts to find a String token that starts with the name of a saved train. First searches
     * modules, then searches the default store.
     * 
     * @param text to find a name in
     * @return name found, null if none found
     */
    public String findName(String text) {
        String foundName = null;
        for (String name : this.getNames()) {
            if (text.startsWith(name) && (foundName == null || name.length() > foundName.length())) {
                foundName = name;
            }
        }
        return foundName;
    }

    /**
     * Tries to remove saved train properties by name.
     * If the same name exists multiple times in different modules, only one
     * instance is removed.
     * 
     * @param name
     * @return True if found and removed
     */
    public boolean remove(String name) {
        try {
            return this.container.remove(name) != null;
        } catch (ReadOnlyModuleException ex) {
            return false; //TODO: Do something with this?
        }
    }

    /**
     * Tries to rename saved train properties.
     * If the same name exists multiple times in different modules, only one
     * instance is renamed.
     * 
     * @param name
     * @param newName
     * @return True if found and renamed
     */
    public boolean rename(String name, String newName) {
        return container.rename(name, newName);
    }

    /**
     * Reverses the carts of a train, reversing both the order and toggling the 'flipped' property
     * for each cart.
     * 
     * @param name of the train
     * @return True if the train was found and reversed
     */
    public boolean reverse(String name) {
        SavedTrainProperties properties = this.getProperties(name);
        if (properties != null) {
            properties.reverse();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a list of all saved trains
     * 
     * @return A List of the names of all saved trains
     */
    public List<String> getNames() {
        return this.container.getNames();
    }

    /**
     * Main (Default) store
     */
    private static class DefaultStore extends SavedTrainPropertiesStore {
        private final ModularConfig modularConfig;

        public DefaultStore(TrainCarts traincarts, ModularConfig modularConfig) {
            super(traincarts, modularConfig);
            this.modularConfig = modularConfig;
        }

        @Override
        public void save(boolean autosave) {
            if (autosave) {
                modularConfig.saveChanges();
            } else {
                modularConfig.save();
            }
        }

        @Override
        public void reload() {
            modularConfig.reload();
        }

        @Override
        public boolean isDefault() {
            return true;
        }

        @Override
        public List<String> getModuleNames() {
            return modularConfig.MODULES.getFileNames();
        }

        @Override
        public SavedTrainPropertiesStore getModule(String moduleName) {
            ModularConfigurationFile<SavedTrainProperties> module = modularConfig.MODULES.getFile(moduleName);
            return module == null ? null : createModule(module);
        }

        @Override
        public void setModuleNameOfTrain(String name, String module) {
            ModularConfigurationEntry<SavedTrainProperties> entry = modularConfig.getIfExists(name);
            if (entry != null) {
                entry.setModule(modularConfig.createModule(module));
            }
        }

        @Override
        public SavedTrainProperties getPropertiesOrNone(String name) {
            return modularConfig.get(name).get();
        }
    }

    /**
     * A sub-module of saved trains inside the SavedTrainModules folder
     */
    public static class ModuleStore extends SavedTrainPropertiesStore {
        private final ModularConfigurationModule<SavedTrainProperties> module;

        public ModuleStore(TrainCarts traincarts, ModularConfigurationModule<SavedTrainProperties> module) {
            super(traincarts, module);
            this.module = module;
        }

        @Override
        public void save(boolean autosave) {
            if (autosave) {
                module.saveChanges();
            } else {
                module.save();
            }
        }

        @Override
        public void reload() {
            module.reload();
        }

        @Override
        public boolean isDefault() {
            return false;
        }

        @Override
        public List<String> getModuleNames() {
            return Collections.emptyList();
        }

        @Override
        public SavedTrainPropertiesStore getModule(String moduleName) {
            return null;
        }

        @Override
        public void setModuleNameOfTrain(String name, String module) {
        }

        @Override
        public SavedTrainProperties getPropertiesOrNone(String name) {
            return module.getMain().get(name).get();
        }
    }

    private static class ModularConfig extends BasicModularConfiguration<SavedTrainProperties> {
        private final TrainCarts traincarts;

        public ModularConfig(TrainCarts plugin, String mainFilePath, String moduleDirectoryPath) {
            super(plugin, mainFilePath, moduleDirectoryPath);
            this.traincarts = plugin;
            this.addResourcePack(TCConfig.resourcePack, "traincarts", "saved_train_properties");
        }

        @Override
        protected void preProcessModuleConfiguration(ConfigurationNode moduleConfig) {
            renameTrainsBeginningWithDigits(moduleConfig);
            storeSavedNameInConfig(moduleConfig);
        }

        @Override
        protected void postProcessEntryConfiguration(ModularConfigurationEntry<SavedTrainProperties> entry) {
            ConfigurationNode config = entry.getWritableConfig();
            if (!config.contains(KEY_SAVED_NAME) ||
                !config.get(KEY_SAVED_NAME, "").equals(entry.getName()))
            {
                config.set(KEY_SAVED_NAME, entry.getName());
            }
        }

        @Override
        protected SavedTrainProperties decodeConfig(ModularConfigurationEntry<SavedTrainProperties> entry) {
            return new SavedTrainProperties(traincarts, entry);
        }

        private void renameTrainsBeginningWithDigits(ConfigurationNode savedTrainsConfig) {
            // Rename trains starting with a number, as this breaks things
            for (ConfigurationNode config : new ArrayList<>(savedTrainsConfig.getNodes())) {
                String name = config.getName();
                if (!name.isEmpty() && !Character.isDigit(name.charAt(0))) {
                    continue;
                }

                String new_name = "t" + name;
                for (int i = 1; savedTrainsConfig.contains(new_name); i++) {
                    new_name = "t" + name + i;
                }

                traincarts.log(Level.WARNING, "Train name '"  + name + "' starts with a digit, renamed to " + new_name);
                config.set(KEY_SAVED_NAME, new_name);
                config.remove();
                savedTrainsConfig.set(new_name, config);
            }
        }

        private void storeSavedNameInConfig(ConfigurationNode savedTrainsConfig) {
            // Stores the key of each saved train node in the configuration itself
            // This is done automatically, so this is a migration method to update old config
            // If people change this field, revert that and log a warning
            boolean logSavedNameFieldWarning = false;
            for (ConfigurationNode config : savedTrainsConfig.getNodes()) {
                if (!config.contains(KEY_SAVED_NAME)) {
                    config.set(KEY_SAVED_NAME, config.getName());
                    continue;
                }

                String setName = config.get(KEY_SAVED_NAME, config.getName());
                if (!config.getName().equals(setName)) {
                    // Note: people may have intended to rename these properties
                    // It is best to notify about this.
                    traincarts.log(Level.WARNING, "Saved train '" + config.getName() + "' has a different "
                            + "name set: '" + setName + "'");
                    logSavedNameFieldWarning = true;

                    config.set(KEY_SAVED_NAME, config.getName());
                }
            }
            if (logSavedNameFieldWarning) {
                traincarts.log(Level.WARNING, "If the intention was to rename the train, instead "
                        + "rename the key, not field '" + KEY_SAVED_NAME + "'");
            }
        }
    }
}

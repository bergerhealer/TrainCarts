package com.bergerkiller.bukkit.tc.properties;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ListIterator;
import java.util.Locale;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.utils.StreamUtil;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentItem;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;

/**
 * Stores the train and cart properties for trains that have been saved using /train save.
 * These properties can also be used on spawner signs, or fused into spawning items.
 */
public class SavedTrainPropertiesStore implements TrainCarts.Provider {
    private static final String KEY_SAVED_NAME = "savedName";

    private final TrainCarts traincarts;
    private final FileConfiguration savedTrainsConfig;
    private String name;
    private String modulesDirectory = "";
    private final List<String> names = new ArrayList<String>();
    private Map<String, SavedTrainPropertiesStore> modules = new HashMap<String, SavedTrainPropertiesStore>();;
    protected boolean changed = false;
    private boolean allowModules;

    public SavedTrainPropertiesStore(TrainCarts traincarts, String name, String filename) {
        this(traincarts, name, filename, true);
    }

    public SavedTrainPropertiesStore(TrainCarts traincarts, String name, String filename, boolean allowModules) {
        this.traincarts = traincarts;
        this.savedTrainsConfig = new FileConfiguration(filename);
        this.savedTrainsConfig.load();
        renameTrainsBeginningWithDigits(this.savedTrainsConfig);
        storeSavedNameInConfig(this.savedTrainsConfig);

        this.name = name;
        this.names.addAll(this.savedTrainsConfig.getKeys());
        this.allowModules = allowModules;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    public void loadModules(String directory) {
        if (this.allowModules) {
            this.modulesDirectory = directory;
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdir();
            }
            for (File file : StreamUtil.listFiles(dir)) {
                String name = file.getName();
                String ext = name.toLowerCase(Locale.ENGLISH);
                if (ext.endsWith(".yml")) {
                    createModule(name);
                } else if (ext.endsWith(".zip")) {
                    traincarts.getLogger().warning("Zip files are not read, please extract '" + name + "'!");
                }
            }
        } else {
            throw new UnsupportedOperationException("This store is not authorized to load modules");
        }
    }

    /**
     * Gets the name of this saved train store. If this is a module,
     * returns the name of the module, otherwise returns null.
     * 
     * @return module name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets whether this store is the default module
     * 
     * @return True if this is the default module
     */
    public boolean isDefault() {
        return this.name == null;
    }

    /**
     * Gets a set of modules that have been created
     * 
     * @return set of module names
     */
    public Set<String> getModuleNames() {
        return this.modules.keySet();
    }

    /**
     * Gets a module by name
     * 
     * @param moduleName
     * @return module, null if no module by this name exists
     */
    public SavedTrainPropertiesStore getModule(String moduleName) {
        return this.modules.get(moduleName.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Checks to see what module a train is saved in.
     * If it is saved in the default place, null is returned.
     * 
     * @param name
     * @return module name, null if not stored in a separate module
     */
    public String getModuleNameOfTrain(String name) {
        if (!this.savedTrainsConfig.isNode(name)) {
            for (Map.Entry<String, SavedTrainPropertiesStore> module : this.modules.entrySet()) {
                if (module.getValue().savedTrainsConfig.isNode(name)) {
                    return module.getKey();
                }
            }
        }
        return null;
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
    public void setModuleNameOfTrain(String name, String module) {
        if (!this.allowModules) {
            return;
        }

        // Retrieve and remove the original configuration
        ConfigurationNode config = null;
        if (this.savedTrainsConfig.isNode(name)) {
            if (module == null) {
                return; // already stored in default store
            }

            // Module is set and is stored in default, remove it
            config = this.savedTrainsConfig.getNode(name).clone();
            this.savedTrainsConfig.remove(name);
            this.names.remove(name);
            this.changed = true;
        } else {
            // Find it in an existing module
            for (Map.Entry<String, SavedTrainPropertiesStore> moduleEntry : this.modules.entrySet()) {
                SavedTrainPropertiesStore moduleStore = moduleEntry.getValue();
                if (moduleStore.savedTrainsConfig.isNode(name)) {
                    if (moduleEntry.getKey().equals(module)) {
                        return; // already stored in this module
                    }
                    config = moduleStore.savedTrainsConfig.getNode(name).clone();
                    moduleStore.savedTrainsConfig.remove(name);
                    moduleStore.names.remove(name);
                    moduleStore.changed = true;
                    break;
                }
            }
            if (config == null) {
                return; // not found
            }
        }

        // Store it in the default or new module
        SavedTrainPropertiesStore moduleStore;
        if (module == null) {
            moduleStore = this;
        } else {
            moduleStore = this.modules.get(module.toLowerCase(Locale.ENGLISH));
            if (moduleStore == null) {
                createModule(module + ".yml");
                moduleStore = this.modules.get(module.toLowerCase(Locale.ENGLISH));
                if (moduleStore == null) {
                    return; // What?
                }
            }
        }

        moduleStore.savedTrainsConfig.set(name, config);
        moduleStore.names.add(name);
        moduleStore.changed = true;
    }

    /**
     * Create a module from a filename. If it does not exist, it will be created.
     * @param fileName The filename of the desired module, in format `moduleName.yml`
     */
    private void createModule(String fileName) {
        String name = fileName;
        if (fileName.indexOf(".") > 0) {
            name = fileName.substring(0, fileName.lastIndexOf("."));
        }
        name = name.toLowerCase(Locale.ENGLISH);

        modules.put(name, new SavedTrainPropertiesStore(traincarts, name, modulesDirectory + File.separator + fileName, false));
    }

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
    public Set<Claim> getClaims(String name) {
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
        setClaims(name, Collections.singleton(new Claim(player)));
    }

    /**
     * Sets a list of players that have claimed ownership over a saved train.
     * An empty list indicates nobody has claimed the saved train.
     * Fails silently if the train does not exist.
     * 
     * @param name
     * @param claims list to set to
     */
    public void setClaims(String name, Set<Claim> claims) {
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
        if (this.savedTrainsConfig.isNode(name)) {
            return true;
        }
        for (SavedTrainPropertiesStore module : this.modules.values()) {
            if (module.containsTrain(name)) {
                return true;
            }
        }
        return false;
    }

    public void save(boolean autosave) {
        for (SavedTrainPropertiesStore module : this.modules.values()) {
            module.save(autosave);
        }

        if (autosave && !this.changed) {
            return;
        }
        this.savedTrainsConfig.save();
        this.changed = false;
    }

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
        // Check if stored in a module, first
        if (!this.savedTrainsConfig.isNode(name)) {
            for (SavedTrainPropertiesStore module : this.modules.values()) {
                if (module.savedTrainsConfig.isNode(name)) {
                    return module.setConfig(name, config);
                }
            }
        }

        // Name validation
        if (name == null || name.isEmpty()) {
            throw new IllegalNameException("Name is empty");
        }
        if (Character.isDigit(name.charAt(0))) {
            throw new IllegalNameException("Name starts with a digit");
        }

        // Clone to prevent the original being mutated later
        // Preserve claims set in the original properties
        ConfigurationNode newConfig = config.clone();
        if (this.savedTrainsConfig.contains(name + ".claims")) {
            newConfig.set("claims", this.savedTrainsConfig.getList(name + ".claims", String.class));
        }

        // Store in mapping
        this.changed = true;
        this.savedTrainsConfig.set(name, newConfig);
        newConfig.set(KEY_SAVED_NAME, name);
        this.names.remove(name);
        this.names.add(name);

        // Return link to this configuration
        return SavedTrainProperties.of(this, name, newConfig);
    }

    /**
     * Gets the properties of a saved train
     * 
     * @param name Name of the saved train
     * @return properties, null if not found
     */
    public SavedTrainProperties getProperties(String name) {
        if (!this.savedTrainsConfig.isNode(name)) {
            for (SavedTrainPropertiesStore module : this.modules.values()) {
                ConfigurationNode config = module.getConfig(name);
                if (config != null) {
                    return SavedTrainProperties.of(module, name, config);
                }
            }
            return null;
        }
        return SavedTrainProperties.of(this, name, this.savedTrainsConfig.getNode(name));
    }

    /**
     * Gets the configuration for a saved train
     * 
     * @param name of the saved train
     * @return configuration, null if the train is not stored
     */
    public ConfigurationNode getConfig(String name) {
        if (!this.savedTrainsConfig.isNode(name)) {
            for (SavedTrainPropertiesStore module : this.modules.values()) {
                ConfigurationNode config = module.getConfig(name);
                if (config != null) {
                    return config;
                }
            }
            return null;
        }
        return this.savedTrainsConfig.getNode(name);
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

        for (SavedTrainPropertiesStore module : this.modules.values()) {
            String name = module.findName(text);
            if (name != null) {
                foundName = name;
            }
        }


        for (String name : this.names) {
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
        if (this.savedTrainsConfig.isNode(name)) {
            this.savedTrainsConfig.remove(name);
            this.names.remove(name);
            this.changed = true;
            return true;
        } else {
            for (SavedTrainPropertiesStore module : this.modules.values()) {
                if (module.remove(name)) {
                    return true;
                }
            }
            return false;
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
        if (this.savedTrainsConfig.isNode(name)) {
            if (name.equals(newName)) {
                return true;
            }
            ConfigurationNode oldConfig = this.savedTrainsConfig.getNode(name).clone();
            this.savedTrainsConfig.remove(name);
            this.names.remove(name);
            this.names.remove(newName);
            this.savedTrainsConfig.set(newName, oldConfig);
            oldConfig.set(KEY_SAVED_NAME, newName);
            this.names.add(newName);
            this.changed = true;
            return true;
        } else {
            for (SavedTrainPropertiesStore module : this.modules.values()) {
                if (module.rename(name, newName)) {
                    return true;
                }
            }
            return false;
        }
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
        if (this.modules.isEmpty()) {
            return this.names;
        }
        List<String> result = new ArrayList<String>(this.names);
        for (SavedTrainPropertiesStore module : this.modules.values()) {
            result.addAll(module.names);
        }
        return result;
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
            this.changed = true;
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

    /**
     * A single claim on a saved train
     */
    public static class Claim {
        public final UUID playerUUID;
        public final String playerName;

        public Claim(OfflinePlayer player) {
            this.playerUUID = player.getUniqueId();
            this.playerName = player.getName();
        }

        public Claim(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.playerName = null;
        }

        public Claim(String config) throws IllegalArgumentException {
            config = config.trim();
            int name_end = config.lastIndexOf(' ');
            if (name_end == -1) {
                // Assume only UUID is specified
                this.playerName = null;
                this.playerUUID = UUID.fromString(config);
            } else {
                // Format 'playername uuid' is used
                this.playerName = config.substring(0, name_end);
                this.playerUUID = UUID.fromString(config.substring(name_end+1).trim());
            }
        }

        public String description() {
            if (this.playerName == null) {
                return "uuid=" + this.playerUUID.toString();
            } else {
                return this.playerName;
            }
        }

        @Override
        public String toString() {
            if (this.playerName == null) {
                return this.playerUUID.toString();
            } else {
                return this.playerName + " " + this.playerUUID.toString();
            }
        }

        @Override
        public int hashCode() {
            return this.playerUUID.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Claim) {
                Claim other = (Claim) o;
                return other.playerUUID.equals(this.playerUUID);
            } else {
                return false;
            }
        }
    }
}

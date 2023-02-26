package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import com.bergerkiller.bukkit.common.config.FileConfiguration;

import java.io.File;
import java.util.Locale;

/**
 * A {@link ModularConfigurationModule} backed by a physical File on disk. Changes made
 * to this module are saved to that file. A reload will load the contents of the
 * file.
 *
 * @param <T> Type of object stored the configuration is for
 */
public class ModularConfigurationFile<T> extends ModularConfigurationModule<T> {

    ModularConfigurationFile(ModularConfiguration<T> main, File file) {
        this(main, decodeModuleNameFromFile(file), file, !file.canWrite());
    }

    ModularConfigurationFile(ModularConfiguration<T> main, String name, File file, boolean readOnly) {
        super(main, name, new FileConfiguration(file), readOnly);
    }

    @Override
    protected void loadConfig() {
        ((FileConfiguration) config).load();
        super.loadConfig();
    }

    @Override
    public void reload() {
        // Discard whatever file changes that exist, we got changes ourselves!
        if (configChanged) {
            saveChanges();
            return;
        }

        main.groupChanges(() -> {
            main.onModuleRemoved(this);
            loadConfig();
            main.onModuleAdded(this);
        });
    }

    @Override
    public void saveChanges() {
        if (configChanged) {
            if (!isReadOnly()) {
                ((FileConfiguration) config).save();
            }
            configChanged = false;
        }
    }

    @Override
    public void save() {
        if (!isReadOnly()) {
            ((FileConfiguration) config).save();
        }
        configChanged = false;
    }

    static String decodeModuleNameFromFile(File file) {
        String name = file.getName();
        if (name.indexOf(".") > 0) {
            name = name.substring(0, name.lastIndexOf("."));
        }
        name = name.toLowerCase(Locale.ENGLISH);
        return name;
    }
}

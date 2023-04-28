package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A directory that can store any number of configuration module
 * files. Files that are read-only are added as read-only file
 * modules. Only includes YAML and YML files found in the directory.<br>
 * <br>
 * Read-only files are sorted to the back of the list. The files are
 * then sorted by name. This means entries declared in file "a" override
 * ones declared in file "b".
 *
 * @param <T> Type of object stored the configuration is for
 */
public class ModularConfigurationDirectory<T> implements ModularConfigurationBlock<T> {
    private final ModularConfiguration<T> main;
    private List<ModularConfigurationFile<T>> files;
    private List<String> names;
    private final Map<String, ModularConfigurationFile<T>> filesByName;
    private final File directory;

    ModularConfigurationDirectory(ModularConfiguration<T> main, File directory) {
        this.main = main;
        this.files = Collections.emptyList();
        this.names = Collections.emptyList();
        this.filesByName = new HashMap<>();
        this.directory = directory;

        this.loadFiles();
    }

    /**
     * Gets the directory files are listed of
     *
     * @return directory
     */
    public File getDirectory() {
        return directory;
    }

    /**
     * Clears all previously loaded file modules that are part of this directory
     */
    public void clear() {
        if (!files.isEmpty()) {
            List<ModularConfigurationFile<T>> filesOrig = files;
            files = Collections.emptyList();
            names = Collections.emptyList();
            filesByName.clear();
            filesOrig.forEach(main::onModuleRemoved);
        }
    }

    /**
     * Lists all the files in the directory and loads them as new file modules.
     * Does not yet add them to the store, only fills {@link #getFiles()}
     */
    private void loadFiles() {
        // If the directory does not (yet) exist, create it and load nothing
        if (!directory.exists()) {
            directory.mkdir();
            files = Collections.emptyList();
            names = Collections.emptyList();
            filesByName.clear();
            return;
        }

        // List all files, handle failure
        File[] directoryFiles = directory.listFiles();
        if (directoryFiles == null) {
            files = Collections.emptyList();
            names = Collections.emptyList();
            filesByName.clear();
            return;
        }

        // Collect all files to be loaded and turn them into modules
        // Ignore files that are completely empty, but don't remove those
        files = Arrays.stream(directoryFiles)
                .filter(file -> {
                    String ext = file.getName().toLowerCase(Locale.ENGLISH);
                    if (ext.endsWith(".zip")) {
                        main.logger.warning("Zip files are not read, please extract '" +
                                file.getAbsolutePath() + "'!");
                        return false;
                    }

                    return ext.endsWith(".yml") || ext.endsWith(".yaml");
                })
                .map(file -> new ModularConfigurationFile<T>(main, file))
                .filter(m -> !m.isEmpty())
                .sorted()
                .collect(Collectors.toList());
        filesByName.clear();
        files.forEach(f -> filesByName.put(f.name, f));
        regenNames();
    }

    /**
     * Gets the configuration file module by name. If it doesn't exist, <i>null</i>
     * is returned.
     *
     * @param name Name of the file module, case-insensitive
     * @return module file, or <i>null</i> if not found
     */
    public ModularConfigurationFile<T> getFile(String name) {
        return filesByName.get(name.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Adds a new writable modular configuration file to this directory. A new
     * file is later created with this name and a <i>.yml</i> extension.<br>
     * <br>
     * If a file by this name already exists, it is returned as-is.
     *
     * @param name Name of the file
     * @return existing or created modular configuration file
     */
    public ModularConfigurationFile<T> createFile(String name) {
        File file = new File(directory, name + ".yml");
        String fixedName = ModularConfigurationFile.decodeModuleNameFromFile(file);
        ModularConfigurationFile<T> fileModule = filesByName.get(fixedName);
        if (fileModule == null) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                main.logger.log(Level.WARNING, "Failed to write to " + file.getAbsolutePath(), ex);
            }
            fileModule = new ModularConfigurationFile<T>(main, fixedName, file, false);

            // Add the file at the right spot into the sorted list of files
            {
                ArrayList<ModularConfigurationFile<T>> newFiles = new ArrayList<>(files);
                int index = Collections.binarySearch(newFiles, fileModule);
                if (index < 0) {
                    index = -index - 1;
                }
                newFiles.add(index, fileModule);
                files = newFiles;
            }

            // Add to by-name mapping also
            filesByName.put(fixedName, fileModule);
            regenNames();
        }
        return fileModule;
    }

    private void regenNames() {
        List<String> names = new ArrayList<>(files.size());
        for (ModularConfigurationFile<T> file : files) {
            names.add(file.getName());
        }
        this.names = Collections.unmodifiableList(names);
    }

    @Override
    public ModularConfiguration<T> getMain() {
        return main;
    }

    @Override
    public List<ModularConfigurationFile<T>> getFiles() {
        return files;
    }

    /**
     * Gets an unmodifiable snapshot of all the module file names that exist inside this
     * directory module right now. Names are sorted in the same way {@link #getFiles()}
     * is, that is, sorted by writable first, name alphabetically second.
     *
     * @return Unmodifiable List of module file names
     */
    public List<String> getFileNames() {
        return names;
    }

    @Override
    public void reload() {
        // Make sure any configuration changes are first committed to disk
        saveChanges();

        clear();
        loadFiles();
        files.forEach(main::onModuleAdded);
    }

    @Override
    public void saveChanges() {
        files.forEach(ModularConfigurationModule::saveChanges);
    }

    @Override
    public void save() {
        files.forEach(ModularConfigurationModule::save);
    }
}

package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores multiple configuration blocks or modules in a simple List.
 * These are all aggregated into one.
 *
 * @param <T> Type of object stored the configuration is for
 */
public abstract class ModularConfigurationBlockList<T> implements ModularConfigurationBlock<T> {
    protected final List<ModularConfigurationBlock<T>> blocks = new ArrayList<>();

    @Override
    public List<? extends ModularConfigurationModule<T>> getFiles() {
        if (blocks.isEmpty()) {
            return Collections.emptyList();
        } else if (blocks.size() == 1) {
            return blocks.get(0).getFiles();
        } else {
            ArrayList<ModularConfigurationModule<T>> allFiles = new ArrayList<>();
            blocks.forEach(b -> allFiles.addAll(b.getFiles()));
            return allFiles;
        }
    }

    @Override
    public void reload() {
        getMain().groupChanges(() -> blocks.forEach(ModularConfigurationBlock::reload));
    }

    @Override
    public void saveChanges() {
        blocks.forEach(ModularConfigurationBlock::saveChanges);
    }

    @Override
    public void save() {
        blocks.forEach(ModularConfigurationBlock::save);
    }

    /**
     * Lists the YAML files that exist inside a directory, and adds all of them
     * to this store without priority. Read-only files are added as read-only
     * file modules, added with lower priority than the writable modules.<br>
     * <br>
     * This directory is added without priority. As such previously added
     * modules will override this one.
     *
     * @param directory Directory to list files of. Is created if it
     *                  does not yet exist.
     * @return ModuleConfigurationDirectory for the directory
     */
    public ModularConfigurationDirectory<T> addDirectoryModule(File directory) {
        return addDirectoryModule(directory, false);
    }

    /**
     * Lists the YAML files that exist inside a directory, and adds all of them
     * to this store without priority. Read-only files are added as read-only
     * file modules, added with lower priority than the writable modules.
     *
     * @param directory Directory to list files of. Is created if it
     *                  does not yet exist.
     * @param priority Whether to create the module with priority. If created
     *                 with priority, then entries declared will override
     *                 existing entries.
     * @return ModuleConfigurationDirectory for the directory
     */
    public ModularConfigurationDirectory<T> addDirectoryModule(File directory, boolean priority) {
        return addBlock(new ModularConfigurationDirectory<T>(getMain(), directory), priority);
    }

    /**
     * Creates a new file module and makes the entries declared inside the file
     * configuration available. Stores without priority, so previously added
     * modules will override this one.
     *
     * @param name Name of the new Module
     * @param file File to add as a new file module
     * @param readOnly Whether the configuration is read-only. A read-only
     *                 module cannot house new entries or modify existing
     *                 ones, and is never saved.
     * @return new Module
     */
    public ModularConfigurationFile<T> addFileModule(String name, File file, boolean readOnly) {
        return addFileModule(name, file, readOnly, false);
    }

    /**
     * Creates a new file module and makes the entries declared inside the file
     * configuration available.
     *
     * @param name Name of the new Module
     * @param file File to add as a new file module
     * @param readOnly Whether the configuration is read-only. A read-only
     *                 module cannot house new entries or modify existing
     *                 ones, and is never saved.
     * @param priority Whether to create the module with priority. If created
     *                 with priority, then entries declared will override
     *                 existing entries.
     * @return new Module
     */
    public ModularConfigurationFile<T> addFileModule(String name, File file, boolean readOnly, boolean priority) {
        return addBlock(new ModularConfigurationFile<T>(getMain(), name, file, readOnly), priority);
    }

    /**
     * Adds a new block of module configurations to this List. If priority is true,
     * the block is added to the start. If false, it is added to the end.
     * Priority blocks override other blocks that were added.
     *
     * @param block ModuleConfigurationBlock to add
     * @param priority Whether to add the block with priority. If added
     *                 with priority, then entries declared will override
     *                 existing entries.
     * @return input block
     * @param <B> Block type
     */
    public <B extends ModularConfigurationBlock<T>> B addBlock(B block, boolean priority) {
        if (priority) {
            this.blocks.add(0, block);
        } else {
            this.blocks.add(block);
        }
        block.getFiles().forEach(getMain()::onModuleAdded);
        return block;
    }

    /**
     * Clears all Modules previously added and marks all pre-existing Entries for
     * removal. Listeners will be notified the entry is removed, unless
     * {@link ModularConfiguration#groupChanges(Runnable)} was used to delay
     * those changes until later.
     */
    public void clear() {
        getMain().groupChanges(() -> {
            ArrayList<ModularConfigurationBlock<T>> copy = new ArrayList<>(blocks);
            blocks.clear();
            copy.stream()
                    .flatMap(b -> b.getFiles().stream())
                    .forEachOrdered(getMain()::onModuleRemoved);
        });
    }
}

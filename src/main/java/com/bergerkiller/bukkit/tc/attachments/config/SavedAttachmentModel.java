package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.properties.SavedClaim;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ModularConfigurationEntry;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Wraps a saved attachment model configuration. In addition to the configuration,
 * the name of the model can be obtained. Might be a non-existent model which
 * throws when reading the configuration, which should be checked with
 * {@link #isNone()}.
 */
public class SavedAttachmentModel extends AttachmentModel {
    private final ModularConfigurationEntry<SavedAttachmentModel> entry;

    SavedAttachmentModel(ModularConfigurationEntry<SavedAttachmentModel> entry) {
        super(entry::getConfig);
        this.entry = entry;
    }

    public SavedAttachmentModelStore getModule() {
        if (entry.isRemoved()) {
            return null;
        } else {
            return SavedAttachmentModelStore.createModule(entry.getModule());
        }
    }

    /**
     * Gets whether this attachment model refers to a missing model
     * that is not yet stored. Only the {@link #getName()} attribute can
     * then be used.
     *
     * @return True if this model does not yet exist
     */
    public boolean isNone() {
        return entry.isRemoved();
    }

    /**
     * Gets the name of this attachment model
     *
     * @return model name
     */
    public String getName() {
        return entry.getName();
    }

    /**
     * Gets whether this saved attachment model is currently empty. It can be empty if it was newly
     * created without an initial attachment configuration. Empty means no attachment options are
     * contained, and no attachment type is known for the root (assumed EMPTY).
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        if (entry.isRemoved()) {
            return true;
        }

        for (String key : entry.getConfig().getKeys()) {
            if (!LogicUtil.contains(key, "claims", "editor", SavedAttachmentModelStore.KEY_SAVED_NAME)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets a set of all the claims configured for this attachment model.
     * Each entry refers to a unique player UUID.
     * 
     * @return set of claims, unmodifiable
     */
    public Set<SavedClaim> getClaims() {
        return entry.isRemoved() ? Collections.emptySet() : SavedClaim.loadClaims(entry.getConfig());
    }

    /**
     * Sets a new list of claims, old claims are discarded.
     * 
     * @param claims New claims to set, value is not stored
     */
    public void setClaims(Collection<SavedClaim> claims) {
        if (!entry.isRemoved()) {
            SavedClaim.saveClaims(entry.getConfig(), claims);
        }
    }

    /**
     * Checks whether a player has permission to make changes to an attachment model.
     * Returns true if no model by this name exists yet.
     * 
     * @param sender
     * @return True if the player has permission
     */
    public boolean hasPermission(CommandSender sender) {
        return entry.isRemoved() || SavedClaim.hasPermission(entry.getConfig(), sender);
    }
}

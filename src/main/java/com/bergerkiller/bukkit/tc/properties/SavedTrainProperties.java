package com.bergerkiller.bukkit.tc.properties;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModelStore;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.utils.SetCallbackCollector;
import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ModularConfigurationEntry;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.CartLockOrientation;
import org.bukkit.entity.Player;

/**
 * Wraps a saved train configuration
 */
public class SavedTrainProperties implements TrainCarts.Provider, SavedAttachmentModelStore.ModelUsing {
    private final TrainCarts traincarts;
    private final ModularConfigurationEntry<SavedTrainProperties> entry;

    SavedTrainProperties(TrainCarts traincarts, ModularConfigurationEntry<SavedTrainProperties> entry) {
        this.traincarts = traincarts;
        this.entry = entry;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    public SavedTrainPropertiesStore getModule() {
        if (entry.isRemoved()) {
            return null;
        } else {
            return SavedTrainPropertiesStore.createModule(entry.getModule());
        }
    }

    /**
     * Gets whether these properties refer to missing properties
     * not yet stored. Only the {@link #getName()} attribute can
     * then be used.
     *
     * @return True if these properties do not yet exist
     */
    public boolean isNone() {
        return entry.isRemoved();
    }

    public String getName() {
        return entry.getName();
    }

    public ConfigurationNode getConfig() {
        return entry.getConfig();
    }

    /**
     * Gets the full configuration of this saved train, similar to {@link #getConfig()}.
     * Omits personalized information like player claims, and includes information about used
     * saved attachment models.
     *
     * @return Train configuration as exported through /savedtrain [name] export
     */
    public ConfigurationNode getExportedConfig() {
        ConfigurationNode exportedConfig = getConfig().clone();
        exportedConfig.remove("claims");
        exportedConfig.set("usedModels", getUsedModelsAsExport());
        return exportedConfig;
    }

    @Override
    public void getUsedModels(SetCallbackCollector<SavedAttachmentModel> collector) {
        for (ConfigurationNode cart : getCarts()) {
            ConfigurationNode modelConfig = cart.getNodeIfExists("model");
            if (modelConfig != null) {
                traincarts.getSavedAttachmentModels().findModelsUsedInConfiguration(modelConfig, collector);
            }
        }
    }

    /**
     * Gets whether this saved train is currently empty. It can be empty if it was newly
     * created without an initial train configuration. Empty means no carts are contained,
     * and thus spawning it will spawn no train.
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return entry.isRemoved() || !entry.getConfig().contains("carts");
    }

    /**
     * Reverses the carts of a train, reversing both the order and toggling the 'flipped' property
     * for each cart.
     */
    public void reverse() {
        if (isEmpty()) {
            return;
        }

        List<ConfigurationNode> carts = entry.getWritableConfig().getNodeList("carts");
        carts.forEach(StandardProperties::reverseSavedCart);
        Collections.reverse(carts);
        entry.getWritableConfig().setNodeList("carts", carts);
    }

    /**
     * Sets whether the saved train orientation is locked. If locked, future saves cannot change
     * the spawn orientation of the train.
     *
     * @param locked
     */
    public void setOrientationLocked(boolean locked) {
        if (isEmpty()) {
            return;
        }

        List<ConfigurationNode> carts = entry.getWritableConfig().getNodeList("carts");
        for (ConfigurationNode cart : carts) {
            if (locked) {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart,
                        Optional.of(CartLockOrientation.locked(cart.get("flipped", false))));
            } else {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart, Optional.empty());
            }
        }
        entry.getWritableConfig().setNodeList("carts", carts);
    }

    /**
     * Gets a set of all the claims configured for this saved train.
     * Each entry refers to a unique player UUID.
     * 
     * @return set of claims, unmodifiable
     */
    public Set<SavedClaim> getClaims() {
        return entry.isRemoved() ? Collections.emptySet() :  SavedClaim.loadClaims(entry.getConfig());
    }

    /**
     * Sets a new list of claims, old claims are discarded.
     * 
     * @param claims New claims to set, value is not stored
     */
    public void setClaims(Collection<SavedClaim> claims) {
        if (!entry.isRemoved()) {
            SavedClaim.saveClaims(entry.getWritableConfig(), claims);
        }
    }

    /**
     * Checks whether a player has permission to make changes to a saved train.
     * Returns true if no train by this name exists yet.
     * 
     * @param sender
     * @return True if the player has permission
     */
    public boolean hasPermission(CommandSender sender) {
        return entry.isRemoved() || SavedClaim.hasPermission(entry.getConfig(), sender);
    }

    /**
     * Creates a SpawnableGroup with this saved train configuration
     *
     * @return SpawnableGroup
     */
    public SpawnableGroup toSpawnableGroup() {
        return SpawnableGroup.fromConfig(this);
    }

    public List<ConfigurationNode> getCarts() {
        if (entry.getConfig().isNode("carts")) {
            return entry.getConfig().getNodeList("carts");
        }
        return Collections.emptyList();
    }

    public int getNumberOfCarts() {
        return getCarts().size();
    }

    public int getNumberOfSeats() {
        int count = 0;
        for (ConfigurationNode cart : getCarts()) {
            if (cart.isNode("model")) {
                count += getNumberOfSeatAttachmentsRecurse(cart.getNode("model"));
            }
        }
        return count;
    }

    public double getTotalTrainLength() {
        double totalLength = 0.0;
        List<ConfigurationNode> carts = getCarts();
        if (!carts.isEmpty()) {
            totalLength += TCConfig.cartDistanceGap * (carts.size() - 1);
            for (ConfigurationNode cart : carts) {
                if (cart.contains("model.physical.cartLength")) {
                    totalLength += cart.get("model.physical.cartLength", 0.0);
                }
            }
        }
        return totalLength;
    }

    /**
     * Gets the maximum number of times this saved train can be spawned on the server.
     * Returns -1 if there is no limit.
     *
     * @return Spawn Limit
     */
    public int getSpawnLimit() {
        return entry.isRemoved() ? -1 : entry.getConfig().getOrDefault("spawnLimit", -1);
    }

    /**
     * Sets the maximum number of times this saved train can be spawned on the server.
     * A negative limit will allow for unlimited number of spawns.
     *
     * @param limit New spawn limit to set
     */
    public void setSpawnLimit(int limit) {
        if (!entry.isRemoved()) {
            if (limit >= 0) {
                entry.getWritableConfig().set("spawnLimit", limit);
            } else {
                entry.getWritableConfig().remove("spawnLimit");
            }
        }
    }

    /**
     * Calculates the total number of trains that have been spawned making use of this configured
     * spawn limit.
     *
     * @return Current spawn count
     * @see #getSpawnLimit()
     */
    public int getSpawnLimitCurrentCount() {
        if (entry.isRemoved()) {
            return 0;
        }
        int count = 0;
        for (TrainProperties properties : TrainPropertiesStore.getAll()) {
            if (properties.get(StandardProperties.ACTIVE_SAVED_TRAIN_SPAWN_LIMITS).contains(getName())) {
                count++;
            }
        }
        return count;
    }

    private static int getNumberOfSeatAttachmentsRecurse(ConfigurationNode attachmentConfig) {
        int count = 0;
        if (AttachmentTypeRegistry.instance().fromConfig(attachmentConfig) == CartAttachmentSeat.TYPE) {
            count = 1;
        }
        if (attachmentConfig.isNode("attachments")) {
            for (ConfigurationNode childAttachment : attachmentConfig.getNodeList("attachments")) {
                count += getNumberOfSeatAttachmentsRecurse(childAttachment);
            }
        }
        return count;
    }
}

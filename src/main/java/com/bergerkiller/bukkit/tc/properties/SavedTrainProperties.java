package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bergerkiller.bukkit.tc.utils.modularconfiguration.ModularConfigurationEntry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore.Claim;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.CartLockOrientation;

/**
 * Wraps a saved train configuration
 */
public class SavedTrainProperties {
    private final ModularConfigurationEntry<SavedTrainProperties> entry;

    SavedTrainProperties(ModularConfigurationEntry<SavedTrainProperties> entry) {
        this.entry = entry;
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

        List<ConfigurationNode> carts = entry.getConfig().getNodeList("carts");
        carts.forEach(StandardProperties::reverseSavedCart);
        Collections.reverse(carts);
        entry.getConfig().setNodeList("carts", carts);
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

        List<ConfigurationNode> carts = entry.getConfig().getNodeList("carts");
        for (ConfigurationNode cart : carts) {
            if (locked) {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart,
                        Optional.of(CartLockOrientation.locked(cart.get("flipped", false))));
            } else {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart, Optional.empty());
            }
        }
        entry.getConfig().setNodeList("carts", carts);
    }

    /**
     * Gets a set of all the claims configured for this saved train.
     * Each entry refers to a unique player UUID.
     * 
     * @return set of claims, unmodifiable
     */
    public Set<SavedTrainPropertiesStore.Claim> getClaims() {
        if (!entry.isRemoved() && entry.getConfig().contains("claims")) {
            List<String> claim_strings = entry.getConfig().getList("claims", String.class);
            if (claim_strings != null && !claim_strings.isEmpty()) {
                Set<SavedTrainPropertiesStore.Claim> claims = new HashSet<>(claim_strings.size());
                for (String claim_str : claim_strings) {
                    try {
                        claims.add(new Claim(claim_str));
                    } catch (IllegalArgumentException ex) {
                        // Ignore
                    }
                }
                return Collections.unmodifiableSet(claims);
            }
        }
        return Collections.emptySet();
    }

    /**
     * Sets a new list of claims, old claims are discarded.
     * 
     * @param claims New claims to set, value is not stored
     */
    public void setClaims(Collection<SavedTrainPropertiesStore.Claim> claims) {
        if (entry.isRemoved()) {
            return;
        }

        // Update configuration
        if (claims.isEmpty()) {
            entry.getConfig().remove("claims");
        } else {
            List<String> claim_strings = new ArrayList<String>(claims.size());
            for (Claim claim : claims) {
                claim_strings.add(claim.toString());
            }
            entry.getConfig().set("claims", claim_strings);
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
        // Console always has permission
        if (!(sender instanceof Player)) {
            return true; 
        }

        // Check claims
        Set<Claim> claims = this.getClaims();
        if (claims.isEmpty()) {
            return true;
        } else {
            UUID playerUUID = ((Player) sender).getUniqueId();
            for (Claim claim : claims) {
                if (playerUUID.equals(claim.playerUUID)) {
                    return true;
                }
            }
            return false;
        }
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

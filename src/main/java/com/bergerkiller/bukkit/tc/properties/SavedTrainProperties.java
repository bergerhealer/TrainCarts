package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore.Claim;

/**
 * Wraps a saved train configuration
 */
public class SavedTrainProperties {
    private final SavedTrainPropertiesStore module;
    private final String name;
    private final ConfigurationNode config;

    private SavedTrainProperties(SavedTrainPropertiesStore module, String name, ConfigurationNode config) {
        this.module = module;
        this.name = name;
        this.config = config;
    }

    public SavedTrainPropertiesStore getModule() {
        return this.module;
    }

    /**
     * Gets whether these properties refer to missing properties
     * not yet stored. Only the {@link #getName()} attribute can
     * then be used.
     *
     * @return True if these properties do not yet exist
     */
    public boolean isNone() {
        return this.module == null && this.config == null;
    }

    public String getName() {
        return this.name;
    }

    public ConfigurationNode getConfig() {
        return this.config;
    }

    /**
     * Gets whether this saved train is currently empty. It can be empty if it was newly
     * created without an initial train configuration. Empty means no carts are contained,
     * and thus spawning it will spawn no train.
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return this.config == null || !this.config.contains("carts");
    }

    /**
     * Reverses the carts of a train, reversing both the order and toggling the 'flipped' property
     * for each cart.
     */
    public void reverse() {
        if (config == null || !config.contains("carts")) {
            return;
        }

        List<ConfigurationNode> carts = config.getNodeList("carts");
        Collections.reverse(carts);
        for (ConfigurationNode cart : carts) {
            cart.set("flipped", !cart.get("flipped", false));
        }
        config.setNodeList("carts", carts);
        module.changed = true;
    }

    /**
     * Gets a set of all the claims configured for this saved train.
     * Each entry refers to a unique player UUID.
     * 
     * @return set of claims, unmodifiable
     */
    public Set<SavedTrainPropertiesStore.Claim> getClaims() {
        if (config != null && config.contains("claims")) {
            List<String> claim_strings = config.getList("claims", String.class);
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
        if (config == null) {
            return;
        }

        // Update configuration
        if (claims.isEmpty()) {
            config.remove("claims");
        } else {
            List<String> claim_strings = new ArrayList<String>(claims.size());
            for (Claim claim : claims) {
                claim_strings.add(claim.toString());
            }
            config.set("claims", claim_strings);
        }

        // Mark changed
        module.changed = true;
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
        if (config.isNode("carts")) {
            return config.getNodeList("carts");
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

    /**
     * Creates the SavedTrainProperties viewing the configuration specified
     * 
     * @param module The store in which these properties are stored
     * @param name Saved train name
     * @param config Saved train configuration
     * @return saved train properties
     */
    public static SavedTrainProperties of(SavedTrainPropertiesStore module, String name, ConfigurationNode config) {
        return new SavedTrainProperties(module, name, config);
    }

    /**
     * Creates new SavedTrainProperties that refers to missing properties.
     * This is used when referencing a saved train by name before one is created.
     *
     * @param name Saved train name
     * @return saved train properties
     */
    public static SavedTrainProperties none(String name) {
        return new SavedTrainProperties(null, name, null);
    }
}

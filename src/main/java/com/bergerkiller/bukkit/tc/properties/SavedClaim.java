package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.exception.command.InvalidClaimPlayerNameException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A single player UUID and name who claimed saved configuration.
 * Only this and other players can modify the saved configuration.
 */
public class SavedClaim {
    public final UUID playerUUID;
    public final String playerName;

    public SavedClaim(OfflinePlayer player) {
        this.playerUUID = player.getUniqueId();
        this.playerName = player.getName();
    }

    public SavedClaim(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.playerName = null;
    }

    private SavedClaim(String config) throws IllegalArgumentException {
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
        } else if (o instanceof SavedClaim) {
            SavedClaim other = (SavedClaim) o;
            return other.playerUUID.equals(this.playerUUID);
        } else {
            return false;
        }
    }

    /**
     * Checks whether a player has permission to make changes to a configuration.
     *
     * @param config ConfigurationNode
     * @param sender CommandSender/Player to check
     * @return True if the player has permission
     */
    public static boolean hasPermission(ConfigurationNode config, CommandSender sender) {
        // Console always has permission
        if (!(sender instanceof Player)) {
            return true;
        }

        // Check claims
        Set<SavedClaim> claims = loadClaims(config);
        if (claims.isEmpty()) {
            return true;
        } else {
            UUID playerUUID = ((Player) sender).getUniqueId();
            for (SavedClaim claim : claims) {
                if (playerUUID.equals(claim.playerUUID)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Reads the claims stored inside a configuration
     *
     * @param config ConfigurationNode
     * @return Claims stored in the configuration, an empty set if none are stored
     */
    public static Set<SavedClaim> loadClaims(ConfigurationNode config) {
        if (!config.contains("claims")) {
            return Collections.emptySet();
        }

        List<String> claim_strings = config.getList("claims", String.class);
        if (claim_strings == null || claim_strings.isEmpty()) {
            return Collections.emptySet();
        }

        Set<SavedClaim> claims = new HashSet<>(claim_strings.size());
        for (String claim_str : claim_strings) {
            try {
                claims.add(new SavedClaim(claim_str));
            } catch (IllegalArgumentException ex) {
                // Ignore
            }
        }
        return Collections.unmodifiableSet(claims);
    }

    /**
     * Updates the claims stored inside a configuration. Removes the claims configuration
     * if an empty collection of claims is specified.
     *
     * @param config ConfigurationNode
     * @param claims Claims to store
     */
    public static void saveClaims(ConfigurationNode config, Collection<SavedClaim> claims) {
        // Update configuration
        if (claims.isEmpty()) {
            config.remove("claims");
        } else {
            List<String> claim_strings = new ArrayList<String>(claims.size());
            for (SavedClaim claim : claims) {
                claim_strings.add(claim.toString());
            }
            config.set("claims", claim_strings);
        }
    }

    /**
     * Parses player names or uuids into SavedClaim objects
     *
     * @param oldClaims Previous claims. Re-purposed if matching one of the names
     * @param players Player names to parse
     * @return Input player names parsed into SavedClaims
     * @throws InvalidClaimPlayerNameException
     */
    public static Set<SavedClaim> parseClaims(
            Set<SavedClaim> oldClaims,
            String[] players
    ) {
        Set<SavedClaim> result = new HashSet<>(players.length);
        for (String playerArg : players) {
            try {
                UUID queryUUID = UUID.fromString(playerArg);

                // Look this player up by UUID
                // If not a Player, hasPlayedBefore() checks whether offline player data is available
                // If this is not the case, then the offline player returned was made up, and the name is unreliable
                OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(queryUUID);
                if (!(player instanceof Player) && (player.getName() == null || !player.hasPlayedBefore())) {
                    player = null;
                }
                if (player != null) {
                    result.add(new SavedClaim(player));
                } else {
                    // Try to find in old claims first
                    boolean uuidMatchesOldClaim = false;
                    for (SavedClaim oldClaim : oldClaims) {
                        if (oldClaim.playerUUID.equals(queryUUID)) {
                            result.add(oldClaim);
                            uuidMatchesOldClaim = true;
                            break;
                        }
                    }

                    // Add without a name
                    if (!uuidMatchesOldClaim) {
                        result.add(new SavedClaim(queryUUID));
                    }
                }
            } catch (IllegalArgumentException ex) {
                // Check old claim values for a match first
                boolean nameMatchesOldClaim = false;
                for (SavedClaim oldClaim : oldClaims) {
                    if (oldClaim.playerName != null && oldClaim.playerName.equals(playerArg)) {
                        result.add(oldClaim);
                        nameMatchesOldClaim = true;
                        break;
                    }
                }
                if (nameMatchesOldClaim) {
                    continue; // skip search by name
                }

                // Argument is a player name. Look it up. Verify the offline player is that of an actual player.
                // It comes up with a fake runtime-generated 'OfflinePlayer' UUID that isn't actually valid
                // In Offline mode though, this is valid, and hasPlayedBefore() should indicate that.
                OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(playerArg);
                if (!(player instanceof Player) && (player.getName() == null || !player.hasPlayedBefore())) {
                    throw new InvalidClaimPlayerNameException(playerArg);
                }
                result.add(new SavedClaim(player));
            }
        }
        return result;
    }

    /**
     * Builds a listing of saved claims
     *
     * @param builder Message Builder
     * @param claims Claims to list
     */
    public static void buildClaimList(MessageBuilder builder, Set<SavedClaim> claims) {
        if (claims.isEmpty()) {
            builder.red("Not Claimed");
        } else {
            builder.setSeparator(ChatColor.WHITE, ", ");
            for (SavedClaim claim : claims) {
                OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(claim.playerUUID);
                String name = player.getName();
                if (name == null) {
                    // Player not known on the server, possibly imported from elsewhere
                    name = claim.playerName;
                    if (name == null) {
                        name = claim.playerUUID.toString();
                    }
                    builder.red(name);
                } else if (player.isOnline()) {
                    builder.aqua(name);
                } else {
                    builder.white(name);
                }
            }
        }
    }
}

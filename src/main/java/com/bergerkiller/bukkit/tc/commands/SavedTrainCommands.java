package com.bergerkiller.bukkit.tc.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;

/**
 * Commands to modify an existing saved train
 */
public class SavedTrainCommands {

    public static void execute(CommandSender sender, String savedTrainName, String[] args) throws NoPermissionException {
        // This section verifies the saved train name, and supplies the list command functionality
        // After this block the sender has been verified to have the permission to modify savedTrainName,
        // and that savedTrainName refers to an existing saved train.
        SavedTrainPropertiesStore savedTrains = TrainCarts.plugin.getSavedTrains();
        ConfigurationNode savedTrainConfig;
        boolean checkPerms = (sender instanceof Player) && !Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(sender);
        String command = "info";
        if (args.length > 0) {
            command = args[0].toLowerCase(Locale.ENGLISH);
            args = StringUtil.remove(args, 0);
        }
        if (savedTrains.containsTrain(savedTrainName)) {
            if (checkPerms && !savedTrains.hasPermission(sender, savedTrainName)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to change this saved train!");
                return;
            }
            savedTrainConfig = savedTrains.getConfig(savedTrainName);
        } else {
            // Saved train is not found. Show a warning (if train name is not 'list').
            // Then show all trains the player could be editing
            boolean isListCommand = savedTrainName.equals("list");
            if (!isListCommand) {
                sender.sendMessage(ChatColor.RED + "Saved train '" + savedTrainName + "' does not exist!");
                sender.sendMessage("");
            }

            SavedTrainPropertiesStore module = savedTrains;
            MessageBuilder builder = new MessageBuilder();
            if (isListCommand && args.length > 0 && args[0].equalsIgnoreCase("modules")) {
                builder.blue("The following modules are available:");
                builder.newLine().setSeparator(ChatColor.WHITE, " / ");
                for (String moduleName : savedTrains.getModuleNames()) {
                    builder.aqua(moduleName);
                }
                builder.send(sender);
                return;
            } else if (isListCommand && args.length > 0) {
                module = savedTrains.getModule(args[0]);
                if (module == null) {
                    sender.sendMessage(ChatColor.RED + "Module '" + args[0] + "' does not exist");
                    return;
                }
                builder.blue("The following saved trains are stored in " + args[0] + ":");
            } else {
                builder.yellow("The following saved trains are available:");
            }

            builder.newLine().setSeparator(ChatColor.WHITE, " / ");
            for (String name : module.getNames()) {
                if (!checkPerms || module.hasPermission(sender, name)) {
                    builder.green(name);
                }
            }
            builder.send(sender);
            return;
        }

        // No command specified or 'info'
        if (command.equals("info")) {
            String module = savedTrains.getModuleNameOfTrain(savedTrainName);
            MessageBuilder builder = new MessageBuilder();
            builder.green("Properties of saved train '").white(savedTrainName).green("':").newLine();
            if (module != null) {
                builder.yellow("Stored in module: ").white(module).newLine();
            }
            builder.yellow("Number of carts: ").white(getNumberOfCarts(savedTrainConfig)).newLine();
            builder.yellow("Number of seats: ").white(getNumberOfSeats(savedTrainConfig)).newLine();
            builder.yellow("Total train length: ").white(getTotalTrainLength(savedTrainConfig)).newLine();

            builder.yellow("Claimed by: ");
            buildClaimList(builder, savedTrains.getClaims(savedTrainName));

            builder.send(sender);
            return;
        }

        // Claim: add player to claims list. When no names are specified, adds the sender himself.
        // Disclaim: removes a player from the claims list. When no names are specified, removes the sender himself.
        boolean addClaim = LogicUtil.contains(command, "claim", "addclaim");
        boolean removeClaim = LogicUtil.contains(command, "disclaim", "unclaim", "removeclaim", "deleteclaim");
        boolean setClaim = LogicUtil.contains(command, "setclaim", "setclaims");
        boolean resetClaim = LogicUtil.contains(command, "resetclaim", "resetclaims", "clearclaim", "clearclaims");
        if (addClaim || removeClaim || setClaim || resetClaim) {
            Permission.COMMAND_SAVEDTRAIN_CLAIM.handle(sender);

            List<SavedTrainPropertiesStore.Claim> oldClaims = savedTrains.getClaims(savedTrainName);
            List<SavedTrainPropertiesStore.Claim> claimArgs = new ArrayList<SavedTrainPropertiesStore.Claim>(args.length + 1);
            if (resetClaim) {
                // No claims are set at all, and no arguments are parsed.
            } else if (args.length == 0) {
                if (sender instanceof Player) {
                    claimArgs.add(new SavedTrainPropertiesStore.Claim((Player) sender));
                } else {
                    sender.sendMessage("As server console you need to specify at least one Player Name or UUID argument");
                    return;
                }
            } else {
                for (String arg : args) {
                    try {
                        UUID queryUUID = UUID.fromString(arg);

                        // Look this player up by UUID
                        // If not a Player, hasPlayedBefore() checks whether offline player data is available
                        // If this is not the case, then the offline player returned was made up, and the name is unreliable
                        OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(queryUUID);
                        if (!(player instanceof Player) && (player.getName() == null || !player.hasPlayedBefore())) {
                            player = null;
                        }
                        if (player != null) {
                            claimArgs.add(new SavedTrainPropertiesStore.Claim(player));
                        } else {
                            // Try to find in old claims first
                            boolean uuidMatchesOldClaim = false;
                            for (SavedTrainPropertiesStore.Claim oldClaim : oldClaims) {
                                if (oldClaim.playerUUID.equals(queryUUID)) {
                                    claimArgs.add(oldClaim);
                                    uuidMatchesOldClaim = true;
                                    break;
                                }
                            }

                            // Add without a name
                            if (!uuidMatchesOldClaim) {
                                claimArgs.add(new SavedTrainPropertiesStore.Claim(queryUUID));
                            }
                        }
                    } catch (IllegalArgumentException ex) {
                        // Check old claim values for a match first
                        boolean nameMatchesOldClaim = false;
                        for (SavedTrainPropertiesStore.Claim oldClaim : oldClaims) {
                            if (oldClaim.playerName != null && oldClaim.playerName.equals(arg)) {
                                claimArgs.add(oldClaim);
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
                        OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(arg);
                        if (!(player instanceof Player) && (player.getName() == null || !player.hasPlayedBefore())) {
                            sender.sendMessage(ChatColor.RED + "Unknown player: " + arg);
                            return;
                        }
                        claimArgs.add(new SavedTrainPropertiesStore.Claim(player));
                    }
                }
            }

            // Create a new list of claims
            List<SavedTrainPropertiesStore.Claim> updatedClaimList = new ArrayList<SavedTrainPropertiesStore.Claim>(oldClaims);
            if (addClaim) {
                // Filter players that are already on the claim list
                for (SavedTrainPropertiesStore.Claim newClaim : claimArgs) {
                    if (oldClaims.contains(newClaim)) {
                        sender.sendMessage(ChatColor.YELLOW + "Player " + newClaim.description() + " was already on the claim list!");
                    } else if (!updatedClaimList.contains(newClaim)) {
                        updatedClaimList.add(newClaim);
                    }
                }
            } else if (removeClaim) {
                // Remove the requested players from the claim list
                for (SavedTrainPropertiesStore.Claim removedClaim : claimArgs) {                            
                    if (!updatedClaimList.remove(removedClaim) && !oldClaims.contains(removedClaim)) {
                        sender.sendMessage(ChatColor.YELLOW + "Player " + removedClaim.description() + " was not on the claim list!");
                    }
                }
            } else if (setClaim) {
                // Set claim values directly
                updatedClaimList.clear();
                updatedClaimList.addAll(claimArgs);
            } else if (resetClaim) {
                // Clear all
                updatedClaimList.clear();
            }

            // Set them
            savedTrains.setClaims(savedTrainName, updatedClaimList);

            // Display new values
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("Saved train '").white(savedTrainName).yellow("' is now claimed by: ");
            buildClaimList(builder, updatedClaimList);
            builder.send(sender);

            return;
        }

        sender.sendMessage(ChatColor.RED + "Unknown command: " + command);
    }

    private static void buildClaimList(MessageBuilder builder, List<SavedTrainPropertiesStore.Claim> claims) {
        if (claims.isEmpty()) {
            builder.red("Not Claimed");
        } else {
            builder.setSeparator(ChatColor.WHITE, ", ");
            for (SavedTrainPropertiesStore.Claim claim : claims) {
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
    
    private static int getNumberOfCarts(ConfigurationNode config) {
        return getCarts(config).size();
    }

    private static int getNumberOfSeats(ConfigurationNode config) {
        int count = 0;
        for (ConfigurationNode cart : getCarts(config)) {
            if (cart.isNode("model")) {
                count += getNumberOfSeatAttachmentsRecurse(cart.getNode("model"));
            }
        }
        return count;
    }

    private static double getTotalTrainLength(ConfigurationNode config) {
        double totalLength = 0.0;
        List<ConfigurationNode> carts = getCarts(config);
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

    private static List<ConfigurationNode> getCarts(ConfigurationNode config) {
        if (config.isNode("carts")) {
            return config.getNodeList("carts");
        }
        return Collections.emptyList();
    }

    private static int getNumberOfSeatAttachmentsRecurse(ConfigurationNode attachmentConfig) {
        int count = 0;
        if (attachmentConfig.get("type", CartAttachmentType.EMPTY) == CartAttachmentType.SEAT) {
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

package com.bergerkiller.bukkit.tc.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.Hastebin.DownloadResult;
import com.bergerkiller.bukkit.common.Hastebin.UploadResult;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.BasicConfiguration;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.commands.cloud.ArgumentList;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;

/**
 * Commands to modify an existing saved train
 */
public class SavedTrainCommands {

    @CommandMethod("savedtrain")
    @CommandDescription("Shows command usage of /savedtrain, lists saved trains")
    private void commandSavedTrain(
              final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Use /savedtrain <trainname> [command] to modify saved trains");
        sender.sendMessage("");
        execute(sender, "list", new String[0]);
    }

    @CommandMethod("savedtrain <trainname> [arguments]")
    @CommandDescription("Performs commands that operate on saved trains by name")
    private void commandSavedTrain(
              final CommandSender sender,
              final ArgumentList arguments,
              final @Argument("trainname") String savedTrainName,
              final @Argument("arguments") @Greedy String unused_arguments
    ) {
        execute(sender, savedTrainName, arguments.range(2).array());
    }

    public static void execute(CommandSender sender, String savedTrainName, String[] args) throws NoPermissionException {
        // This section verifies the saved train name, and supplies the list command functionality
        // After this block the sender has been verified to have the permission to modify savedTrainName,
        // and that savedTrainName refers to an existing saved train.
        SavedTrainPropertiesStore savedTrains = TrainCarts.plugin.getSavedTrains();
        ConfigurationNode savedTrainConfig = null;
        boolean checkPermissions = true;
        boolean isClaimedBySender = false;
        String command = "";
        if (args.length > 0) {
            command = args[0].toLowerCase(Locale.ENGLISH);
            args = StringUtil.remove(args, 0);
        }

        // Preprocess a 'force' modifier on the commandline, which allows using the global permission
        if (LogicUtil.contains(command, "force", "forced") && Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(sender)) {
            checkPermissions = false;
            isClaimedBySender = true;
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Please specify the command to force");
                sender.sendMessage(ChatColor.RED + "/savedtrain " + savedTrainName + " force [command]");
                return;
            }

            // Take one extra argument for the command
            command = args[0].toLowerCase(Locale.ENGLISH);
            args = StringUtil.remove(args, 0);
        }

        // Find a previous train by this name, and if it exists, check permissions
        if (savedTrains.containsTrain(savedTrainName)) {
            if (checkPermissions && !savedTrains.hasPermission(sender, savedTrainName)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to change this saved train!");
                return;
            }
            savedTrainConfig = savedTrains.getConfig(savedTrainName);
        }

        // Import: load a pasted saved train configuration from a hastebin server
        // It's an exception case where the train does not already have to exist
        if (LogicUtil.contains(command, "load", "import", "download")) {
            executeImport(sender, savedTrainName, args);
            return;
        }

        if (savedTrainConfig == null) {
            // Saved train is not found. Show a warning (if train name is not 'list').
            // Then show all trains the player could be editing
            boolean isListCommand = savedTrainName.equals("list");
            if (!isListCommand) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.RED + "Saved train '" + savedTrainName + "' does not exist!");
            }

            SavedTrainPropertiesStore module = savedTrains;
            MessageBuilder builder = new MessageBuilder();
            builder.newLine();
            if (isListCommand && command.equals("modules")) {
                builder.blue("The following modules are available:");
                builder.newLine().setSeparator(ChatColor.WHITE, " / ");
                for (String moduleName : savedTrains.getModuleNames()) {
                    builder.aqua(moduleName);
                }
                builder.send(sender);
                return;
            } else if (isListCommand && !command.isEmpty()) {
                module = savedTrains.getModule(command);
                if (module == null) {
                    sender.sendMessage(ChatColor.RED + "Module '" + command + "' does not exist");
                    return;
                }
                builder.blue("The following saved trains are stored in module '" + command + "':");
            } else {
                builder.yellow("The following saved trains are available:");
            }

            builder.newLine().setSeparator(ChatColor.WHITE, " / ");
            for (String name : module.getNames()) {
                if (!checkPermissions || module.hasPermission(sender, name)) {
                    builder.green(name);
                }
            }
            builder.send(sender);
            return;
        }

        // No command specified or 'info'
        if (command.isEmpty() || command.equals("info")) {
            String module = savedTrains.getModuleNameOfTrain(savedTrainName);
            MessageBuilder builder = new MessageBuilder();
            builder.newLine();
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
            if (updatedClaimList.size() > 1) {
                builder.newLine();
            }
            builder.yellow("Saved train '").white(savedTrainName).yellow("' is now claimed by: ");
            buildClaimList(builder, updatedClaimList);
            builder.send(sender);

            return;
        }

        // Change the module in which the train is saved
        if (LogicUtil.contains(command, "module")) {
            String module = (args.length > 0) ? args[0] : null;
            savedTrains.setModuleNameOfTrain(savedTrainName, module);
            if (module == null) {
                sender.sendMessage(ChatColor.GREEN + "Train '" + savedTrainName + "' is now stored in the default module!");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Train '" + savedTrainName + "' is now stored in module '" + module + "'!");
            }
            return;
        }

        // Paste: paste the saved train configuration to a hastebin server
        if (LogicUtil.contains(command, "paste", "share", "export", "upload")) {
            Permission.COMMAND_SAVEDTRAIN_EXPORT.handle(sender);
            ConfigurationNode exportedConfig = savedTrainConfig.clone();
            exportedConfig.remove("claims");
            exportedConfig.set("name", savedTrainName);
            TCConfig.hastebin.upload(exportedConfig.toString()).thenAccept(new Consumer<UploadResult>() {
                @Override
                public void accept(UploadResult t) {
                    if (t.success()) {
                        sender.sendMessage(ChatColor.GREEN + "Train '" + ChatColor.YELLOW + savedTrainName +
                                ChatColor.GREEN + "' exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to export train '" + savedTrainName + "': " + t.error());
                    }
                }
            });
            return;
        }

        // Deny if not claimed by the sender, unless forced
        if (!isClaimedBySender) {
            sender.sendMessage(ChatColor.RED + "The saved train '" + savedTrainName + "' is not yours!");
            sender.sendMessage(ChatColor.RED + "To force the " + command + " command, use:");
            sender.sendMessage(ChatColor.RED + "/savedtrain " + savedTrainName + " force " + command + " " + StringUtil.join(" ", args));
            return;
        }

        // Remove: removes the train. If not self-owned, requires force as second parameter
        if (LogicUtil.contains(command, "delete", "remove", "erase")) {
            Permission.COMMAND_SAVEDTRAIN_DELETE.handle(sender);

            savedTrains.remove(savedTrainName);
            sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + savedTrainName +
                    ChatColor.YELLOW + "' has been removed!");
            return;
        }

        // Rename: change the name of a train
        if (LogicUtil.contains(command, "rename", "changename", "move")) {
            Permission.COMMAND_SAVEDTRAIN_RENAME.handle(sender);

            // Verify name is specified
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Please specify the new name for " + savedTrainName);
                return;
            }
            if (args[0].equals(savedTrainName)) {
                sender.sendMessage(ChatColor.RED + "The new name is the same as the current name");
                return;
            }

            savedTrains.rename(savedTrainName, args[0]);
            sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + savedTrainName +
                    ChatColor.YELLOW + "' has been renamed to '" + ChatColor.WHITE + args[0] +
                    ChatColor.YELLOW + "'!");
            return;
        }

        // Reverse: reverse the order of the carts and flip each individual cart around
        if (LogicUtil.contains(command, "reverse", "inverse", "invert", "flip")) {
            Permission.COMMAND_SAVEDTRAIN_REVERSE.handle(sender);

            savedTrains.reverse(savedTrainName);
            sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrainName +
                    ChatColor.GREEN + "' has been reversed!");
            return;
        }

        sender.sendMessage(ChatColor.RED + "Unknown command: '" + command + "'. Available sub-commands:");
        sender.sendMessage(ChatColor.RED + "[info/claim/unclaim/clearclaims/remove/rename/reverse]");
    }

    public static void executeImport(CommandSender sender, final String savedTrainName, String[] args) throws NoPermissionException {
        Permission.COMMAND_SAVEDTRAIN_IMPORT.handle(sender);

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify the URL to a Hastebin-hosted paste to download from");
            return;
        }

        TCConfig.hastebin.download(args[0]).thenAccept(new Consumer<DownloadResult>() {
            @Override
            public void accept(DownloadResult result) {
                // Check successful
                if (!result.success()) {
                    sender.sendMessage(ChatColor.RED + "Failed to import train: " + result.error());
                    return;
                }

                // Parse the String contents as YAML
                BasicConfiguration config;
                try {
                    config = result.contentYAML();
                } catch (IOException ex) {
                    sender.sendMessage(ChatColor.RED + "Failed to import train because of YAML decode error: " + ex.getMessage());
                    return;
                }

                // Update configuration
                SavedTrainPropertiesStore savedTrains = TrainCarts.plugin.getSavedTrains();
                boolean isNewTrain = !savedTrains.containsTrain(savedTrainName);
                try {
                    savedTrains.setConfig(savedTrainName, config);
                } catch (IllegalNameException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid train name: " + savedTrainName);
                    return;
                }
                if (isNewTrain) {
                    sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrainName);
                    if (TCConfig.claimNewSavedTrains && sender instanceof Player) {
                        savedTrains.setClaim(savedTrainName, (Player) sender);
                    }
                } else {
                    sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrainName + ", a previous train was overwritten");
                }
            }
        });
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

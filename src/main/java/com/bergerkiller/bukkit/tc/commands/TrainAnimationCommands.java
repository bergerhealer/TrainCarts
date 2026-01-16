package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.Hastebin;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.ui.AnimationFramesImportExport;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.utils.QuoteEscapedString;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.FlagYielding;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Commands to read or modify the current train animation selected in the attachment editor
 */
@Command("train animation")
public class TrainAnimationCommands {

    private AnimationFramesImportExport tryAccessAnimationMenu(Player player) {
        // Get editor instance
        MapDisplay display = MapDisplay.getHeldDisplay(player, AttachmentEditor.class);
        if (display == null) {
            display = MapDisplay.getHeldDisplay(player);
            if (display == null) {
                player.sendMessage(ChatColor.RED + "You do not have an editor menu open");
                return null;
            }
        }

        // Find focused widget
        MapWidget focused = display.getFocusedWidget();
        if (!(focused instanceof AnimationFramesImportExport)) {
            focused = display.getActivatedWidget();
        }
        if (!(focused instanceof AnimationFramesImportExport)) {
            player.sendMessage(ChatColor.RED + "Train attachment animation menu is not open!");
            return null;
        }

        AnimationFramesImportExport menu = (AnimationFramesImportExport) focused;
        if (menu.getAnimationName() == null) {
            player.sendMessage(ChatColor.RED + "No animation is selected yet, please create one!");
            return null;
        }

        return menu;
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("export")
    @CommandDescription("Exports the train animation frames to a hastebin server")
    private void commandTrainAnimationExport(
            final TrainCarts plugin,
            final Player player
    ) {
        AnimationFramesImportExport menu = tryAccessAnimationMenu(player);
        if (menu == null) {
            return;
        }

        List<AnimationNode> nodes = menu.exportAnimationFrames();
        if (nodes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Animation is empty");
            return;
        }

        final String animationName = menu.getAnimationName();

        // Build up the basic configuration
        ConfigurationNode tmp = new ConfigurationNode();
        tmp.set("nodes", nodes.stream()
                .map(AnimationNode::serializeToString)
                .collect(Collectors.toList()));

        // Serialize as yaml, but omit the parent nodes: heading and omit the indent of each animation line
        final String config = Pattern.compile("\r?\n").splitAsStream(tmp.toString())
                        .skip(1)
                        .map(String::trim)
                        .collect(Collectors.joining("\n"));

        TCConfig.hastebin.upload(config).thenAccept(new Consumer<Hastebin.UploadResult>() {
            @Override
            public void accept(Hastebin.UploadResult t) {
                if (t.success()) {
                    player.sendMessage(ChatColor.GREEN + "Animation '" + ChatColor.YELLOW + animationName +
                            ChatColor.GREEN + "' exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to export animation '" + animationName + "': " + t.error());
                }
            }
        });
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("import <url>")
    @CommandDescription("Imports train attachment animation frames from an online hastebin server by url")
    private void commandTrainAnimationImport(
            final TrainCarts plugin,
            final Player player,
            final @Greedy @FlagYielding @Argument(value="url", description="The URL to a Hastebin-hosted paste to download from") String url,
            final @Flag("insert") boolean insert
    ) {
        // Little check before doing the actual download to avoid wasted time
        if (tryAccessAnimationMenu(player) == null) {
            return;
        }

        TCConfig.hastebin.download(url).thenAccept(new Consumer<Hastebin.DownloadResult>() {
            @Override
            public void accept(Hastebin.DownloadResult result) {
                // Check successful
                if (!result.success()) {
                    Localization.COMMAND_IMPORT_ERROR.message(player, result.error());
                    return;
                }

                // Parse the contents. This is probably edited by the user themselves, so be very relaxed
                // about how to parse the lines.
                // Omit all special encoding stuff like indents and quote-escaping
                List<AnimationNode> frames;
                try {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(result.contentInputStream(), StandardCharsets.UTF_8))) {
                        frames = reader.lines()
                                // If indented, omit that
                                .map(String::trim)
                                // If starting with a list prefix (-), omit that
                                .map(s -> s.startsWith("-") ? (s.substring(1).trim()) : s)
                                // If quote-escaped, undo that
                                .map(s -> {
                                    QuoteEscapedString q = QuoteEscapedString.tryParseQuoted(s);
                                    return q.isQuoteEscaped() ? q.getUnescaped() : s;
                                })
                                // Parse as animation node
                                .map(AnimationNode::parseFromString)
                                // Omit invalid (empty) lines
                                .filter(AnimationNode::hasValidDuration)
                                // Done
                                .collect(Collectors.toList());
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed to import animation", t);
                    Localization.COMMAND_IMPORT_ERROR.message(player, t.getMessage());
                    return;
                }

                // Guard against empty (import error?)
                if (frames.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No animation frames could be read from the provided url");
                    return;
                }

                // Check still in menu
                AnimationFramesImportExport menu = tryAccessAnimationMenu(player);
                if (menu == null) {
                    return;
                }

                // Apply to menu
                String animationName = menu.getAnimationName();
                menu.importAnimationFrames(frames, insert);
                player.sendMessage(ChatColor.GREEN + "Imported " + ChatColor.WHITE + frames.size() +
                        ChatColor.GREEN + " frames into animation '" + ChatColor.YELLOW + animationName +
                        ChatColor.GREEN + "'!");
            }
        });
    }
}

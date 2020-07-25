package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.permissions.PermissionEnum;
import com.bergerkiller.bukkit.common.wrappers.ChatText;

/**
 * Stores parameters important when handling the event of a player placing down a sign.
 * Once all the details have been filled out, the {@link #handle(Player)} function can be
 * called to handle everything.
 */
public class SignBuildOptions {
    private PermissionEnum permission = null;
    private String name = null;
    private String helpURL = null;
    private String helpAlt = null;
    private String description = null;

    protected SignBuildOptions() {
    }

    /**
     * Sets a required permission for building the sign
     * 
     * @param permission
     * @return this
     */
    public SignBuildOptions setPermission(PermissionEnum permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Sets the name of the sign being built
     * 
     * @param name
     * @return this
     */
    public SignBuildOptions setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets an extra short description of the sign being built
     * 
     * @param description
     * @return this
     */
    public SignBuildOptions setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Calls {@link #setHelpURL(url, alt)} with information about a page
     * on the Minecraft Wiki.
     * 
     * @param page Path on the wiki where the information is located
     * @return this
     */
    public SignBuildOptions setMinecraftWIKIHelp(String page) {
        return setHelpURL("https://minecraft.gamepedia.com/" + page,
                "Click here to visit the Minecraft WIKI for help with this sign");
    }

    /**
     * Sets a URL a player can navigate to for extra help with the sign.
     * The url appears where the name of the sign appears as a clickable link.
     * 
     * @param url The url
     * @return this
     */
    public SignBuildOptions setHelpURL(String url) {
        this.helpURL = url;
        return this;
    }

    /**
     * Sets a URL a player can navigate to for extra help with the sign.
     * The url appears where the name of the sign appears as a clickable link.
     * 
     * @param url The url
     * @param alt The text shown when hovering over the link
     * @return this
     */
    public SignBuildOptions setHelpURL(String url, String alt) {
        this.helpURL = url;
        this.helpAlt = alt;
        return this;
    }

    /**
     * Handles building of the sign. Checks permission, if set, and sends the appropriate messages
     * 
     * @param player The player to check permission on and send messages to
     * @return True if the player could build the sign
     */
    public boolean handle(Player player) {
        // Permission
        if (permission != null && !permission.handleMsg(player, ChatColor.RED + "You do not have permission to use this sign")) {
            return false;
        }

        // Tell what sign was just built
        if (name != null) {
            ChatText message = ChatText.fromMessage(ChatColor.YELLOW + "You built a ");
            if (helpURL != null) {
                message.appendClickableURL(ChatColor.WHITE.toString() + ChatColor.UNDERLINE.toString() + name, helpURL, helpAlt);
            } else {
                message.append(ChatColor.WHITE.toString() + name);
            }
            message.append(ChatColor.YELLOW + "!");
            message.sendTo(player);
        }

        // Give a description of the sign that was built
        if (description != null) {
            player.sendMessage(ChatColor.GREEN + "This sign can " + description + ".");
        }

        return true;
    }

    /**
     * Creates a new instance of the options, ready for filling in the details
     * 
     * @return new SignBuildOptions
     */
    public static SignBuildOptions create() {
        return new SignBuildOptions();
    }
}

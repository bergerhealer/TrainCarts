package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.permissions.IPermissionEnum;
import com.bergerkiller.bukkit.common.permissions.PermissionEnum;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Localization;

/**
 * Stores parameters important when handling the event of a player placing down a sign.
 * Once all the details have been filled out, the {@link #handle(Player)} function can be
 * called to handle everything.
 */
public class SignBuildOptions {
    private String permission = null;
    private String name = null;
    private String helpURL = null;
    private String helpAlt = null;
    private String description = null;

    protected SignBuildOptions() {
    }

    /**
     * Sets a required permission for building the sign
     * 
     * @param permission Permission using the PermissionEnum BKCommonLib API.
     *                   Specify null to not check any permissions.
     * @return this
     */
    public SignBuildOptions setPermission(PermissionEnum permission) {
        return setPermission((permission == null) ? null : permission.getName());
    }

    /**
     * Sets a required permission for building the sign
     * 
     * @param permission Permission using the IPermissionEnum BKCommonLib API.
     *                   Specify null to not check any permissions.
     * @return this
     */
    public SignBuildOptions setPermission(IPermissionEnum permission) {
        return setPermission((permission == null) ? null : permission.getName());
    }

    /**
     * Sets a required permission for building the sign
     * 
     * @param permissionNode Permission node to check.
     *                       Specify null to not check any permissions.
     * @return this
     */
    public SignBuildOptions setPermission(String permissionNode) {
        this.permission = permissionNode;
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
     * Calls {@link #setHelpURL(String, String)}  with information about a page
     * on the Traincarts Wiki.
     * 
     * @param page Path on the wiki where the information is located
     * @return this
     */
    public SignBuildOptions setTraincartsWIKIHelp(String page) {
        return setHelpURL("https://wiki.traincarts.net/index.php/" + page,
                "Click here to visit the Traincarts WIKI for help with this sign");
    }

    /**
     * Calls {@link #setHelpURL(String, String)} with information about a page
     * on the Minecraft Wiki.
     * 
     * @param page Path on the wiki where the information is located
     * @return this
     */
    public SignBuildOptions setMinecraftWIKIHelp(String page) {
        return setHelpURL("https://minecraft.wiki/w/" + page,
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
     * Checks that the player has permission to build this type of sign at all.
     * Sends a message indicating the player can't if this returns false.
     *
     * @param player The player to check permission on
     * @return True if the player can build the sign
     */
    public boolean checkBuildPermission(Player player) {
        // Permission
        if (permission != null && !CommonUtil.hasPermission(player, this.permission)) {
            Localization.SIGN_NO_PERMISSION.message(player, LogicUtil.fixNull(name, ""));
            return false;
        }

        return true;
    }

    /**
     * Shows a successful build message of this type of sign with additional information
     * as provided through this builder.
     *
     * @param player The player to send the build message to
     */
    public void showBuildMessage(Player player) {
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
    }

    /**
     * Handles building of the sign. Checks permission, if set, and sends the appropriate messages
     * 
     * @param player The player to check permission on and send messages to
     * @return True if the player could build the sign
     * @see #checkBuildPermission(Player)
     * @see #showBuildMessage(Player)
     */
    public boolean handle(Player player) {
        if (checkBuildPermission(player)) {
            showBuildMessage(player);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Handles building of the sign. Checks permission, if set, and sends the appropriate messages.
     * In addition to {@link #handle(Player)} checks whether the sign was placed non-interactively,
     * in which case no build message is sent.
     *
     * @param event SignChangeActionEvent describing the building of a new sign
     * @return True if the player could build the sign
     * @see SignChangeActionEvent#isInteractive()
     * @see #handle(Player)
     */
    public boolean handle(SignChangeActionEvent event) {
        if (!checkBuildPermission(event.getPlayer())) {
            return false;
        } else {
            if (event.isInteractive()) {
                showBuildMessage(event.getPlayer());
            }
            return true;
        }
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

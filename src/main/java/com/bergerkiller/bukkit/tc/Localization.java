package com.bergerkiller.bukkit.tc;

import cloud.commandframework.captions.Caption;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.localization.LocalizationEnum;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;

public class Localization extends LocalizationEnum {
    public static final Localization COMMAND_USAGE = new Localization("command.usage", ChatColor.GREEN + "See " + "[" + ChatColor.WHITE + ChatColor.UNDERLINE + "the WIKI](https://wiki.traincarts.net/p/TrainCarts)" + ChatColor.RESET + ChatColor.GREEN + " for more information, or use /train help"),
            COMMAND_NOPERM = new Localization("command.noperm", ChatColor.RED + "You do not have permission, ask an admin to do this for you."),
            COMMAND_SAVEDTRAIN_CLAIMED = new Localization("command.savedtrain.claimed", ChatColor.RED + "Saved train with name %0% is claimed by someone else, you can not access it!"),
            COMMAND_SAVEDTRAIN_GLOBAL_NOPERM = new Localization("command.savedtrain.global.noperm", ChatColor.RED + "You do not have permission to force access to saved trains by others, ask an admin to do this for you."),
            COMMAND_SAVEDTRAIN_NOTFOUND = new Localization("command.savedtrain.notfound", ChatColor.RED + "Saved train with name %0% does not exist!"),
            COMMAND_SAVEDTRAIN_FORCE = new Localization("command.savedtrain.force", ChatColor.RED + "Saved train with name %0% is claimed by someone else, you can access it anyway with --force"),
            COMMAND_SAVEDTRAIN_CLAIM_INVALID = new Localization("command.savedtrain.claim.invalid", ChatColor.RED + "Invalid player name specified: %0%"),
            COMMAND_SAVEDTRAIN_INVALID_NAME = new Localization("command.savedtrain.name.invalid", ChatColor.RED + "Invalid train name: %0%"),
            COMMAND_TICKET_NOTFOUND = new Localization("command.ticket.notfound", ChatColor.RED + "Ticket with name %0% does not exist"),
            COMMAND_TICKET_NOTEDITING = new Localization("command.ticket.notediting",
                    ChatColor.RED + "You are not editing any tickets right now\n" +
                            ChatColor.RED + "To create a new train ticket, use /train ticket create\n" +
                            ChatColor.RED + "To edit an existing train ticket, use /train ticket edit [name]"),
            COMMAND_TRAIN_NOT_FOUND = new Localization("command.input.train.notfound", ChatColor.RED + "Train with name %0% does not exist"),
            COMMAND_CART_NOT_FOUND_IN_TRAIN = new Localization("command.input.cart.notintrain", ChatColor.RED + "Cart '%0%' does not exist in the selected train"),
            COMMAND_CART_NOT_FOUND_BY_UUID = new Localization("command.input.cart.uuidnotfound", ChatColor.RED + "Cart with unique ID %0% does not exist"),
            COMMAND_CART_NOT_FOUND_NEARBY = new Localization("command.input.cart.notnearby", ChatColor.RED + "No cart was found near the specified coordinates"),
            COMMAND_INPUT_SPEED_INVALID = new Localization("command.input.speed.invalid", ChatColor.RED + "Input value %0% is not a valid number or speed expression"),
            COMMAND_INPUT_ACCELERATION_INVALID = new Localization("command.input.acceleration.invalid", ChatColor.RED + "Input value %0% is not a valid number or acceleration expression"),
            COMMAND_INPUT_DIRECTION_INVALID = new Localization("command.input.direction.invalid", ChatColor.RED + "Input value %0% is not a valid direction"),
            COMMAND_INPUT_NAME_EMPTY = new Localization("command.input.name.empty", ChatColor.RED + "Input train name is empty!"),

    PROPERTY_NOTFOUND = new Localization("property.notfound", ChatColor.RED + "Property with name '%0%' does not exist"),
            PROPERTY_ERROR = new Localization("property.error", ChatColor.RED + "An internal error occurred while parsing value '%1%' for property '%0%'"),
            PROPERTY_INVALID_INPUT = new Localization("property.invalidinput", ChatColor.RED + "Value '%1%' for property '%0%' is invalid: %2%"),
            PROPERTY_NOPERM_ANY = new Localization("property.nopermissionany", ChatColor.RED + "You do not have permission to modify train properties"),
            PROPERTY_NOPERM = new Localization("property.nopermission", ChatColor.RED + "You do not have permission to modify the property with name '%0%'"),

    EDIT_NOSELECT = new Localization("edit.noselect", ChatColor.YELLOW + "You haven't selected a train to edit yet!"),
            EDIT_NOTALLOWED = new Localization("edit.notallowed", ChatColor.RED + "You are not allowed to own trains!"),
            EDIT_NONEFOUND = new Localization("edit.nonefound", ChatColor.RED + "You do not own any trains you can edit."),
            EDIT_NOTOWNED = new Localization("edit.notowned", ChatColor.RED + "You do not own this train!"),
            EDIT_NOTLOADED = new Localization("edit.notloaded", ChatColor.RED + "The selected train is not loaded right now!"),

    SPAWN_DISALLOWED_TYPE = new Localization("spawn.type.notallowed", ChatColor.RED + "You do not have permission to create minecarts of type %0%"),

    SELECT_DESTINATION = new Localization("select.destination", ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + "%0%" + ChatColor.YELLOW + " as your destination!"),
            TICKET_EXPIRED = new Localization("ticket.expired", ChatColor.RED + "Your ticket for %0% is expired"),
            TICKET_REQUIRED = new Localization("ticket.required", ChatColor.RED + "You do not own a ticket for this train!"),
            TICKET_USED = new Localization("ticket.used", ChatColor.GREEN + "You have used your " + ChatColor.YELLOW + "%0%" + ChatColor.GREEN + " ticket!"),
            TICKET_CONFLICT = new Localization("ticket.conflict", ChatColor.RED + "You own multiple tickets that can be used for this train. Please hold the right ticket in your hand!"),
            TICKET_CONFLICT_OWNER = new Localization("ticket.ownerConflict", ChatColor.RED + "The train ticket %0% is not yours, it belongs to %1%!"),
            TICKET_CONFLICT_TYPE = new Localization("ticket.typeConflict", ChatColor.RED + "The train ticket %0% can not be used for this train!"),
            WAITER_TARGET_NOT_FOUND = new Localization("waiter.notfound", ChatColor.RED + "Didn't find a " + ChatColor.YELLOW + "%0%" + ChatColor.RED + " sign on the track!"),

    TICKET_ADD = new Localization("ticket.add", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You received %0% in your bank account!"),
            TICKET_CHECK = new Localization("ticket.check", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You currently have %0% in your bank account!"),
            TICKET_BUYFAIL = new Localization("ticket.buyfail", ChatColor.WHITE + "[Ticket System]" + ChatColor.RED + " You can't afford a Ticket for %0%, sorry."),
            TICKET_BUY = new Localization("ticket.buy", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You bought a Ticket for %0%."),
            TICKET_BUYOWNER = new Localization("ticket.buyowner", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " %0% " + ChatColor.YELLOW + "bought a Ticket for %1% on " + ChatColor.WHITE + "%2%" + ChatColor.YELLOW + "."),
            TICKET_MAP_INVALID = new Localization("ticket.map.invalid", "Invalid Ticket"),
            TICKET_MAP_EXPIRED = new Localization("ticket.map.expired", "EXPIRED"),
            TICKET_MAP_USES = new Localization("ticket.map.uses", "%1%/%0% uses") {
                @Override
                public void writeDefaults(ConfigurationNode config, String path) {
                    ConfigurationNode node = config.getNode(path);
                    node.set("1", "Single use");
                    node.set("-1", "Unlimited uses");
                    node.set("default", "%1%/%0% uses");
                }
            },

    // pathfinding
    PATHING_BUSY = new Localization("pathfinding.busy", ChatColor.YELLOW + "Looking for a way to reach the destination..."),
            PATHING_FAILED = new Localization("pathfinding.failed", ChatColor.RED + "Destination " + ChatColor.YELLOW + "%0%" + ChatColor.RED + " could not be reached from here!"),

    // train storing chest
    CHEST_NOPERM = new Localization("chest.noperm", ChatColor.RED + "You do not have permission to use the train storage chest!"),
            CHEST_NOITEM = new Localization("chest.noitem", ChatColor.RED + "You are not currently holding a train storage chest item!"),
            CHEST_GIVE = new Localization("chest.give", ChatColor.GREEN + "You have been given a train storage chest item. Use it to store and spawn trains"),
            CHEST_GIVE_TO = new Localization("chest.giveto", ChatColor.GREEN + "Gave a train storage chest item to player %0%"),
            CHEST_UPDATE = new Localization("chest.update", ChatColor.GREEN + "Your train storage chest item has been updated"),
            CHEST_LOCKED = new Localization("chest.locked", ChatColor.RED + "Your train storage chest item is locked and can not pick up the train"),
            CHEST_PICKUP = new Localization("chest.pickup", ChatColor.GREEN + "Train picked up and stored inside the item!"),
            CHEST_FULL = new Localization("chest.full", ChatColor.RED + "Your train storage chest item is full and can not pick up the train"),
            CHEST_IMPORTED = new Localization("chest.imported", ChatColor.GREEN + "The train was imported into the chest item"),
            CHEST_SPAWN_SUCCESS = new Localization("chest.spawn.success", ChatColor.GREEN + "Train stored inside the item has been spawned on the rails!"),
            CHEST_SPAWN_EMPTY = new Localization("chest.spawn.empty", ChatColor.RED + "Train can not be spawned, no train is stored in the item!"),
            CHEST_SPAWN_NORAIL = new Localization("chest.spawn.norail", ChatColor.RED + "Train can not be spawned, clicked block is not a known rail!"),
            CHEST_SPAWN_RAILTOOSHORT = new Localization("chest.spawn.railtooshort", ChatColor.RED + "Train can not be spawned, rails not long enough to fit the train!"),
            CHEST_SPAWN_BLOCKED = new Localization("chest.spawn.blocked", ChatColor.RED + "Train can not be spawned, no space on rails because another train is in the way!"),

    // signs
    SIGN_NO_PERMISSION = new Localization("sign.noperm", ChatColor.RED + "You do not have permission to use this sign"),

    // animate command
    COMMAND_ANIMATE_SUCCESS = new Localization("command.animate.success", ChatColor.GREEN + "Now playing animation " + ChatColor.YELLOW + "%0%" +
            ChatColor.GREEN + " at speed " + ChatColor.YELLOW + "%1%" + ChatColor.GREEN + " with phase delay " + ChatColor.YELLOW + "%2%"),
    COMMAND_ANIMATE_FAILURE = new Localization("command.animate.failure", ChatColor.RED + "Failed to find animation " + ChatColor.YELLOW + "%0%" + ChatColor.RED + "!");

    private Localization(String name, String defValue) {
        super(name, defValue);
    }

    /**
     * Gets a boolean 'yes' or 'no' colored red/green respectively
     *
     * @param value
     * @return Yes if true, No if false
     */
    public static String boolStr(boolean value) {
        return value ? (ChatColor.GREEN + "Yes") : (ChatColor.RED + "No");
    }

    @Override
    public String get(String... arguments) {
        return TrainCarts.plugin.getLocale(this.getName(), arguments);
    }

    /**
     * Gets the cloud framework caption matching this localization
     *
     * @return caption
     */
    public Caption getCaption() {
        return Caption.of(getName());
    }

    public void broadcast(MinecartGroup group, String... arguments) {
        final HashSet<Player> receivers = new HashSet<>();
        for (MinecartMember<?> member : group) {
            // Editing
            receivers.addAll(member.getProperties().getEditingPlayers());
            // Occupants
            if (member.getEntity().hasPlayerPassenger()) receivers.add(member.getEntity().getPlayerPassenger());
        }
        receivers.forEach(player -> this.message(player, arguments));
    }
}

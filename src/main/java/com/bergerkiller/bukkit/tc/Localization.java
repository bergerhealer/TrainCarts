package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import cloud.commandframework.captions.Caption;

import java.util.HashSet;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Localization extends LocalizationEnum {
    public static final Localization COMMAND_USAGE = new Localization("command.usage", ChatColor.GREEN + "See " + "[" + ChatColor.WHITE + ChatColor.UNDERLINE + "the WIKI](https://wiki.traincarts.net/p/TrainCarts)" + ChatColor.RESET + ChatColor.GREEN + " for more information, or use /train help");
    public static final Localization COMMAND_NOPERM = new Localization("command.noperm", ChatColor.RED + "You do not have permission, ask an admin to do this for you.");
    public static final Localization COMMAND_SAVEDTRAIN_CLAIMED = new Localization("command.savedtrain.claimed", ChatColor.RED + "Saved train with name %0% is claimed by someone else, you can not access it!");
    public static final Localization COMMAND_SAVEDTRAIN_GLOBAL_NOPERM = new Localization("command.savedtrain.global.noperm", ChatColor.RED + "You do not have permission to force access to saved trains by others, ask an admin to do this for you.");
    public static final Localization COMMAND_SAVEDTRAIN_NOTFOUND = new Localization("command.savedtrain.notfound", ChatColor.RED + "Saved train with name %0% does not exist!");
    public static final Localization COMMAND_SAVEDTRAIN_FORCE = new Localization("command.savedtrain.force", ChatColor.RED + "Saved train with name %0% is claimed by someone else, you can access it anyway with --force");
    public static final Localization COMMAND_SAVEDTRAIN_CLAIM_INVALID = new Localization("command.savedtrain.claim.invalid", ChatColor.RED + "Invalid player name specified: %0%");
    public static final Localization COMMAND_SAVEDTRAIN_INVALID_NAME = new Localization("command.savedtrain.name.invalid", ChatColor.RED + "Invalid train name: %0%");
    public static final Localization COMMAND_TICKET_NOTFOUND = new Localization("command.ticket.notfound", ChatColor.RED + "Ticket with name %0% does not exist");
    public static final Localization COMMAND_TICKET_NOTEDITING = new Localization("command.ticket.notediting",
            ChatColor.RED + "You are not editing any tickets right now\n" +
            ChatColor.RED + "To create a new train ticket, use /train ticket create\n" +
            ChatColor.RED + "To edit an existing train ticket, use /train ticket edit [name]");

    public static final Localization COMMAND_TRAIN_NOT_FOUND = new Localization("command.input.train.notfound", ChatColor.RED + "Train with name %0% does not exist");
    public static final Localization COMMAND_CART_NOT_FOUND_IN_TRAIN = new Localization("command.input.cart.notintrain", ChatColor.RED + "Cart '%0%' does not exist in the selected train");
    public static final Localization COMMAND_CART_NOT_FOUND_BY_UUID = new Localization("command.input.cart.uuidnotfound", ChatColor.RED + "Cart with unique ID %0% does not exist");
    public static final Localization COMMAND_CART_NOT_FOUND_NEARBY = new Localization("command.input.cart.notnearby", ChatColor.RED + "No cart was found near the specified coordinates");
    public static final Localization COMMAND_INPUT_SPEED_INVALID = new Localization("command.input.speed.invalid", ChatColor.RED + "Input value %0% is not a valid number or speed expression");
    public static final Localization COMMAND_INPUT_ACCELERATION_INVALID = new Localization("command.input.acceleration.invalid", ChatColor.RED + "Input value %0% is not a valid number or acceleration expression");
    public static final Localization COMMAND_INPUT_DIRECTION_INVALID = new Localization("command.input.direction.invalid", ChatColor.RED + "Input value %0% is not a valid direction");
    public static final Localization COMMAND_INPUT_NAME_EMPTY = new Localization("command.input.name.empty", ChatColor.RED + "Input train name is empty!");

    public static final Localization PROPERTY_NOTFOUND = new Localization("property.notfound", ChatColor.RED + "Property with name '%0%' does not exist");
    public static final Localization PROPERTY_ERROR = new Localization("property.error", ChatColor.RED + "An internal error occurred while parsing value '%1%' for property '%0%'");
    public static final Localization PROPERTY_INVALID_INPUT = new Localization("property.invalidinput", ChatColor.RED + "Value '%1%' for property '%0%' is invalid: %2%");
    public static final Localization PROPERTY_NOPERM_ANY = new Localization("property.nopermissionany", ChatColor.RED + "You do not have permission to modify train properties");
    public static final Localization PROPERTY_NOPERM = new Localization("property.nopermission", ChatColor.RED + "You do not have permission to modify the property with name '%0%'");

    public static final Localization EDIT_NOSELECT = new Localization("edit.noselect", ChatColor.YELLOW + "You haven't selected a train to edit yet!");
    public static final Localization EDIT_NOTALLOWED = new Localization("edit.notallowed", ChatColor.RED + "You are not allowed to own trains!");
    public static final Localization EDIT_NONEFOUND = new Localization("edit.nonefound", ChatColor.RED + "You do not own any trains you can edit.");
    public static final Localization EDIT_NOTOWNED = new Localization("edit.notowned", ChatColor.RED + "You do not own this train!");
    public static final Localization EDIT_NOTLOADED = new Localization("edit.notloaded", ChatColor.RED + "The selected train is not loaded right now!");

    public static final Localization SELECT_DESTINATION = new Localization("select.destination", ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + "%0%" + ChatColor.YELLOW + " as your destination!");
    public static final Localization TICKET_EXPIRED = new Localization("ticket.expired", ChatColor.RED + "Your ticket for %0% is expired");
    public static final Localization TICKET_REQUIRED = new Localization("ticket.required", ChatColor.RED + "You do not own a ticket for this train!");
    public static final Localization TICKET_USED = new Localization("ticket.used", ChatColor.GREEN + "You have used your " + ChatColor.YELLOW + "%0%" + ChatColor.GREEN + " ticket!");
    public static final Localization TICKET_CONFLICT = new Localization("ticket.conflict", ChatColor.RED + "You own multiple tickets that can be used for this train. Please hold the right ticket in your hand!");
    public static final Localization TICKET_CONFLICT_OWNER = new Localization("ticket.ownerConflict", ChatColor.RED + "The train ticket %0% is not yours, it belongs to %1%!");
    public static final Localization TICKET_CONFLICT_TYPE = new Localization("ticket.typeConflict", ChatColor.RED + "The train ticket %0% can not be used for this train!");
    public static final Localization WAITER_TARGET_NOT_FOUND = new Localization("waiter.notfound", ChatColor.RED + "Didn't find a " + ChatColor.YELLOW + "%0%" + ChatColor.RED + " sign on the track!");

    public static final Localization TICKET_ADD = new Localization("ticket.add", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You received %0% in your bank account!");
    public static final Localization TICKET_CHECK = new Localization("ticket.check", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You currently have %0% in your bank account!");
    public static final Localization TICKET_BUYFAIL = new Localization("ticket.buyfail", ChatColor.WHITE + "[Ticket System]" + ChatColor.RED + " You can't afford a Ticket for %0%, sorry.");
    public static final Localization TICKET_BUY = new Localization("ticket.buy", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You bought a Ticket for %0%.");
    public static final Localization TICKET_BUYOWNER = new Localization("ticket.buyowner", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " %0% " + ChatColor.YELLOW + "bought a Ticket for %1% on " + ChatColor.WHITE + "%2%" + ChatColor.YELLOW + ".");

    // pathfinding
    public static final Localization PATHING_BUSY = new Localization("pathfinding.busy", ChatColor.YELLOW + "Looking for a way to reach the destination...");
    public static final Localization PATHING_FAILED = new Localization("pathfinding.failed", ChatColor.RED + "Destination " + ChatColor.YELLOW + "%0%" + ChatColor.RED + " could not be reached from here!");

    // train storing chest
    public static final Localization CHEST_NOPERM = new Localization("chest.noperm", ChatColor.RED + "You do not have permission to use the train storage chest!");
    public static final Localization CHEST_NOITEM = new Localization("chest.noitem", ChatColor.RED + "You are not currently holding a train storage chest item!");
    public static final Localization CHEST_GIVE = new Localization("chest.give", ChatColor.GREEN + "You have been given a train storage chest item. Use it to store and spawn trains");
    public static final Localization CHEST_UPDATE = new Localization("chest.update", ChatColor.GREEN + "Your train storage chest item has been updated");
    public static final Localization CHEST_LOCKED = new Localization("chest.locked", ChatColor.RED + "Your train storage chest item is locked and can not pick up the train");
    public static final Localization CHEST_PICKUP = new Localization("chest.pickup", ChatColor.GREEN + "Train picked up and stored inside the item!");
    public static final Localization CHEST_IMPORTED = new Localization("chest.imported", ChatColor.GREEN + "The train was imported into the chest item");
    public static final Localization CHEST_SPAWN_SUCCESS = new Localization("chest.spawn.success", ChatColor.GREEN + "Train stored inside the item has been spawned on the rails!");
    public static final Localization CHEST_SPAWN_EMPTY = new Localization("chest.spawn.empty", ChatColor.RED + "Train can not be spawned, no train is stored in the item!");
    public static final Localization CHEST_SPAWN_NORAIL = new Localization("chest.spawn.norail", ChatColor.RED + "Train can not be spawned, clicked block is not a known rail!");
    public static final Localization CHEST_SPAWN_BLOCKED = new Localization("chest.spawn.blocked", ChatColor.RED + "Train can not be spawned, no space on rails!");

    // signs
    public static final Localization SIGN_NO_PERMISSION = new Localization("sign.noperm", ChatColor.RED + "You do not have permission to use this sign");

    // animate command
    public static final Localization COMMAND_ANIMATE_SUCCESS = new Localization("command.animate.success", ChatColor.GREEN + "Now playing animation " + ChatColor.YELLOW + "%0%" +
            ChatColor.GREEN + " at speed " + ChatColor.YELLOW + "%1%" + ChatColor.GREEN + " with phase delay " + ChatColor.YELLOW + "%2%");
    public static final Localization COMMAND_ANIMATE_FAILURE = new Localization("command.animate.failure", ChatColor.RED + "Failed to find animation " + ChatColor.YELLOW + "%0%" + ChatColor.RED + "!");

    private Localization(String name, String defValue) {
        super(name, defValue);
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
        HashSet<Player> receivers = new HashSet<>();
        for (MinecartMember<?> member : group) {
            // Editing
            receivers.addAll(member.getProperties().getEditingPlayers());
            // Occupants
            if (member.getEntity().hasPlayerPassenger()) {
                receivers.add(member.getEntity().getPlayerPassenger());
            }
        }
        for (Player player : receivers) {
            this.message(player, arguments);
        }
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
}

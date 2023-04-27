package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.permissions.IPermissionEnum;

import cloud.commandframework.permission.PredicatePermission;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

/**
 * All permissions used by TrainCarts
 */
public enum Permission implements IPermissionEnum {
    COMMAND_GLOBALPROPERTIES("train.command.globalproperties", PermissionDefault.OP, "The player can edit the properties of all carts"),
    COMMAND_PROPERTIES("train.command.properties", PermissionDefault.TRUE, "The player can edit the properties of owned carts"),
    COMMAND_TARGET_NEAR("train.command.targetnear", PermissionDefault.TRUE, "The player can use the --near selector to target owned carts"),

    // Powerful permission to apply all properties from defaults
    PROPERTY_APPLYDEFAULTS("train.property.applydefaults", PermissionDefault.OP, "The player can apply defaults from DefaultTrainProperties.yml to trains"),

    // These permissions are active for both property-based commands, and building new property signs
    PROPERTY_NAME("train.property.name", PermissionDefault.TRUE, "The player can change the name of the train"),
    PROPERTY_OWNERS("train.property.owners", PermissionDefault.TRUE, "The player can change who has ownership of carts and can edit them"),
    PROPERTY_MAXSPEED("train.property.maxspeed", PermissionDefault.TRUE, "The player can alter the maximum speed of trains"),
    PROPERTY_SLOWDOWN("train.property.slowdown", PermissionDefault.TRUE, "The player can change whether trains slow down due to gravity or friction"),
    PROPERTY_COLLISION("train.property.collision", PermissionDefault.TRUE, "The player can change what happens when trains collide with entities or blocks"),
    PROPERTY_PLAYERENTER("train.property.playerenter", PermissionDefault.TRUE, "The player can change whether players can enter carts or not"),
    PROPERTY_PLAYEREXIT("train.property.playerexit", PermissionDefault.TRUE, "The player can change whether players can exit from carts or not"),
    PROPERTY_KEEPCHUNKSLOADED("train.property.keepchunksloaded", PermissionDefault.OP, "The player can change whether the train keeps nearby chunks and itself loaded"),
    PROPERTY_GRAVITY("train.property.gravity", PermissionDefault.TRUE, "The player can change the gravity multiplier of the train"),
    PROPERTY_FRICTION("train.property.friction", PermissionDefault.TRUE, "The player can change the friction multiplier of the train"),
    PROPERTY_BANKING("train.property.banking", PermissionDefault.TRUE, "The player can change the way trains bank in curves"),
    PROPERTY_DESTINATION("train.property.destination", PermissionDefault.TRUE, "The player can change what destination a train is path-finding to"),
    PROPERTY_ROUTE("train.property.route", PermissionDefault.TRUE, "The player can change what sequence of destinations a train follows"),
    PROPERTY_TAGS("train.property.tags", PermissionDefault.TRUE, "The player can add or remove tags to trains or carts, used by switchers and detectors"),
    PROPERTY_ONLYOWNERSCANENTER("train.property.onlyownerscanenter", PermissionDefault.TRUE, "The player can change whether only owners or all players can enter a train"),
    PROPERTY_PICKUPITEMS("train.property.pickupitems", PermissionDefault.TRUE, "The player can change whether storage carts pick up items off the ground"),
    PROPERTY_SOUNDENABLED("train.property.soundenabled", PermissionDefault.TRUE, "The player can change whether a train makes sound while moving, or is quiet"),
    PROPERTY_INVINCIBLE("train.property.invincible", PermissionDefault.TRUE, "The player can change whether a train is invincible to damage or can be destroyed"),
    PROPERTY_ALLOWPLAYERTAKE("train.property.allowplayertake", PermissionDefault.TRUE, "The player can change whether the players take carts with them when they leave the server"),
    PROPERTY_SPAWNITEMDROPS("train.property.spawnitemdrops", PermissionDefault.TRUE, "The player can change whether carts drop items when destroyed"),
    PROPERTY_REQUIREPOWEREDCART("train.property.requirepoweredcart", PermissionDefault.TRUE, "The player can change whether a train must have a furnace minecart to exist, or unlink"),
    PROPERTY_DISPLAYNAME("train.property.displayname", PermissionDefault.TRUE, "The player can change the display name of a train, which is used with the trigger sign"),
    PROPERTY_ALLOWMOBMANUALMOVEMENT("train.property.allowmobmanualmovement", PermissionDefault.TRUE, "The player can allow a train to be set in motion by mob passengers"),
    PROPERTY_ALLOWPLAYERMANUALMOVEMENT("train.property.allowplayermanualmovement", PermissionDefault.TRUE, "The player can allow player passengers to control the train using steering controls"),
    PROPERTY_BREAKBLOCKS_NORMAL("train.property.breakblocks.normal", PermissionDefault.TRUE, "The player can configure a train to automatically break blocks from a whitelist for farming setups."),
    PROPERTY_BREAKBLOCKS_ADMIN("train.property.breakblocks.admin", PermissionDefault.OP, "The player can configure a train to break any type of block at all"),
    PROPERTY_REALTIME("train.property.realtime", PermissionDefault.OP, "The player can configure that a train updates in realtime, adjusting for server tick lag and jitter"),
    PROPERTY_TICKETS("train.property.tickets", PermissionDefault.TRUE, "The player can assign tickets required for entering the train"),
    PROPERTY_VIEW_DISTANCE("train.property.viewdistance", PermissionDefault.OP, "The player can change the player view distance set for passengers of a train"),
    PROPERTY_TRACKING_RANGE("train.property.trackingrange", PermissionDefault.OP, "The player can change the range from which the train is visible to players"),
    PROPERTY_ENTER_MESSAGE("train.property.entermessage", PermissionDefault.TRUE, "The player can change the message displayed to players when they enter the train"),
    PROPERTY_EXIT_OFFSET("train.property.exitoffset", PermissionDefault.OP, "The player can change where players are ejected relative to the train"),

    COMMAND_DESTROY("train.command.destroy", PermissionDefault.OP, "The player can destroy owned carts through commands"),
    COMMAND_DESTROYALL("train.command.destroyall", PermissionDefault.OP, "The player can destroy all trains on the server"),
    COMMAND_FIXBUGGED("train.command.fixbugged", PermissionDefault.OP, "The player can destroy all bugged minecarts on the server"),
    COMMAND_UPGRADESAVED("train.command.upgradesavedtrains", PermissionDefault.OP, "The player can upgrade all saved trains model positions to the new TC version"),
    COMMAND_REROUTE("train.command.reroute", PermissionDefault.OP, "The player can force path finding to re-calculate"),
    COMMAND_RELOAD("train.command.reload", PermissionDefault.OP, "The player can reload the configuration"),
    COMMAND_DEFAULT("train.command.default", PermissionDefault.OP, "The player can apply default settings to his owned trains"),
    COMMAND_SAVEALL("train.command.saveall", PermissionDefault.OP, "Whether the player can save all TrainCarts stored information to file"),
    COMMAND_MESSAGE("train.command.message", PermissionDefault.OP, "Whether the player can add message shortcuts"),
    COMMAND_PATHINFO("train.command.pathinfo", PermissionDefault.OP, "Whether the player can view the route the train is following to reach the destination"),
    COMMAND_TELEPORT("train.command.teleport", PermissionDefault.OP, "Whether the player can teleport to where trains are"),
    COMMAND_CHANGEBLOCK("train.command.changeblock", PermissionDefault.OP, "Whether the player can alter the type of block displayed in a minecart"),
    COMMAND_CHANGETICK("train.command.changetick", PermissionDefault.OP, "Whether the player can alter the global update tick rate of TrainCarts (debug!)"),
    COMMAND_ISSUE("train.command.issue", PermissionDefault.TRUE, "Whether the player can report problems with TrainCarts"),
    COMMAND_GIVE_EDITOR("train.command.editor", PermissionDefault.OP, "Whether the player can use in-game editors for trains (models) and signs"),
    COMMAND_STORAGE_CHEST_USE("train.command.chest.use", PermissionDefault.OP, "Whether the player can use a chest item to suck up trains or spawn the train stored within"),
    COMMAND_STORAGE_CHEST_CREATE("train.command.chest.create", PermissionDefault.OP, "Whether the player can create and modify a chest item that can store and spawn trains"),
    COMMAND_SAVE_TRAIN("train.command.save", PermissionDefault.OP, "Whether the player can use a command to save a train under a name"),
    COMMAND_SAVE_ROUTE("train.command.route.save", PermissionDefault.OP, "Whether the player can save a train or cart route to name to a global store"),

    COMMAND_SAVEDTRAIN_LIST("train.command.savedtrain.list", PermissionDefault.TRUE, "Whether the player can view saved trains and modules and use the /savedtrain command at all"),
    COMMAND_SAVEDTRAIN_REVERSE("train.command.savedtrain.reverse", PermissionDefault.OP, "Whether the player can reverse a saved train, so that back becomes front when spawning"),
    COMMAND_SAVEDTRAIN_RENAME("train.command.savedtrain.rename", PermissionDefault.OP, "Whether the player can rename a saved train to a new name"),
    COMMAND_SAVEDTRAIN_COPY("train.command.savedtrain.copy", PermissionDefault.OP, "Whether the player can copy an existing saved train configuration and save it as a new name"),
    COMMAND_SAVEDTRAIN_DELETE("train.command.savedtrain.delete", PermissionDefault.OP, "Whether the player can delete a previously saved train"),
    COMMAND_SAVEDTRAIN_CLAIM("train.command.savedtrain.claim", PermissionDefault.OP, "Whether the player can change who has claimed a saved train"),
    COMMAND_SAVEDTRAIN_GLOBAL("train.command.savedtrain.global", PermissionDefault.OP, "Whether the player can modify, delete or claim saved trains claimed by other players"),
    COMMAND_SAVEDTRAIN_IMPORT("train.command.savedtrain.import", PermissionDefault.OP, "Whether the player can import (saved) trains from online using hastebin"),
    COMMAND_SAVEDTRAIN_EXPORT("train.command.savedtrain.export", PermissionDefault.OP, "Whether the player can export (paste) (saved) trains to online using hastebin"),

    COMMAND_ENTER("train.command.enter", PermissionDefault.OP, "Whether the player can use a command to enter the train/cart being edited"),
    COMMAND_EJECT("train.command.eject", PermissionDefault.OP, "Whether the player can use a command to eject players from the train/cart being edited"),
    COMMAND_LAUNCH("train.command.launch", PermissionDefault.OP, "Whether the player can use a command to launch the train being edited"),
    COMMAND_ANIMATE("train.command.animate", PermissionDefault.OP, "Whether the player can use a command to play an animation"),
    COMMAND_LOCATE("train.command.locate", PermissionDefault.OP, "Whether the player can locate trains, which shows a line from player to trains"),
    COMMAND_FLIP("train.command.flip", PermissionDefault.OP, "Whether the player can use a command to flip a cart or train around 180 degrees"),

    COMMAND_MODEL_CONFIG_LIST("train.command.model.config.list", PermissionDefault.TRUE, "Whether the player can view saved attachment models and modules and use the /train model config command at all"),
    COMMAND_MODEL_CONFIG_RENAME("train.command.model.config.rename", PermissionDefault.OP, "Whether the player can rename a saved attachment model to a new name"),
    COMMAND_MODEL_CONFIG_COPY("train.command.model.config.copy", PermissionDefault.OP, "Whether the player can copy an existing saved attachment model and save it as a new name"),
    COMMAND_MODEL_CONFIG_DELETE("train.command.model.config.delete", PermissionDefault.OP, "Whether the player can delete a previously saved attachment model"),
    COMMAND_MODEL_CONFIG_CLAIM("train.command.model.config.claim", PermissionDefault.OP, "Whether the player can change who has claimed a saved attachment model"),
    COMMAND_MODEL_CONFIG_GLOBAL("train.command.model.config.global", PermissionDefault.OP, "Whether the player can modify, delete or claim saved attachment models claimed by other players"),
    COMMAND_MODEL_CONFIG_IMPORT("train.command.model.config.import", PermissionDefault.OP, "Whether the player can import (saved) attachment models from online using hastebin"),
    COMMAND_MODEL_CONFIG_EXPORT("train.command.model.config.export", PermissionDefault.OP, "Whether the player can export (paste) (saved) attachment models to online using hastebin"),
    COMMAND_MODEL_SEARCH("train.command.model.search", PermissionDefault.OP, "Whether the player can use a command to list and show all item models of the server resource pack"),

    SPAWNER_AUTOMATIC("train.spawner.automatic", PermissionDefault.TRUE, "The player can build spawners which automatically create carts"),
    SPAWNER_REGULAR("train.spawner.regular", PermissionDefault.TRUE, "The player can build spawners which create regular minecarts"),
    SPAWNER_POWERED("train.spawner.powered", PermissionDefault.TRUE, "The player can build spawners which create powered minecarts"),
    SPAWNER_STORAGE("train.spawner.storage", PermissionDefault.TRUE, "The player can build spawners which create minecarts with chests"),
    SPAWNER_TNT("train.spawner.tnt", PermissionDefault.TRUE, "The player can build spawners which create minecarts with TNT"),
    SPAWNER_HOPPER("train.spawner.hopper", PermissionDefault.TRUE, "The player can build spawners which create minecarts with hoppers"),
    SPAWNER_COMMAND("train.spawner.command", PermissionDefault.FALSE, "The player can build spawners which create minecarts with command blocks"),
    SPAWNER_SPAWNER("train.spawner.spawner", PermissionDefault.TRUE, "The player can build spawners which create minecarts with spawners"),

    BUILD_STATION("train.build.station", PermissionDefault.OP, "The player can build train stations"),
    BUILD_SPAWNER("train.build.spawner", PermissionDefault.OP, "The player can build train spawners"),
    BUILD_TRIGGER("train.build.trigger", PermissionDefault.OP, "The player can build train triggers"),
    BUILD_DESTINATION("train.build.destination", PermissionDefault.OP, "The player can build destinations"),
    BUILD_SWITCHER("train.build.switcher", PermissionDefault.OP, "The player can build track switchers"),
    BUILD_DESTRUCTOR("train.build.destructor", PermissionDefault.OP, "The player can build train destructors"),
    BUILD_DETECTOR("train.build.detector", PermissionDefault.OP, "The player can build train detectors"),
    BUILD_EJECTOR("train.build.ejector", PermissionDefault.OP, "The player can build train ejectors"),
    BUILD_EJECTOR_ABSOLUTE("train.build.ejector.absolute", PermissionDefault.OP, "The player can build train ejectors that teleport to absolute world coordinates"),
    BUILD_PROPERTY("train.build.property", PermissionDefault.OP, "The player can build train property setters"),
    BUILD_COLLECTOR("train.build.collector", PermissionDefault.OP, "The player can build systems to let trains collect from storage blocks"),
    BUILD_DEPOSITOR("train.build.depositor", PermissionDefault.OP, "The player can build systems to fill storage blocks with items from trains"),
    BUILD_ELEVATOR("train.build.elevator", PermissionDefault.OP, "The player can build systems to teleport trains vertically"),
    BUILD_TELEPORTER("train.build.teleporter", PermissionDefault.OP, "The player can build train teleporters (portals)"),
    BUILD_BLOCKER("train.build.blocker", PermissionDefault.OP, "The player can build train blockers"),
    BUILD_WAIT("train.build.wait", PermissionDefault.OP, "The player can build train wait signs"),
    BUILD_CRAFTER("train.build.crafter", PermissionDefault.OP, "The player can build item crafter signs"),
    BUILD_TICKET("train.build.ticket", PermissionDefault.OP, "The player can build a sign that will charge money or ejects players that can't pay"),
    BUILD_ANNOUNCER("train.build.announcer", PermissionDefault.OP, "The player can build a sign that sends a message to all the players in a train"),
    BUILD_EFFECT("train.build.effect", PermissionDefault.OP, "The player can build a sign that can play an effect"),
    BUILD_SOUND("train.build.sound", PermissionDefault.OP, "The player can build a sign that can play a sound"),
    BUILD_BLOCKCHANGER("train.build.blockchanger", PermissionDefault.OP, "The player can build a sign that alters the block displayed in minecarts"),
    BUILD_JUMPER("train.build.jumper", PermissionDefault.OP, "The player can build a sign that can cause a train to jump in a certain direction"),
    BUILD_LAUNCHER("train.build.launcher", PermissionDefault.OP, "The player can build a sign that can launch trains"),
    BUILD_ENTER("train.build.enter", PermissionDefault.OP, "The player can build a sign that can make nearby players/mobs enter a train"),
    BUILD_SKIPPER("train.build.skipper", PermissionDefault.OP, "The player can build a sign that can tell a train or minecart to skip upcoming signs"),
    BUILD_MUTEX("train.build.mutex", PermissionDefault.OP, "The player can build a sign that defines a mutual exclusion zone, where only one train can be at"),
    BUILD_FLIPPER("train.build.flip", PermissionDefault.OP, "The player can build a sign that flips the orientation of a Minecart 180 degrees"),
    BUILD_ANIMATOR("train.build.animator", PermissionDefault.OP, "The player can build a sign that plays train animations"),

    GENERAL_PLACE_MINECART("train.place.minecart", PermissionDefault.TRUE, "The player can place minecarts"),
    GENERAL_PLACE_TRAINCART("train.place.traincart", PermissionDefault.TRUE, "The player can place TrainCarts minecarts"),
    GENERAL_PROPERTIES_ADMIN("train.properties.admin", PermissionDefault.OP, "Carts placed by this player get the admin properties"),
    BREAK_MINECART_SELF("train.break.self", PermissionDefault.TRUE, "The player can break their own minecarts"),
    BREAK_MINECART_ANY("train.break.any", PermissionDefault.FALSE, "The player can break all carts in the game"),
    TICKET_MANAGE("train.ticket.manage", PermissionDefault.OP, "The player can edit the details of existing tickets or create new tickets"),
    // Special hidden debug sekretz
    DEBUG_COMMAND_DEBUG("train.debug", PermissionDefault.OP, "The player can use special commands useful for debugging the plugin"),
    // Create and change Schematic attachments
    USE_SCHEMATIC_ATTACHMENTS("train.attachments.schematic.use", PermissionDefault.OP, "The player can create and modify SCHEMATIC attachments");

    private final String _root;
    private final PermissionDefault _default;
    private final String _description;
    private final PredicatePermission<CommandSender> _cloud;

    private Permission(final String node, final PermissionDefault permdefault, final String desc) {
        this._root = node;
        this._default = permdefault;
        this._description = desc;
        this._cloud = this::has;
    }

    @Override
    public boolean has(CommandSender sender) {
        // Brigadier nags us about this after the plugin disables, which causes problems
        if (CommonPlugin.hasInstance()) {
            return IPermissionEnum.super.has(sender);
        } else {
            return sender.hasPermission(this.getName());
        }
    }

    @Override
    public String getRootName() {
        return this._root;
    }

    @Override
    public PermissionDefault getDefault() {
        return this._default;
    }

    @Override
    public String getDescription() {
        return this._description;
    }

    /**
     * Cloud representation of this permission
     *
     * @return Cloud Command Permission
     */
    public cloud.commandframework.permission.CommandPermission cloudPermission() {
        return _cloud;
    }
}

package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Optional;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.category.*;

/**
 * All standard TrainCarts built-in train and cart properties
 */
public class StandardProperties {

    public static final ModelProperty MODEL = new ModelProperty();
    public static final DestinationProperty DESTINATION = new DestinationProperty();
    public static final DestinationRouteProperty DESTINATION_ROUTE = new DestinationRouteProperty();
    public static final DestinationRouteProperty.IndexProperty DESTINATION_ROUTE_INDEX = new DestinationRouteProperty.IndexProperty();
    public static final TagSetProperty TAGS = new TagSetProperty();
    public static final ExitOffsetProperty EXIT_OFFSET = new ExitOffsetProperty();
    public static final TicketSetProperty TICKETS = new TicketSetProperty();
    public static final KeepChunksLoadedProperty KEEP_CHUNKS_LOADED = new KeepChunksLoadedProperty();
    public static final BankingOptionsProperty BANKING = new BankingOptionsProperty();
    public static final SlowdownProperty SLOWDOWN = new SlowdownProperty();
    public static final CollisionProperty COLLISION = new CollisionProperty();
    public static final PlayerEnterProperty ALLOW_PLAYER_ENTER = new PlayerEnterProperty();
    public static final PlayerExitProperty ALLOW_PLAYER_EXIT = new PlayerExitProperty();
    public static final GravityProperty GRAVITY = new GravityProperty();
    public static final SpeedLimitProperty SPEEDLIMIT = new SpeedLimitProperty();
    public static final TrainNameProperty TRAIN_NAME = new TrainNameProperty();
    public static final OnlyOwnersCanEnterProperty ONLY_OWNERS_CAN_ENTER = new OnlyOwnersCanEnterProperty();
    public static final PickUpItemsProperty PICK_UP_ITEMS = new PickUpItemsProperty();
    public static final SoundEnabledProperty SOUND_ENABLED = new SoundEnabledProperty();
    public static final InvincibleProperty INVINCIBLE = new InvincibleProperty();
    public static final AllowPlayerTakeProperty ALLOW_PLAYER_TAKE = new AllowPlayerTakeProperty();
    public static final SpawnItemDropsProperty SPAWN_ITEM_DROPS = new SpawnItemDropsProperty();
    public static final DisplayNameProperty DISPLAY_NAME = new DisplayNameProperty();
    public static final AllowManualMobMovementProperty ALLOW_MOB_MANUAL_MOVEMENT = new AllowManualMobMovementProperty();
    public static final AllowManualPlayerMovementProperty ALLOW_PLAYER_MANUAL_MOVEMENT = new AllowManualPlayerMovementProperty();
    public static final OwnerSetProperty OWNERS = new OwnerSetProperty();
    public static final OwnerPermissionSet OWNER_PERMISSIONS = new OwnerPermissionSet();
    public static final BreakBlocksProperty BLOCK_BREAK_TYPES = new BreakBlocksProperty();

    public static final ICartProperty<String> ENTER_MESSAGE = new ICartProperty<String>() {

        @PropertyParser("entermessage|entermsg")
        public String parseMessage(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "enterMessage", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "enterMessage", value);
        }
    };

    public static final ICartProperty<String> DRIVE_SOUND = new ICartProperty<String>() {

        @PropertyParser("drivesound|driveeffect")
        public String parseSound(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "driveSound", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "driveSound", value);
        }
    };

    public static final ICartProperty<String> DESTINATION_LAST_PATH_NODE = new ICartProperty<String>() {
        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "lastPathNode", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "lastPathNode", value);
        }
    };

    public static final ITrainProperty<String> KILL_MESSAGE = new ITrainProperty<String>() {

        @PropertyParser("killmessage")
        public String parseMessage(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "killMessage", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "killMessage", value);
        }
    };

    public static final ITrainProperty<Boolean> SUFFOCATION = new ITrainProperty<Boolean>() {

        @PropertyParser("suffocation")
        public boolean parseSuffocate(PropertyParseContext<Boolean> parser) {
            return parser.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "suffocation", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "suffocation", value);
        }
    };

    public static final ITrainProperty<Boolean> REQUIRE_POWERED_MINECART = new ITrainProperty<Boolean>() {

        @PropertyParser("requirepoweredminecart|requirepowered")
        public boolean parseRequirePowered(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public boolean hasPermission(CommandSender sender, String name) {
            return Permission.PROPERTY_REQUIREPOWEREDCART.has(sender);
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "requirePoweredMinecart", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "requirePoweredMinecart", value);
        }
    };

    public static final ITrainProperty<Double> COLLISION_DAMAGE = new ITrainProperty<Double>() {
        private final Double DEFAULT = 1.0;

        @PropertyParser("collisiondamage")
        public double parseDamage(PropertyParseContext<Double> context) {
            return context.inputDouble();
        }

        @Override
        public Double getDefault() {
            return DEFAULT;
        }

        @Override
        public Optional<Double> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "collisionDamage", double.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
            Util.setConfigOptional(config, "collisionDamage", value);
        }
    };

    /**
     * Configures train behavior for waiting on obstacles on the track ahead
     */
    public static final WaitOptionsProperty WAIT = new WaitOptionsProperty();

    /**
     * The persistent data stored for the sign skip feature of carts and trains.
     * This property is used internally by the SignSkipOptions class.
     */
    public static final SignSkipOptionsProperty SIGN_SKIP = new SignSkipOptionsProperty();

    /**
     * Applies default train properties from configuration by name to the train.
     * Only used to make this available as a property.
     */
    public static final DefaultConfigSyntheticProperty DEFAULT_CONFIG = new DefaultConfigSyntheticProperty();
}

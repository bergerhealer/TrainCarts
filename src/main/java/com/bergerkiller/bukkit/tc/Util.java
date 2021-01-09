package com.bergerkiller.bukkit.tc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Rails;
import org.bukkit.material.Stairs;
import org.bukkit.material.Step;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.MaterialTypeProperty;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityPropertyUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil.ItemSynchronizer;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.AveragedItemParser;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bergerkiller.bukkit.tc.utils.TrackMovingPoint;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bergerkiller.generated.net.minecraft.server.AxisAlignedBBHandle;
import com.bergerkiller.generated.net.minecraft.server.ChunkHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.reflection.net.minecraft.server.NMSItem;

public class Util {
    public static final MaterialTypeProperty ISVERTRAIL = new MaterialTypeProperty(Material.LADDER);
    public static final MaterialTypeProperty ISTCRAIL = new MaterialTypeProperty(ISVERTRAIL, MaterialUtil.ISRAILS, MaterialUtil.ISPRESSUREPLATE);
    private static final String SEPARATOR_REGEX = "[|/\\\\]";
    private static List<Block> blockbuff = new ArrayList<>();

    public static void setItemMaxSize(Material material, int maxstacksize) {
        NMSItem.maxStackSize.set(Conversion.toItemHandle.convert(material), maxstacksize);
    }

    // Number format used by stringifyNumberBoxValue
    private static final NumberFormat numberBox_NumberFormat = createNumberFormat(1, 4);

    // Number formats used by stringifyAnimationNodeTime
    private static final NumberFormat animationodeTime_NumberFormat1000 = createNumberFormat(0, 0);
    private static final NumberFormat animationodeTime_NumberFormat100 = createNumberFormat(1, 1);
    private static final NumberFormat animationodeTime_NumberFormat10 = createNumberFormat(1, 2);
    private static final NumberFormat animationodeTime_NumberFormat1 = createNumberFormat(1, 3);

    // Internal use
    private static NumberFormat createNumberFormat(int min_fractionDigits, int max_fractionDigits) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.ENGLISH);
        fmt.setMinimumFractionDigits(min_fractionDigits);
        fmt.setMaximumFractionDigits(max_fractionDigits);
        fmt.setGroupingUsed(false);
        return fmt;
    }

    /**
     * Returns the minimal index into a String, exempting the -1 constant. Examples:
     * <ul>
     * <li>minStringIndex(-1, 2) == 2</li>
     * <li>minStringIndex(3, -1) == 3</li>
     * <li>minStringIndex(5, 12) == 5</li>
     * <li>minStringIndex(-1, -1) == -1</li>
     * </ul>
     * 
     * @param a index1
     * @param b index2
     * @return minimal index.
     */
    public static int minStringIndex(int a, int b) {
        if (a == -1 || b == -1) {
            return (a > b) ? a : b;
        } else {
            return (a < b) ? a : b;
        }
    }

    /**
     * Splits a text into separate parts delimited by the separator characters
     *
     * @param text to split
     * @return split parts
     */
    public static String[] splitBySeparator(String text) {
        return text.split(SEPARATOR_REGEX);
    }

    /**
     * Gets the BlockFace.UP or BlockFace.DOWN based on a boolean input
     *
     * @param up - True to get UP, False to get DOWN
     * @return UP or DOWN
     */
    public static BlockFace getVerticalFace(boolean up) {
        return up ? BlockFace.UP : BlockFace.DOWN;
    }

    /**
     * Snaps a block face to one of the 8 possible radial block faces (NESW/NE/etc.)
     *
     * @param face to snap to a nearby valid face
     * @return Snapped block face
     */
    public static BlockFace snapFace(BlockFace face) {
        switch (face) {
            case NORTH_NORTH_EAST:
                return BlockFace.NORTH_EAST;
            case EAST_NORTH_EAST:
                return BlockFace.EAST;
            case EAST_SOUTH_EAST:
                return BlockFace.SOUTH_EAST;
            case SOUTH_SOUTH_EAST:
                return BlockFace.SOUTH;
            case SOUTH_SOUTH_WEST:
                return BlockFace.SOUTH_WEST;
            case WEST_SOUTH_WEST:
                return BlockFace.WEST;
            case WEST_NORTH_WEST:
                return BlockFace.NORTH_WEST;
            case NORTH_NORTH_WEST:
                return BlockFace.NORTH;
            default:
                return face;
        }
    }

    /**
     * <b>Deprecated: use {@link RailSignCache#getSigns(RailType, Block)} instead</b>
     */
    @Deprecated
    public static List<Block> getSignsFromRails(Block railsblock) {
        return getSignsFromRails(blockbuff, railsblock);
    }

    /**
     * <b>Deprecated: use {@link RailSignCache#getSigns(RailType, Block)} instead</b>
     */
    @Deprecated
    public static List<Block> getSignsFromRails(List<Block> rval, Block railsblock) {
        rval.clear();
        addSignsFromRails(rval, railsblock);
        return rval;
    }

    /**
     * <b>Deprecated: use {@link RailSignCache#getSigns(RailType, Block)} instead</b>
     */
    @Deprecated
    public static void addSignsFromRails(List<Block> rval, Block railsBlock) {
        RailType railType = RailType.getType(railsBlock);
        if (railType == RailType.NONE) {
            return;
        }
        for (RailSignCache.TrackedSign trackedSign : RailSignCache.getSigns(railType, railsBlock)) {
            rval.add(trackedSign.signBlock);
        }
    }

    public static boolean hasAttachedSigns(final Block middle) {
        return addAttachedSigns(middle, null);
    }

    public static boolean addAttachedSigns(final Block middle, final Collection<Block> rval) {
        boolean found = false;
        for (BlockFace face : FaceUtil.AXIS) {
            Block b = middle.getRelative(face);
            if (MaterialUtil.ISSIGN.get(b) && BlockUtil.getAttachedFace(b) == face.getOppositeFace()) {
                found = true;
                if (rval != null) {
                    rval.add(b);
                }
            }
        }
        return found;
    }

    /**
     * <b>Deprecated: use RailSignCache.getRailsFromSign(signblock) instead</b>
     * 
     * @param signblock
     * @return rail block
     */
    @Deprecated
    public static Block getRailsFromSign(Block signblock) {
        return RailSignCache.getRailsFromSign(signblock).block();
    }

    public static Block findRailsVertical(Block from, BlockFace mode) {
        int sy = from.getY();
        int x = from.getX();
        int z = from.getZ();
        World world = from.getWorld();
        if (mode == BlockFace.DOWN) {
            for (int y = sy - 1; y > 0; --y) {
                Block block = world.getBlockAt(x, y, z);
                if (RailType.getType(block) != RailType.NONE) {
                    return block;
                }
            }
        } else if (mode == BlockFace.UP) {
            int height = world.getMaxHeight();
            for (int y = sy + 1; y < height; y++) {
                Block block = world.getBlockAt(x, y, z);
                if (RailType.getType(block) != RailType.NONE) {
                    return block;
                }
            }
        }
        return null;
    }

    public static ItemParser[] getParsers(String... items) {
        return getParsers(StringUtil.join(";", items));
    }

    public static ItemParser[] getParsers(final String items) {
        List<ItemParser> parsers = new ArrayList<>();
        int multiIndex, multiplier = -1;
        for (String type : items.split(";")) {
            type = type.trim();
            if (type.isEmpty()) {
                continue;
            }
            // Check to see whether this is a multiplier
            multiIndex = type.indexOf('#');
            if (multiIndex != -1) {
                multiplier = ParseUtil.parseInt(type.substring(0, multiIndex), -1);
                type = type.substring(multiIndex + 1);
            }
            // Parse the amount and a possible multiplier from it
            int amount = -1;
            int idx = StringUtil.firstIndexOf(type, "x", "X", " ", "*");
            if (idx > 0) {
                amount = ParseUtil.parseInt(type.substring(0, idx), -1);
                if (amount != -1) {
                    type = type.substring(idx + 1);
                }
            }
            // Obtain the item parsers for this item and amount, apply a possible multiplier
            ItemParser[] keyparsers = TrainCarts.plugin.getParsers(type, amount);
            if (multiIndex != -1) {
                // Convert to proper multiplied versions
                for (int i = 0; i < keyparsers.length; i++) {
                    keyparsers[i] = new AveragedItemParser(keyparsers[i], multiplier);
                }
            }
            // Add the parsers
            parsers.addAll(Arrays.asList(keyparsers));
        }
        if (parsers.isEmpty()) {
            parsers.add(new ItemParser(null));
        }
        return parsers.toArray(new ItemParser[0]);
    }

    public static Block getRailsBlock(Block from) {
        if (ISTCRAIL.get(from)) {
            return from;
        } else {
            from = from.getRelative(BlockFace.DOWN);
            return ISTCRAIL.get(from) ? from : null;
        }
    }

    /**
     * Parses a long time value to a readable time String
     *
     * @param time to parse
     * @return time in the hh:mm:ss format
     */
    public static String getTimeString(long time) {
        if (time == 0) {
            return "00:00:00";
        }
        time = (long) Math.ceil(0.001 * time); // msec -> sec
        int seconds = (int) (time % 60);
        int minutes = (int) ((time % 3600) / 60);
        int hours = (int) (time / 3600);
        StringBuilder rval = new StringBuilder(8);
        // Hours
        if (hours < 10) {
            rval.append('0');
        }
        rval.append(hours).append(':');
        // Minutes
        if (minutes < 10) {
            rval.append('0');
        }
        rval.append(minutes).append(':');
        // Seconds
        if (seconds < 10) {
            rval.append('0');
        }
        rval.append(seconds);
        return rval.toString();
    }

    private static boolean isRailsAt(Block block, BlockFace direction) {
        return getRailsBlock(block.getRelative(direction)) != null;
    }

    /**
     * This will return: South or west if it's a straight piece Self if it is a cross-intersection
     */
    public static BlockFace getPlateDirection(Block plate) {
        boolean s = isRailsAt(plate, BlockFace.NORTH) || isRailsAt(plate, BlockFace.SOUTH);
        boolean w = isRailsAt(plate, BlockFace.EAST) || isRailsAt(plate, BlockFace.WEST);
        if (s && w) {
            return BlockFace.SELF;
        } else if (w) {
            return BlockFace.EAST;
        } else if (s) {
            return BlockFace.SOUTH;
        } else {
            return BlockFace.SELF;
        }
    }

    /**
     * Checks if a given rail is sloped
     *
     * @param railsData of the rails
     * @return True if sloped, False if not
     */
    public static boolean isSloped(int railsData) {
        railsData &= 0x7;
        return railsData >= 0x2 && railsData <= 0x5;
    }

    /**
     * Checks if a given rails block has a vertical rail above facing the direction specified
     *
     * @param rails     to check
     * @param direction of the vertical rail
     * @return True if a vertical rail is above, False if not
     */
    public static boolean isVerticalAbove(Block rails, BlockFace direction) {
        Block above = rails.getRelative(BlockFace.UP);
        return Util.ISVERTRAIL.get(above) && getVerticalRailDirection(above) == direction;
    }

    /**
     * Checks if a given rails block has a vertical rail below facing the direction specified
     *
     * @param rails     to check
     * @param direction of the vertical rail
     * @return True if a vertical rail is below, False if not
     */
    public static boolean isVerticalBelow(Block rails, BlockFace direction) {
        Block below = rails.getRelative(BlockFace.DOWN);
        return Util.ISVERTRAIL.get(below) && getVerticalRailDirection(below) == direction;
    }

    /**
     * Gets the direction a vertical rail pushes the minecart (the wall side)
     *
     * @param railsBlock of the vertical rail
     * @return the direction the minecart is pushed
     */
    public static BlockFace getVerticalRailDirection(Block railsBlock) {
        return getVerticalRailDirection(MaterialUtil.getRawData(railsBlock));
    }

    /**
     * Gets the direction a vertical rail pushes the minecart (the wall side)
     *
     * @param raildata of the vertical rail
     * @return the direction the minecart is pushed
     */
    public static BlockFace getVerticalRailDirection(int raildata) {
        switch (raildata) {
            case 0x2:
                return BlockFace.SOUTH;
            case 0x3:
                return BlockFace.NORTH;
            case 0x4:
                return BlockFace.EAST;
            default:
            case 0x5:
                return BlockFace.WEST;
        }
    }

    public static int getOperatorIndex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isOperator(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isOperator(char character) {
        return LogicUtil.containsChar(character, '!', '=', '<', '>');
    }

    /**
     * Gets if a given Entity can be a passenger of a Minecart
     *
     * @param entity to check
     * @return True if it can be a passenger, False if not
     */
    public static boolean canBePassenger(Entity entity) {
        return entity instanceof LivingEntity;
    }

    public static boolean matchText(Collection<String> textValues, String expression) {
        if (textValues.isEmpty() || expression.isEmpty()) {
            return false;
        } else if (expression.startsWith("!")) {
            return !matchText(textValues, expression.substring(1));
        } else {
            String[] elements = expression.split("\\*");
            boolean first = expression.startsWith("*");
            boolean last = expression.endsWith("*");
            for (String text : textValues) {
                if (matchText(text, elements, first, last)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean matchText(String text, String expression) {
        if (expression.isEmpty()) {
            return false;
        } else if (expression.startsWith("!")) {
            return !matchText(text, expression.substring(1));
        } else {
            return matchText(text, expression.split("\\*"), expression.startsWith("*"), expression.endsWith("*"));
        }
    }

    public static boolean matchText(String text, String[] elements, boolean firstAny, boolean lastAny) {
        if (elements == null || elements.length == 0) {
            return true;
        }
        int index = 0;
        boolean has = true;
        boolean first = true;
        for (String element : elements) {
            if (element.length() == 0) continue;
            index = text.indexOf(element, index);
            if (index == -1 || (first && !firstAny && index != 0)) {
                has = false;
                break;
            } else {
                index += element.length();
            }
            first = false;
        }
        if (has) {
            if (lastAny || index == text.length()) {
                return true;
            }
        }
        return false;
    }

    public static boolean evaluate(double value, String text) {
        if (text == null || text.isEmpty()) {
            return false; // no valid input
        }
        int idx = getOperatorIndex(text);
        if (idx == -1) {
            return value > 0; // no operators, just perform a 'has'
        } else {
            text = text.substring(idx);
        }
        if (text.startsWith(">=") || text.startsWith("=>")) {
            return value >= ParseUtil.parseDouble(text.substring(2), 0.0);
        } else if (text.startsWith("<=") || text.startsWith("=<")) {
            return value <= ParseUtil.parseDouble(text.substring(2), 0.0);
        } else if (text.startsWith("==")) {
            return value == ParseUtil.parseDouble(text.substring(2), 0.0);
        } else if (text.startsWith("!=") || text.startsWith("<>") || text.startsWith("><")) {
            return value != ParseUtil.parseDouble(text.substring(2), 0.0);
        } else if (text.startsWith(">")) {
            return value > ParseUtil.parseDouble(text.substring(1), 0.0);
        } else if (text.startsWith("<")) {
            return value < ParseUtil.parseDouble(text.substring(1), 0.0);
        } else if (text.startsWith("=")) {
            return value == ParseUtil.parseDouble(text.substring(1), 0.0);
        } else {
            return false;
        }
    }

    /**
     * Gets whether a particular entity is in a state of destroying minecarts instantly. This is when they are in creative
     * mode, and only when not sneaking (players).
     * 
     * @param entity to check
     * @return True if the entity can instantly destroy a minecart, False if not
     */
    public static boolean canInstantlyBreakMinecart(Entity entity) {
        if (!TCConfig.instantCreativeDestroy || !canInstantlyBuild(entity)) {
            return false;
        }
        if (entity instanceof Player && ((Player) entity).isSneaking()) {
            return false;
        }
        return true;
    }

    public static boolean canInstantlyBuild(Entity entity) {
        return entity instanceof HumanEntity && EntityPropertyUtil.getAbilities((HumanEntity) entity).canInstantlyBuild();
    }

    /**
     * Checks whether a Block supports placement/attachment of solid blocks on a particular face. Note that signs do not use
     * this logic - they allow pretty much any sort of attachment.
     *
     * @param block to check
     * @param face  to check
     * @return True if supported, False if not
     */
    public static boolean isSupportedFace(Block block, BlockFace face) {
        BlockData block_data = WorldUtil.getBlockData(block);
        if (block_data.isOccluding(block)) {
            return true;
        }

        // Special block types that only support one face at a time
        MaterialData data = block_data.getMaterialData();

        // Steps only support TOP or BOTTOM
        if (data instanceof Step) {
            return face == FaceUtil.getVertical(((Step) data).isInverted());
        }

        // Stairs only support the non-exit side + the up/down
        if (data instanceof Stairs) {
            if (FaceUtil.isVertical(face)) {
                return face == FaceUtil.getVertical(((Stairs) data).isInverted());
            } else {
                // For some strange reason...stairs don't support attachments to the back
                // return face == ((Stairs) data).getFacing().getOppositeFace();
                return false;
            }
        }

        // Unsupported/unknown Block
        return false;
    }

    public static boolean isSignSupported(Block block) {
        Block attached = BlockUtil.getAttachedBlock(block);
        return WorldUtil.getBlockData(attached).isBuildable();
    }

    public static boolean isSupported(Block block) {
        if (MaterialUtil.ISSIGN.get(block)) {
            return isSignSupported(block);
        }

        BlockFace attachedFace = BlockUtil.getAttachedFace(block);
        Block attached = block.getRelative(attachedFace);

        // For all other cases, check whether the side is properly supported
        return isSupportedFace(attached, attachedFace.getOppositeFace());
    }

    /**
     * Gets the Rails MaterialData for a Block with readonly access.
     * The Rails material data should not be modified.
     * 
     * @param block
     * @return readonly rails material data
     */
    public static Rails getRailsRO(Block block) {
        MaterialData data = WorldUtil.getBlockData(block).getMaterialData();
        return (data instanceof Rails) ? (Rails) data : null;
    }

    public static boolean isValidEntity(String entityName) {
        try {
            return org.bukkit.entity.EntityType.valueOf(entityName) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public static Vector parseVector(String text, Vector def) {
        String[] offsettext = splitBySeparator(text);
        Vector offset = new Vector();
        if (offsettext.length == 3) {
            offset.setX(ParseUtil.parseDouble(offsettext[0], 0.0));
            offset.setY(ParseUtil.parseDouble(offsettext[1], 0.0));
            offset.setZ(ParseUtil.parseDouble(offsettext[2], 0.0));
        } else if (offsettext.length == 2) {
            offset.setX(ParseUtil.parseDouble(offsettext[0], 0.0));
            offset.setZ(ParseUtil.parseDouble(offsettext[1], 0.0));
        } else if (offsettext.length == 1) {
            offset.setY(ParseUtil.parseDouble(offsettext[0], 0.0));
        } else {
            return def;
        }
        return offset;
    }

    /**
     * Parses a number with optional acceleration unit to an acceleration value.
     * Assumes 1 block is 1 meter.
     * Supports the following formats:
     * <ul>
     * <li>12       =  12 blocks/tick^2</li>
     * <li>12.5     =  12.5 blocks/tick^2</li>
     * <li>1g       =  9.81 blocks/second^2 (0.024525 blocks/tick^2)</li>
     * <li>20/tt    =  20 blocks/tick^2 (0.024525 blocks/tick^2)</li>
     * <li>20/ss    =  20 blocks/second^2 (0.05 blocks/tick^2)</li>
     * <li>20/s2    =  20 blocks/second^2 (0.05 blocks/tick^2)</li>
     * <li>20/mm    =  20 blocks/minute^2</li>
     * <li>20m/s/s  =  20 blocks/second^2</li>
     * <li>20km/h/s =  20000 blocks/hour per second</li>
     * <li>1mi/h/s  =  0.44704 blocks/second^2 (0.0011176 blocks/tick^2)</li>
     * <li>1mph/s   =  1mi/h/s</li>
     * <li>3.28ft/s/s = 1 blocks/second^2</li>
     * </ul>
     * 
     * @param accelerationString The text to parse
     * @param defaultValue The default value to return if parsing fails
     * @return parsed acceleration in blocks/tick^2
     */
    public static double parseAcceleration(String accelerationString, double defaultValue) {
        // Avoid out of range
        if (accelerationString.isEmpty()) {
            return defaultValue;
        }

        // To lowercase to prevent weird problems with units
        accelerationString = accelerationString.toLowerCase(Locale.ENGLISH);

        // Some common accepted aliases
        accelerationString = accelerationString.replace("kmh", "kmph");
        accelerationString = accelerationString.replace("kmph", "km/h");
        accelerationString = accelerationString.replace("miph", "mph");
        accelerationString = accelerationString.replace("mph", "mi/h");

        // Parsing of the /tt and so on formats of acceleration
        int slashIndex = accelerationString.indexOf('/');
        if (slashIndex != -1) {
            double value;

            // Process the number value contents before the slash
            {
                // Take all characters before the slash, eliminate non-digit ones
                // When encountering a 'k', assume 1000 blocks unit
                double factor = 1.0;
                StringBuilder valueStr = new StringBuilder(slashIndex+1);
                for (int i = 0; i < slashIndex; i++) {
                    char c = accelerationString.charAt(i);
                    if (Character.isDigit(c) || c == '.' || c == ',' || c == '-') {
                        valueStr.append(c);
                    } else if (c == 'k') {
                        factor = 1000.0; // kilo(meter)
                    } else if (c == 'f' && accelerationString.charAt(i+1) == 't') {
                        factor = 1.0 / 3.28; // feet
                        i++; // skip 't'
                    } else if (c == 'm' && accelerationString.charAt(i+1) == 'i') {
                        factor = 1609.344; // miles
                        i++; // skip 'i'
                    }
                }
                value = ParseUtil.parseDouble(valueStr.toString(), Double.NaN);
                if (Double.isNaN(value)) {
                    return defaultValue;
                }

                // Factor following the number (e.g: km)
                value *= factor;
            }

            int num_units = 0;
            double factor = 1.0; // tick
            for (int i = slashIndex+1; i < accelerationString.length() && num_units < 2; i++) {
                char c = accelerationString.charAt(i);
                if (c == 's') {
                    factor *= 20.0; // second is 20 ticks
                    num_units++;
                } else if (c == 'm') {
                    factor *= 1200.0; // minute is 1200 ticks
                    num_units++;
                } else if (c == 'h') {
                    factor *= 72000.0; // hour is 72000 ticks
                    num_units++;
                }
            }

            // If only one time unit was specified, assume we have to square it
            // For example, 20m/s -> 20m/ss
            // 20m/s2 -> 20m/ss
            if (num_units == 1) {
                factor *= factor;
            }

            return value / factor;
        }

        // Check whether unit is in g's
        char lastChar = accelerationString.charAt(accelerationString.length()-1);
        if (lastChar == 'g') {
            // Unit is in g's
            String g_value_str = accelerationString.substring(0, accelerationString.length()-1);
            double value = ParseUtil.parseDouble(g_value_str, Double.NaN);
            if (Double.isNaN(value)) {
                return defaultValue;
            }
            return 0.024525 * value;
        }

        // Assume blocks/tick unit
        return ParseUtil.parseDouble(accelerationString, defaultValue);
    }

    /**
     * Parses a number with optional velocity unit to a velocity value.
     * Assumes 1 block is 1 meter.
     * Supports the following formats:
     * <ul>
     * <li>12     =  12 blocks/tick</li>
     * <li>12.5   =  12.5 blocks/tick</li>
     * <li>20m/s  =  20 meters/second (1 blocks/tick)</li>
     * <li>20km/h =  20 kilometers/hour (0.27778 blocks/tick)</li>
     * <li>20mi/h =  20 miles/hour (0.44704 blocks/tick)</li>
     * <li>3.28ft/s = same as 1 meters/second (0.05 blocks/tick)</li>
     * <li>20kmh  =  same as 20km/h</li>
     * <li>20kmph =  same as 20km/h</li>
     * <li>20mph  =  same as 20mi/h</li>
     * </ul>
     * 
     * @param velocityString The text to parse
     * @param defaultValue The default value to return if parsing fails
     * @return parsed velocity in blocks/tick
     * @see FormattedSpeed#parse(String, FormattedSpeed)
     */
    public static double parseVelocity(String velocityString, double defaultValue) {
        FormattedSpeed speed = FormattedSpeed.parse(velocityString, null);
        return (speed != null) ? speed.getValue() : defaultValue;
    }

    /**
     * Obtains the maximum straight length achieved from a particular block. This length is limited to 20 blocks.
     *
     * @param railsBlock to calculate from
     * @param direction  to look into, use SELF to check all possible directions
     * @return straight length
     */
    public static double calculateStraightLength(Block railsBlock, BlockFace direction) {
        TrackWalkingPoint p = new TrackWalkingPoint(railsBlock, direction);
        Vector start_dir = null;
        while (p.movedTotal < 20.0 && p.move(0.1)) {
            if (start_dir == null) {
                start_dir = p.state.motionVector();
            } else {
                // Verify that the start and current motion vector are still somewhat the same
                // Somewhat is subjective, so use the dot product and hope for the best
                if (p.state.position().motDot(start_dir) < 0.75) {
                    break;
                }
            }
        }
        return p.movedTotal;

//        // Read track information and parameters
//        RailType type = RailType.getType(railsBlock);
//        boolean diagonal = FaceUtil.isSubCardinal(type.getDirection(railsBlock));
//        final BlockFace[] toCheck;
//        if (direction == BlockFace.SELF) {
//            toCheck = type.getPossibleDirections(railsBlock);
//        } else {
//            toCheck = new BlockFace[] { direction };
//        }
//        double length = 0.0;
//        TrackIterator iter = new TrackIterator(null, null, 20, false);
//        // Check all directions
//        for (BlockFace face : toCheck) {
//            double trackLength = 0.0;
//            iter.reset(railsBlock, face);
//            // Skip the start block, abort if no start block was found
//            if (iter.hasNext()) {
//                iter.next();
//            } else {
//                continue;
//            }
//            // Two modes: diagonal and straight
//            if (diagonal) {
//                // Diagonal mode
//                BlockFace lastFace = null;
//                int lastAngle = Integer.MAX_VALUE;
//                while (iter.hasNext()) {
//                    iter.next();
//                    // Check that the direction alternates
//                    if (lastFace == null) {
//                        // Start block: store it's information
//                        lastFace = iter.currentDirection();
//                    } else {
//                        BlockFace newFace = iter.currentDirection();
//                        int newAngle = MathUtil.wrapAngle(FaceUtil.faceToYaw(newFace) - FaceUtil.faceToYaw(lastFace));
//                        if (Math.abs(newAngle) != 90) {
//                            // Not a 90-degree angle!
//                            break;
//                        }
//                        if (lastAngle != Integer.MAX_VALUE && newAngle != -lastAngle) {
//                            // Not the exact opposite from last time
//                            break;
//                        }
//                        lastFace = newFace;
//                        lastAngle = newAngle;
//                    }
//                    trackLength += MathUtil.HALFROOTOFTWO;
//                }
//            } else {
//                // Straight mode
//                while (iter.hasNext()) {
//                    iter.next();
//                    // Check that the direction stays the same
//                    if (iter.currentDirection() != face) {
//                        break;
//                    }
//                    trackLength++;
//                }
//            }
//            // Update the length
//            if (trackLength > length) {
//                length = trackLength;
//            }
//        }
//        return length;
    }

    /**
     * Attempts to parse the text as time ticks, converting values such as '12s' and '500ms' into ticks. If no time
     * statement is found, -1 is returned.
     * 
     * @param text to parse as time
     * @return time ticks, or -1 if not parsed
     */
    public static int parseTimeTicks(String text) {
        text = text.toLowerCase(Locale.ENGLISH);
        double ticks = -1.0;
        if (text.endsWith("ms")) {
            ticks = 0.02 * ParseUtil.parseDouble(text.substring(0, text.length() - 2), -1);
        } else if (text.endsWith("m")) {
            ticks = 1200.0 * ParseUtil.parseDouble(text.substring(0, text.length() - 1), -1);
        } else if (text.endsWith("s")) {
            ticks = 20.0 * ParseUtil.parseDouble(text.substring(0, text.length() - 1), -1);
        } else if (text.endsWith("t")) {
            ticks = ParseUtil.parseInt(text.substring(0, text.length() - 1), -1);
        }
        return (ticks < 0.0) ? -1 : (int) ticks;
    }

    /**
     * Will return for hexcode for every char
     * 
     * @param unicode to get hexcode for
     * @return hexcode, in unicode format
     */
    public static String getUnicode(char unicode) {
        return "\\u" + Integer.toHexString(unicode | 0x10000).substring(1);
    }

    /**
     * Reads a line from a sign change event and clears characters that can't be parsed by TC. If the line contains no
     * invalid characters, the exact same String is returned without the overhead of allocating a new String. If the line is
     * null, an empty String is returned instead. A null event will also result in an empty String.
     * 
     * @param event to get a clean line of
     * @param line  index
     * @return clean line of the sign, guaranteed to never be null or have invalid characters
     */
    public static String getCleanLine(SignChangeEvent event, int line) {
        if (event == null) {
            return "";
        } else {
            return cleanSignLine(event.getLine(line));
        }
    }

    /**
     * Reads a line from a sign and clears characters that can't be parsed by TC. If the line contains no invalid
     * characters, the exact same String is returned without the overhead of allocating a new String. If the line is null,
     * an empty String is returned instead. A null sign will also result in an empty String.
     * 
     * @param sign to get a clean line of
     * @param line index
     * @return clean line of the sign, guaranteed to never be null or have invalid characters
     */
    public static String getCleanLine(Sign sign, int line) {
        if (sign == null) {
            return "";
        } else {
            return cleanSignLine(sign.getLine(line));
        }
    }

    /**
     * Clears input of characters that can't be parsed by TC. If the line contains no invalid characters, the exact same
     * String is returned without the overhead of allocating a new String. If the line is null, an empty String is returned
     * instead.
     * 
     * @param line to parse
     * @return line cleared from invalid characters
     */
    public static String cleanSignLine(String line) {
        if (line == null) {
            return "";
        }
        for (int i = 0; i < line.length(); i++) {
            if (isInvalidCharacter(line.charAt(i))) {
                // One or more character is invalid
                // Proceed to create a new String with these characters removed
                StringBuilder clear = new StringBuilder(line.length() - 1);
                clear.append(line, 0, i);
                for (int j = i + 1; j < line.length(); j++) {
                    char c = line.charAt(j);
                    if (!isInvalidCharacter(c)) {
                        clear.append(c);
                    }
                }
                return clear.toString();
            }
        }
        return line; // no invalid characters, return input String
    }

    /**
     * Clears input sign lines of characters that can't be parsed by TC. If none of the lines contain invalid characters,
     * the exact same String[] array is returned without the overhead of allocating a new String[] array. If the input array
     * is null, or its length is not 4, it is resized so it is using a newly allocated array. The lines are guaranteed to
     * not be null.
     * 
     * @param lines to parse
     * @return lines cleared from invalid characters
     */
    public static String[] cleanSignLines(String[] lines) {
        if (lines == null) {
            return new String[] { "", "", "", "" };
        }

        // Create a new array of Strings only if one of the lines has invalid characters
        boolean hasInvalid = false;
        if (lines.length != 4) {
            hasInvalid = true;
            String[] newLines = new String[] { "", "", "", "" };
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                newLines[i] = lines[i];
            }
            lines = newLines;
        }

        // We do so using a String identity check (equals is unneeded)
        // Only when trimInvalidCharacters returns a new String do we update the input
        // array
        for (int i = 0; i < lines.length; i++) {
            String oldLine = lines[i];
            String newLine = cleanSignLine(oldLine);
            if (oldLine != newLine) {
                if (!hasInvalid) {
                    hasInvalid = true;
                    lines = lines.clone();
                }
                lines[i] = newLine;
            }
        }
        return lines;
    }

    /**
     * Checks whether a particular character is valid on TrainCarts signs. Control codes and other unsupported characters
     * return True.
     * 
     * @param c character to test
     * @return True if the character is invalid
     */
    public static boolean isInvalidCharacter(char c) {
        return Character.getType(c) == Character.PRIVATE_USE;
    }

    /**
     * Checks whether Minecraft's crappy rotation system will crap out when rotating from one angle to another
     * 
     * @param angleOld
     * @param angleNew
     * @return angle of the rotation performed
     */
    public static boolean isProtocolRotationGlitched(float angleOld, float angleNew) {
        int protOld = EntityTrackerEntryHandle.getProtocolRotation(angleOld);
        int protNew = EntityTrackerEntryHandle.getProtocolRotation(angleNew);
        return Math.abs(protNew - protOld) > 128;
    }

    /**
     * For debugging: spawns a particle at a particular location
     * 
     * @param loc      to spawn at
     * @param particle to spawn
     */
    public static void spawnParticle(Location loc, Particle particle) {
        loc.getWorld().spawnParticle(particle, loc, 1);
    }

    public static void spawnBubble(Location loc) {
        spawnParticle(loc, Particle.WATER_BUBBLE);
    }

    /**
     * Spawns a colored dust particle, the color can be specified
     * 
     * @param loc   to spawn at
     * @param color color value
     */
    public static void spawnDustParticle(Location loc, org.bukkit.Color color) {
        spawnDustParticle(loc, (double) color.getRed() / 255.0, (double) color.getGreen() / 255.0, (double) color.getBlue() / 255.0);
    }

    /**
     * Spawns a colored dust particle, the color can be specified
     * 
     * @param loc   to spawn at
     * @param red   color value [0.0 ... 1.0]
     * @param green color value [0.0 ... 1.0]
     * @param blue  color value [0.0 ... 1.0]
     */
    public static void spawnDustParticle(Location loc, double red, double green, double blue) {
        int c_red = (int) MathUtil.clamp(255.0 * red, 0.0, 255.0);
        int c_green = (int) MathUtil.clamp(255.0 * green, 0.0, 255.0);
        int c_blue = (int) MathUtil.clamp(255.0 * blue, 0.0, 255.0);
        org.bukkit.Color color = org.bukkit.Color.fromRGB(c_red, c_green, c_blue);
        Vector position = loc.toVector();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) > (256.0 * 256.0)) {
                continue;
            }
            PlayerUtil.spawnDustParticles(player, position, color);
        }
    }

    /**
     * Rotates the yaw/pitch of a Location to invert the direction it is pointing into
     * 
     * @param loc to rotate
     * @return input loc (loc is modified)
     */
    public static Location invertRotation(Location loc) {
        // TODO: Maybe this can be done without Quaternion?
        Quaternion q = Quaternion.fromYawPitchRoll(loc.getPitch(), loc.getYaw(), 0.0);
        q.rotateYFlip();
        Vector ypr_new = q.getYawPitchRoll();
        loc.setYaw((float) ypr_new.getY());
        loc.setPitch((float) ypr_new.getX());
        return loc;
    }

    // some magic to turn a vector into the most appropriate block face
    public static BlockFace vecToFace(Vector vector, boolean useSubCardinalDirections) {
        return vecToFace(vector.getX(), vector.getY(), vector.getZ(), useSubCardinalDirections);
    }

    // some magic to turn a vector into the most appropriate block face
    public static BlockFace vecToFace(double dx, double dy, double dz, boolean useSubCardinalDirections) {
        double sqlenxz = dx * dx + dz * dz;
        double sqleny = dy * dy;
        if (sqleny > (sqlenxz + 1e-6)) {
            return FaceUtil.getVertical(dy);
        } else {
            return FaceUtil.getDirection(dx, dz, useSubCardinalDirections);
        }
    }

    /**
     * Linearly interpolates an orientation 'up' vector between two stages, performing a clean rotation between the two.
     * 
     * @param up0
     * @param up1
     * @param theta
     * @return orientation up-vector at theta
     */
    public static Vector lerpOrientation(Vector up0, Vector up1, double theta) {
        Quaternion qa = Quaternion.fromLookDirection(up0);
        Quaternion qb = Quaternion.fromLookDirection(up1);
        Quaternion q = Quaternion.slerp(qa, qb, theta);
        return q.forwardVector();
    }

    /**
     * Calculates the 3 rotation angles for an armor stand pose from a Quaternion rotation
     * 
     * @param rotation
     * @return armor stand x/y/z rotation angles
     */
    public static Vector getArmorStandPose(Quaternion rotation) {
        double qx = rotation.getX();
        double qy = rotation.getY();
        double qz = rotation.getZ();
        double qw = rotation.getW();

        double rx = 1.0 + 2.0 * (-qy * qy - qz * qz);
        double ry = 2.0 * (qx * qy + qz * qw);
        double rz = 2.0 * (qx * qz - qy * qw);
        double uz = 2.0 * (qy * qz + qx * qw);
        double fz = 1.0 + 2.0 * (-qx * qx - qy * qy);

        if (Math.abs(rz) < (1.0 - 1E-15)) {
            // Standard calculation
            return new Vector(MathUtil.atan2(uz, fz), fastAsin(rz), MathUtil.atan2(-ry, rx));
        } else {
            // At the -90 or 90 degree angle singularity
            final double sign = (rz < 0) ? -1.0 : 1.0;
            return new Vector(0.0, sign * 90.0, -sign * 2.0 * MathUtil.atan2(qx, qw));
        }
    }

    /**
     * Lower-accuracy arcsin, returns an angle in degrees
     * 
     * @param x
     * @return angle
     */
    public static float fastAsin(double x) {
        return MathUtil.atan(x / Math.sqrt(1.0 - x * x));
    }

    /**
     * Calculates the next Minecart block position when going on a rail block in a particular direction. This logic is
     * largely deprecated and is only used in places where there is no alternative possible yet.
     * 
     * @param railBlock
     * @param direction
     * @return next minecart Block position, null if no such rail exists
     */
    public static Block getNextPos(Block railBlock, BlockFace direction) {
        TrackMovingPoint p = new TrackMovingPoint(railBlock, direction);
        if (!p.hasNext()) {
            return null;
        }
        p.next();
        if (!p.hasNext()) {
            return null;
        }
        p.next(false);
        return p.current;
    }

    /**
     * Marks a chunk as dirty, so that it is saved again when it unloads
     * 
     * @param chunk
     */
    public static final void markChunkDirty(Chunk chunk) {
        ChunkHandle.fromBukkit(chunk).markDirty();
    }

    /**
     * Attempts to find the most appropriate junction for a BlockFace wind direction. This is used when switcher signs have
     * to switch rails based on wind directions, but no wind direction names are used for the junction names. This is also
     * used for sign-relative left/right/forward/backward logic, which is first turned into a BlockFace.
     * 
     * @param junctions to select from
     * @param face      to find
     * @return the best matching junction, null if not found
     */
    public static RailJunction faceToJunction(List<RailJunction> junctions, BlockFace face) {
        for (RailJunction junc : junctions) {
            if (junc.position().getMotionFace() == face) {
                return junc;
            }
        }
        return null;
    }

    /**
     * Checks for a 'contents' field in the configuration, and if it exists, loads all items contained within. The inventory
     * is wiped beforehand.
     * 
     * @param inventory
     * @param config
     */
    public static void loadInventoryFromConfig(Inventory inventory, ConfigurationNode config) {
        inventory.clear();
        if (config.isNode("contents")) {
            ConfigurationNode contents = config.getNode("contents");
            for (String indexStr : contents.getKeys()) {
                int index;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (NumberFormatException ex) {
                    continue;
                }
                ItemStack item = contents.get(indexStr, ItemStack.class);
                if (!ItemUtil.isEmpty(item)) {
                    inventory.setItem(index, item.clone());
                }
            }
        }
    }

    /**
     * Saves all items in the inventory to the configuration under a 'contents' field. If the inventory is empty, nothing is
     * saved.
     * 
     * @param inventory
     * @param config
     */
    public static void saveInventoryToConfig(Inventory inventory, ConfigurationNode config) {
        ConfigurationNode contents = null;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (!ItemUtil.isEmpty(item)) {
                if (contents == null) {
                    contents = config.getNode("contents");
                }
                contents.set(Integer.toString(i), item.clone());
            }
        }
    }

    /**
     * Sets the value of a vector to that of another
     * 
     * @param v  to set
     * @param v2 to set the x/y/z values to
     */
    public static void setVector(Vector v, Vector v2) {
        v.setX(v2.getX());
        v.setY(v2.getY());
        v.setZ(v2.getZ());
    }

    /**
     * Checks whether the orientation quaternion q is inverted compared to forward velocity vel
     * 
     * @param vel
     * @param q
     * @return True if orientation q is inverted from vel
     */
    public static boolean isOrientationInverted(Vector vel, Quaternion q) {
        double x = q.getX();
        double y = q.getY();
        double z = q.getZ();
        double w = q.getW();
        double px = vel.getX();
        double py = vel.getY();
        double pz = vel.getZ();
        return (((px * (x * z + y * w) + py * (y * z - x * w) - pz * (x * x + y * y - 0.5))) <= 0.0);
    }

    /**
     * Retrieves just the yaw angle from the Quaternion getYawPitchRoll function. Saves a little on computation.
     * 
     * @param rotation
     * @return yaw angle
     */
    public static double fastGetRotationYaw(Quaternion rotation) {
        double x = rotation.getX();
        double y = rotation.getY();
        double z = rotation.getZ();
        double w = rotation.getW();

        double yaw;
        final double test = 2.0 * (w * x - y * z);
        if (Math.abs(test) < (1.0 - 1E-15)) {
            double x2 = x * x;
            double y2 = y * y;
            double z2 = z * z;

            // Standard angle
            yaw = MathUtil.atan2(2.0 * (w * y + z * x), 1.0 - 2.0 * (x2 + y2));

            // Wrap around yaw when roll exceeds a limit
            if ((x2 + z2) > 0.5) {
                yaw += (yaw < 0.0) ? 180.0 : -180.0;
            }
        } else {
            // This is at the pitch=90.0 or pitch=-90.0 singularity
            // All we can do is yaw (or roll) around the vertical axis
            yaw = 2.0 * MathUtil.atan2(z, w);
            if (test >= 0.0) {
                yaw = -yaw;
            }
        }

        // Wrap yaw angle between -180 and 180 degrees
        if (yaw > 180.0) {
            yaw -= 360.0;
        } else if (yaw < -180.0) {
            yaw += 360.0;
        }

        return -yaw;
    }

    /**
     * Checks whether a method is called from a thread other than the main thread.
     * 
     * @param what descriptor what was called
     */
    public static void checkMainThread(String what) {
        if (!CommonUtil.isMainThread()) {
            TrainCarts.plugin.log(Level.WARNING, what + " called from a thread other than the main thread!");
            Thread.dumpStack();
        }
    }

    /**
     * Adjusts the teleport position to avoid an entity getting glitched in a block. The player is teleported upwards any
     * block they are currently inside of with their feet.
     * 
     * @param loc to correct
     */
    public static void correctTeleportPosition(Location loc) {
        Block locBlock = loc.getBlock();
        Vector rel = loc.toVector();
        rel.setX(rel.getX() - locBlock.getX());
        rel.setY(rel.getY() - locBlock.getY());
        rel.setZ(rel.getZ() - locBlock.getZ());
        AxisAlignedBBHandle bounds = WorldUtil.getBlockData(locBlock).getBoundingBox(locBlock);
        if (bounds != null /* AIR */ && rel.getX() >= bounds.getMinX() && rel.getX() <= bounds.getMaxX() && rel.getY() >= bounds.getMinY() && rel.getY() <= bounds.getMaxY()
                && rel.getZ() >= bounds.getMinZ() && rel.getZ() <= bounds.getMaxZ()) {
            loc.setY(locBlock.getY() + bounds.getMaxY() + 1e-5);
        }
    }

    /**
     * Adds protocol-limited rotation steps the yaw of an entity to rotate in accordance of the requested change in yaw.
     * 
     * @param old_yaw
     * @param yaw_change
     * @return new entity yaw
     */
    public static float getNextEntityYaw(float old_yaw, double yaw_change) {
        // When change is too large, do not use entity yaw for it, snap pose instead
        if (yaw_change < -90.0 || yaw_change > 90.0) {
            return old_yaw;
        }

        int prot_yaw_rot_old = EntityTrackerEntryHandle.getProtocolRotation(old_yaw);
        int prot_yaw_rot_new = EntityTrackerEntryHandle.getProtocolRotation((float) (old_yaw + yaw_change));
        if (prot_yaw_rot_new != prot_yaw_rot_old) {

            // Do not change entity yaw to beyond the angle requested
            // This causes the pose yaw angle to compensate, which looks very twitchy
            float new_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
            double new_yaw_change = MathUtil.wrapAngle((double) new_yaw - (double) old_yaw);
            if (yaw_change < 0.0) {
                if (new_yaw_change < yaw_change) {
                    prot_yaw_rot_new++;
                    new_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
                }
            } else {
                if (new_yaw_change > yaw_change) {
                    prot_yaw_rot_new--;
                    new_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
                }
            }

            // Has a change in protocol yaw value, accept the changes
            return new_yaw;
        }
        return old_yaw;
    }

    /**
     * Turns a double value into the text displayed in a number box, limited to 4
     * decimals
     * 
     * @param value
     * @return number box text value
     */
    public static String stringifyNumberBoxValue(double value) {
        return numberBox_NumberFormat.format(value);
    }

    /**
     * Turns a number value into a length-limited (4 digits) decimal value
     * 
     * @param time
     * @return stringified time
     */
    public static String stringifyAnimationNodeTime(double time) {
        if (time >= 9999) {
            return "9999";
        } else if (time >= 999.95) {
            return animationodeTime_NumberFormat1000.format(time);
        } else if (time >= 99.995) {
            return animationodeTime_NumberFormat100.format(time);
        } else if (time >= 9.9995) {
            return animationodeTime_NumberFormat10.format(time);
        } else if (time >= 0.0005) {
            return animationodeTime_NumberFormat1.format(time);
        } else {
            return "0.0";
        }
    }

    /**
     * Checks whether a given direction motion vector is sub-cardinal,
     * pointing into a diagonal and not along a single axis.
     * The y-axis is ignored.
     * 
     * @param direction
     * @return True if diagonal
     */
    public static boolean isDiagonal(Vector direction) {
        double sq_x = direction.getX() * direction.getX();
        double sq_z = direction.getZ() * direction.getZ();
        double sq_xz = sq_x + sq_z;
        return sq_xz >= 1e-10 &&
               (sq_x / sq_xz) < SQ_COS_22_5 &&
               (sq_z / sq_xz) < SQ_COS_22_5;
    }
    private static final double SQ_COS_22_5 = Math.pow(Math.cos(Math.PI / 8.0), 2.0);

    /**
     * Check if a rails block connects with another rails block in the direction specified
     *
     * @param rails The rails block and type to connect from
     * @param direction to connect to
     * @return True if connected, False if not
     */
    public static boolean isConnectedRails(RailPiece rails, BlockFace direction) {
        // Check not invalid
        if (rails == null || rails.block() == null) {
            return false;
        }

        // Move from the current rail minecart position one block into the direction
        // Check if a rail exists there. If there is, check if it points at this rail
        // If so, then there is a rails there!
        RailJunction junction = Util.faceToJunction(rails.type().getJunctions(rails.block()), direction);
        if (junction == null) {
            return false;
        }
        RailState state = rails.type().takeJunction(rails.block(), junction);
        if (state == null) {
            return false;
        }

        // Move backwards again from the rails found
        state.setMotionVector(state.motionVector().multiply(-1.0));
        state.initEnterDirection();
        TrackWalkingPoint wp = new TrackWalkingPoint(state);
        wp.skipFirst();
        if (!wp.moveFull()) {
            return false;
        }

        // Verify this is the Block we came from
        return wp.state.railType() == rails.type() &&
               wp.state.railBlock().equals(rails.block());
    }

    public static boolean isUpsideDownRailSupport(Block block) {
        BlockData blockdata = WorldUtil.getBlockData(block);

        // Shortcut for most common case
        if (blockdata == BlockData.AIR) {
            return false;
        }

        // Fully solid (suffocating) blocks are always supports
        if (blockdata.isSuffocating(block)) {
            return true;
        }

        // Configurable: do we allow all blocks with a hitbox to support upside-down rails?
        if (TCConfig.upsideDownSupportedByAll && blockdata.canSupportOnFace(block, BlockFace.DOWN)) {
            return true;
        }

        return false;
    }

    /**
     * Default value of the displayed block offset.
     * Might be different on some server versions, was 9 at some point apparently.
     *
     * @return default displayed block offset
     */
    public static int getDefaultDisplayedBlockOffset() {
        return 6;
    }

    public static Optional<Set<String>> getConfigStringSetOptional(ConfigurationNode config, String key) {
        if (config.contains(key)) {
            List<String> configList = config.getList(key, String.class);
            Set<String> resultSet = new HashSet<String>(configList);
            return Optional.of(Collections.unmodifiableSet(resultSet));
        } else {
            return Optional.empty();
        }
    }

    public static Optional<List<String>> getConfigStringListOptional(ConfigurationNode config, String key) {
        if (config.contains(key)) {
            List<String> configList = config.getList(key, String.class);
            List<String> listCopy = new ArrayList<String>(configList);
            return Optional.of(Collections.unmodifiableList(listCopy));
        } else {
            return Optional.empty();
        }
    }

    public static void setConfigStringCollectionOptional(ConfigurationNode config, String key, Optional<? extends Collection<String>> value) {
        if (value.isPresent()) {
            //TODO: Use ItemSynchronizer.identity()
            LogicUtil.synchronizeList(
                    config.getList(key, String.class),
                    value.get(),
                    new ItemSynchronizer<String, String>() {
                        @Override
                        public boolean isItem(String item, String value) {
                            return Objects.equals(item, value);
                        }

                        @Override
                        public String onAdded(String value) {
                            return value;
                        }

                        @Override
                        public void onRemoved(String item) {
                        }
                    }
            );
        } else {
            config.remove(key);
        }
    }

    /**
     * Uses {@link ConfigurationNode#get(String, Class)} when the value is contained in the
     * configuration node, otherwise returns empty if the value does not exist or is of an
     * incompatible type.
     * 
     * @param <T> Value type
     * @param config Configuration to read from
     * @param key Key to read from
     * @param type Type of value to get
     * @return read value as an optional, or {@link Optional#empty()}
     */
    public static <T> Optional<T> getConfigOptional(ConfigurationNode config, String key, Class<T> type) {
        if (config.contains(key)) {
            return Optional.ofNullable(config.get(key, type, null));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Uses {@link ConfigurationNode#set(String, Object)} when the value {@link Optional#isPresent()},
     * otherwise uses {@link ConfigurationNode#remove(String)} to remove the key. Automatically
     * removes empty nodes that result from removing.
     * 
     * @param config Configuration to update
     * @param key Key to update
     * @param value New value to set
     */
    public static void setConfigOptional(ConfigurationNode config, String key, Optional<?> value) {
        if (value.isPresent()) {
            config.set(key, value.get());
        } else if (config.contains(key)) {
            config.remove(key);

            // Clean up parent nodes part of the key that have no children
            YamlPath parentYamlPath = YamlPath.create(key).parent();
            while (parentYamlPath != YamlPath.ROOT) {
                String parentPath = parentYamlPath.toString();
                if (!config.isNode(parentPath)) {
                    break;
                }

                ConfigurationNode parent = config.getNode(parentPath);
                if (!parent.isEmpty()) {
                    break;
                }

                parent.remove();
                parentYamlPath = parentYamlPath.parent();
            }
        }
    }
}

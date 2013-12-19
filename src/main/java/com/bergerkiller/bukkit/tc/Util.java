package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Stairs;
import org.bukkit.util.Vector;

import com.avaje.ebeaninternal.server.deploy.BeanDescriptor.EntityType;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.MaterialTypeProperty;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.reflection.FieldAccessor;
import com.bergerkiller.bukkit.common.reflection.MethodAccessor;
import com.bergerkiller.bukkit.common.reflection.SafeField;
import com.bergerkiller.bukkit.common.reflection.SafeMethod;
import com.bergerkiller.bukkit.common.reflection.classes.BlockRef;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.properties.IParsable;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.AveragedItemParser;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

public class Util {
	public static final MaterialTypeProperty ISVERTRAIL = new MaterialTypeProperty(Material.LADDER);
	public static final MaterialTypeProperty ISTCRAIL = new MaterialTypeProperty(ISVERTRAIL, MaterialUtil.ISRAILS, MaterialUtil.ISPRESSUREPLATE);
	private static final String SEPARATOR_REGEX = "[|/\\\\]";
	private static final FieldAccessor<Object> blockMaterial = BlockRef.TEMPLATE.getField("material");
	private static final MethodAccessor<Boolean> materialBuildable = new SafeMethod<Boolean>(Common.NMS_ROOT + ".Material.isBuildable");
	private static final FieldAccessor<Integer> itemMaxSizeField = new SafeField<Integer>(CommonUtil.getNMSClass("Item"), "maxStackSize");

	public static void setItemMaxSize(Material material, int maxstacksize) {
		itemMaxSizeField.set(Conversion.toItemHandle.convert(material), maxstacksize);
	}

	/**
	 * Splits a text into separate parts delimited by the separator characters
	 * 
	 * @param text to split
	 * @param limit of the split text
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

	private static BlockFace[] possibleFaces = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};
	private static List<Block> blockbuff = new ArrayList<Block>();
	public static List<Block> getSignsFromRails(Block railsblock) {
		return getSignsFromRails(blockbuff, railsblock);
	}
	
	public static List<Block> getSignsFromRails(List<Block> rval, Block railsblock) {
		rval.clear();
		addSignsFromRails(rval, railsblock);
		return rval;
	}

	public static void addSignsFromRails(List<Block> rval, Block railsBlock) {
		BlockFace dir = RailType.getType(railsBlock).getSignColumnDirection(railsBlock);
		// Has sign support at all?
		if (dir == null || dir == BlockFace.SELF) {
			return;
		}
		addSignsFromRails(rval, railsBlock, dir);
	}

	public static void addSignsFromRails(List<Block> rval, Block railsBlock, BlockFace signDirection) {
		final boolean hasSignPost = FaceUtil.isVertical(signDirection);

		// Ignore mid-sections
		Block currentBlock = railsBlock.getRelative(signDirection);
		addAttachedSigns(currentBlock, rval);
		currentBlock = currentBlock.getRelative(signDirection);
		// Keep going into the sign direction
		while (true) {
			if (hasSignPost && MaterialUtil.isType(currentBlock, Material.SIGN_POST)) {
				// Found a sign post - add it and continue
				rval.add(currentBlock);
			} else if (!addAttachedSigns(currentBlock, rval)) {
				// No wall signs found either - end it here
				break;
			}
			currentBlock = currentBlock.getRelative(signDirection);
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

	public static Block getRailsFromSign(Block signblock) {
		if (signblock == null) {
			return null;
		}

		final Material type = signblock.getType();
		final Block mainBlock;
		if (type == Material.WALL_SIGN) {
			mainBlock = BlockUtil.getAttachedBlock(signblock);
		} else if (type == Material.SIGN_POST) {
			mainBlock = signblock;
		} else {
			return null;
		}
		boolean hasSigns;
		for (BlockFace dir : possibleFaces) {
			Block block = mainBlock;
			hasSigns = true;
			while (true) {
				// Go to the next block
				block = block.getRelative(dir);

				// Check for rails
				BlockFace columnDir = RailType.getType(block).getSignColumnDirection(block);
				if (dir == columnDir.getOppositeFace()) {
					return block;
				}

				// End of the loop?
				if (!hasSigns) {
					break;
				}

				// Go to the next block
				hasSigns = hasAttachedSigns(block);
			}
		}
		return null;
	}

	public static Block findRailsVertical(Block from, BlockFace mode) {
		int sy = from.getY();
		int x = from.getX();
		int z = from.getZ();
		World world = from.getWorld();
		if (mode == BlockFace.DOWN) {
			for (int y = sy - 1; y > 0; --y) {
				if (ISTCRAIL.get(world, x, y, z)) {
					return world.getBlockAt(x, y, z);
				}
			}
		} else if (mode == BlockFace.UP) {
			int height = world.getMaxHeight();
			for (int y = sy + 1; y < height; y++) {
				if (ISTCRAIL.get(world, x, y, z)) {
					return world.getBlockAt(x, y, z);
				}
			}
		}
		return null;
	}

	public static ItemParser[] getParsers(String... items) {
		return getParsers(StringUtil.join(";", items));
	}

	public static ItemParser[] getParsers(final String items) {
		List<ItemParser> parsers = new ArrayList<ItemParser>();
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
			// Obtain the item parsers for this type and amount, apply a possible multiplier
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
	 * This will return:
	 * South or west if it's a straight piece
	 * Self if it is a cross-intersection
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
	 * @param rails to check
	 * @param direction of the vertical rail
	 * @return True if a vertical rail is above, False if not
	 */
	public static boolean isVerticalAbove(Block rails, BlockFace direction) {
		Block above = rails.getRelative(BlockFace.UP);
		return Util.ISVERTRAIL.get(above) && getVerticalRailDirection(above) == direction;
	}

	/**
	 * Gets the direction a vertical rail pushes the minecart (the wall side)
	 * 
	 * @param block of the vertical rail
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
		if (elements == null|| elements.length == 0) {
			return true;
		}
		int index = 0;
		boolean has = true;
		boolean first = true;
		for (int i = 0; i < elements.length; i++) {
			if (elements[i].length() == 0) continue;
			index = text.indexOf(elements[i], index);
			if (index == -1 || (first && !firstAny && index != 0)) {
				has = false;
				break;
			} else {
				index += elements[i].length();
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
			return false; //no valid input
		}
		int idx = getOperatorIndex(text);
		if (idx == -1) {
			return value > 0; //no operators, just perform a 'has'
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

	public static boolean canInstantlyBuild(Entity entity) {
		return entity instanceof HumanEntity && EntityUtil.getAbilities((HumanEntity) entity).canInstantlyBuild();
	}

	/**
	 * Checks whether a Block supports placement/attachment of solid blocks on a particular face.
	 * Note that signs do not use this logic - they allow pretty much any sort of attachment.
	 * 
	 * @param block to check
	 * @param face to check
	 * @return True if supported, False if not
	 */
	public static boolean isSupportedFace(Block block, BlockFace face) {
		Material type = block.getType();
		if (MaterialUtil.ISSOLID.get(type)) {
			return true;
		}
		// Special block types that only support one face at a time
		int rawData = MaterialUtil.getRawData(block);
		MaterialData data = BlockUtil.getData(type, rawData);

		// Steps only support TOP or BOTTOM
		if (MaterialUtil.isType(type, Material.WOOD_STEP, Material.STEP)) {
			return face == FaceUtil.getVertical((rawData & 0x8) == 0x8);
		}

		// Stairs only support the non-exit side + the up/down
		if (data instanceof Stairs) {
			if (FaceUtil.isVertical(face)) {
				return face == FaceUtil.getVertical(((Stairs) data).isInverted());
			} else {
				// For some strange reason...stairs don't support attachments to the back
				//return face == ((Stairs) data).getFacing().getOppositeFace();
				return false;
			}
		}

		// Unsupported/unknown Block
		return false;
	}

	public static boolean isSupported(Block block) {
		BlockFace attachedFace = BlockUtil.getAttachedFace(block);
		Block attached = block.getRelative(attachedFace);
		if (MaterialUtil.ISSIGN.get(block)) {
			// Only check the 'isBuildable' state of the Material
			Object attachedHandle = Conversion.toBlockHandle.convert(attached);
			if (attachedHandle == null) {
				return false;
			}
			Object material = blockMaterial.get(attachedHandle);
			if (material == null) {
				return false;
			}
			return materialBuildable.invoke(material);
		}
		// For all other cases, check whether the side is properly supported
		return isSupportedFace(attached, attachedFace.getOppositeFace());
	}

	public static boolean isValidEntity(String entityName) {
		try {
			return EntityType.valueOf(entityName) != null;
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
		if (offset.length() > TrainCarts.maxEjectDistance) {
			offset.normalize().multiply(TrainCarts.maxEjectDistance);
		}
		return offset;
	}

	public static boolean parseProperties(IParsable properties, String key, String args) {
		IProperties prop;
		IPropertiesHolder holder;
		if (properties instanceof IPropertiesHolder) {
			holder = ((IPropertiesHolder) properties);
			prop = holder.getProperties();
		} else if (properties instanceof IProperties) {
			prop = (IProperties) properties;
			holder = prop.getHolder();
		} else {
			return false;
		}
		if (holder == null) {
			return prop.parseSet(key, args);
		} else if (prop.parseSet(key, args) || holder.parseSet(key, args))  {
			holder.onPropertiesChanged();
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Obtains the maximum straight length achieved from a particular block.
	 * This length is limited to 20 blocks.
	 * 
	 * @param railsBlock to calculate from
	 * @param direction to look into, use SELF to check all possible directions
	 * @return straight length
	 */
	public static double calculateStraightLength(Block railsBlock, BlockFace direction) {
		// Read track information and parameters
		RailType type = RailType.getType(railsBlock);
		boolean diagonal = FaceUtil.isSubCardinal(type.getDirection(railsBlock));
		final BlockFace[] toCheck;
		if (direction == BlockFace.SELF) {
			toCheck = type.getPossibleDirections(railsBlock);
		} else {
			toCheck = new BlockFace[] {direction};
		}
		double length = 0.0;
		TrackIterator iter = new TrackIterator(null, null, 20, false);

		// Check all directions
		for (BlockFace face : toCheck) {
			double trackLength = 0.0;
			iter.reset(railsBlock, face);
			// Skip the start block, abort if no start block was found
			if (iter.hasNext()) {
				iter.next();
			} else {
				continue;
			}
			// Two modes: diagonal and straight
			if (diagonal) {
				// Diagonal mode
				BlockFace lastFace = null;
				int lastAngle = Integer.MAX_VALUE;
				while (iter.hasNext()) {
					iter.next();
					// Check that the direction alternates
					if (lastFace == null) {
						// Start block: store it's information
						lastFace = iter.currentDirection();
					} else {
						BlockFace newFace = iter.currentDirection();
						int newAngle = MathUtil.wrapAngle(FaceUtil.faceToYaw(newFace) - FaceUtil.faceToYaw(lastFace));
						if (Math.abs(newAngle) != 90) {
							// Not a 90-degree angle!
							break;
						}
						if (lastAngle != Integer.MAX_VALUE && newAngle != -lastAngle) {
							// Not the exact opposite from last time
							break;
						}
						lastFace = newFace;
						lastAngle = newAngle;
					}
					trackLength += MathUtil.HALFROOTOFTWO;
				}
			} else {
				// Straight mode
				while (iter.hasNext()) {
					iter.next();
					// Check that the direction stays the same
					if (iter.currentDirection() != face) {
						break;
					}
					trackLength++;
				}
			}
			// Update the length
			if (trackLength > length) {
				length = trackLength;
			}
		}
		return length;
	}
}
